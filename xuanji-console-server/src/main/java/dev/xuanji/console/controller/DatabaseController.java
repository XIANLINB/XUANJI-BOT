package dev.xuanji.console.controller;

import dev.xuanji.core.storage.BotDataSourceRegistry;
import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库浏览接口 — 直接展示表数据。多数据源：
 * <ul>
 *   <li>业务库（@Primary）+ 日志库（@Qualifier("logJdbcTemplate")）：璇玑框架层表（xuanji_* / xlog_*）</li>
 *   <li>平台共享库（data/{platform}/{platform}.mv.db）：跨 bot 共享的平台表（qqbot_bot / qqbot_botinfo / qqbot_message 等）</li>
 *   <li>各机器人实例库（data/{platform}/{instanceId}/data/{instanceId}.mv.db）：平台表（qqbot_* / onebot_*）</li>
 * </ul>
 *
 * <pre>
 * GET /xuanji/api/v1/db/tables                — 全部表名（含框架库 + 平台共享库 + 各 bot 实例库），带 SOURCE 标记
 * GET /xuanji/api/v1/db/rows?table=&source=   — 指定表全部行（限制 1000）；source 缺省查业务库
 * GET /xuanji/api/v1/db/query?sql=&source=    — 只读 SQL 查询
 * </pre>
 *
 * <p>SOURCE 取值：{@code business}（框架业务库）、{@code log}（框架日志库）、
 * {@code qqbot:shared} / {@code onebot:shared}（平台共享库，跨 bot 公共表）、
 * {@code qqbot:{appid}} / {@code onebot:{selfId}}（各机器人实例库）。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/db")
public class DatabaseController {

    private final JdbcTemplate jdbc;          // 业务库（@Primary）
    private final JdbcTemplate logJdbc;       // 日志库（消息/事件流水）
    private final BotDataSourceRegistry botRegistry;
    private final dev.xuanji.console.service.AuditService auditService;

    public DatabaseController(JdbcTemplate jdbc,
                             @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc,
                             BotDataSourceRegistry botRegistry,
                             dev.xuanji.console.service.AuditService auditService) {
        this.jdbc = jdbc;
        this.logJdbc = logJdbc;
        this.botRegistry = botRegistry;
        this.auditService = auditService;
    }

    /** 列出全部表：框架库（business/log）+ 平台共享库（qqbot:shared/onebot:shared）+ 各 bot 实例库。 */
    @GetMapping("/tables")
    public List<Map<String, Object>> tables() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.addAll(queryTables(jdbc, "business"));
        list.addAll(queryTables(logJdbc, "log"));

        // 从所有 bot 实例里收集出现的 platform 集合（避免硬编码 qqbot/onebot）
        Set<String> platforms = new LinkedHashSet<>();
        for (Map<String, Object> inst : botInstances()) {
            platforms.add(String.valueOf(inst.get("PLATFORM")));
        }

        // 1) 平台共享库（如 qqbot:shared，存放跨 bot 公共表：qqbot_bot / qqbot_botinfo / qqbot_message 等）
        for (String platform : platforms) {
            try {
                JdbcTemplate sharedTpl = botRegistry.forPlatform(platform);
                for (var r : sharedTpl.queryForList(
                        "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME")) {
                    r.put("SOURCE", platform + ":shared");
                    list.add(r);
                }
            } catch (Exception e) {
                log.debug("[DB] 列举共享库表失败: {} - {}", platform, e.getMessage());
            }
        }

        // 2) 各 bot 实例库
        for (Map<String, Object> inst : botInstances()) {
            String platform = String.valueOf(inst.get("PLATFORM"));
            String id = String.valueOf(inst.get("INSTANCE_ID"));
            try {
                JdbcTemplate bt = botRegistry.forInstance(platform, id);
                List<Map<String, Object>> rows = bt.queryForList(
                    "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
                for (var r : rows) {
                    r.put("SOURCE", platform + ":" + id);
                    list.add(r);
                }
            } catch (Exception e) {
                log.debug("[DB] 列举实例库表失败: {}/{} - {}", platform, id, e.getMessage());
            }
        }
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("TABLE_NAME"))));
        return list;
    }

