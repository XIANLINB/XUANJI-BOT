package dev.xuanji.core.storage;

import dev.xuanji.core.config.XuanjiRobotProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据库与目录结构初始化器。
 *
 * <h3>数据域划分</h3>
 * <pre>
 * 框架域（全局一份）：
 *   xuanji_bot              — Bot 实例注册表
 *   xuanji_user_binding     — 跨平台账号绑定
 *   xuanji_plugin_kv        — 插件 KV 存储
 *   xuanji_event_dedup      — 事件幂等去重（TTL 24h 定时清理）
 *   xuanji_blacklist        — 黑名单（framework / bot / group 三级 scope）
 *   xuanji_super_admin      — 群超管
 *
 * 平台域（按平台分表）：
 *   xuanji_qqbot_user       — QQ 机器人用户档案
 *   xuanji_onebot_user      — OneBot 用户档案（预留）
 *   xuanji_qqbot_group      — QQ 机器人群档案
 *   xuanji_qqbot_group_member — QQ 机器人群成员
 * </pre>
 *
 * <h3>目录层级</h3>
 * <pre>
 * data/xuanji/
 *   data/                    ← 业务 H2 文件
 *   log/                     ← 日志 H2 文件
 *   backup/                  ← 自动快照
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;

    @PostConstruct
    void init() {
        createDirectories();
        createAllTables();
        registerBotsFromConfig();
    }

    private void createDirectories() {
        try {
            Path base = Path.of("data/xuanji");
            Files.createDirectories(base.resolve("data"));
            Files.createDirectories(base.resolve("log"));
            Files.createDirectories(base.resolve("backup"));

            var bots = robotProperties.getRobots();
            if (bots != null) {
                for (var entry : bots.entrySet()) {
                    String adapter = entry.getValue().getAdapter();
                    if (adapter == null || adapter.isEmpty()) adapter = "qqbot";
                    String appId = entry.getValue().getAppId();
                    if (appId == null || appId.isEmpty()) appId = entry.getKey();

                    Path botDataDir = base.resolve("data").resolve(adapter).resolve(appId);
                    Files.createDirectories(botDataDir.resolve("groups"));
                    Path botLogDir = base.resolve("log").resolve(adapter).resolve(appId);
                    Files.createDirectories(botLogDir);
                    log.info("[DB] 创建目录: data={}, log={}", botDataDir, botLogDir);
                }
            }
            log.info("[DB] 目录层级已就绪: {}", base.toAbsolutePath());
        } catch (IOException e) {
            log.error("[DB] 创建目录失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private void createAllTables() {
        boolean firstRun;
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM xuanji_bot", Integer.class);
            firstRun = false;
        } catch (Exception e) {
            firstRun = true;
        }

        log.info("[DB] {}建表...", firstRun ? "首次" : "");

        // ==================== 框架域 ====================

        // 1. Bot 实例注册表
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_bot (
                id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                platform        VARCHAR(16)  NOT NULL,
                bot_identifier  VARCHAR(64)  NOT NULL,
                bot_key         VARCHAR(64),
                status          VARCHAR(16)  DEFAULT 'OFFLINE',
                create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 2. 跨平台账号绑定（同一真人在不同平台的 ID 归一）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_user_binding (
                internal_id      VARCHAR(64) NOT NULL,
                platform         VARCHAR(16) NOT NULL,
                platform_user_id VARCHAR(64) NOT NULL,
                create_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (platform, platform_user_id)
            )
        """);

        // 3. 插件 KV 存储
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_plugin_kv (
                plugin_id   VARCHAR(128) NOT NULL,
                kv_key      VARCHAR(256) NOT NULL,
                kv_value    TEXT,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (plugin_id, kv_key)
            )
        """);

        // 4. 事件幂等去重（Pipeline 接入后才有数据；定时清理 24h 前记录）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_event_dedup (
                event_id    VARCHAR(128) PRIMARY KEY,
                platform    VARCHAR(16)  NOT NULL,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 5. 黑名单
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_blacklist (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                scope       VARCHAR(64)  NOT NULL,
                target_type VARCHAR(16)  NOT NULL,
                target_id   VARCHAR(128) NOT NULL,
                reason      VARCHAR(512),
                expires_at  TIMESTAMP,
                created_by  VARCHAR(64),
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 6. 群超管
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_super_admin (
                id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key       VARCHAR(64)  NOT NULL,
                group_id      VARCHAR(128) NOT NULL,
                member_openid VARCHAR(128) NOT NULL,
                create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ==================== 平台域 ====================

        // 7. QQ 机器人用户档案
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_user (
                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id           VARCHAR(64)  NOT NULL,
                platform_user_id VARCHAR(64)  NOT NULL,
                internal_id      VARCHAR(64),
                nickname         VARCHAR(128),
                framework_role   VARCHAR(32),
                authority        INT          DEFAULT 0,
                is_deleted       TINYINT      DEFAULT 0,
                create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 8. OneBot 用户档案（预留）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_onebot_user (
                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id           VARCHAR(64)  NOT NULL,
                platform_user_id VARCHAR(64)  NOT NULL,
                internal_id      VARCHAR(64),
                nickname         VARCHAR(128),
                framework_role   VARCHAR(32),
                authority        INT          DEFAULT 0,
                is_deleted       TINYINT      DEFAULT 0,
                create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 9. QQ 机器人群档案
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_group (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id      VARCHAR(64)  NOT NULL,
                group_id    VARCHAR(128) NOT NULL,
                group_name  VARCHAR(256),
                status      VARCHAR(16)  DEFAULT 'active',
                is_deleted  TINYINT      DEFAULT 0,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 10. QQ 机器人群成员
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_group_member (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id      VARCHAR(64)  NOT NULL,
                group_id    VARCHAR(128) NOT NULL,
                member_id   VARCHAR(128) NOT NULL,
                role        VARCHAR(32),
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 11. QQ Bot 信息表（启动时从 /users/@me 接口同步）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_qqbot_info (
                id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id             VARCHAR(64)  NOT NULL,
                bot_key            VARCHAR(64)  NOT NULL,
                username           VARCHAR(128),
                avatar             VARCHAR(512),
                bot                BOOLEAN,
                union_openid       VARCHAR(128),
                union_user_account VARCHAR(128),
                share_url          VARCHAR(512),
                welcome_msg        VARCHAR(1024),
                raw_json           TEXT,
                updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (bot_id)
            )
        """);

        log.info("[DB] {}", firstRun ? "框架初始化建表完成（11 张）" : "建表已完成（已存在）");
    }

    /** 从 YAML 配置中注册 Bot 实例到 xuanji_bot 表 */
    private void registerBotsFromConfig() {
        var bots = robotProperties.getRobots();
        if (bots == null || bots.isEmpty()) {
            log.info("[DB] 未配置 Bot 实例，跳过注册");
            return;
        }

        for (var entry : bots.entrySet()) {
            String botKey = entry.getKey();
            String appId = entry.getValue().getAppId();
            String adapter = entry.getValue().getAdapter();
            if (adapter == null || adapter.isEmpty()) adapter = "qqbot";
            if (appId == null || appId.isEmpty()) appId = botKey;

            String platform = switch (adapter) {
                case "qqbot" -> "qq";
                case "onebot" -> "onebot";
                case "feishu" -> "feishu";
                default -> adapter;
            };

            try {
                jdbc.update("""
                    MERGE INTO xuanji_bot (platform, bot_identifier, bot_key, status)
                    KEY (platform, bot_identifier) VALUES (?, ?, ?, ?)
                """, platform, appId, botKey, "ONLINE");
                log.info("[DB] Bot 已注册: platform={}, id={}, key={}", platform, appId, botKey);
            } catch (Exception e) {
                log.warn("[DB] Bot 注册失败: key={}, error={}", botKey, e.getMessage());
            }
        }
    }
}
