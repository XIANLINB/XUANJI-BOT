package dev.xuanji.core.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 插件 KV 存储 — 每个插件一个命名空间，读写在 xuanji_plugin_kv 表。
 */
@Component
@RequiredArgsConstructor
public class PluginKvStore {

    private final JdbcTemplate jdbc;

    public Optional<String> get(String pluginId, String key) {
        var rows = jdbc.query(
                "SELECT kv_value FROM xuanji_plugin_kv WHERE plugin_id=? AND kv_key=?",
                (rs, i) -> rs.getString("kv_value"), pluginId, key);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    public void put(String pluginId, String key, String value) {
        jdbc.update("""
            MERGE INTO xuanji_plugin_kv (plugin_id, kv_key, kv_value, updated_at)
            KEY (plugin_id, kv_key) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, pluginId, key, value);
    }

    public void remove(String pluginId, String key) {
        jdbc.update("DELETE FROM xuanji_plugin_kv WHERE plugin_id=? AND kv_key=?", pluginId, key);
    }
}
