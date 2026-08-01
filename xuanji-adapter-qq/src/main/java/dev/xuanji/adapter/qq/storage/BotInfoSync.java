package dev.xuanji.adapter.qq.storage;

import dev.xuanji.adapter.qq.api.QqApiService;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时调用 QQ /users/@me 接口，同步 Bot 信息到 xuanji_qqbot_info 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotInfoSync {

    private final QqApiService qqApiService;
    private final XuanjiRobotProperties robotProperties;
    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void sync() {
        var bots = robotProperties.getRobots();
        if (bots == null || bots.isEmpty()) return;

        for (var entry : bots.entrySet()) {
            String botKey = entry.getKey();
            var robot = entry.getValue();
            String appId = robot.getAppId();
            if (appId == null || appId.isEmpty()) continue;

            try {
                log.info("[BotInfoSync] 同步 Bot 信息: botKey={}", botKey);
                var result = qqApiService.get(appId, null, "/users/@me");
                if (result == null) continue;

                // 使用 API 返回的真实 id（非 hashCode）
                String botId = result.has("id") && !result.get("id").isNull()
                        ? result.get("id").asText() : appId;

                String username = stringOrNull(result, "username");
                String avatar = stringOrNull(result, "avatar");
                Boolean bot = result.has("bot") && !result.get("bot").isNull() ? result.get("bot").asBoolean() : null;
                String unionOpenid = stringOrNull(result, "union_openid");
                String unionAccount = stringOrNull(result, "union_user_account");
                String shareUrl = stringOrNull(result, "share_url");
                String welcomeMsg = stringOrNull(result, "welcome_msg");

                jdbc.update("""
                    MERGE INTO xuanji_qqbot_info
                    (bot_id, bot_key, username, avatar, bot, union_openid,
                     union_user_account, share_url, welcome_msg, raw_json, updated_at)
                    KEY (bot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, botId, botKey, username, avatar, bot,
                    unionOpenid, unionAccount, shareUrl, welcomeMsg, result.toString());

                log.info("[BotInfoSync] Bot 信息已同步: {} ({})", username, botId);
            } catch (Exception e) {
                log.error("[BotInfoSync] 同步失败: botKey={}, error={}", botKey, e.getMessage());
            }
        }
    }

    private static String stringOrNull(com.fasterxml.jackson.databind.node.ObjectNode obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isNull()) return null;
        String v = obj.get(key).asText();
        return v.isEmpty() ? null : v;
    }
}
