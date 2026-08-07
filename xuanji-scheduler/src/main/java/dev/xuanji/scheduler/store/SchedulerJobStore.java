package dev.xuanji.scheduler.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务存储：框架库 xuanji_scheduler_job / xuanji_scheduler_job_log 两表的读写。
 * 返回统一小写驼峰 key（前端直接使用），与 H2 默认大写列名解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerJobStore {

    private final JdbcTemplate jdbc;

    // ═══════════════════ 任务定义 ═══════════════════

    public List<Map<String, Object>> listJobs() {
        return mapRows(jdbc.queryForList(
                "SELECT * FROM xuanji_scheduler_job ORDER BY id DESC"));
    }

    public Map<String, Object> getJob(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM xuanji_scheduler_job WHERE id=?", id);
        return rows.isEmpty() ? null : mapRow(rows.get(0));
    }

    public long createJob(Map<String, Object> j) {
        jdbc.update("""
                INSERT INTO xuanji_scheduler_job
                    (name, job_type, cron, target_platform, target_bot, target_type,
                     target_id, content, enabled, next_run, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """,
                j.get("name"), j.get("jobType"), j.get("cron"),
                str(j.get("targetPlatform")), str(j.get("targetBot")), str(j.get("targetType")),
                str(j.get("targetId")), str(j.get("content")),
                Boolean.TRUE.equals(j.get("enabled")) || j.get("enabled") == null,
                System.currentTimeMillis() / 1000);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM xuanji_scheduler_job", Long.class);
        return id == null ? 0 : id;
    }

    public boolean updateJob(long id, Map<String, Object> j) {
        return jdbc.update("""
                UPDATE xuanji_scheduler_job SET
                    name=?, job_type=?, cron=?, target_platform=?, target_bot=?,
                    target_type=?, target_id=?, content=?, enabled=?, remark=?
                WHERE id=?
                """,
                j.get("name"), j.get("jobType"), j.get("cron"),
                str(j.get("targetPlatform")), str(j.get("targetBot")), str(j.get("targetType")),
                str(j.get("targetId")), str(j.get("content")),
                Boolean.TRUE.equals(j.get("enabled")), str(j.get("remark")), id) > 0;
    }

    public boolean deleteJob(long id) {
        jdbc.update("DELETE FROM xuanji_scheduler_job_log WHERE job_id=?", id);
        return jdbc.update("DELETE FROM xuanji_scheduler_job WHERE id=?", id) > 0;
    }

    public boolean setEnabled(long id, boolean enabled) {
        return jdbc.update("UPDATE xuanji_scheduler_job SET enabled=? WHERE id=?", enabled, id) > 0;
    }

    public void updateNextRun(long id, long nextRunEpochSeconds) {
        jdbc.update("UPDATE xuanji_scheduler_job SET next_run=? WHERE id=?", nextRunEpochSeconds, id);
    }

    /** 记录一次执行结果（成功/失败计数 + 上次执行时间）。 */
    public void touchRun(long id, boolean success) {
        if (success) {
            jdbc.update("UPDATE xuanji_scheduler_job SET run_count=run_count+1, last_run=? WHERE id=?",
                    System.currentTimeMillis() / 1000, id);
        } else {
            jdbc.update("UPDATE xuanji_scheduler_job SET fail_count=fail_count+1, last_run=? WHERE id=?",
                    System.currentTimeMillis() / 1000, id);
        }
    }

    /** 调度扫描：启用的、已到执行时间（next_run=0 首次调度也执行）的任务。 */
    public List<Map<String, Object>> listDueJobs(long nowEpochSeconds) {
        return mapRows(jdbc.queryForList(
                "SELECT * FROM xuanji_scheduler_job WHERE enabled=TRUE AND next_run<=?", nowEpochSeconds));
    }

    // ═══════════════════ 执行日志 ═══════════════════

    public void addLog(long jobId, String jobName, long startMs, long endMs, String status, String result, String error) {
        try {
            jdbc.update("""
                    INSERT INTO xuanji_scheduler_job_log
                        (job_id, job_name, start_time, end_time, elapsed_ms, status, result, error, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    jobId, jobName, startMs / 1000, endMs / 1000, Math.max(0, endMs - startMs),
                    status, truncate(result, 2000), truncate(error, 2000), System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            log.warn("[Scheduler] 执行日志写入失败: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> listLogs(long jobId, int limit) {
        return mapRows(jdbc.queryForList(
                "SELECT * FROM xuanji_scheduler_job_log WHERE job_id=? ORDER BY id DESC LIMIT ?",
                jobId, Math.min(Math.max(limit, 1), 200)));
    }

    // ═══════════════════ 执行分析（批次5） ═══════════════════

    /** 任务执行分析：全局概览 + 按任务聚合（执行数/失败数/成功率/平均耗时/最近状态与错误）。 */
    public Map<String, Object> execStats() {
        Map<String, Object> m = new LinkedHashMap<>();

        // 全局概览（任务定义表）
        try {
            var r = jdbc.queryForMap("""
                SELECT COUNT(*) AS TOTAL,
                       SUM(CASE WHEN enabled THEN 1 ELSE 0 END) AS ENABLED_CNT,
                       SUM(run_count) AS RUNS,
                       SUM(fail_count) AS FAILS
                FROM xuanji_scheduler_job
                """);
            long runs = asLong(r.get("RUNS")), fails = asLong(r.get("FAILS"));
            m.put("jobTotal", asLong(r.get("TOTAL")));
            m.put("jobEnabled", asLong(r.get("ENABLED_CNT")));
            m.put("runTotal", runs);
            m.put("failTotal", fails);
            // run_count = 成功次数、fail_count = 失败次数 → 成功率 = 成功/(成功+失败)
            m.put("successRate", runs + fails == 0 ? 100.0 : Math.round(runs * 10000.0 / (runs + fails)) / 100.0);
        } catch (Exception e) {
            log.debug("[Scheduler] 统计概览失败: {}", e.getMessage());
        }

        // 今日执行（日志表，start_time 为 epoch 秒）
        long todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
        try {
            m.put("todayRuns", asLong(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM xuanji_scheduler_job_log WHERE start_time>=?",
                    Long.class, todayStart)));
            m.put("todayFails", asLong(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM xuanji_scheduler_job_log WHERE start_time>=? AND status='FAIL'",
                    Long.class, todayStart)));
            Double avg = jdbc.queryForObject(
                    "SELECT AVG(elapsed_ms) FROM xuanji_scheduler_job_log WHERE start_time>=?",
                    Double.class, todayStart);
            m.put("todayAvgMs", avg == null ? 0 : Math.round(avg));
        } catch (Exception e) {
            log.debug("[Scheduler] 今日统计失败: {}", e.getMessage());
        }

        // 按任务聚合（日志表）
        List<Map<String, Object>> perJob = new ArrayList<>();
        try {
            List<Map<String, Object>> agg = mapRows(jdbc.queryForList("""
                SELECT job_id, job_name, COUNT(*) AS RUNS,
                       SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END) AS FAILS,
                       AVG(elapsed_ms) AS AVG_MS, MAX(elapsed_ms) AS MAX_MS
                FROM xuanji_scheduler_job_log
                GROUP BY job_id, job_name ORDER BY RUNS DESC
                """));
            // 每个任务最近一次执行的状态/错误
            Map<Long, Map<String, Object>> lastByJob = new LinkedHashMap<>();
            try {
                jdbc.query("""
                        SELECT job_id, status, error, elapsed_ms, start_time FROM xuanji_scheduler_job_log
                        WHERE id IN (SELECT MAX(id) FROM xuanji_scheduler_job_log GROUP BY job_id)
                        """, (java.sql.ResultSet rs) -> {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("lastStatus", rs.getString("status"));
                    lm.put("lastError", rs.getString("error"));
                    lm.put("lastElapsedMs", rs.getLong("elapsed_ms"));
                    lm.put("lastStart", rs.getLong("start_time"));
                    lastByJob.put(rs.getLong("job_id"), lm);
                });
            } catch (Exception e) {
                log.debug("[Scheduler] 最近状态查询失败: {}", e.getMessage());
            }
            for (Map<String, Object> a : agg) {
                long runs = asLong(a.get("runs")), fails = asLong(a.get("fails"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("jobId", a.get("jobId"));
                row.put("jobName", a.get("jobName"));
                row.put("runs", runs);
                row.put("fails", fails);
                row.put("successRate", runs == 0 ? 100.0 : Math.round((runs - fails) * 10000.0 / runs) / 100.0);
                row.put("avgMs", a.get("avgMs") == null ? 0 : Math.round(asDouble(a.get("avgMs"))));
                row.put("maxMs", asLong(a.get("maxMs")));
                Map<String, Object> last = lastByJob.get(asLong(a.get("jobId")));
                if (last != null) {
                    row.put("lastStatus", last.get("lastStatus"));
                    row.put("lastError", last.get("lastError"));
                    row.put("lastElapsedMs", last.get("lastElapsedMs"));
                    row.put("lastStart", last.get("lastStart"));
                }
                perJob.add(row);
            }
        } catch (Exception e) {
            log.debug("[Scheduler] 按任务聚合失败: {}", e.getMessage());
        }
        m.put("perJob", perJob);
        return m;
    }

    /** 执行趋势：近 days 天按日聚合执行数/失败数/平均耗时（D 为 yyyy-MM-dd，前端按日期补全 0 值）。 */
    public List<Map<String, Object>> execTrend(int days) {
        long since = java.time.LocalDate.now().minusDays(Math.max(1, Math.min(days, 90)) - 1L)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
        return mapRows(jdbc.queryForList("""
                SELECT FORMATDATETIME(DATEADD('SECOND', start_time, TIMESTAMP '1970-01-01'), 'yyyy-MM-dd') AS D,
                       COUNT(*) AS RUNS,
                       SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END) AS FAILS,
                       AVG(elapsed_ms) AS AVG_MS
                FROM xuanji_scheduler_job_log
                WHERE start_time>=?
                GROUP BY D ORDER BY D
                """, since));
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ═══════════════════ 工具 ═══════════════════

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Map<String, Object> mapRow(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            m.put(camel(e.getKey()), e.getValue());
        }
        return m;
    }

    /** H2 列名（大写，可带下划线）→ 小写驼峰：ID→id、JOB_TYPE→jobType、TARGET_ID→targetId。 */
    private static String camel(String key) {
        String lower = key.toLowerCase();
        StringBuilder sb = new StringBuilder(lower.length());
        boolean up = false;
        for (char c : lower.toCharArray()) {
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    private static List<Map<String, Object>> mapRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(mapRow(r));
        return out;
    }
}
