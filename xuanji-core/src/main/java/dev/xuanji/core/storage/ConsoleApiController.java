package dev.xuanji.core.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 控制台 API：系统状态、日志、权限管理。
 */
@RestController
@RequestMapping("/xuanji/api/console")
@RequiredArgsConstructor
public class ConsoleApiController {

    private final JdbcTemplate jdbc;
    private static final Instant START_TIME = Instant.now();

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        Duration d = Duration.between(START_TIME, Instant.now());
        m.put("uptime", formatDuration(d));
        m.put("uptimeSeconds", d.getSeconds());
        m.put("startTime", START_TIME.toString());
        int tables = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", Integer.class);
        m.put("tables", tables);
        m.put("dbPath", "./data/xuanji/data/xuanji.mv.db");
        m.put("memUsed", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + "MB");
        return m;
    }

    private static String formatDuration(Duration d) {
        long s = d.getSeconds();
        if (s < 60) return s + "秒";
        if (s < 3600) return (s / 60) + "分" + (s % 60) + "秒";
        long h = s / 3600;
        long m = (s % 3600) / 60;
        return h + "小时" + m + "分";
    }

    // ==================== 超管管理 ====================

    @GetMapping("/super-admins")
    public List<Map<String, Object>> listSuperAdmins() {
        return jdbc.queryForList("SELECT * FROM xuanji_super_admin ORDER BY bot_key, group_id");
    }

    @DeleteMapping("/super-admin")
    public Map<String, String> removeSuperAdmin(@RequestParam String botKey,
                                                 @RequestParam String groupId,
                                                 @RequestParam String memberOpenid) {
        jdbc.update("DELETE FROM xuanji_super_admin WHERE bot_key=? AND group_id=? AND member_openid=?",
                botKey, groupId, memberOpenid);
        return Map.of("status", "deleted");
    }

    // ==================== 黑名单管理 ====================

    @GetMapping("/blacklists")
    public List<Map<String, Object>> listBlacklists() {
        return jdbc.queryForList("SELECT * FROM xuanji_blacklist ORDER BY create_time DESC");
    }

    @DeleteMapping("/blacklist")
    public Map<String, String> removeBlacklist(@RequestParam long id) {
        jdbc.update("DELETE FROM xuanji_blacklist WHERE id=?", id);
        return Map.of("status", "deleted");
    }

    // ==================== 日志 ====================

    /** 最多展示 200 行，避免上千万行卡顿；通过 ?lines=N 可自定义 */
    @GetMapping("/logs")
    public String tailLogs(@RequestParam(defaultValue = "200") int lines) {
        try {
            Path logFile = Paths.get("logs", "xuanji-bot.log");
            if (!Files.exists(logFile)) return "日志文件不存在: " + logFile.toAbsolutePath();
            List<String> all = Files.readAllLines(logFile);
            int max = Math.min(lines, 500); // 单次上限 500 行
            int start = Math.max(0, all.size() - max);
            return String.join("\n", all.subList(start, all.size()));
        } catch (IOException e) {
            return "日志读取失败: " + e.getMessage();
        }
    }

    /** 获取日志文件大小（用于判断是否需要清理） */
    @GetMapping("/log-size")
    public Map<String, Object> logSize() {
        try {
            Path logFile = Paths.get("logs", "xuanji-bot.log");
            if (!Files.exists(logFile)) return Map.of("exists", false);
            long bytes = Files.size(logFile);
            long mb = bytes / 1024 / 1024;
            return Map.of("exists", true, "sizeMB", mb, "sizeBytes", bytes);
        } catch (IOException e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== 消息流水 ====================

    /** 最近 200 条消息事件（环形缓冲区） */
    private static final List<Map<String, Object>> recentEvents =
            Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_EVENTS = 200;

    /** 记录一条消息事件（框架内部调用，不是 HTTP API） */
    public static void recordEvent(String direction, String type, String user,
                                    String groupId, String content, String detail) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("time", java.time.LocalTime.now().toString().substring(0, 12));
        evt.put("direction", direction);  // IN / OUT
        evt.put("type", type);            // text / markdown / image / audio / video
        evt.put("user", user);
        evt.put("groupId", groupId);
        evt.put("content", content);
        evt.put("detail", detail);
        synchronized (recentEvents) {
            recentEvents.add(evt);
            while (recentEvents.size() > MAX_EVENTS) recentEvents.removeFirst();
        }
    }

    @GetMapping("/events")
    public List<Map<String, Object>> events() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private final dev.xuanji.core.storage.log.FrameworkLogger frameworkLogger;

    /** 查询框架运行日志 */
    @GetMapping("/framework-logs")
    public List<Map<String, Object>> frameworkLogs(@RequestParam(defaultValue = "50") int limit) {
        return jdbc.queryForList(
            "SELECT * FROM xlog_framework ORDER BY create_time DESC LIMIT ?",
            Math.min(limit, 200));
    }
}
