package dev.xuanji.console.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务 — 敏感操作留痕（登录/登出/改口令/SQL 执行/备份恢复/插件卸载等）。
 *
 * <p>落框架库 {@code xuanji_audit} 表（安全中心展示）；写入失败静默降级，不影响主流程。
 */
@Slf4j
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录一条审计。action 用英文短码（LOGIN_OK/LOGIN_FAIL/CHANGE_PIN/SQL_EXEC/BACKUP_CREATE/...）。 */
    public void record(String action, String detail, String ip) {
        try {
            jdbc.update("""
                INSERT INTO xuanji_audit (action, detail, ip, create_time)
                VALUES (?, ?, ?, ?)
            """, action, truncate(detail), ip, System.currentTimeMillis() / 1000L);
        } catch (Exception e) {
            log.debug("[Audit] 写入失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 查询审计（倒序，支持筛选：动作 / IP / 详情关键词 / 时间范围 [startTime, endTime] epoch 秒）。 */
    public List<Map<String, Object>> list(int limit, String action, String ip, String keyword,
                                          Long startTime, Long endTime) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, action, detail, ip, create_time FROM xuanji_audit WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                sql.append(" AND action=?");
                args.add(action.trim());
            }
            if (ip != null && !ip.isBlank()) {
                sql.append(" AND ip LIKE ?");
                args.add("%" + ip.trim() + "%");
            }
            if (keyword != null && !keyword.isBlank()) {
                sql.append(" AND detail LIKE ?");
                args.add("%" + keyword.trim() + "%");
            }
            if (startTime != null && startTime > 0) {
                sql.append(" AND create_time>=?");
                args.add(startTime);
            }
            if (endTime != null && endTime > 0) {
                sql.append(" AND create_time<=?");
                args.add(endTime);
            }
            sql.append(" ORDER BY id DESC LIMIT ?");
            args.add(Math.min(Math.max(limit, 1), 10_000));
            return jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.debug("[Audit] 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 清空审计日志（返回删除行数）。 */
    public int clear() {
        try {
            return jdbc.update("DELETE FROM xuanji_audit");
        } catch (Exception e) {
            log.debug("[Audit] 清空失败: {}", e.getMessage());
            return 0;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1024 ? s.substring(0, 1024) : s;
    }
}
