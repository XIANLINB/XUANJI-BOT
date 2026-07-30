package dev.xuanji.core.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 数据库浏览接口 — 直接展示表数据（不再查 INFORMATION_SCHEMA 的列结构，H2 兼容性问题多）。
 *
 * <pre>
 * GET /xuanji/api/db/tables          — 所有表名
 * GET /xuanji/api/db/rows?table=     — 指定表全部行（限制 1000）
 * GET /xuanji/api/db/query?sql=      — 只读 SQL 查询
 * </pre>
 */
@RestController
@RequestMapping("/xuanji/api/db")
@RequiredArgsConstructor
public class DatabaseController {

    private final JdbcTemplate jdbc;

    @GetMapping("/tables")
    public List<Map<String, Object>> tables() {
        return jdbc.queryForList(
            "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
    }

    /** 直接查表的全部数据行（最多 1000 行），返回列名 + 行数组，前端用列名动态渲染表头 */
    @GetMapping("/rows")
    public Map<String, Object> rows(@RequestParam String table) {
        // 先取一行看有哪些列
        List<Map<String, Object>> all = jdbc.queryForList(
            "SELECT * FROM " + table + " LIMIT 1000");
        List<String> columns = all.isEmpty()
            ? jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? ORDER BY COLUMN_NAME",
                table).stream().map(m -> (String) m.get("COLUMN_NAME")).toList()
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
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return Map.of("count", rows.size(), "rows", rows);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("url", "jdbc:h2:file:./data/xuanji/data/xuanji");
        info.put("tables", jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", Integer.class));
        return info;
    }
}
