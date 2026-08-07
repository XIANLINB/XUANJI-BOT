package dev.xuanji.core.storage;

import dev.xuanji.api.plugin.PluginStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 插件持久化服务 — 包装 {@link PluginKvStore}，按插件 id 返回隔离的 {@link PluginStorage} 视图。
 *
 * <p>由 {@code CommandRegistry} 在方法参数解析时自动注入（插件方法声明
 * {@code PluginStorage} 参数即可使用），同一插件所有命令共享命名空间。
 */
@Component
@RequiredArgsConstructor
public class PluginStorageService {

    private final PluginKvStore kv;

    /** 返回指定插件的存储视图（线程安全，可跨线程使用）。 */
    public PluginStorage view(String pluginId) {
        return new PluginStorage() {
            @Override
            public String getString(String key, String defaultValue) {
                return kv.get(pluginId, key).orElse(defaultValue);
            }

            @Override
            public long getLong(String key, long defaultValue) {
                String v = kv.get(pluginId, key).orElse(null);
                if (v == null) return defaultValue;
                try {
                    return Long.parseLong(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }

            @Override
            public int getInt(String key, int defaultValue) {
                String v = kv.get(pluginId, key).orElse(null);
                if (v == null) return defaultValue;
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                String v = kv.get(pluginId, key).orElse(null);
                if (v == null) return defaultValue;
                return v.equalsIgnoreCase("true") || v.equals("1");
            }

            @Override
            public void set(String key, String value) {
                kv.put(pluginId, key, value);
            }

            @Override
            public void remove(String key) {
                kv.remove(pluginId, key);
            }
        };
    }
}
