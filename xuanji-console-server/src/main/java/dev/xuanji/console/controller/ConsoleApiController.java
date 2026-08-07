package dev.xuanji.console.controller;

import dev.xuanji.core.storage.MessageEventRecorder;
import dev.xuanji.core.web.XuanjiApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * 控制台 API：系统状态、日志、权限管理。
 */
@XuanjiApi
@RestController
@RequestMapping("/console")
@RequiredArgsConstructor
public class ConsoleApiController {

    private final JdbcTemplate jdbc;
    private final MessageEventRecorder eventRecorder;
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
        Runtime rt = Runtime.getRuntime();
        m.put("memUsed", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "MB");
        m.put("memMax", rt.maxMemory() / 1024 / 1024 + "MB");
        m.put("cpuCores", rt.availableProcessors());
        m.put("threads", Thread.activeCount());
        // 虚拟线程数（JDK 21+）
        try {
            var fb = Thread.class.getMethod("isVirtual");
            long vt = Thread.getAllStackTraces().keySet().stream().filter(t -> {
                try { return (boolean) fb.invoke(t); } catch (Exception e) { return false; }
            }).count();
            m.put("virtualThreads", vt);
        } catch (Exception ignored) {}
        // H2 数据库大小
        try {
            java.nio.file.Path db = java.nio.file.Paths.get("data", "xuanji", "xuanji.mv.db");
            if (java.nio.file.Files.exists(db)) m.put("dbSize", java.nio.file.Files.size(db) / 1024 + "KB");
            java.nio.file.Path logDb = java.nio.file.Paths.get("data", "xuanji", "xuanji-log.mv.db");
            if (java.nio.file.Files.exists(logDb)) m.put("logDbSize", java.nio.file.Files.size(logDb) / 1024 + "KB");
        } catch (Exception ignored) {}
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

    // ==================== 日志 ====================

    /** 最多展示 200 行，避免上千万行卡顿；通过 ?lines=N 可自定义 */
    @GetMapping("/logs")
    public String tailLogs(@RequestParam(defaultValue = "200") int lines) {
        try {
            Path logFile = Paths.get("logs", "xuanji-bot.log");
            if (!Files.exists(logFile)) return "日志文件不存在: " + logFile.toAbsolutePath();
            int max = Math.min(lines, 500); // 单次上限 500 行
            // 流式读取，仅保留尾部 max 行，避免把整个日志文件读进内存（大日志内存炸弹）
            Deque<String> tail = new ArrayDeque<>(max);
            try (Stream<String> stream = Files.lines(logFile, StandardCharsets.UTF_8)) {
                for (Iterator<String> it = stream.iterator(); it.hasNext(); ) {
                    tail.addLast(it.next());
                    if (tail.size() > max) tail.removeFirst();
                }
            }
            return String.join("\n", tail);
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

    // ==================== 消息流水（实时事件面板，委托 MessageEventRecorder） ====================

    /** 最近 200 条消息事件（环形缓冲区，经注入的 {@link MessageEventRecorder}） */
    @GetMapping("/events")
    public List<Map<String, Object>> events() {
        return eventRecorder.snapshot();
    }

    /** 查询框架运行日志 */
    @GetMapping("/framework-logs")
    public List<Map<String, Object>> frameworkLogs(@RequestParam(defaultValue = "50") int limit) {
        return jdbc.queryForList(
            "SELECT * FROM xlog_framework ORDER BY create_time DESC LIMIT ?",
            Math.min(limit, 200));
    }
}
