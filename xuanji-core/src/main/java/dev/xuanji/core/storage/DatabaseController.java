package dev.xuanji.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 数据库浏览接口 — 直接展示表数据（不再查 INFORMATION_SCHEMA 的列结构，H2 兼容性问题多）。
 * 双数据源：业务库（@Primary）+ 日志库（@Qualifier("logJdbcTemplate")），本控制器合并两者视图。
 *
 * <pre>
 * GET /xuanji/api/db/tables          — 两个库的全部表名（带 source 标记）
 * GET /xuanji/api/db/rows?table=     — 指定表全部行（限制 1000），业务库优先、日志库兜底
 * GET /xuanji/api/db/query?sql=      — 只读 SQL 查询，业务库优先、日志库兜底
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/xuanji/api/db")
public class DatabaseController {

    private final JdbcTemplate jdbc;          // 业务库（@Primary）
    private final JdbcTemplate logJdbc;       // 日志库（消息/事件流水）

    public DatabaseController(JdbcTemplate jdbc,
                             @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        this.jdbc = jdbc;
        this.logJdbc = logJdbc;
    }

    @GetMapping("/tables")
    public List<Map<String, Object>> tables() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.addAll(queryTables(jdbc, "business"));
        list.addAll(queryTables(logJdbc, "log"));
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("TABLE_NAME"))));
        return list;
    }

    private List<Map<String, Object>> queryTables(JdbcTemplate tpl, String source) {
        try {
            List<Map<String, Object>> rows = tpl.queryForList(
                "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
            for (var r : rows) r.put("SOURCE", source);
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 直接查表的全部数据行（最多 1000 行），返回列名 + 行数组，前端用列名动态渲染表头 */
    @GetMapping("/rows")
    public Map<String, Object> rows(@RequestParam String table) {
        List<Map<String, Object>> all = tryQuery("SELECT * FROM " + table + " LIMIT 1000");
        List<String> columns = all.isEmpty()
            ? tryColumns(table)
            : new ArrayList<>(all.get(0).keySet());
        return Map.of("table", table, "columns", columns, "rows", all, "count", all.size());
    }

    @GetMapping("/query")
    public Object query(@RequestParam String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") ||
            upper.startsWith("DELETE") || upper.startsWith("DROP") ||
            upper.startsWith("ALTER") || upper.startsWith("CREATE") ||
            upper.startsWith("TRUNCATE") || upper.startsWith("MERGE")) {
            return Map.of("error", "仅支持只读查询（SELECT）");
        }
        List<Map<String, Object>> rows = tryQuery(sql);
        return Map.of("count", rows.size(), "rows", rows);
    }

    /** 业务库优先；失败（如表在日志库）则查日志库 */
    private List<Map<String, Object>> tryQuery(String sql) {
        try {
            return jdbc.queryForList(sql);
        } catch (Exception e) {
            try {
                return logJdbc.queryForList(sql);
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }

    private List<String> tryColumns(String table) {
        for (JdbcTemplate tpl : List.of(jdbc, logJdbc)) {
            try {
                return tpl.queryForList(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? ORDER BY COLUMN_NAME",
                        table).stream().map(m -> (String) m.get("COLUMN_NAME")).toList();
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("url", "jdbc:h2:file:./data/xuanji/data/xuanji");
        info.put("businessTables", safeCount(jdbc));
        info.put("logTables", safeCount(logJdbc));
        return info;
    }

    private int safeCount(JdbcTemplate tpl) {
        try {
            return tpl.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", Integer.class);
        } catch (Exception e) { return 0; }
    }
}