//    /**
//     * 发现所有 bot 实例（platform + instanceId）。
//     * 来源一：框架 {@code xuanji_bot} 表的 (platform, instance_id)；
//     * 来源二：兜底扫描 {@code data/{qqbot,onebot}/*/data/*.mv.db}，避免框架表尚未落库时漏列。
//     */
    private List<Map<String, Object>> botInstances() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> r : jdbc.queryForList(
                    "SELECT platform AS PLATFORM, instance_id AS INSTANCE_ID FROM xuanji_bot")) {
                map.put(r.get("PLATFORM") + ":" + r.get("INSTANCE_ID"), r);
            }
        } catch (Exception ignored) {}
        // 兜底：扫描 data/{qqbot,onebot}/*/data/*.mv.db
        try {
            Path data = Path.of("data");
            if (Files.exists(data)) {
                try (var ps = Files.newDirectoryStream(data)) {
                    for (Path platDir : ps) {
                        if (!Files.isDirectory(platDir)) continue;
                        String platform = platDir.getFileName().toString();
                        if (!platform.equals("qqbot") && !platform.equals("onebot")) continue;
                        try (var is = Files.newDirectoryStream(platDir)) {
                            for (Path instDir : is) {
                                if (!Files.isDirectory(instDir)) continue;
                                String id = instDir.getFileName().toString();
                                if (Files.exists(instDir.resolve("data").resolve(id + ".mv.db"))) {
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    entry.put("PLATFORM", platform);
                                    entry.put("INSTANCE_ID", id);
                                    map.putIfAbsent(platform + ":" + id, entry);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(map.values());
    }

    /** 查指定表全部数据行（最多 1000 行）。source 缺省=business；log=日志库；platform:id=实例库。 */
    @GetMapping("/rows")
    public Map<String, Object> rows(@RequestParam String table,
                                    @RequestParam(defaultValue = "") String source) {
        JdbcTemplate tpl = resolve(source);
        if (tpl == null) {
            return Map.of("table", table, "columns", List.of(), "rows", List.of(),
                    "count", 0, "error", "无效的 source: " + source);
        }
        List<Map<String, Object>> all = tryQuery(tpl, "SELECT * FROM " + table + " LIMIT 1000");
        List<String> columns = all.isEmpty()
            ? tryColumns(tpl, table)
            : new ArrayList<>(all.get(0).keySet());
        return Map.of("table", table, "source", source, "columns", columns, "rows", all, "count", all.size());
    }

    @GetMapping("/query")
    public Object query(@RequestParam String sql,
                        @RequestParam(defaultValue = "") String source,
                        jakarta.servlet.http.HttpServletRequest request) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") ||
            upper.startsWith("DELETE") || upper.startsWith("DROP") ||
            upper.startsWith("ALTER") || upper.startsWith("CREATE") ||
            upper.startsWith("TRUNCATE") || upper.startsWith("MERGE")) {
            return Map.of("error", "仅支持只读查询（SELECT）");
        }
        JdbcTemplate tpl = resolve(source);
        if (tpl == null) return Map.of("error", "无效的 source: " + source);
        // 查询留痕（安全中心审计）
        String detail = sql.trim();
        if (detail.length() > 120) detail = detail.substring(0, 120) + "…";
        auditService.record("SQL_QUERY", "source=" + (source.isBlank() ? "business" : source) + " | " + detail,
                request.getRemoteAddr());
        List<Map<String, Object>> rows = tryQuery(tpl, sql);
        return Map.of("count", rows.size(), "rows", rows);
    }

    /** 按 source 解析目标 JdbcTemplate。支持 {@code qqbot:shared} / {@code onebot:shared}（平台共享库）。 */
    private JdbcTemplate resolve(String source) {
        if (source == null || source.isBlank()) return jdbc;
        if ("business".equalsIgnoreCase(source)) return jdbc;   // 框架业务库
        if ("log".equalsIgnoreCase(source)) return logJdbc;     // 框架日志库
        int idx = source.indexOf(':');
        if (idx <= 0) return null;
        String platform = source.substring(0, idx);
        String id = source.substring(idx + 1);
        try {
            // 平台共享库（id="shared"）→ 跨 bot 公共表
            if ("shared".equalsIgnoreCase(id)) {
                return botRegistry.forPlatform(platform);
            }
            return botRegistry.forInstance(platform, id);
        } catch (Exception e) {
            return null;
        }
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

    /** 业务库优先；失败（如表在日志库）则查日志库 */
    private List<Map<String, Object>> tryQuery(JdbcTemplate tpl, String sql) {
        try {
            return tpl.queryForList(sql);
        } catch (Exception e) {
            log.debug("[DB] 查询失败: {} - {}", sql, e.getMessage());
            return List.of();
        }
    }

    private List<String> tryColumns(JdbcTemplate tpl, String table) {
        try {
            return tpl.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? ORDER BY COLUMN_NAME",
                    table).stream().map(m -> (String) m.get("COLUMN_NAME")).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("url", "jdbc:h2:file:./data/xuanji/data/xuanji");
        info.put("businessTables", safeCount(jdbc));
        info.put("logTables", safeCount(logJdbc));
        info.put("botInstances", botInstances().size());
        return info;
    }

    private int safeCount(JdbcTemplate tpl) {
        try {
            return tpl.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", Integer.class);
        } catch (Exception e) { return 0; }
    }
}
