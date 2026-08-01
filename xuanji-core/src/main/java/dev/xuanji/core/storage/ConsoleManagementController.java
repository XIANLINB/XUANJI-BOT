package dev.xuanji.core.storage;

import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.plugin.XuanjiPluginManager;
import dev.xuanji.core.command.CommandRegistry;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * 控制台管理 API：仪表盘、Bot 管理、插件管理。
 */
@RestController
@RequestMapping("/xuanji/api/console")
public class ConsoleManagementController {

    private final JdbcTemplate jdbc;
    private final JdbcTemplate logJdbc;
    private final XuanjiRobotProperties robotProperties;
    private final XuanjiPluginManager pluginManager;
    private final CommandRegistry commandRegistry;

    public ConsoleManagementController(JdbcTemplate jdbc,
                                        @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc,
                                        XuanjiRobotProperties robotProperties,
                                        XuanjiPluginManager pluginManager,
                                        CommandRegistry commandRegistry) {
        this.jdbc = jdbc;
        this.logJdbc = logJdbc;
        this.robotProperties = robotProperties;
        this.pluginManager = pluginManager;
        this.commandRegistry = commandRegistry;
    }

    // ═══════════════════ 仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        // Bot
        int botsOnline = qInt("SELECT COUNT(*) FROM xuanji_bot WHERE status='ONLINE'");
        int botsTotal = qInt("SELECT COUNT(*) FROM xuanji_bot");
        m.put("botsOnline", botsOnline);
        m.put("botsTotal", botsTotal);

        // 群/好友
        m.put("groupsTotal", qInt("SELECT COUNT(*) FROM xuanji_qqbot_group WHERE is_deleted=0"));
        m.put("friendsTotal", qInt("SELECT COUNT(*) FROM xuanji_qqbot_user WHERE is_deleted=0"));

        // 今日
        String today = "CURRENT_DATE";
        m.put("todayGroupAdd", qInt("SELECT COUNT(*) FROM xuanji_qqbot_event WHERE event_type='GROUP_ADD_ROBOT' AND create_time>="+today));
        m.put("todayGroupDel", qInt("SELECT COUNT(*) FROM xuanji_qqbot_event WHERE event_type='GROUP_DEL_ROBOT' AND create_time>="+today));
        m.put("todayFriendAdd", qInt("SELECT COUNT(*) FROM xuanji_qqbot_event WHERE event_type='FRIEND_ADD' AND create_time>="+today));
        m.put("todayFriendDel", qInt("SELECT COUNT(*) FROM xuanji_qqbot_event WHERE event_type='FRIEND_DEL' AND create_time>="+today));
        m.put("todayGroupMessages", logInt("SELECT COUNT(*) FROM xuanji_qqbot_group_message WHERE create_time>=CURRENT_DATE"));
        m.put("todayC2cMessages", logInt("SELECT COUNT(*) FROM xuanji_qqbot_c2c_message WHERE create_time>=CURRENT_DATE"));

        // 插件
        m.put("pluginsLoaded", pluginManager.getPlugins().size());

