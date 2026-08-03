package dev.xuanji.core.storage.log;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class LogDbInitializer {

    private final JdbcTemplate jdbc;       // 业务库（@Primary），存 xlog_framework
    private final JdbcTemplate logJdbc;     // 日志库（@Qualifier("logJdbcTemplate")），存 bot 事件流水

    public LogDbInitializer(JdbcTemplate jdbc,
                           @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        this.jdbc = jdbc;
        this.logJdbc = logJdbc;
    }

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

        // OneBot 群聊消息流水
        logJdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_onebot_group_message (
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

        // OneBot 事件流水
        logJdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_onebot_event (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                direction   VARCHAR(8)   NOT NULL,
                bot_id      VARCHAR(64)  NOT NULL,
                event_type  VARCHAR(64),
                group_id    VARCHAR(128),
                raw_json    TEXT,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        log.info("[LOG-DB] OneBot 消息/事件流水表就绪 (group_message / event)");

        // ==================== 流水表查询索引（控制台按 bot/group/时间过滤） ====================
        logJdbc.execute("CREATE INDEX IF NOT EXISTS idx_qqbot_msg ON xuanji_qqbot_group_message (bot_id, group_id, create_time)");
        logJdbc.execute("CREATE INDEX IF NOT EXISTS idx_onebot_msg ON xuanji_onebot_group_message (bot_id, group_id, create_time)");
        logJdbc.execute("CREATE INDEX IF NOT EXISTS idx_qqbot_event ON xuanji_qqbot_event (bot_id, event_type, create_time)");
        logJdbc.execute("CREATE INDEX IF NOT EXISTS idx_onebot_event ON xuanji_onebot_event (bot_id, event_type, create_time)");
        logJdbc.execute("CREATE INDEX IF NOT EXISTS idx_qqbot_c2c ON xuanji_qqbot_c2c_message (bot_id, user_id, create_time)");
        log.info("[LOG-DB] 流水表查询索引就绪");
    }
}
