package dev.xuanji.core.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化器 — 启动时自动建表（框架核心表）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;

    @PostConstruct
    void init() {
        log.info("[DB] 开始建表...");

        // 框架域 —— 全局唯一
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_user (
                id          VARCHAR(64) PRIMARY KEY,
                platform    VARCHAR(16)  NOT NULL,
                platform_user_id VARCHAR(64) NOT NULL,
                nickname    VARCHAR(128),
                framework_role VARCHAR(32),
                authority   INT DEFAULT 0,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_user_binding (
                internal_id  VARCHAR(64) NOT NULL,
                platform     VARCHAR(16) NOT NULL,
                platform_user_id VARCHAR(64) NOT NULL,
                PRIMARY KEY (platform, platform_user_id)
            )
        """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_plugin_kv (
                plugin_id   VARCHAR(128) NOT NULL,
                kv_key      VARCHAR(256) NOT NULL,
                kv_value    TEXT,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (plugin_id, kv_key)
            )
        """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_event_dedup (
                event_id    VARCHAR(128) PRIMARY KEY,
                platform    VARCHAR(16) NOT NULL,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_blacklist (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                scope       VARCHAR(64) NOT NULL,
                target_type VARCHAR(16) NOT NULL,
                target_id   VARCHAR(128) NOT NULL,
                reason      VARCHAR(512),
                expires_at  TIMESTAMP,
                created_by  VARCHAR(64),
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        log.info("[DB] 建表完成");
    }
}
