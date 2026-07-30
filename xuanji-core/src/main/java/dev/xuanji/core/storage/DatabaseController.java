package dev.xuanji.core.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库管理接口 — 替代 H2 Console，提供表结构查询和数据浏览。
 *
 * <pre>
 * GET /xuanji/api/db/tables          — 所有表名
 * GET /xuanji/api/db/schema?table=   — 指定表的列信息
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

    @GetMapping("/schema")
    public List<Map<String, Object>> schema(@RequestParam String table) {
        return jdbc.queryForList(
            "SELECT COLUMN_NAME, TYPE_NAME, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
            "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? ORDER BY ORDINAL_POSITION", table);
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
