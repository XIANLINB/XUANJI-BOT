package dev.xuanji.starter;

import dev.xuanji.adapter.qq.registry.RobotRegistry;
import dev.xuanji.adapter.qq.model.Robot;
import dev.xuanji.adapter.qq.model.RobotEnvironment;
import dev.xuanji.adapter.qq.webhook.SignatureVerifier;
import dev.xuanji.adapter.qq.websocket.QqBotWsManager;
import dev.xuanji.core.config.XuanjiRobotProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Bot 配置管理：读写 xuanji-robots.yml + 热重载。
 */
@RestController
@RequestMapping("/xuanji/api/bot-config")
public class BotConfigController {

    private final ApplicationContext ctx;
    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;

    public BotConfigController(ApplicationContext ctx, JdbcTemplate jdbc, XuanjiRobotProperties robotProperties) {
        this.ctx = ctx;
        this.jdbc = jdbc;
        this.robotProperties = robotProperties;
    }

    /** 获取所有 Bot 配置 */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String yml = Files.readString(RobotsFile.PATH);
            // 简单解析：找 robots: 块下的每个 key
            String[] lines = yml.split("\\n");
            String key = null;
            Map<String, String> kv = null;
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.matches("^\\w[\\w-]*:$") && line.startsWith("    ") && !line.startsWith("    -")) {
                    if (key != null && kv != null) {
                        kv.put("key", key);
                        result.add(new LinkedHashMap<>(kv));
                    }
                    key = t.substring(0, t.length() - 1);
                    kv = new LinkedHashMap<>();
                } else if (key != null && kv != null && line.startsWith("      ")) {
                    String[] parts = t.split(":", 2);
                    if (parts.length == 2) {
                        String k = parts[0].trim();
                        String v = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                        String mappedKey;
                        switch (k) {
                            case "app-id" -> mappedKey = "appId";
                            case "client-secret" -> mappedKey = "clientSecret";
                            case "connection-method" -> mappedKey = "connectionMethod";
                            default -> mappedKey = k;
                        }
                        kv.put(mappedKey, v);
                    }
                }
            }
            if (key != null && kv != null) {
                kv.put("key", key);
                result.add(new LinkedHashMap<>(kv));
            }
        } catch (Exception e) { /* 文件不存在返回空列表 */ }
        return result;
    }

    /** 保存（新增或更新）Bot */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        String sandbox = body.getOrDefault("sandbox", "false");
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");

        if (appId == null || appId.isBlank()) return Map.of("error", "AppID 不能为空");
        if (secret == null || secret.isBlank()) return Map.of("error", "AppSecret 不能为空");

        try {
            String yml;
            try { yml = Files.readString(RobotsFile.PATH); } catch (Exception e) {
                yml = "# 璇玑机器人配置\nxuanji:\n  robots:\n";
            }

            String section = "    " + appId + ":\n"
                    + "      app-id: " + appId + "\n"
                    + "      client-secret: " + secret + "\n"
                    + "      sandbox: " + sandbox + "\n"
                    + "      connection-method: " + method;
            if (!domain.isEmpty()) section += "\n      domain: " + domain;

            // 替换已有或追加
            if (yml.contains("    " + appId + ":")) {
                yml = yml.replaceAll("(?m)^    " + Pattern.quote(appId) + ":.*?(?=\\n    \\w|\\n\\n|\\z)", section);
            } else {
                int idx = yml.indexOf("robots:");
                if (idx < 0) yml += "\n  robots:\n";
                idx = yml.indexOf("robots:");
                yml = yml.substring(0, yml.indexOf("\n", idx) + 1) + section + "\n" + yml.substring(yml.indexOf("\n", idx) + 1);
            }
            RobotsFile.ensureDirs();
            Files.write(RobotsFile.PATH, yml.getBytes(StandardCharsets.UTF_8));
            robotProperties.reloadFromYaml(); // 刷新属性 bean，使后续 getRobots()/findBotKeyByRobotId() 拿到新数据
            return Map.of("status", "ok");
        } catch (Exception e) { return Map.of("error", e.getMessage()); }
    }

    /** 删除 Bot */
    @DeleteMapping("/{appId}")
    public Map<String, Object> delete(@PathVariable String appId) {
        try {
            String yml = Files.readString(RobotsFile.PATH);
            yml = yml.replaceAll("(?m)^    " + Pattern.quote(appId) + ":.*?\\n(?:      .+\\n)*", "");
            Files.write(RobotsFile.PATH, yml.getBytes(StandardCharsets.UTF_8));
            robotProperties.reloadFromYaml();
            return Map.of("status", "ok");
        } catch (Exception e) { return Map.of("error", e.getMessage()); }
    }

    /** 热重载：重新读取 YAML 并重连 Bot */
    @PostMapping("/reload")
    public Map<String, Object> reload() {
        List<Map<String, Object>> bots = list();
        if (bots.isEmpty()) return Map.of("error", "无 Bot 配置");

        try {
            QqBotWsManager ws = ctx.getBean(QqBotWsManager.class);
            RobotRegistry registry = ctx.getBean(RobotRegistry.class);
            int count = 0;
            for (var b : bots) {
                String appId = (String) b.get("appId");
                String secret = (String) b.get("clientSecret");
                String sandbox = (String) b.getOrDefault("sandbox", "false");
                if (appId == null || secret == null) continue;
                String robotId = appId;
                String envType = "true".equals(sandbox) ? "SANDBOX" : "PRODUCTION";

                String method = (String) b.getOrDefault("connectionMethod", "websocket");

                // 1. 注册到 RobotRegistry
                Robot robot = new Robot();
                robot.setId(robotId);
                robot.setAppId(appId);
                robot.setAppSecretEncrypted(secret);
                robot.setRobotName(appId);
                robot.setActiveEnv(envType);
                registry.registerRobot(robot);

                RobotEnvironment envObj = new RobotEnvironment();
                envObj.setRobotId(robotId);
                envObj.setEnvType(envType);
                envObj.setConnectMode(method);
                registry.registerEnvironment(envObj);

                // 2. Webhook 模式：注册 Ed25519 密钥
                if ("webhook".equalsIgnoreCase(method)) {
                    SignatureVerifier sv = ctx.getBean(SignatureVerifier.class);
                    sv.registerSecretPlain(robotId, envType, secret);
                }

                // 3. WebSocket 模式：建立连接
                if ("websocket".equalsIgnoreCase(method)) {
                    ws.registerRobot(robotId, "true".equals(sandbox) ? "sandbox" : "production", appId, secret, 0);
                    ws.start(robotId, "true".equals(sandbox) ? "sandbox" : "production");
                }
                count++;
            }

            // 3.1 清理已删除 bot 的残留 WS 连接与数据库行
            java.util.Set<String> currentAppIds = new java.util.HashSet<>();
            for (var b : bots) {
                String a = (String) b.get("appId");
                if (a != null) currentAppIds.add(a);
            }
            for (var st : ws.getAllStatus()) {
                String key = (String) st.get("key");
                String rid = key.contains(":") ? key.substring(0, key.indexOf(":")) : key;
                if (!currentAppIds.contains(rid)) ws.stop(rid);
            }
            for (var row : jdbc.queryForList("SELECT bot_identifier FROM xuanji_bot")) {
                String id = String.valueOf(row.get("BOT_IDENTIFIER"));
                if (!currentAppIds.contains(id)) {
                    jdbc.update("DELETE FROM xuanji_bot WHERE bot_identifier=?", id);
                }
            }

            // 3. 更新 xuanji_bot 表（与 DatabaseInitializer 列名一致）
            try {
                for (var b : bots) {
                    String appId = (String) b.get("appId");
                    String key = (String) b.getOrDefault("key", appId);
                    jdbc.update(
                        "MERGE INTO xuanji_bot (platform, bot_identifier, bot_key, status) KEY(platform, bot_identifier) VALUES ('qq', ?, ?, 'ONLINE')",
                        appId, key);
                }
            } catch (Exception ignored) {}

            robotProperties.reloadFromYaml(); // 刷新属性 bean，使 handlers/PermissionService/WhitelistStage 拿到最新配置
            return Map.of("status", "ok", "updated", count, "msg", "已重新加载 " + count + " 个 Bot");
        } catch (Exception e) {
            return Map.of("error", "重载失败，确认 xuanji-adapter-qq 已加载: " + e.getMessage());
        }
    }
}
