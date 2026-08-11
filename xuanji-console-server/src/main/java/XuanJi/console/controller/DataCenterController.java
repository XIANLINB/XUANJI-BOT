package XuanJi.console.controller;

import XuanJi.console.security.SessionStore;
import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 控制台 · 数据中心：聚合统计 / 缓存清理 / 媒体文件存储。
 *
 * <p>统计跨所有机器人（botRefs × PlatformDataProvider.stats）累加；
 * 媒体目录为 {@code data/xuanji/media}（框架级），删除仅限目录内文件（防路径穿越）。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console/data")
public class DataCenterController {

    private static final String MEDIA_DIR = "data/xuanji/media";
    private static final int MAX_LIST = 1000;

    private final ConsoleQueryService queryService;
    private final JdbcTemplate jdbc;
    private final JdbcTemplate logJdbc;
    private final SessionStore sessionStore;
    private final XuanJi.console.service.AuditService auditService;

    public DataCenterController(ConsoleQueryService queryService,
                                JdbcTemplate jdbc,
                                @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc,
                                SessionStore sessionStore,
                                XuanJi.console.service.AuditService auditService) {
        this.queryService = queryService;
        this.jdbc = jdbc;
        this.logJdbc = logJdbc;
        this.sessionStore = sessionStore;
        this.auditService = auditService;
    }

    // ═══════════════════ 聚合统计 ═══════════════════

