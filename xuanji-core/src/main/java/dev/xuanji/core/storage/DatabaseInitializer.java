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
 *   xuanji_dedup      — 事件幂等去重（TTL 24h 定时清理）
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

        // ==================== 框架域（v3.3 收敛） ====================

        // 1. Bot 实例注册表（instance_id = 真实 appId / selfId，跨平台唯一索引）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_bot (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                platform    VARCHAR(16)  NOT NULL,
                instance_id VARCHAR(128) NOT NULL,
                bot_key     VARCHAR(128),
                status      VARCHAR(16)  DEFAULT 'OFFLINE',
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_bot_platform_id ON xuanji_bot (platform, instance_id)");

        // 2. 全局 KV 配置（唯一真相源之一）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_config (
                config_key   VARCHAR(128) PRIMARY KEY,
                config_value TEXT,
                updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 3. 每机器人配置（EAV：bot_key / config_key / config_value）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_bot_setting (
                bot_key      VARCHAR(128) NOT NULL,
                config_key   VARCHAR(128) NOT NULL,
                config_value TEXT,
                updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (bot_key, config_key)
            )
        """);

        // 3.5 群级配置（EAV：bot_key / group_id / config_key / config_value，三级配置的最小粒度）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_group_setting (
                bot_key      VARCHAR(128) NOT NULL,
                group_id     VARCHAR(128) NOT NULL,
                config_key   VARCHAR(128) NOT NULL,
                config_value TEXT,
                updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (bot_key, group_id, config_key)
            )
        """);

        // 4. 事件幂等去重（event_id 512，适配长 ID）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_dedup (
                event_id    VARCHAR(512) PRIMARY KEY,
                platform    VARCHAR(16)  NOT NULL,
                create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 5. 插件注册表
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_plugin (
                plugin_id   VARCHAR(128) PRIMARY KEY,
                enabled     TINYINT DEFAULT 1,
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 6. 插件 KV 存储（兼容旧 xuanji_plugin_kv 用法）
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

        // 7. 机器人主人（每 bot 唯一）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_bot_owner (
                bot_key      VARCHAR(128) PRIMARY KEY,
                owner_openid VARCHAR(128) NOT NULL,
                create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // 8. 黑名单（bot_key / group_id / user_id 唯一）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_blacklist (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128) NOT NULL,
                user_id     VARCHAR(128) NOT NULL,
                reason      VARCHAR(512),
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_blacklist ON xuanji_blacklist (bot_key, group_id, user_id)");

        // 8.1 黑名单操作日志（风控中心时间线：拉黑/解除均留痕）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_blacklist_log (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128) NOT NULL,
                user_id     VARCHAR(128) NOT NULL,
                action      VARCHAR(16)  NOT NULL,
                reason      VARCHAR(512),
                create_time BIGINT       DEFAULT 0
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_blacklist_log ON xuanji_blacklist_log (bot_key, create_time)");

        // 9. 插件-机器人绑定
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_plugin_binding (
                plugin_id  VARCHAR(128) NOT NULL,
                platform   VARCHAR(32)  NOT NULL,
                bot_key    VARCHAR(128) NOT NULL,
                created_at BIGINT       DEFAULT 0,
                PRIMARY KEY (plugin_id, platform, bot_key)
            )
        """);

        // 10. 审计日志（安全中心：登录/登出/改PIN/SQL/备份恢复/插件卸载等敏感操作留痕）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_audit (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                action      VARCHAR(64)  NOT NULL,
                detail      VARCHAR(1024),
                ip          VARCHAR(64),
                create_time BIGINT       DEFAULT 0
            )
        """);

        // 11. 定时任务定义（xuanji-scheduler 模块）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_scheduler_job (
                id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                name            VARCHAR(128) NOT NULL,
                job_type        VARCHAR(32)  NOT NULL,
                cron            VARCHAR(64)  NOT NULL,
                target_platform VARCHAR(32)  DEFAULT '',
                target_bot      VARCHAR(128) DEFAULT '',
                target_type     VARCHAR(16)  DEFAULT '',
                target_id       VARCHAR(256) DEFAULT '',
                content         TEXT         DEFAULT '',
                enabled         BOOLEAN      DEFAULT TRUE,
                last_run        BIGINT       DEFAULT 0,
                next_run        BIGINT       DEFAULT 0,
                run_count       BIGINT       DEFAULT 0,
                fail_count      BIGINT       DEFAULT 0,
                remark          VARCHAR(256) DEFAULT '',
                created_at      BIGINT       DEFAULT 0
            )
        """);

        // 12. 定时任务执行日志（xuanji-scheduler 模块）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_scheduler_job_log (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_id      BIGINT       NOT NULL,
                job_name    VARCHAR(128) DEFAULT '',
                start_time  BIGINT       DEFAULT 0,
                end_time    BIGINT       DEFAULT 0,
                elapsed_ms  BIGINT       DEFAULT 0,
                status      VARCHAR(16)  DEFAULT 'SUCCESS',
                result      TEXT         DEFAULT '',
                error       TEXT         DEFAULT '',
                created_at  BIGINT       DEFAULT 0
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_audit_time ON xuanji_audit (create_time DESC)");

        // 13. 预警中心：每 bot 告警配置（预警用户 ID + 开关）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_alert_config (
                id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key       VARCHAR(128) NOT NULL,
                enabled       BOOLEAN      DEFAULT FALSE,
                alert_user_id VARCHAR(128) DEFAULT '',
                created_at    BIGINT       DEFAULT 0,
                updated_at    BIGINT       DEFAULT 0
            )
        """);

        // 14. 预警中心：告警记录
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_alert_record (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                rule        VARCHAR(64)  NOT NULL,
                message     VARCHAR(1024) DEFAULT '',
                create_time BIGINT       DEFAULT 0
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_alert_record_time ON xuanji_alert_record (create_time DESC)");

        // ==================== 向导进度（xuanji_setup） ====================
        // 兼容指南 §一/§三：所有建表统一放在 DatabaseInitializer，使用完整 5 列 schema
        // （与 SetupController 的 CREATE TABLE IF NOT EXISTS 完全一致，幂等不冲突）。
        // SetupController 内的 ALTER COLUMN IF NOT EXISTS 仍作为防御兜底保留，
        // 用于补齐历史上可能用 2 列 schema 建过的旧库，彻底消除对初始化顺序的依赖。
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_setup (
                id          INT          PRIMARY KEY,
                pin_salt    VARCHAR(64),
                pin_hash    VARCHAR(128),
                step        INT          DEFAULT 0,
                completed   BOOLEAN      DEFAULT FALSE
            )
        """);

        // ==================== 旧结构演进（幂等补列，v3.3 收敛） ====================

        // 旧 xuanji_bot 用 bot_identifier，新库用 instance_id —— 迁移旧表数据
        migrateLegacyTables();

        log.info("[DB] {}", firstRun ? "框架初始化建表完成（v3.3 收敛）" : "建表已完成（已存在）");
    }

    /**
     * 旧版框架表迁移：v3.3 之前 xuanji_bot 使用 bot_identifier 列、xuanji_setting/xuanji_bot_config
     * 已被 xuanji_config / xuanji_bot_setting 取代 —— 仅对旧库执行，新库无操作。
     */
    private void migrateLegacyTables() {
        try {
            // 旧 xuanji_bot 补 instance_id 并回填
            jdbc.execute("ALTER TABLE xuanji_bot ADD COLUMN IF NOT EXISTS instance_id VARCHAR(128)");
            jdbc.execute("UPDATE xuanji_bot SET instance_id = bot_identifier WHERE instance_id IS NULL OR instance_id = ''");
            // 旧列兜底：新库没有 bot_identifier 列，此语句仅对旧库生效（列不存在时 H2 抛错被忽略）
            try {
                jdbc.execute("ALTER TABLE xuanji_bot DROP COLUMN IF EXISTS bot_identifier");
            } catch (Exception ignored) {
            }
            // 旧 xuanji_setting → xuanji_config（只迁移仍存在的旧表）
            try {
                jdbc.execute("""
                    INSERT INTO xuanji_config (config_key, config_value)
                    SELECT setting_key, setting_value FROM xuanji_setting
                    WHERE NOT EXISTS (SELECT 1 FROM xuanji_config WHERE xuanji_config.config_key = xuanji_setting.setting_key)
                """);
            } catch (Exception ignored) {
            }
            // 旧 xuanji_bot_config（固定列）→ xuanji_bot_setting（EAV）
            try {
                jdbc.execute("""
                    INSERT INTO xuanji_bot_setting (bot_key, config_key, config_value)
                    SELECT bot_key, 'client_secret', client_secret FROM xuanji_bot_config
                    WHERE client_secret IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM xuanji_bot_setting WHERE xuanji_bot_setting.bot_key = xuanji_bot_config.bot_key AND config_key='client_secret')
                """);
            } catch (Exception ignored) {
            }
            // 迁移完成后删除旧表
            for (String t : new String[]{"xuanji_setting", "xuanji_bot_config"}) {
                try {
                    jdbc.execute("DROP TABLE IF EXISTS " + t);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("[DB] 旧表迁移跳过（新库）: {}", e.getMessage());
        }
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
                    MERGE INTO xuanji_bot (platform, instance_id, bot_key, status)
                    KEY (platform, instance_id) VALUES (?, ?, ?, ?)
                """, platform, appId, botKey, "ONLINE");
                log.info("[DB] Bot 已注册: platform={}, id={}, key={}", platform, appId, botKey);
            } catch (Exception e) {
                log.warn("[DB] Bot 注册失败: key={}, error={}", botKey, e.getMessage());
            }
        }
    }
}