        return m;
    }

    // ═══════════════════ Bot 管理 ═══════════════════

    @GetMapping("/bots")
    public List<Map<String, Object>> bots() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT platform, bot_identifier, bot_key, status FROM xuanji_bot");
        } catch (Exception e) {
            return list;
        }
        for (var row : rows) {
            String appId = String.valueOf(row.get("BOT_IDENTIFIER"));
            String botKey = String.valueOf(row.getOrDefault("BOT_KEY", appId));
            String platform = String.valueOf(row.getOrDefault("PLATFORM", "qq"));
            String status = String.valueOf(row.getOrDefault("STATUS", "unknown"));
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("botKey", botKey);
            b.put("appId", appId);
            b.put("platform", platform);
            b.put("status", status);
            b.put("groupsTotal", qInt("SELECT COUNT(*) FROM xuanji_qqbot_group WHERE bot_id=? AND is_deleted=0", appId));
            b.put("friendsTotal", qInt("SELECT COUNT(*) FROM xuanji_qqbot_user WHERE bot_id=? AND is_deleted=0", appId));
            b.put("todayMessages", logInt("SELECT COUNT(*) FROM xuanji_qqbot_group_message WHERE bot_id=? AND create_time>=CURRENT_DATE", appId));

            try {
                var info = jdbc.queryForMap("SELECT username, avatar, share_url, welcome_msg FROM xuanji_qqbot_info WHERE bot_id=?", appId);
                b.put("nickname", info.get("USERNAME"));
                b.put("avatar", info.get("AVATAR"));
                b.put("shareUrl", info.get("SHARE_URL"));
            } catch (Exception ignored) {}

            list.add(b);
        }
        return list;
    }

    @GetMapping("/bots/{botKey}")
    public Map<String, Object> botDetail(@PathVariable String botKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 优先从 xuanji_bot 表（live）读取
        String appId;
        String platform;
        String status;
        try {
            var r = jdbc.queryForMap("SELECT bot_identifier, platform, status FROM xuanji_bot WHERE bot_key=?", botKey);
            appId = String.valueOf(r.get("BOT_IDENTIFIER"));
            platform = String.valueOf(r.getOrDefault("PLATFORM", "qq"));
            status = String.valueOf(r.getOrDefault("STATUS", "unknown"));
        } catch (Exception e) {
            return Map.of("error", "Bot not found");
        }
        m.put("botKey", botKey);
        m.put("appId", appId);
        m.put("platform", platform);
        m.put("status", status);

        // 密钥：尽力从 robotProperties 获取（控制台写入的可能未热刷新）
        String secret = null;
        var robots = robotProperties.getRobots();
        if (robots != null) {
            var cfg = robots.get(botKey);
            if (cfg != null) secret = cfg.getClientSecret();
        }
        m.put("appSecret", secret != null && secret.length() > 4
                ? "***" + secret.substring(secret.length() - 4)
                : (secret == null ? "—（由控制台管理）" : secret));
        m.put("env", robots != null && robots.get(botKey) != null && robots.get(botKey).isSandbox() ? "SANDBOX" : "PRODUCTION");

        try {
            var info = jdbc.queryForMap("SELECT * FROM xuanji_qqbot_info WHERE bot_id=?", appId);
            Map<String, String> infoMap = new LinkedHashMap<>();
            for (var e : info.entrySet())
                infoMap.put(e.getKey().toLowerCase(), e.getValue() != null ? e.getValue().toString() : "");
            m.put("info", infoMap);
        } catch (Exception e) { m.put("info", Map.of()); }

        return m;
    }

    // ═══════════════════ 插件管理 ═══════════════════

    @GetMapping("/plugins")
    public List<Map<String, Object>> plugins() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PluginWrapper pw : pluginManager.getPlugins()) {
            Map<String, Object> p = new LinkedHashMap<>();
            var d = pw.getDescriptor();
            p.put("id", pw.getPluginId());
            p.put("name", d.getPluginId()); // PF4J 用 id 作为名称
            p.put("version", d.getVersion());
            p.put("provider", d.getProvider());
            p.put("description", d.getPluginDescription());
            p.put("state", pw.getPluginState().toString());
            p.put("running", commandRegistry.isPluginEnabled(pw.getPluginId()));
            list.add(p);
        }
        for (PluginWrapper pw : pluginManager.getResolvedPlugins()) {
            if (pluginManager.getPlugins().stream().noneMatch(pl -> pl.getPluginId().equals(pw.getPluginId()))) {
                var d = pw.getDescriptor();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", pw.getPluginId());
                p.put("name", d.getPluginId());
                p.put("version", d.getVersion());
                p.put("provider", d.getProvider());
                p.put("description", d.getPluginDescription());
                p.put("state", pw.getPluginState().toString());
                p.put("running", false);
                list.add(p);
            }
        }
        return list;
    }

    @PostMapping("/plugins/{pluginId}/stop")
    public Map<String, Object> stopPlugin(@PathVariable String pluginId) {
        commandRegistry.setPluginEnabled(pluginId, false);
        return Map.of("status", "ok");
    }

    @PostMapping("/plugins/{pluginId}/start")
    public Map<String, Object> startPlugin(@PathVariable String pluginId) {
        commandRegistry.setPluginEnabled(pluginId, true);
        return Map.of("status", "ok");
    }

    // ═══════════════════ 消息监控 ═══════════════════

    @GetMapping("/group-messages")
    public Map<String, Object> groupMessages(@RequestParam(defaultValue = "") String bot,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return queryLogTable("xuanji_qqbot_group_message", bot, page, size);
    }

    @GetMapping("/c2c-messages")
    public Map<String, Object> c2cMessages(@RequestParam(defaultValue = "") String bot,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return queryLogTable("xuanji_qqbot_c2c_message", bot, page, size);
    }

    /** 群列表（用于联系人列表） */
    @GetMapping("/contacts/groups")
    public List<Map<String, Object>> contactGroups() {
        return logJdbc.queryForList("SELECT DISTINCT group_id, bot_id FROM (SELECT group_id, bot_id FROM xuanji_qqbot_group_message ORDER BY create_time DESC) LIMIT 100")
                .stream().map(m -> {
                    Map<String, Object> r = new LinkedHashMap<>(m);
                    r.put("type", "group");
                    try {
                        var n = jdbc.queryForMap("SELECT group_name FROM xuanji_qqbot_group WHERE group_id=?", m.get("GROUP_ID"));
                        r.put("name", n.getOrDefault("GROUP_NAME", m.get("GROUP_ID")));
                    } catch (Exception e) { r.put("name", m.get("GROUP_ID")); }
                    return r;
                }).toList();
    }

    /** 单聊联系人列表 */
    @GetMapping("/contacts/friends")
    public List<Map<String, Object>> contactFriends() {
        return logJdbc.queryForList("SELECT DISTINCT user_id, bot_id FROM (SELECT user_id, bot_id FROM xuanji_qqbot_c2c_message ORDER BY create_time DESC) LIMIT 100")
                .stream().map(m -> {
                    Map<String, Object> r = new LinkedHashMap<>(m);
                    r.put("type", "c2c");
                    r.put("name", m.get("USER_ID"));
                    return r;
                }).toList();
    }

    /** 指定联系人的历史消息 */
    @GetMapping("/contact-messages")
    public List<Map<String, Object>> contactMessages(@RequestParam String type,
                                                      @RequestParam String targetId) {
        String table = "group".equals(type) ? "xuanji_qqbot_group_message" : "xuanji_qqbot_c2c_message";
        String col = "group".equals(type) ? "group_id" : "user_id";
        return logJdbc.queryForList(
                "SELECT * FROM " + table + " WHERE " + col + "=? ORDER BY create_time DESC LIMIT 100",
                targetId);
    }

    private Map<String, Object> queryLogTable(String table, String bot, int page, int size) {
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (bot != null && !bot.isEmpty()) { where.add("bot_id=?"); args.add(bot); }
        String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        int total = logInt("SELECT COUNT(*) FROM " + table + w, args.toArray());
        List<Map<String, Object>> rows = logJdbc.queryForList(
                "SELECT * FROM " + table + w + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                Stream.concat(args.stream(), Stream.of(size, page * size)).toArray());
        return Map.of("rows", rows, "total", total, "page", page, "size", size);
    }

    private int qInt(String sql, Object... args) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, args);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }

    private int logInt(String sql, Object... args) {
        try {
            Integer v = logJdbc.queryForObject(sql, Integer.class, args);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }
}
