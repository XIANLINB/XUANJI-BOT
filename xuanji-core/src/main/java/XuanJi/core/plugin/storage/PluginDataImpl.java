package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.PluginData;
import XuanJi.api.plugin.PluginRepository;
import XuanJi.api.plugin.PluginStorage;
import XuanJi.core.storage.PluginStorageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PluginData} 默认实现：绑定到单个插件（pluginId），按实体类缓存
 * {@link PluginRepositoryImpl}。{@code repo()} 只返回本插件已声明实体的仓储，
 * 越界访问直接抛错（天然隔离）。
 */
public class PluginDataImpl implements PluginData {

    private final String pluginId;
    private final Map<Class<?>, EntityMetadata> metaByClass;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final PluginSchemaGenerator schema;
    private final PluginStorageService storageService;
    private final Map<Class<?>, PluginRepository<?>> repoCache = new ConcurrentHashMap<>();

    public PluginDataImpl(String pluginId, List<EntityMetadata> metas,
                         org.springframework.jdbc.core.JdbcTemplate jdbc,
                         PluginSchemaGenerator schema, PluginStorageService storageService) {
        this.pluginId = pluginId;
        this.jdbc = jdbc;
        this.schema = schema;
        this.storageService = storageService;
        this.metaByClass = new HashMap<>();
        for (EntityMetadata m : metas) metaByClass.put(m.entityClass, m);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> PluginRepository<T> repo(Class<T> entityClass) {
        EntityMetadata m = metaByClass.get(entityClass);
        if (m == null) {
            throw new IllegalArgumentException("插件 " + pluginId + " 未声明实体 "
                    + entityClass.getName() + "（请用 @PluginEntity 标注并确保在插件 classpath 内）");
        }
        return (PluginRepository<T>) repoCache.computeIfAbsent(entityClass,
                k -> new PluginRepositoryImpl<>(m, jdbc, schema));
    }

    @Override
    public PluginStorage storage() {
        return storageService != null ? storageService.view(pluginId) : null;
    }
}
