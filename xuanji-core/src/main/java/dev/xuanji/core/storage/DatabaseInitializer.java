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
 * <h3>目录层级</h3>
 * <pre>
 * data/xuanji/
 *   data/                    ← 业务数据 H2 文件
 *     xuanji.mv.db           ← 框架全局表
 *     qqbot/{botKey}/        ← 每个 QQ bot 实例目录（群档案备份等）
 *     onebot/{botKey}/
 *     feishu/{botKey}/
 *   log/                     ← 日志 H2 文件
 *     xuanji-log.mv.db
 *   backup/                  ← 自动快照备份
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
        createFrameworkTables();
    }

    private void createDirectories() {
        try {
            Path base = Path.of("data/xuanji");
            Files.createDirectories(base.resolve("data"));
            Files.createDirectories(base.resolve("log"));
            Files.createDirectories(base.resolve("backup"));

            // 按配置文件中的 bot 创建平台级目录
            var bots = robotProperties.getRobots();
            if (bots != null) {
                for (var entry : bots.entrySet()) {
                    String botKey = entry.getKey();
                    String adapter = entry.getValue().getAdapter();
                    if (adapter == null) adapter = "qqbot"; // 默认 QQ

                    Path botDir = base.resolve("data").resolve(adapter).resolve(botKey);
                    Files.createDirectories(botDir.resolve("groups"));
                    log.info("[DB] 创建目录: {}", botDir);
                }
            }
            log.info("[DB] 目录层级已就绪: {}", base.toAbsolutePath());
        } catch (IOException e) {
            log.error("[DB] 创建目录失败: {}", e.getMessage());
        }
    }

    private void createFrameworkTables() {
        log.info("[DB] 开始建表...");

        // 框架域 —— 全局唯一
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_user (
                id              VARCHAR(64) PRIMARY KEY,
                platform        VARCHAR(16) NOT NULL,
                platform_user_id VARCHAR(64) NOT NULL,
                nickname        VARCHAR(128),
                framework_role  VARCHAR(32),
                authority       INT DEFAULT 0,
                created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_user_binding (
                internal_id     VARCHAR(64) NOT NULL,
                platform        VARCHAR(16) NOT NULL,
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