    /** 跨所有 bot 聚合：热力图 + 消息类型分布 + 活跃群/用户/机器人 TOP + 方向 + 事件类型。days 默认 30。
     * botKey 可选：传值则只统计该机器人数据（按 appId 过滤），不传则聚合全部机器人。 */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "30") int days,
                                     @RequestParam(required = false) String botKey) {
        long since = System.currentTimeMillis() / 1000 - (long) Math.min(Math.max(days, 1), 90) * 86400L;

        long[][] heat = new long[7][24];                     // [dow-1][hour]
        Map<String, Long> typeCnt = new HashMap<>();
        Map<String, long[]> groupCnt = new HashMap<>();      // id -> [cnt]
        Map<String, long[]> userCnt = new HashMap<>();
        Map<String, long[]> botCnt = new HashMap<>();
        Map<String, Long> dirCnt = new HashMap<>();          // IN/OUT
        Map<String, Long> evtCnt = new HashMap<>();
        Map<String, String> groupNames = new HashMap<>();
        Map<String, String> userNames = new HashMap<>();
        Map<String, String> botNames = new HashMap<>();      // appId -> bot_name

        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            // 按机器人筛选：botKey 不为空且不匹配该 ref（按 appId/instanceId/botKey 任一匹配），跳过
            if (botKey != null && !botKey.isBlank()) {
                String id = ref.instanceId();
                boolean match = botKey.equals(id) || botKey.equals(ref.botKey());
                if (!match) continue;
            }
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            Map<String, Object> st = p.stats(ref.instanceId(), since);
            // ... 累加到 heatmap/typeDist/activeGroups/activeUsers/dirCnt/evtCnt
            for (Map<String, Object> row : castList(st.get("heatmap"))) {
                int dow = (int) num(row.get("DOW"), row.get("dow")) - 1;
                int hr = (int) num(row.get("HR"), row.get("hr"));
                if (dow >= 0 && dow < 7 && hr >= 0 && hr < 24) {
                    heat[dow][hr] += num(row.get("CNT"), row.get("cnt"));
                }
            }
            for (Map<String, Object> row : castList(st.get("typeDist"))) {
                String t = str(row.get("MSG_TYPE"), row.get("msgType"));
                if (t == null || t.isBlank()) t = "unknown";
                typeCnt.merge(t, num(row.get("CNT"), row.get("cnt")), Long::sum);
            }
            for (Map<String, Object> row : castList(st.get("activeGroups"))) {
                String id = str(row.get("GID"), row.get("id"));
                if (id == null || id.isBlank()) continue;
                groupCnt.computeIfAbsent(id, k -> new long[1])[0] += num(row.get("CNT"), row.get("cnt"));
                String name = str(row.get("GNAME"), row.get("name"));
                if (name != null && !name.isBlank()) groupNames.putIfAbsent(id, name);
            }
            for (Map<String, Object> row : castList(st.get("activeUsers"))) {
                String id = str(row.get("UID"), row.get("id"));
                if (id == null || id.isBlank()) continue;
                userCnt.computeIfAbsent(id, k -> new long[1])[0] += num(row.get("CNT"), row.get("cnt"));
                String name = str(row.get("NICK"), row.get("name"));
                if (name != null && !name.isBlank()) userNames.putIfAbsent(id, name);
            }
            // activeBots：按 ref 的 appId（=instanceId）累加该 bot 总消息数（取 typeDist 总和）
            long botMsgTotal = 0;
            for (Map<String, Object> row : castList(st.get("typeDist"))) {
                botMsgTotal += num(row.get("CNT"), row.get("cnt"));
            }
            String botAppId = ref.instanceId();
            botCnt.computeIfAbsent(botAppId, k -> new long[1])[0] += botMsgTotal;
            if (ref.botName() != null && !ref.botName().isBlank()) botNames.putIfAbsent(botAppId, ref.botName());

            for (Map<String, Object> row : castList(st.get("directionDist"))) {
                String d = str(row.get("DIRECTION"), row.get("direction"));
                if (d == null || d.isBlank()) continue;
                dirCnt.merge(d, num(row.get("CNT"), row.get("cnt")), Long::sum);
            }
            for (Map<String, Object> row : castList(st.get("eventTypeDist"))) {
                String t = str(row.get("ETYPE"), row.get("eventType"));
                if (t == null || t.isBlank()) t = "unknown";
                evtCnt.merge(t, num(row.get("CNT"), row.get("cnt")), Long::sum);
            }
        }

        List<long[]> heatRows = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (heat[d][h] > 0) heatRows.add(new long[]{d, h, heat[d][h]});
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heatmap", heatRows);
        m.put("typeDist", topType(typeCnt));
        m.put("activeGroups", topTargets(groupCnt, groupNames));
        m.put("activeUsers", topTargets(userCnt, userNames));
        m.put("activeBots", topBots(botCnt, botNames));
        m.put("directionDist", topType(dirCnt));
        m.put("eventTypeDist", topType(evtCnt));
        m.put("days", days);
        m.put("botKey", botKey == null ? "" : botKey);
        return m;
    }

    private static List<Map<String, Object>> topType(Map<String, Long> cnt) {
        return cnt.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("name", e.getKey());
                    r.put("value", e.getValue());
                    return r;
                }).toList();
    }

    private static List<Map<String, Object>> topTargets(Map<String, long[]> cnt, Map<String, String> names) {
        return cnt.entrySet().stream()
                .sorted(Map.Entry.<String, long[]>comparingByValue((a, b) -> Long.compare(b[0], a[0])))
                .limit(10)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", e.getKey());
                    r.put("name", names.getOrDefault(e.getKey(), ""));
                    r.put("value", e.getValue()[0]);
                    return r;
                }).toList();
    }

    /** 活跃机器人 TOP：id 是 bot 实例 ID（如 1/2），前端通过 BotStore.nameMap 映射到 appId/名称；若无映射则显示 ID。 */
    private static List<Map<String, Object>> topBots(Map<String, long[]> cnt, Map<String, String> names) {
        return cnt.entrySet().stream()
                .sorted(Map.Entry.<String, long[]>comparingByValue((a, b) -> Long.compare(b[0], a[0])))
                .limit(10)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", e.getKey());
                    r.put("name", names.getOrDefault(e.getKey(), ""));
                    r.put("value", e.getValue()[0]);
                    return r;
                }).toList();
    }

    // ═══════════════════ 缓存清理 ═══════════════════

    /** 缓存状态：每项独立字段（rows 大小 + 说明），前端可选择性清理。
     * 清理安全等级：safe=可一键清理无任何影响；lossy=数据可重建；caution=业务数据建议保留。
     * scope：framework=框架级（跨 bot 共享）/ bot=机器人级（每个 bot 一个实例库，受 botKey 影响）。
     * botKey 可选：传值则 bot 级项只统计该机器人数据，框架级项不受影响。 */
    @GetMapping("/cache")
    public Map<String, Object> cache(@RequestParam(required = false) String botKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 消息去重缓存（框架级，自动重建，安全）
        m.put("dedup", item("消息去重缓存", "xuanji_dedup 表", "safe", "framework",
                "近期事件 ID 去重记录，框架自动重建；清空后近期少量事件可能重复处理一次", null,
                count(jdbc, "SELECT COUNT(*) FROM xuanji_dedup")));
        // 登录会话（框架级，内存，重启失效）
        m.put("sessions", item("登录会话", "内存 ConcurrentHashMap", "safe", "framework",
                "控制台登录会话；清空后所有用户需重新登录", null,
                sessionStore.count()));
        // 框架日志（框架级，xlog_framework）
        m.put("frameworkLog", item("框架日志", "xlog_framework 表", "safe", "framework",
                "框架级 INFO/WARN/ERROR 日志；清空后历史日志丢失，磁盘空间释放", null,
                count(logJdbc, "SELECT COUNT(*) FROM xlog_framework")));
        // 审计日志（框架级，xuanji_audit）
        m.put("auditLog", item("审计日志", "xuanji_audit 表", "safe", "framework",
                "用户登录/操作审计；清空后历史审计记录丢失", null,
                count(jdbc, "SELECT COUNT(*) FROM xuanji_audit")));
        // 事件流水（机器人级，qqbot_event per-bot 日志库）
        m.put("eventLog", item("系统事件流水", "qqbot_event 表", "lossy", "bot",
                "加群/退群/加好友/撤回等系统事件；清空后历史事件不可恢复", null,
                countInstancesLogFiltered(botKey, "SELECT COUNT(*) FROM qqbot_event")));
        // 调度任务日志（框架级）
        m.put("schedulerLog", item("调度任务日志", "xuanji_scheduler_job_log 表", "safe", "framework",
                "每个定时任务每次执行的记录；清空后历史执行记录丢失", null,
                count(jdbc, "SELECT COUNT(*) FROM xuanji_scheduler_job_log")));
        // 黑名单操作日志（框架级）
        m.put("blacklistLog", item("黑名单操作日志", "xuanji_blacklist_log 表", "safe", "framework",
                "黑名单拉黑/解除的审计；清空后历史操作记录丢失", null,
                count(jdbc, "SELECT COUNT(*) FROM xuanji_blacklist_log")));
        // 告警记录（框架级）
        m.put("alertRecord", item("告警记录", "xuanji_alert_record 表", "safe", "framework",
                "7 类检查器命中后的告警记录；清空后历史告警丢失", null,
                count(jdbc, "SELECT COUNT(*) FROM xuanji_alert_record")));
        // 消息历史（机器人级，per-bot 日志库流水，谨慎清理）
        m.put("messages", item("消息历史", "qqbot_message 表", "caution", "bot",
                "已处理的所有消息；清空后所有历史消息不可恢复，群聊监控/聊天窗口将无数据", null,
                countInstancesLogFiltered(botKey, "SELECT COUNT(*) FROM qqbot_message")));
        // 媒体文件（框架级共享存储，不区分 bot）
        m.put("mediaFiles", item("媒体文件", "data/xuanji/media 目录", "caution", "framework",
                "已发送/接收的图片语音视频文件（框架级共享，内容哈希去重）；清空后释放磁盘，媒体链接失效", null,
                countMediaFiles()));
        return m;
    }

    /** 单个缓存项结构。 */
    private static Map<String, Object> item(String name, String source, String level, String scope, String desc, Object extra, long rows) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("source", source);
        r.put("level", level);  // safe | lossy | caution
        r.put("scope", scope);  // framework | bot
        r.put("desc", desc);
        r.put("rows", rows);
        return r;
    }

    /** 跨所有 bot 实例库聚合查询某表的总行数；传 botKey 则只查该机器人实例库。 */
    private long countInstancesFiltered(String botKey, String sql) {
        long total = 0;
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (botKey != null && !botKey.isBlank()) {
                boolean match = botKey.equals(ref.instanceId()) || botKey.equals(ref.botKey());
                if (!match) continue;
            }
            try {
                Integer n = queryService.qIntForInstance(ref.instanceId(), sql);
                if (n != null) total += n;
            } catch (Exception ignored) { /* 单实例库失败跳过 */ }
        }
        return total;
    }

    /** 统计 data/xuanji/media 目录下文件总数（不递归子目录，按文件名清单扫）。 */
    private long countMediaFiles() {
        long total = 0;
        try {
            java.nio.file.Path mediaRoot = mediaRoot();
            if (java.nio.file.Files.isDirectory(mediaRoot)) {
                try (var fs = java.nio.file.Files.list(mediaRoot)) {
                    total = fs.count();
                }
            }
        } catch (Exception ignored) { /* 目录不存在等 */ }
        return total;
    }

    /** 按勾选类别清理缓存。每项单独 try-catch，单项失败不影响其他项。
     * categories 用逗号分隔字符串传递（兼容无 body 的 POST）。
     * botKey 可选：传值则机器人级项（eventLog/messages）只清理该机器人的实例库，框架级项不受影响。 */
    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache(@RequestParam(value = "categories", required = false) String categories,
                                          @RequestParam(value = "botKey", required = false) String botKey,
                                          jakarta.servlet.http.HttpServletRequest req) {
        java.util.List<String> cats = (categories == null || categories.isBlank())
                ? java.util.List.of()
                : java.util.Arrays.asList(categories.split(","));
        if (cats.isEmpty()) {
            return Map.of("status", "error", "msg", "请至少选择一项要清理的缓存");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        java.util.List<String> cleared = new java.util.ArrayList<>();
        java.util.Map<String, String> errors = new LinkedHashMap<>();
        for (String cat : cats) {
            String trimmed = cat.trim();
            if (trimmed.isEmpty()) continue;
            try {
                long n = clearOne(trimmed, botKey);
                cleared.add(trimmed + "(" + n + ")");
            } catch (Exception e) {
                errors.put(trimmed, e.getMessage());
                log.warn("[DataCenter] 清理 {} 失败: {}", trimmed, e.getMessage());
            }
        }
        result.put("cleared", cleared);
        if (!errors.isEmpty()) result.put("errors", errors);
        result.put("msg", cleared.isEmpty() ? "未清理任何项" : ("已清理: " + String.join(", ", cleared)));
        auditService.record("CACHE_CLEAR", "清理缓存: " + String.join(", ", cleared)
                + (botKey != null && !botKey.isBlank() ? " (bot=" + botKey + ")" : ""), req);
        return result;
    }

    /** 单项清理，返回受影响行数。botKey 影响机器人级项。 */
    private long clearOne(String cat, String botKey) {
        switch (cat) {
            case "dedup":
                return jdbc.update("DELETE FROM xuanji_dedup");
            case "sessions":
                int n = sessionStore.count();
                sessionStore.clearAll();
                return n;
            case "frameworkLog":
                return logJdbc.update("DELETE FROM xlog_framework");
            case "auditLog":
                return jdbc.update("DELETE FROM xuanji_audit");
            case "schedulerLog":
                return jdbc.update("DELETE FROM xuanji_scheduler_job_log");
            case "blacklistLog":
                return jdbc.update("DELETE FROM xuanji_blacklist_log");
            case "alertRecord":
                return jdbc.update("DELETE FROM xuanji_alert_record");
            case "eventLog":
                return deleteFromInstancesLogFiltered(botKey, "DELETE FROM qqbot_event");
            case "messages":
                return deleteFromInstancesLogFiltered(botKey, "DELETE FROM qqbot_message");
            case "mediaFiles":
                return deleteMediaFiles();
            default:
                throw new IllegalArgumentException("未知类别: " + cat);
        }
    }

    /** 跨所有 bot 实例库删除（事件流水/消息历史）；传 botKey 则只删该机器人实例库。 */
    private long deleteFromInstancesFiltered(String botKey, String sql) {
        long total = 0;
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (botKey != null && !botKey.isBlank()) {
                boolean match = botKey.equals(ref.instanceId()) || botKey.equals(ref.botKey());
                if (!match) continue;
            }
            try {
                int n = queryService.jdbcUpdateForInstance(ref.instanceId(), sql);
                total += n;
            } catch (Exception ignored) { /* 单实例失败跳过 */ }
        }
        return total;
    }

    /** 跨所有 bot 实例日志库聚合查询（message/event 流水）；传 botKey 则只查该机器人日志库。 */
    private long countInstancesLogFiltered(String botKey, String sql) {
        long total = 0;
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (botKey != null && !botKey.isBlank()) {
                boolean match = botKey.equals(ref.instanceId()) || botKey.equals(ref.botKey());
                if (!match) continue;
            }
            try {
                Integer n = queryService.qIntForLog(ref.instanceId(), sql);
                if (n != null) total += n;
            } catch (Exception ignored) { /* 单日志库失败跳过 */ }
        }
        return total;
    }

    /** 跨所有 bot 实例日志库删除（事件流水/消息历史）；传 botKey 则只删该机器人日志库。 */
    private long deleteFromInstancesLogFiltered(String botKey, String sql) {
        long total = 0;
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (botKey != null && !botKey.isBlank()) {
                boolean match = botKey.equals(ref.instanceId()) || botKey.equals(ref.botKey());
                if (!match) continue;
            }
            try {
                int n = queryService.jdbcUpdateForLog(ref.instanceId(), sql);
                total += n;
            } catch (Exception ignored) { /* 单日志库失败跳过 */ }
        }
        return total;
    }

    /** 删除 data/xuanji/media 下所有文件（框架级共享存储）。 */
    private long deleteMediaFiles() {
        long total = 0;
        try {
            java.nio.file.Path mediaRoot = mediaRoot();
            if (java.nio.file.Files.isDirectory(mediaRoot)) {
                try (var fs = java.nio.file.Files.list(mediaRoot)) {
                    for (var f : fs.toArray(java.nio.file.Path[]::new)) {
                        try { java.nio.file.Files.deleteIfExists(f); total++; }
                        catch (Exception ignored) { /* 单文件失败跳过 */ }
                    }
                }
            }
        } catch (Exception ignored) { /* 目录不存在 */ }
        return total;
    }

    // ═══════════════════ 媒体文件存储 ═══════════════════

    /** 媒体文件浏览：统计 + 文件列表（相对路径/类型/大小/时间/URL）。
     * 返回 byTypeSize（各类型占用字节数）+ quotaBytes（配额，默认 4GB）+ usedBytes。
     * 媒体为框架级共享存储（data/xuanji/media），不区分 bot。 */
    @GetMapping("/files")
    public Map<String, Object> files() {
        Path root = mediaRoot();
        if (!Files.isDirectory(root)) {
            return Map.of("total", 0, "sizeBytes", 0L, "quotaBytes", mediaQuotaBytes(), "byType", Map.of(), "byTypeSize", Map.of(), "files", List.of());
        }
        long total = 0;
        long size = 0;
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byTypeSize = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 6)) {
            List<Path> files = walk.filter(Files::isRegularFile).limit(MAX_LIST).toList();
            for (Path f : files) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                long sz;
                long mt;
                try {
                    sz = Files.size(f);
                    mt = Files.getLastModifiedTime(f).toMillis() / 1000;
                } catch (IOException e) {
                    continue;
                }
                total++;
                size += sz;
                String type = fileType(rel);
                byType.merge(type, 1L, Long::sum);
                byTypeSize.merge(type, sz, Long::sum);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("path", rel);
                r.put("type", type);
                r.put("size", sz);
                r.put("mtime", mt);
                r.put("url", "/media/" + rel);
                list.add(r);
            }
        } catch (IOException e) {
            log.warn("[DataCenter] 扫描媒体目录失败: {}", e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("sizeBytes", size);
        m.put("quotaBytes", mediaQuotaBytes());
        m.put("byType", byType);
        m.put("byTypeSize", byTypeSize);
        m.put("files", list);
        return m;
    }

    /** 媒体配额（默认 4GB，读取 xuanji_config media.storage.max_bytes）。 */
    private long mediaQuotaBytes() {
        try {
            String v = queryService.configValue("media.storage.max_bytes");
            if (v != null && !v.isBlank()) {
                long n = Long.parseLong(v.trim());
                if (n > 0) return n;
            }
        } catch (Exception ignored) { /* 用默认 */ }
        return 4L * 1024 * 1024 * 1024;
    }

    /** 按类型删除媒体文件（type=image|voice|video|file，空=全部）。媒体框架级共享不区分 bot。 */
    @PostMapping("/files/clear")
    public Map<String, Object> clearFiles(@RequestParam(required = false) String type,
                                          jakarta.servlet.http.HttpServletRequest req) {
        Path root = mediaRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return Map.of("status", "ok", "msg", "无文件", "removed", 0, "freedBytes", 0L);
        }
        String want = type == null || type.isBlank() ? null : type.trim().toLowerCase();
        long removed = 0;
        long freed = 0;
        try (Stream<Path> walk = Files.walk(root, 6)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                if (want != null && !fileType(rel).equals(want)) continue;
                try {
                    freed += Files.size(f);
                    Files.deleteIfExists(f);
                    removed++;
                } catch (IOException ignored) { /* 单文件失败跳过 */ }
            }
        } catch (IOException e) {
            return Map.of("status", "error", "msg", e.getMessage());
        }
        log.info("[DataCenter] 按类型删除媒体: type={}, 删除{}个, 释放{}B", want, removed, freed);
        auditService.record("FILE_CLEAR", "按类型删除媒体 type=" + (want == null ? "全部" : want)
                + "，删除 " + removed + " 个，释放 " + fmtBytes(freed), req);
        return Map.of("status", "ok", "msg", "已删除 " + removed + " 个文件，释放 " + fmtBytes(freed),
                "removed", removed, "freedBytes", freed);
    }

    private static String fmtBytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.2f MB", n / 1024.0 / 1024.0);
    }

    /** 删除媒体目录内指定文件（仅相对路径，防穿越）。 */
    @DeleteMapping("/files")
    public Map<String, Object> deleteFile(@RequestParam String path,
                                          jakarta.servlet.http.HttpServletRequest req) {
        Path root = mediaRoot().toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            return Map.of("status", "error", "msg", "非法路径（禁止越出媒体目录）");
        }
        try {
            boolean ok = Files.deleteIfExists(target);
            if (ok) {
                log.info("[DataCenter] 删除媒体文件: {}", path);
                auditService.record("FILE_DELETE", "删除媒体文件: " + path, req);
            }
            return Map.of("status", ok ? "ok" : "error", "msg", ok ? "已删除" : "文件不存在");
        } catch (IOException e) {
            return Map.of("status", "error", "msg", e.getMessage());
        }
    }

    private static Path mediaRoot() {
        return Paths.get(MEDIA_DIR).toAbsolutePath().normalize();
    }

    private static String fileType(String rel) {
        String lower = rel.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) return "image";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".amr")
                || lower.endsWith(".silk") || lower.endsWith(".m4a") || lower.endsWith(".ogg")) return "voice";
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi")
                || lower.endsWith(".webm") || lower.endsWith(".mkv")) return "video";
        return "file";
    }

    private static long count(JdbcTemplate t, String sql) {
        try {
            Long v = t.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static long num(Object... vals) {
        for (Object v : vals) {
            if (v instanceof Number n) return n.longValue();
        }
        return 0;
    }

    private static String str(Object... vals) {
        for (Object v : vals) {
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }
}
