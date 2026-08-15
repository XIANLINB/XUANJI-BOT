package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.PluginData;
import XuanJi.core.storage.PluginStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PluginDataProvider} 默认实现：
 * <ul>
 *   <li>加载期：扫描实体 → 单表硬约束 → 建表/迁移 → 登记 → 缓存 {@link PluginDataImpl}。</li>
 *   <li>运行期：按 pluginId 提供隔离的 {@link PluginData}（命令/定时任务注入）。</li>
 * </ul>
 */
@Slf4j(topic = "xuanji.plugin.storage")
@Component
public class PluginDataProviderImpl implements PluginDataProvider {

    private final JdbcTemplate jdbc;
    private final PluginEntityScanner scanner;
    private final PluginSchemaGenerator schema;
    private final PluginStorageService storageService;
    private final Map<String, PluginDataImpl> registry = new ConcurrentHashMap<>();

    public PluginDataProviderImpl(JdbcTemplate jdbc, PluginEntityScanner scanner,
                                 PluginSchemaGenerator schema, PluginStorageService storageService) {
        this.jdbc = jdbc;
        this.scanner = scanner;
        this.schema = schema;
        this.storageService = storageService;
    }

    @Override
    public PluginData scanAndRegister(String pluginId, ClassLoader pluginClassLoader) {
        // 框架登记/配额表（幂等）
        schema.createFrameworkTables(jdbc);

        List<EntityMetadata> metas = scanner.scan(pluginId, pluginClassLoader);
        if (metas.size() > 1) {
            throw new PluginEntityScanner.PluginStructureException(
                    "插件 " + pluginId + " 声明了 " + metas.size() + " 个 @PluginEntity，最多允许 1 个（单表硬约束）");
        }

        for (EntityMetadata m : metas) {
            String oldHash = schema.registeredHash(jdbc, pluginId, m.physicalTable);
            if (oldHash == null) {
                schema.createTable(jdbc, m);
            } else if (!oldHash.equals(m.columnsHash)) {
                schema.migrate(jdbc, m);
            }
        }

        PluginDataImpl pd = new PluginDataImpl(pluginId, metas, jdbc, schema, storageService);
        registry.put(pluginId, pd);
        log.info("[Storage] 插件 {} 结构化存储就绪（实体数 {}）", pluginId, metas.size());
        return pd;
    }

    @Override
    public PluginData get(String pluginId) {
        return registry.get(pluginId);
    }

    @Override
    public void unregister(String pluginId) {
        registry.remove(pluginId);
        log.debug("[Storage] 已清理插件 {} 的存储元数据缓存", pluginId);
    }
}
