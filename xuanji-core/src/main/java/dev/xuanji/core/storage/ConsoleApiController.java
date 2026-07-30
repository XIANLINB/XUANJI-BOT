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
        m.put("uptime", Duration.between(START_TIME, Instant.now()).toString());
        m.put("startTime", START_TIME.toString());
        int tables = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", Integer.class);
        m.put("tables", tables);
        m.put("dbPath", "./data/xuanji/data/xuanji.mv.db");
        return m;
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
        return jdbc.queryForList("SELECT * FROM xuanji_blacklist ORDER BY created_at DESC");
    }

    @DeleteMapping("/blacklist")
    public Map<String, String> removeBlacklist(@RequestParam long id) {
        jdbc.update("DELETE FROM xuanji_blacklist WHERE id=?", id);
        return Map.of("status", "deleted");
    }

    // ==================== 日志 ====================

    @GetMapping("/logs")
    public String tailLogs() {
        try {
            Path logFile = Paths.get("logs", "xuanji-bot.log");
            if (!Files.exists(logFile)) return "日志文件不存在: " + logFile.toAbsolutePath();
            List<String> lines = Files.readAllLines(logFile);
            int start = Math.max(0, lines.size() - 80);
            return String.join("\n", lines.subList(start, lines.size()));
        } catch (IOException e) {
            return "日志读取失败: " + e.getMessage();
        }
    }
}
