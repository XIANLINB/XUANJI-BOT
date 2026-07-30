package dev.xuanji.core.storage.log;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 框架日志写入器 —— 将关键事件写入 xlog_framework 表。
 */
@Component
@RequiredArgsConstructor
public class FrameworkLogger {

    private final JdbcTemplate jdbc;  // 业务库 — xlog_framework 在此

    public void info(String module, String message) {
        insert("INFO", module, message, null, null, null);
    }

    public void warn(String module, String message) {
        insert("WARN", module, message, null, null, null);
    }

    public void error(String module, String message, String detail) {
        insert("ERROR", module, message, detail, null, null);
    }

    /** 带事件 ID 和 bot ID 的日志（后续接入 Pipeline 后使用） */
    public void event(String module, String message, String eventId, String botId) {
        insert("INFO", module, message, null, eventId, botId);
    }

    private void insert(String level, String module, String message,
                        String detail, String eventId, String botId) {
        try {
            jdbc.update("""
                INSERT INTO xlog_framework (level, module, message, detail, event_id, bot_id)
                VALUES (?, ?, ?, ?, ?, ?)
            """, level, module, message, detail, eventId, botId);
        } catch (Exception ignored) {
            // 日志库写入失败不抛异常，不影响主流程
        }
    }
}
