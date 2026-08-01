package dev.xuanji.starter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/xuanji/api/setup")
public class SetupController {

    private static final Path LOCK_FILE = Paths.get("data", "wizard.lock");

    private final JdbcTemplate jdbc;

    public SetupController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        try { jdbc.execute("CREATE TABLE IF NOT EXISTS xuanji_setup (id INT PRIMARY KEY, admin_password VARCHAR(128), step INT DEFAULT 0)"); } catch (Exception ignored) {}
        try { jdbc.update("MERGE INTO xuanji_setup (id) VALUES (1)"); } catch (Exception ignored) {}
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean done = Files.exists(LOCK_FILE);
        try {
            Integer step = jdbc.queryForObject("SELECT step FROM xuanji_setup WHERE id=1", Integer.class);
            return Map.of("completed", done, "step", done ? 3 : (step != null ? step : 0));
        } catch (Exception e) { return Map.of("completed", done, "step", 0); }
    }

    @PostMapping("/password")
    public Map<String, Object> setPassword(@RequestBody Map<String, String> body) {
        String pwd = body.get("password");
        if (pwd == null || pwd.length() < 4) return Map.of("error", "密码至少 4 位");
        jdbc.update("MERGE INTO xuanji_setup (id, admin_password, step) KEY(id) VALUES (1,?,1)", sha256(pwd));
        return Map.of("status", "ok");
    }

    @PostMapping("/bot")
    public Map<String, Object> setupBot(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        boolean sandbox = "true".equals(body.get("sandbox"));
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");

        if (appId == null || appId.isBlank()) return Map.of("error", "AppID 不能为空");
        if (secret == null || secret.isBlank()) return Map.of("error", "AppSecret 不能为空");

        try {
            String yml = "# 璇玑机器人配置\nxuanji:\n  robots:\n    " + appId + ":\n"
                    + "      app-id: " + appId + "\n"
                    + "      client-secret: " + secret + "\n"
                    + "      sandbox: " + sandbox + "\n"
                    + "      connection-method: " + method;
            if (!domain.isEmpty()) yml += "\n      domain: " + domain;
            yml += "\n";
            RobotsFile.ensureDirs();
            Files.write(RobotsFile.PATH, yml.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            jdbc.update("UPDATE xuanji_setup SET step=2 WHERE id=1");
            return Map.of("status", "ok");
        } catch (Exception e) { return Map.of("error", "写入失败: " + e.getMessage()); }
    }

    @PostMapping("/complete")
    public Map<String, Object> complete() {
        try {
            Files.createDirectories(LOCK_FILE.getParent());
            Files.write(LOCK_FILE, String.valueOf(System.currentTimeMillis()).getBytes());
            jdbc.update("UPDATE xuanji_setup SET step=3 WHERE id=1");
            return Map.of("status", "ok");
        } catch (Exception e) { return Map.of("error", e.getMessage()); }
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return s; }
    }
}
