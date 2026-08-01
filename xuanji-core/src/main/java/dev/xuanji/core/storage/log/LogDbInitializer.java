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

        // ==================== 日志库 — Bot 消息/事件流水 ====================

        // 群聊消息流水
        logJdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_group_message (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                direction   VARCHAR(8)   NOT NULL,
                bot_id      VARCHAR(64)  NOT NULL,
                group_id    VARCHAR(128),
                member_id   VARCHAR(128),
                msg_type    VARCHAR(32),
                content     TEXT,
                raw_json    TEXT,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 单聊消息流水
        logJdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_c2c_message (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                direction   VARCHAR(8)   NOT NULL,
                bot_id      VARCHAR(64)  NOT NULL,
                user_id     VARCHAR(128),
                msg_type    VARCHAR(32),
                content     TEXT,
                raw_json    TEXT,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 事件流水
        logJdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_event (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                direction   VARCHAR(8)   NOT NULL,
                bot_id      VARCHAR(64)  NOT NULL,
                event_type  VARCHAR(64),
                group_id    VARCHAR(128),
                raw_json    TEXT,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        log.info("[LOG-DB] QQ Bot 消息/事件流水表就绪 (group_message / c2c_message / event)");
    }
}
