package dev.xuanji.adapter.qqbot.storage;

import dev.xuanji.adapter.qqbot.api.QqApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时调用 QQ /users/@me 接口，同步 Bot 信息到平台共享库 qqbot_botinfo 表。
 *
 * <p>数据源为数据库（qqbot_bot 表枚举，v3.3 注册真相源），而非 xuanji-robots.yml——
 * 控制台/向导写入的机器人不在 yaml 里，读 yaml 会导致新机器人永不同步资料（头像缺失）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotInfoSync {

    private final QqApiService qqApiService;
    private final QqBotRepository qqBotRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void sync() {
        try {
            for (String appId : qqBotRepository.listInstanceIds()) {
                syncByAppId(appId);
            }
        } catch (Exception e) {
            log.debug("[BotInfoSync] 枚举注册机器人失败（库未就绪？）: {}", e.getMessage());
        }
    }

    /** 按 appId 同步单个 bot（控制台保存配置后调用）。 */
    public void syncBot(String appId) {
        syncByAppId(appId);
    }

    private void syncByAppId(String appId) {
        if (appId == null || appId.isBlank()) return;
        try {
            var row = qqBotRepository.getBotRow(appId);
            if (row == null || row.isEmpty()) return;
            String secret = String.valueOf(row.getOrDefault("BOT_CLIENTSECRET", ""));
            if (secret == null || secret.isBlank()) return;

            log.info("[BotInfoSync] 同步 Bot 信息: appId={}", appId);
            var result = qqApiService.get(appId, null, "/users/@me");
            if (result == null) return;

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

            qqBotRepository.upsertBotInfo(appId, botId, username, avatar, bot,
                    unionOpenid, shareUrl, welcomeMsg);

            log.info("[BotInfoSync] Bot 信息已同步: {} ({})", username, botId);
        } catch (Exception e) {
            log.error("[BotInfoSync] 同步失败: appId={}, error={}", appId, e.getMessage());
        }
    }

    private static String stringOrNull(com.fasterxml.jackson.databind.node.ObjectNode obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isNull()) return null;
        String v = obj.get(key).asText();
        return v.isEmpty() ? null : v;
    }
}
