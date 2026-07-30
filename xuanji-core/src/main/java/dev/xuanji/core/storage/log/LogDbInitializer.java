package dev.xuanji.core.storage.log;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 日志库初始化。
 *
 * <pre>
 * 业务库（xuanji.mv.db）：
 *   xlog_framework      — 框架运行日志（量小，放业务库便于控制台查询）
 *
 * 日志库（xuanji.log.mv.db）：
 *   后续按 bot 实例建模板表：
 *     xlog_qqbot_{appId}_event  — 消息事件流水（量大，独立文件防膨胀）
 *     xlog_qqbot_{appId}_api    — API 调用记录
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogDbInitializer {

    private final JdbcTemplate jdbc;       // 业务库，存 xlog_framework
    private final JdbcTemplate logJdbc;     // 日志库，后续存 bot 事件流水

    @PostConstruct
    void init() {
        // 框架日志表 —— 业务库（量小，方便控制台直接查）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xlog_framework (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                level       VARCHAR(8)   NOT NULL,
                module      VARCHAR(64),
                message     VARCHAR(1024),
                detail      TEXT,
                event_id    VARCHAR(128),
                bot_id      VARCHAR(64),
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        log.info("[LOG-DB] 框架日志表就绪 (xlog_framework)");
        log.info("[LOG-DB] 日志库文件: data/xuanji/xuanji.log.mv.db (bot 事件流水预留)");
    }
}
