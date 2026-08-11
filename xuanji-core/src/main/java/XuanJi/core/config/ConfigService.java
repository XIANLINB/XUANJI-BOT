package XuanJi.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时配置 — 唯一真相源：
 * <ul>
 *   <li>{@code xuanji_config}：全局 k/v（v3.3 并入旧 xuanji_setting）</li>
 *   <li>{@code xuanji_bot_setting}：每机器人 EAV（bot_key/config_key/config_value，取代旧固定列 xuanji_bot_config）</li>
 * </ul>
 */
@Slf4j
@Service
public class ConfigService {

    private final JdbcTemplate jdbc;

    public ConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 全局 + 每机器人配置快照（控制台设置页数据源）。 */
    public Map<String, Object> getConfigView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("global", getGlobalConfig());
        view.put("bots", getBotConfigMap());
        view.put("groups", getGroupConfigMap());
        return view;
    }

    /** 群级配置（xuanji_group_setting EAV 展开为 botKey → groupId → {key: value}）。过滤历史脏数据群（undefined/null）。 */
    public Map<String, Map<String, Map<String, String>>> getGroupConfigMap() {
        Map<String, Map<String, Map<String, String>>> out = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT bot_key, group_id, config_key, config_value FROM xuanji_group_setting", rs -> {
                String gid = rs.getString("group_id");
                // 防御：早期前端大写字段 bug 曾写入 group_id='undefined' 的脏行，读时过滤
                if (gid == null || gid.isBlank() || "undefined".equalsIgnoreCase(gid) || "null".equalsIgnoreCase(gid)) return;
                String bk = rs.getString("bot_key");
                out.computeIfAbsent(bk, k -> new LinkedHashMap<>())
                        .computeIfAbsent(gid, k -> new LinkedHashMap<>())
                        .put(rs.getString("config_key"), rs.getString("config_value"));
            });
        } catch (Exception e) {
            log.debug("[Config] 读取群配置失败（表未就绪？）: {}", e.getMessage());
        }
        return out;
    }

    /** 全局 KV（xuanji_config）。 */
    public Map<String, String> getGlobalConfig() {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT config_key, config_value FROM xuanji_config", rs -> {
                m.put(rs.getString("config_key"), rs.getString("config_value"));
            });
        } catch (Exception e) {
            log.debug("[Config] 读取全局配置失败（表未就绪？）: {}", e.getMessage());
        }
        return m;
    }

    /** 每机器人配置（xuanji_bot_setting EAV 展开为 botKey → {key: value}）。 */
    public Map<String, Map<String, String>> getBotConfigMap() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT bot_key, config_key, config_value FROM xuanji_bot_setting", rs -> {
                out.computeIfAbsent(rs.getString("bot_key"), k -> new LinkedHashMap<>())
                        .put(rs.getString("config_key"), rs.getString("config_value"));
            });
        } catch (Exception e) {
            log.debug("[Config] 读取 bot 配置失败（表未就绪？）: {}", e.getMessage());
        }
        return out;
    }

    /** 写全局 KV。 */
    public void setGlobal(String key, String value) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_config (config_key, config_value)
                KEY (config_key) VALUES (?, ?)
            """, key, value == null ? "" : value);
        } catch (Exception e) {
            log.warn("[Config] 写全局配置失败: key={}, err={}", key, e.getMessage());
        }
    }

    /** 按作用域删除单键（一键重置）：scope ∈ global/bot/group，botKey 是 appId（global 时可传空），groupId 仅 group 用。 */
    public void deleteKey(String scope, String botKey, String groupId, String key) {
        try {
            switch (scope) {
                case "global" -> jdbc.update("DELETE FROM xuanji_config WHERE config_key=?", key);
                case "bot" -> jdbc.update("DELETE FROM xuanji_bot_setting WHERE bot_key=? AND config_key=?",
                        botKey == null ? "" : botKey, key);
                case "group" -> jdbc.update("DELETE FROM xuanji_group_setting WHERE bot_key=? AND group_id=? AND config_key=?",
                        botKey == null ? "" : botKey, groupId == null ? "" : groupId, key);
                default -> log.warn("[Config] deleteKey 未支持 scope={}", scope);
            }
            log.info("[Config] 重置: scope={}, bot={}, group={}, key={}", scope, botKey, groupId, key);
        } catch (Exception e) {
            log.warn("[Config] 重置失败: scope={}, key={}, err={}", scope, key, e.getMessage());
        }
    }

    /** 写某机器人配置（body: 字段映射）。 */
    public void setBotConfig(String botKey, Map<String, String> body) {
        body.forEach((k, v) -> {
            try {
                jdbc.update("""
                    MERGE INTO xuanji_bot_setting (bot_key, config_key, config_value)
                    KEY (bot_key, config_key) VALUES (?, ?, ?)
                """, botKey, k, v == null ? "" : v);
            } catch (Exception e) {
                log.warn("[Config] 写 bot 配置失败: botKey={}, key={}, err={}", botKey, k, e.getMessage());
            }
        });
    }

    /** 读某机器人某键（bot 级 EAV），缺失返回 null。 */
    public String getBotConfig(String botKey, String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT config_value FROM xuanji_bot_setting WHERE bot_key=? AND config_key=?",
                    String.class, botKey, key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 写群级配置（bot_key + group_id + config_key）。 */
    public void setGroupConfig(String botKey, String groupId, String key, String value) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_group_setting (bot_key, group_id, config_key, config_value)
                KEY (bot_key, group_id, config_key) VALUES (?, ?, ?, ?)
            """, botKey, groupId, key, value == null ? "" : value);
        } catch (Exception e) {
            log.warn("[Config] 写群配置失败: botKey={}, groupId={}, key={}, err={}", botKey, groupId, key, e.getMessage());
        }
    }

    /** 读群级配置，缺失返回 null。 */
    public String getGroupConfig(String botKey, String groupId, String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT config_value FROM xuanji_group_setting WHERE bot_key=? AND group_id=? AND config_key=?",
                    String.class, botKey, groupId, key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 三级「忽略其他机器人消息」判定（群 &gt; bot &gt; 全局 &gt; 默认 true）：
     * 值为 "true"（忽略）时返回 true；未配置任何级别时默认忽略（防其他机器人消息误触发插件）。
     */
    public boolean isIgnoreBotMessages(String botKey, String groupId) {
        // 1. 群级
        if (groupId != null && !groupId.isBlank()) {
            String g = getGroupConfig(botKey, groupId, "ignore_bot_messages");
            if (g != null) return "true".equalsIgnoreCase(g.trim());
        }
        // 2. bot 级
        String b = getBotConfig(botKey, "ignore_bot_messages");
        if (b != null) return "true".equalsIgnoreCase(b.trim());
        // 3. 全局
        String gl = getGlobalConfig().get("ignore_bot_messages");
        if (gl != null) return "true".equalsIgnoreCase(gl.trim());
        // 4. 默认：忽略（未配置任何级别时，其他机器人的消息不触发插件）
        return true;
    }

    /**
     * 每机器人出站节奏毫秒数（P2-E）：per-bot {@code outbound_pace_ms} EAV &gt; 全局 {@code outbound.pace_ms} &gt; 默认 0（不节流）。
     */
    public long getOutboundPaceMs(String botId) {
        try {
            String v = jdbc.queryForObject(
                    "SELECT config_value FROM xuanji_bot_setting WHERE bot_key=? AND config_key='outbound_pace_ms'",
                    String.class, botId);
            if (v != null && !v.isBlank()) {
                long ms = parseLong(v);
                if (ms > 0) return ms;
            }
        } catch (Exception ignored) {
        }
        try {
            String v = jdbc.queryForObject(
                    "SELECT config_value FROM xuanji_config WHERE config_key='outbound.pace_ms'",
                    String.class);
            if (v != null && !v.isBlank()) {
                long ms = parseLong(v);
                if (ms > 0) return ms;
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
