package XuanJi.adapter.qqbot.service;

import XuanJi.adapter.qqbot.config.ConditionalOnQqbotEnabled;
import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.core.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 聊天消息留存自动清理 — 防止 per-bot 日志库（qqbot_message）无限增长。
 *
 * <p>群聊 + 单聊消息共用 {@code qqbot_message} 表，按消息时间（create_time，epoch 秒）判定新旧。
 * 全局配置键 {@code msg.retention.days}（默认 30）控制「只保留最近 N 天」；
 * 每日 04:10（避开 03:00 自动备份 / 03:30 消息备份）扫描全部 per-bot 日志库，
 * 删除 {@code create_time < 现在 - N 天} 的消息。配置即时生效（每次执行时读取）。
 *
 * <p>配置入口：框架配置 → 存储 → 「聊天消息留存天数」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnQqbotEnabled
public class MessageRetentionCleanupService {

    /** 全局配置键：聊天消息留存天数（默认 30）。 */
    public static final String RETENTION_KEY = "msg.retention.days";

    private final QqBotRepository repo;
    private final ConfigService configService;

    /** 每日 04:10 执行（配置即时读取，改后下一日生效）。 */
    @Scheduled(cron = "0 10 4 * * *")
    public void cleanup() {
        int days = resolveDays();
        try {
            long deleted = repo.deleteOldMessages(days);
            log.info("[消息留存] 自动清理完成：保留 {} 天，共删除 {} 行消息", days, deleted);
        } catch (Exception e) {
            log.warn("[消息留存] 自动清理异常: {}", e.getMessage());
        }
    }

    /** 解析留存天数（全局配置优先，缺省/非法 → 30）。 */
    public int resolveDays() {
        try {
            String v = configService.getGlobalConfig().get(RETENTION_KEY);
            if (v != null && !v.isBlank()) {
                int d = Integer.parseInt(v.trim());
                if (d >= 1) return d;
            }
        } catch (Exception e) {
            log.debug("[消息留存] 读取留存天数失败，用默认 30: {}", e.getMessage());
        }
        return 30;
    }
}
