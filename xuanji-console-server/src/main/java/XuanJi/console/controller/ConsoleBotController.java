package XuanJi.console.controller;

import XuanJi.console.service.AuditService;
import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.config.XuanJiRobotProperties;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static XuanJi.console.service.ConsoleQueryService.strOrEmpty;

/**
 * 控制台 · XuanJiBot 管理（列表 / 详情 / 启停）。
 *
 * <p>机器人清单数据来源：框架库 {@code xuanji_bot}（跨平台注册索引）；
 * 群/好友/消息等统计经 {@link PlatformDataProvider} 按平台聚合，core 不出现平台字样。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleBotController {

    private final ConsoleQueryService queryService;
    private final XuanJiRobotProperties robotProperties;
    private final AuditService auditService;

    public ConsoleBotController(ConsoleQueryService queryService, XuanJiRobotProperties robotProperties,
                                AuditService auditService) {
        this.queryService = queryService;
        this.robotProperties = robotProperties;
        this.auditService = auditService;
    }

    /** 机器人列表（卡片页数据源）。 */
    @GetMapping("/bots")
    public List<Map<String, Object>> bots() {
        List<Map<String, Object>> list = new ArrayList<>();
        long since = ConsoleQueryService.todayStartEpochSeconds();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("botKey", queryService.resolveBotKey(ref.instanceId()));
            b.put("appId", ref.instanceId());
            b.put("platform", ref.platform());
            b.put("status", ref.status());

            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p != null) {
                b.put("groupsTotal", p.countGroups(ref.instanceId()));
                b.put("friendsTotal", p.countFriends(ref.instanceId()));
                b.put("todayMessages",
                        p.countMessagesSince(ref.instanceId(), PlatformDataProvider.CHAT_GROUP, since)
                                + p.countMessagesSince(ref.instanceId(), PlatformDataProvider.CHAT_C2C, since));
                // 机器人基础信息（头像/名称，来自平台实例库 botinfo 表）
                // H2 列名大小写取决于表定义，统一按大小写兼容取值，避免取空
                Map<String, Object> info = p.getBotInfo(ref.instanceId());
                b.put("name", strOrEmpty(info.get("NAME"), info.get("name")));
                b.put("avatar", strOrEmpty(info.get("AVATAR"), info.get("avatar")));
                // 连接方式（websocket / webhook）
                b.put("connectionType", p.getConnectionType(ref.instanceId()));
                // webhook 回调域名（qqbot_bot.webhook_url 存的是域名，完整回调 = https://{domain}/webhook/{appId}）
                Map<String, Object> cfg = p.getBotConfig(ref.instanceId());
                b.put("domain", strOrEmpty(cfg.get("webhookUrl")));
            } else {
                b.put("groupsTotal", 0);
                b.put("friendsTotal", 0);
                b.put("todayMessages", 0);
                b.put("name", "");
                b.put("avatar", "");
                b.put("connectionType", "");
                b.put("domain", "");
            }
            list.add(b);
        }
        return list;
    }

    /** 机器人详情（含脱敏密钥 / 环境 / 统计 / 机器人信息）。 */
    @GetMapping("/bots/{botKey}")
    public Map<String, Object> botDetail(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null) return Map.of("error", "XuanJiBot not found");

        String platform;
        String status;
        Map<String, String> row = queryService.botPlatformStatus(appId);
        if (row == null) return Map.of("error", "XuanJiBot not found");
        platform = row.get("platform");
        status = row.get("status");

        Map<String, Object> m = new LinkedHashMap<>();
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

        PlatformDataProvider p = queryService.providerFor(platform);
        long today0 = ConsoleQueryService.todayStartEpochSeconds();
        long week0 = java.time.Instant.now().getEpochSecond() - 7L * 86400;
        long month0 = java.time.Instant.now().getEpochSecond() - 30L * 86400;
        m.put("groupsTotal", p == null ? 0 : p.countGroups(appId));
        m.put("friendsTotal", p == null ? 0 : p.countFriends(appId));
        m.put("todayMessages", p == null ? 0
                : p.countMessagesSince(appId, PlatformDataProvider.CHAT_GROUP, today0)
                + p.countMessagesSince(appId, PlatformDataProvider.CHAT_C2C, today0));
        m.put("connectionType", p == null ? "" : p.getConnectionType(appId));
        // webhook 回调域名（完整回调 = https://{domain}/webhook/{appId}）
        String domain = p == null ? "" : strOrEmpty(p.getBotConfig(appId).get("webhookUrl"));
        m.put("domain", domain);

        // 机器人信息（union_openid / share_url / welcome_msg，从 qqbot_botinfo 表，启动后调 /users/@me 同步）
        // 注意：queryForMap 会把查询到的列全部放进 map，即使值为 NULL（key 存在 → getOrDefault 也返回 null）！
        // 因此必须用 strOrEmpty 做 null 安全取值（同时兼容大小写列名），绝不能用 getOrDefault(...).toString()。
        if (p != null) {
            Map<String, Object> info = p.getBotInfo(appId);
            m.put("unionOpenid", strOrEmpty(info.get("UNION_OPENID"), info.get("union_openid")));
            m.put("shareUrl", strOrEmpty(info.get("SHARE_URL"), info.get("share_url")));
            m.put("welcomeMsg", strOrEmpty(info.get("WELCOME_MSG"), info.get("welcome_msg")));
            m.put("botName", strOrEmpty(info.get("NAME"), info.get("name")));
            m.put("botAvatar", strOrEmpty(info.get("AVATAR"), info.get("avatar")));
            m.put("botId", strOrEmpty(info.get("BOT_ID"), info.get("bot_id")));

            // 消息统计：今日/本周/本月 × 入站/出站
            long now = java.time.Instant.now().getEpochSecond();
            Map<String, Object> msgStats = new LinkedHashMap<>();
            msgStats.put("today", p.messageDirectionStats(appId, today0, now));
            msgStats.put("week",  p.messageDirectionStats(appId, week0,  now));
            msgStats.put("month", p.messageDirectionStats(appId, month0, now));
            m.put("messageStats", msgStats);
        } else {
            m.put("unionOpenid", "");
            m.put("shareUrl", "");
            m.put("welcomeMsg", "");
            m.put("botName", "");
            m.put("botAvatar", "");
            m.put("botId", "");
            m.put("messageStats", Map.of("today", Map.of("in", 0, "out", 0),
                    "week", Map.of("in", 0, "out", 0), "month", Map.of("in", 0, "out", 0)));
        }

        // 创建时间（从框架库 xuanji_bot.create_time 读取）
        try {
            var ts = queryService.getJdbc().queryForObject(
                    "SELECT CREATE_TIME FROM xuanji_bot WHERE INSTANCE_ID=?",
                    java.sql.Timestamp.class, appId);
            if (ts != null) m.put("createTime", ts.getTime() / 1000);
        } catch (Exception ignored) { /* 不影响主体返回 */ }

        return m;
    }

    /** 切换连接方式（websocket ↔ webhook）：校验 → 更新配置 → stop+start 重连。 */
    @PutMapping("/bots/{botKey}/conn-mode")
    public Map<String, Object> updateConnMode(@PathVariable String botKey,
                                               @RequestParam String mode,
                                               HttpServletRequest req) {
        if (mode == null || (!mode.equalsIgnoreCase("websocket") && !mode.equalsIgnoreCase("webhook"))) {
            return Map.of("error", "mode 必须是 websocket 或 webhook");
        }
        String appId = queryService.resolveAppId(botKey);
        if (appId == null || appId.isBlank()) return Map.of("error", "XuanJiBot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);

        String normalized = mode.toLowerCase();
        // webhook 模式：必须先有 webhookUrl
        if ("webhook".equals(normalized)) {
            String webhookUrl = strOrEmpty(p.getBotConfig(appId).get("webhookUrl"));
            if (webhookUrl.isBlank()) {
                return Map.of("error", "切换 webhook 前请先配置 webhook 回调域名（在 '运行环境' 配置中设置）");
            }
        }

        try {
            // 1. 写框架库 xuanji_bot_setting.conn_mode（下次启动生效）
            String sqlSet = "MERGE INTO xuanji_bot_setting (bot_key, config_key, config_value) KEY(bot_key, config_key) VALUES (?, 'conn_mode', ?)";
            queryService.getJdbc().update(sqlSet, botKey, normalized);

            // 2. 同步更新适配器（QQ 平台写平台库 + 内存 Robot）
            p.updateConnMode(appId, normalized);

            // 3. 同步更新 starter 的 XuanJiRobotProperties 内存（getRobots() 拿到的对象）
            robotProperties.updateConnectionMethod(botKey, normalized);

            // 4. stopBot：断开旧连接（webhook 标停用；websocket 真正断开）
            try { p.stopBot(appId); } catch (Exception ignored) {}

            // 5. startBot：按新模式建立连接（startBot 内部会读取 Robot.connectionMethod）
            String env = "SANDBOX";
            var allRobots = queryService.getRobotProperties().getRobots();
            if (allRobots != null && allRobots.get(botKey) != null && !allRobots.get(botKey).isSandbox()) env = "PRODUCTION";
            p.startBot(appId, env);

            log.info("[Console] 切换机器人连接方式: botKey={}, appId={}, mode={}", botKey, appId, normalized);
            auditService.record("BOT_CONN_MODE_CHANGE",
                    "appId=" + appId + " 切换连接方式 → " + normalized, req);
            return Map.of("ok", true, "msg", "已切换到 " + normalized + "，连接已重启");
        } catch (Exception e) {
            log.error("[Console] 切换连接方式失败: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /** 停止机器人连接（WebSocket 断开 / Webhook 标记停用）。 */
    @PostMapping("/bots/{botKey}/stop")
    public Map<String, Object> stopBot(@PathVariable String botKey, HttpServletRequest req) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null || appId.isBlank()) return Map.of("error", "XuanJiBot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);
        try {
            p.stopBot(appId);
            log.info("[Console] 停止机器人: appId={}, platform={}", appId, platform);
            auditService.record("BOT_STOP", "appId=" + appId + " (" + platform + ") 停止连接", req);
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("[Console] 停止机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /** 群变动数据（今日/昨日新增群 + 活跃成员）。统计卡「群变动 / 成员变动」用。 */
    @GetMapping("/bots/{botKey}/group-variation")
    public Map<String, Object> groupVariation(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null) return Map.of("error", "XuanJiBot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);
        long now = java.time.Instant.now().getEpochSecond();
        long today0 = ConsoleQueryService.todayStartEpochSeconds();
        long yday0 = today0 - 86400L;
        // 接口签名 Map<String,Long>，Controller 暴露 Map<String,Object>，强转
        return (Map<String, Object>) (Map) p.groupVariation(appId, today0, yday0, now);
    }

    /** 单聊用户变动数据（今日/昨日新增用户 + 活跃用户）。统计卡「用户变动」用。 */
    @GetMapping("/bots/{botKey}/friend-variation")
    public Map<String, Object> friendVariation(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null) return Map.of("error", "XuanJiBot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);
        long now = java.time.Instant.now().getEpochSecond();
        long today0 = ConsoleQueryService.todayStartEpochSeconds();
        long yday0 = today0 - 86400L;
        return (Map<String, Object>) (Map) p.friendVariation(appId, today0, yday0, now);
    }

    /** 启动机器人连接。envType 取自数据库配置（SANDBOX / PRODUCTION）。 */
    @PostMapping("/bots/{botKey}/start")
    public Map<String, Object> startBot(@PathVariable String botKey, HttpServletRequest req) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null || appId.isBlank()) return Map.of("error", "XuanJiBot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);
        String env = "SANDBOX";
        var robots = robotProperties.getRobots();
        if (robots != null && robots.get(botKey) != null && !robots.get(botKey).isSandbox()) {
            env = "PRODUCTION";
        }
        try {
            p.startBot(appId, env);
            log.info("[Console] 启用机器人: appId={}, platform={}, env={}", appId, platform, env);
            auditService.record("BOT_START", "appId=" + appId + " (" + platform + ") 启动连接 env=" + env, req);
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("[Console] 启用机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
}
