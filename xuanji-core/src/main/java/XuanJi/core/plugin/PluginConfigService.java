package XuanJi.core.plugin;

import XuanJi.api.plugin.PluginConfig;
import XuanJi.api.plugin.PluginConfigField;
import XuanJi.api.plugin.PluginConfigProvider;
import XuanJi.core.storage.PluginKvStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件配置服务 — 维护各插件声明的配置 schema，提供插件侧读取与控制台侧读写。
 *
 * <p><b>读取优先级</b>：控制台配置值（{@code xuanji_plugin_kv}） &gt;
 * 插件 {@link PluginConfigProvider#configSchema()} 声明的 defaultValue &gt; 代码兜底参数。
 *
 * <p>schema 在插件加载（{@link XuanJiPluginManager#registerIfPossible}）时注册、
 * 卸载时注销；控制台经 {@code /console/plugins/{id}/config-schema|config} 读写。
 */
@Component
public class PluginConfigService {

    private final PluginKvStore kv;
    private final Map<String, List<PluginConfigField>> schemas = new ConcurrentHashMap<>();

    public PluginConfigService(PluginKvStore kv) {
        this.kv = kv;
    }

    /** 插件加载时注册配置 schema（插件实例实现了 {@link PluginConfigProvider}）。 */
    public void register(String pluginId, PluginConfigProvider provider) {
        if (provider == null || provider.configSchema() == null) return;
        schemas.put(pluginId, new ArrayList<>(provider.configSchema()));
    }

    /** 插件卸载时注销 schema。 */
    public void unregister(String pluginId) {
        schemas.remove(pluginId);
    }

    /** 插件声明的配置字段列表（未声明返回空）。 */
    public List<PluginConfigField> schema(String pluginId) {
        return schemas.getOrDefault(pluginId, List.of());
    }

    /** 返回插件侧配置读取视图（自动注入用）。 */
    public PluginConfig view(String pluginId) {
        List<PluginConfigField> sc = schema(pluginId);
        return new PluginConfig() {
            @Override
            public String getString(String key, String defaultValue) {
                String v = kv.get(pluginId, key).orElse(null);
                if (v != null) return v;
                return defaultOf(sc, key, defaultValue);
            }

            @Override
            public long getLong(String key, long defaultValue) {
                String v = getString(key, null);
                if (v == null) return defaultValue;
                try {
                    return Long.parseLong(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }

            @Override
            public int getInt(String key, int defaultValue) {
                String v = getString(key, null);
                if (v == null) return defaultValue;
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                String v = getString(key, null);
                if (v == null) return defaultValue;
                return v.equalsIgnoreCase("true") || v.equals("1");
            }
        };
    }

    /**
     * 控制台表单回显：schema 默认值 + DB 已配置值合并（DB 覆盖默认）。
     * 仅返回 schema 声明过的 key，避免插件内部临时 key 污染表单。
     */
    public Map<String, String> configMap(String pluginId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (PluginConfigField f : schema(pluginId)) {
            out.put(f.key(), f.defaultValue());
        }
        Map<String, String> stored = kv.list(pluginId);
        for (PluginConfigField f : schema(pluginId)) {
            String v = stored.get(f.key());
            if (v != null) out.put(f.key(), v);
        }
        return out;
    }

    /** 保存控制台提交的配置（只落 schema 声明过的 key，非法值忽略）。 */
    public void saveConfig(String pluginId, Map<String, String> values) {
        if (values == null) return;
        List<PluginConfigField> sc = schema(pluginId);
        for (Map.Entry<String, String> e : values.entrySet()) {
            boolean declared = sc.stream().anyMatch(f -> f.key().equals(e.getKey()));
            if (declared && e.getValue() != null) {
                kv.put(pluginId, e.getKey(), e.getValue());
            }
        }
    }

    private static String defaultOf(List<PluginConfigField> sc, String key, String fallback) {
        for (PluginConfigField f : sc) {
            if (f.key().equals(key)) return f.defaultValue();
        }
        return fallback;
    }
}
