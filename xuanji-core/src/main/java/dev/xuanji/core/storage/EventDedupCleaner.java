package dev.xuanji.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件去重表清理器 — 定期清理 {@code xuanji_dedup} 中超过 24h 的记录。
 *
 * <p>该表由 DedupStage 写入（order=25 幂等阶段），主键为 event_id。
 * 为避免表无限膨胀，每天清理一次 24h 前的记录。DB 不可用时静默跳过。
 */
@Slf4j
@Component
public class EventDedupCleaner {

    private final JdbcTemplate jdbc;

    public EventDedupCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 86_400_000L, initialDelay = 3_600_000L)
    public void clean() {
        try {
            int n = jdbc.update("DELETE FROM xuanji_dedup " +
                    "WHERE create_time < DATEADD('HOUR', -24, CURRENT_TIMESTAMP)");
            if (n > 0) log.info("[dedup] 清理过期事件去重记录: {} 条", n);
        } catch (Exception e) {
            log.warn("[dedup] 清理 xuanji_dedup 失败: {}", e.getMessage());
        }
    }
}
