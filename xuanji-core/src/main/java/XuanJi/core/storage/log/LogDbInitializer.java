package XuanJi.core.storage.log;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 框架日志库初始化。
 *
 * <p>框架日志库 {@code data/xuanji/xuanji.log.mv.db} 只承载框架级日志：
 * <pre>
 *   xlog_framework — 框架运行日志（控制台「数据中心 → 框架日志」查询/清空）
 * </pre>
 *
 * <p>消息 / 事件流水已迁移到 per-bot 日志库（{@code data/{platform}/{appid}/log/{appid}.log.mv.db}，
 * 见 {@code BotSchemaProvider#initLogSchema}），框架日志库不再冗余存放平台流水。
 */
@Slf4j
@Component
public class LogDbInitializer {

    private final JdbcTemplate logJdbc;     // 日志库（@Qualifier("logJdbcTemplate")），存 xlog_framework

    public LogDbInitializer(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        this.logJdbc = logJdbc;
    }

    @PostConstruct
    void init() {
        // 框架日志表 —— 日志库（量小；控制台按 level/module/bot 过滤查询）
        logJdbc.execute("""
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
    }
}
