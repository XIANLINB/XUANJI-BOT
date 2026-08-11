package XuanJi.core.plugin;

import XuanJi.core.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件-机器人绑定实现 — 持久化于框架库 {@code xuanji_plugin_binding} 表。
 */
@Slf4j
@Repository
public class PluginBotBindingServiceImpl implements PluginBotBindingService {

    private final JdbcTemplate jdbc;

    public PluginBotBindingServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
    }

    private void ensureTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS xuanji_plugin_binding (
                    plugin_id  VARCHAR(128) NOT NULL,
                    platform   VARCHAR(32)  NOT NULL,
                    bot_key    VARCHAR(128) NOT NULL,
                    created_at BIGINT       DEFAULT 0,
                    PRIMARY KEY (plugin_id, platform, bot_key)
                )
            """);
        } catch (Exception e) {
            log.warn("[PluginBinding] 建表失败（可能框架库未就绪，后续绑定操作会重试）: {}", e.getMessage());
        }
    }

    @Override
    public List<PluginBotBinding> list(String pluginId) {
        return jdbc.query(
                "SELECT plugin_id, platform, bot_key, created_at FROM xuanji_plugin_binding WHERE plugin_id=? ORDER BY created_at",
                (rs, i) -> new PluginBotBinding(
                        rs.getString("plugin_id"),
                        rs.getString("platform"),
                        rs.getString("bot_key"),
                        rs.getLong("created_at")),
                pluginId);
    }

    @Override
    public void bind(String pluginId, String platform, String botKey) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_plugin_binding (plugin_id, platform, bot_key, created_at)
                KEY (plugin_id, platform, bot_key) VALUES (?, ?, ?, ?)
            """, pluginId, platform, botKey, TimeUtils.nowEpochSeconds());
        } catch (Exception e) {
            ensureTable();
            jdbc.update("""
                MERGE INTO xuanji_plugin_binding (plugin_id, platform, bot_key, created_at)
                KEY (plugin_id, platform, bot_key) VALUES (?, ?, ?, ?)
            """, pluginId, platform, botKey, TimeUtils.nowEpochSeconds());
        }
    }

    @Override
    public void unbind(String pluginId, String platform, String botKey) {
        jdbc.update("DELETE FROM xuanji_plugin_binding WHERE plugin_id=? AND platform=? AND bot_key=?",
                pluginId, platform, botKey);
    }

    @Override
    public void deleteAll(String pluginId) {
        try {
            jdbc.update("DELETE FROM xuanji_plugin_binding WHERE plugin_id=?", pluginId);
        } catch (Exception e) {
            log.warn("[PluginBinding] 清除插件绑定失败 {}: {}", pluginId, e.getMessage());
        }
    }

    @Override
    public boolean isAllowedForBot(String pluginId, String platform, String botKey) {
        try {
            List<PluginBotBinding> all = list(pluginId);
            if (all.isEmpty()) return true; // 无绑定 = 全局生效
            return all.stream().anyMatch(b -> b.botKey() != null && b.botKey().equals(botKey));
        } catch (Exception e) {
            return true; // 绑定表不可用（库未就绪）时降级为全局生效，不误伤
        }
    }
}
