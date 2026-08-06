package dev.xuanji.console.controller;

import dev.xuanji.console.service.ConsoleQueryService;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.PlatformDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.xuanji.console.service.ConsoleQueryService.strOrEmpty;

/**
 * 控制台 · Bot 管理（列表 / 详情 / 启停）。
 *
 * <p>机器人清单数据来源：框架库 {@code xuanji_bot}（跨平台注册索引）；
 * 群/好友/消息等统计经 {@link PlatformDataProvider} 按平台聚合，core 不出现平台字样。
 */
@Slf4j
@RestController
@RequestMapping("/xuanji/api/console")
public class ConsoleBotController {

    private final ConsoleQueryService queryService;
    private final XuanjiRobotProperties robotProperties;

    public ConsoleBotController(ConsoleQueryService queryService, XuanjiRobotProperties robotProperties) {
        this.queryService = queryService;
        this.robotProperties = robotProperties;
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

    /** 机器人详情（含脱敏密钥 / 环境 / 统计）。 */
    @GetMapping("/bots/{botKey}")
    public Map<String, Object> botDetail(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null) return Map.of("error", "Bot not found");

        String platform;
        String status;
        Map<String, String> row = queryService.botPlatformStatus(appId);
        if (row == null) return Map.of("error", "Bot not found");
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
        long since = ConsoleQueryService.todayStartEpochSeconds();
        m.put("groupsTotal", p == null ? 0 : p.countGroups(appId));
        m.put("friendsTotal", p == null ? 0 : p.countFriends(appId));
        m.put("todayMessages", p == null ? 0
                : p.countMessagesSince(appId, PlatformDataProvider.CHAT_GROUP, since)
                + p.countMessagesSince(appId, PlatformDataProvider.CHAT_C2C, since));
        m.put("connectionType", p == null ? "" : p.getConnectionType(appId));
        // webhook 回调域名（完整回调 = https://{domain}/webhook/{appId}）
        m.put("domain", p == null ? "" : strOrEmpty(p.getBotConfig(appId).get("webhookUrl")));
        return m;
    }

    /** 停止机器人连接（WebSocket 断开 / Webhook 标记停用）。 */
    @PostMapping("/bots/{botKey}/stop")
    public Map<String, Object> stopBot(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null || appId.isBlank()) return Map.of("error", "Bot not found");
        String platform = queryService.resolvePlatform(appId);
        PlatformDataProvider p = queryService.providerFor(platform);
        if (p == null) return Map.of("error", "unsupported platform: " + platform);
        try {
            p.stopBot(appId);
            log.info("[Console] 停止机器人: appId={}, platform={}", appId, platform);
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("[Console] 停止机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /** 启动机器人连接。envType 取自数据库配置（SANDBOX / PRODUCTION）。 */
    @PostMapping("/bots/{botKey}/start")
    public Map<String, Object> startBot(@PathVariable String botKey) {
        String appId = queryService.resolveAppId(botKey);
        if (appId == null || appId.isBlank()) return Map.of("error", "Bot not found");
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
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("[Console] 启用机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
}
