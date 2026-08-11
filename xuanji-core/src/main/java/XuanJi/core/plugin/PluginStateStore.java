package XuanJi.core.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 插件启用态持久化 — 框架域表 {@code xuanji_plugin_state}（全局一份）。
 *
 * <p>记录每个插件的启用/停用状态，重启后由 {@link XuanJiPluginManager} 读取并恢复，
 * 实现「停用某个插件后重启仍然停用」。
 */
@Slf4j(topic = "xuanji.plugin")
@Component
public class PluginStateStore {

    private final JdbcTemplate jdbc;

    public PluginStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
    }

    private void ensureTable() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS xuanji_plugin_state (" +
                    "plugin_id VARCHAR(255) PRIMARY KEY, " +
                    "enabled BOOLEAN NOT NULL, " +
                    "updated_at TIMESTAMP)");
        } catch (Exception e) {
            log.warn("[Plugin] 创建 xuanji_plugin_state 失败: {}", e.getMessage());
        }
    }

    /**
     * 插件是否启用。
     *
     * <p><b>未记录时默认停用</b>：新加入的插件默认不启用，需在插件市场手动启用，
     * 避免新插件一进来就抢占命令/行为不可预期。
     */
    public boolean isEnabled(String pluginId) {
        try {
            Boolean e = jdbc.queryForObject(
                    "SELECT enabled FROM xuanji_plugin_state WHERE plugin_id = ?",
                    Boolean.class, pluginId);
            return e != null && e;
        } catch (EmptyResultDataAccessException ex) {
            return false;
        } catch (Exception e) {
            log.warn("[Plugin] 读取插件状态失败 {}: {}", pluginId, e.getMessage());
            return false;
        }
    }

    public void setEnabled(String pluginId, boolean enabled) {
        jdbc.update(
                "MERGE INTO xuanji_plugin_state (plugin_id, enabled, updated_at) KEY(plugin_id) VALUES (?, ?, ?)",
                pluginId, enabled, Timestamp.from(Instant.now()));
    }

    public List<PluginStateRow> list() {
        try {
            return jdbc.query(
                    "SELECT plugin_id, enabled FROM xuanji_plugin_state",
                    (rs, i) -> new PluginStateRow(rs.getString(1), rs.getBoolean(2)));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 删除某插件的持久记录（彻底移除时调用） */
    public void delete(String pluginId) {
        jdbc.update("DELETE FROM xuanji_plugin_state WHERE plugin_id = ?", pluginId);
    }

    public record PluginStateRow(String pluginId, boolean enabled) {}
}
