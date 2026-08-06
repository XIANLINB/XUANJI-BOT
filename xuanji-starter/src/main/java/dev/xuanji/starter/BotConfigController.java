/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.adapter.qqbot.model.Robot
 *  dev.xuanji.adapter.qqbot.model.RobotEnvironment
 *  dev.xuanji.adapter.qqbot.registry.RobotRegistry
 *  dev.xuanji.adapter.qqbot.storage.BotInfoSync
 *  dev.xuanji.adapter.qqbot.storage.QqBotRepository
 *  dev.xuanji.adapter.qqbot.webhook.SignatureVerifier
 *  dev.xuanji.adapter.qqbot.websocket.QqBotWsManager
 *  dev.xuanji.core.config.XuanjiRobotProperties
 *  dev.xuanji.core.storage.BotDataSourceRegistry
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.context.ApplicationContext
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package dev.xuanji.starter;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.model.RobotEnvironment;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.adapter.qqbot.storage.BotInfoSync;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.adapter.qqbot.webhook.SignatureVerifier;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.BotDataSourceRegistry;
import dev.xuanji.core.storage.FrameworkBotRepository;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/xuanji/api/bot-config"})
public class BotConfigController {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(BotConfigController.class);
    private final ApplicationContext ctx;
    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final FrameworkBotRepository frameworkBotRepository;
    private final ObjectProvider<BotInfoSync> botInfoSync;

    public BotConfigController(ApplicationContext ctx, XuanjiRobotProperties robotProperties, ObjectProvider<QqBotRepository> qqBotRepository, FrameworkBotRepository frameworkBotRepository, ObjectProvider<BotInfoSync> botInfoSync) {
        this.ctx = ctx;
        this.robotProperties = robotProperties;
        this.qqBotRepository = qqBotRepository;
        this.frameworkBotRepository = frameworkBotRepository;
        this.botInfoSync = botInfoSync;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        QqBotRepository repo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
        if (repo == null) {
            return result;
        }
        for (Map.Entry entry : repo.loadAllBotRows().entrySet()) {
            String appId = (String)entry.getKey();
            Map row = (Map)entry.getValue();
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("key", appId);
            item.put("appId", appId);
            item.put("clientSecret", BotConfigController.str(row, "BOT_CLIENTSECRET"));
            item.put("sandbox", String.valueOf(BotConfigController.bool(row.get("IS_SANDBOX"))));
            item.put("connectionMethod", BotConfigController.defaultIfBlank(BotConfigController.str(row, "CONN_MODE"), "websocket"));
            item.put("domain", BotConfigController.str(row, "WEBHOOK_URL"));
            item.put("status", BotConfigController.defaultIfBlank(BotConfigController.str(row, "STATUS"), "OFFLINE"));
            result.add(item);
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        String sandbox = body.getOrDefault("sandbox", "false");
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");
        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (secret == null || secret.isBlank()) {
            return Map.of("error", "AppSecret \u4e0d\u80fd\u4e3a\u7a7a");
        }
        try {
            QqBotRepository repo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ \u9002\u914d\u5668\u672a\u542f\u7528 (xuanji.qqbot.enabled=false)");
            }
            Map existing = repo.getBotRow(appId);
            String status = BotConfigController.defaultIfBlank(BotConfigController.str(existing, "STATUS"), "ONLINE");
            String webhookUrl = "webhook".equalsIgnoreCase(method) && !domain.isBlank() ? domain : null;
            repo.upsertBot(appId, secret, method, "true".equals(sandbox), status, webhookUrl);
            try {
                this.frameworkBotRepository.upsert("qqbot", appId, "qqbot", status);
            }
            catch (Exception e) {
                log.warn("[BotConfig] \u5199\u5165 xuanji_bot \u5931\u8d25: appId={}, {}", (Object)appId, (Object)e.getMessage());
            }
            this.robotProperties.reload();
            log.info("[BotConfig] \u5df2\u4fdd\u5b58\u673a\u5668\u4eba\u914d\u7f6e: appId={}, mode={}", (Object)appId, (Object)method);
            return Map.of("status", "ok");
        }
        catch (Exception e) {
            log.error("[BotConfig] \u4fdd\u5b58\u673a\u5668\u4eba\u5931\u8d25: appId={}, {}", new Object[]{appId, e.getMessage(), e});
            return Map.of("error", e.getMessage());
        }
    }

    @DeleteMapping(value={"/{appId}"})
    public Map<String, Object> delete(@PathVariable String appId) {
        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        try {
            block27: {
                try {
                    QqBotWsManager ws = (QqBotWsManager)this.ctx.getBeanProvider(QqBotWsManager.class).getIfAvailable();
                    if (ws != null) {
                        ws.stop(appId);
                    }
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u505c\u6b62 WS \u8fde\u63a5\u5931\u8d25(\u53ef\u5ffd\u7565): appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    RobotRegistry registry = (RobotRegistry)this.ctx.getBeanProvider(RobotRegistry.class).getIfAvailable();
                    if (registry != null) {
                        registry.unregisterRobot(appId);
                    }
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u53cd\u6ce8\u518c RobotRegistry \u5931\u8d25(\u53ef\u5ffd\u7565): appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    SignatureVerifier sv = (SignatureVerifier)this.ctx.getBeanProvider(SignatureVerifier.class).getIfAvailable();
                    if (sv != null) {
                        sv.unregister(appId);
                    }
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u79fb\u9664\u7b7e\u540d\u5bc6\u94a5\u5931\u8d25(\u53ef\u5ffd\u7565): appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    BotDataSourceRegistry dsr = (BotDataSourceRegistry)this.ctx.getBean(BotDataSourceRegistry.class);
                    dsr.closeInstance("qqbot", appId);
                    dsr.closeInstance("onebot", appId);
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u5173\u95ed bot \u6570\u636e\u6e90\u5931\u8d25(\u53ef\u5ffd\u7565): appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    this.frameworkBotRepository.delete("qqbot", appId);
                    log.info("[BotConfig] \u5df2\u5220\u9664 xuanji_bot \u8bb0\u5f55: appId={}", (Object)appId);
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u5220\u9664 xuanji_bot \u8bb0\u5f55\u5931\u8d25: appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    QqBotRepository repo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
                    if (repo != null) {
                        repo.deleteBot(appId);
                        log.info("[BotConfig] \u5df2\u5220\u9664\u5e73\u53f0\u5e93\u6863\u6848: appId={}", (Object)appId);
                    }
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u5220\u9664\u5e73\u53f0\u5e93\u6863\u6848\u5931\u8d25(\u53ef\u5ffd\u7565): appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
                try {
                    Path dataRoot = Paths.get("data", new String[0]);
                    if (!Files.exists(dataRoot, new LinkOption[0])) break block27;
                    try (Stream<Path> platforms = Files.list(dataRoot);){
                        platforms.forEach(p -> {
                            Path botDir = p.resolve(appId);
                            if (Files.exists(botDir, new LinkOption[0])) {
                                this.deleteRecursively(botDir);
                                if (Files.exists(botDir, new LinkOption[0])) {
                                    log.warn("[BotConfig] per-bot \u6570\u636e\u76ee\u5f55\u4ecd\u6b8b\u7559\uff08\u53ef\u80fd\u6587\u4ef6\u88ab\u5360\u7528\uff09: {}", (Object)botDir);
                                } else {
                                    log.info("[BotConfig] \u5df2\u5220\u9664 per-bot \u6570\u636e\u76ee\u5f55: {}", (Object)botDir);
                                }
                            }
                        });
                    }
                }
                catch (Exception e) {
                    log.warn("[BotConfig] \u5220\u9664 per-bot \u6570\u636e\u76ee\u5f55\u5931\u8d25: appId={}, {}", (Object)appId, (Object)e.getMessage());
                }
            }
            this.robotProperties.reload();
            log.info("[BotConfig] \u5df2\u5f7b\u5e95\u5220\u9664\u673a\u5668\u4eba: appId={}", (Object)appId);
            return Map.of("status", "ok");
        }
        catch (Exception e) {
            log.error("[BotConfig] \u5220\u9664\u673a\u5668\u4eba\u5931\u8d25: appId={}, {}", new Object[]{appId, e.getMessage(), e});
            return Map.of("error", e.getMessage());
        }
    }

    private void deleteRecursively(Path target) {
        block9: {
            try {
                if (Files.isDirectory(target, new LinkOption[0])) {
                    try (Stream<Path> walk = Files.walk(target, new FileVisitOption[0]);){
                        walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            }
                            catch (IOException iOException) {
                                // empty catch block
                            }
                        });
                        break block9;
                    }
                }
                Files.deleteIfExists(target);
            }
            catch (IOException e) {
                log.warn("[BotConfig] \u9012\u5f52\u5220\u9664\u5931\u8d25: {} - {}", (Object)target, (Object)e.getMessage());
            }
        }
    }

    @PostMapping(value={"/reload"})
    public Map<String, Object> reload() {
        List<Map<String, Object>> bots = this.list();
        if (bots.isEmpty()) {
            return Map.of("error", "\u65e0 Bot \u914d\u7f6e");
        }
        try {
            QqBotWsManager ws = (QqBotWsManager)this.ctx.getBeanProvider(QqBotWsManager.class).getIfAvailable();
            RobotRegistry registry = (RobotRegistry)this.ctx.getBeanProvider(RobotRegistry.class).getIfAvailable();
            SignatureVerifier sv = (SignatureVerifier)this.ctx.getBeanProvider(SignatureVerifier.class).getIfAvailable();
            QqBotRepository repo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ \u9002\u914d\u5668\u672a\u542f\u7528 (xuanji.qqbot.enabled=false)");
            }
            int count = 0;
            for (Map<String, Object> b : bots) {
                String appId = (String)b.get("appId");
                String secret = (String)b.get("clientSecret");
                String sandbox = (String)b.getOrDefault("sandbox", "false");
                if (appId == null || secret == null || secret.isBlank()) continue;
                if (!"ONLINE".equalsIgnoreCase((String)b.getOrDefault("status", "ONLINE"))) {
                    log.info("[BotConfig] \u673a\u5668\u4eba {} \u5904\u4e8e\u505c\u7528\u72b6\u6001\uff0c\u8df3\u8fc7\u70ed\u91cd\u8f7d\u542f\u52a8", (Object)appId);
                    continue;
                }
                String robotId = appId;
                String envType = "true".equals(sandbox) ? "SANDBOX" : "PRODUCTION";
                String method = (String)b.getOrDefault("connectionMethod", "websocket");
                String domain = (String)b.getOrDefault("domain", "");
                Robot robot = new Robot();
                robot.setId(robotId);
                robot.setAppId(appId);
                robot.setAppSecretEncrypted(secret);
                robot.setRobotName(appId);
                robot.setIsSandbox(Boolean.valueOf("true".equals(sandbox)));
                robot.setConnectionMethod(method);
                robot.setStatus(Integer.valueOf(1));
                robot.setActiveEnv(envType);
                if (registry != null) {
                    registry.registerRobot(robot);
                }
                RobotEnvironment envObj = new RobotEnvironment();
                envObj.setRobotId(robotId);
                envObj.setEnvType(envType);
                envObj.setConnectMode(method);
                envObj.setWebhookUrl(domain);
                if (registry != null) {
                    registry.registerEnvironment(envObj);
                }
                if ("webhook".equalsIgnoreCase(method) && sv != null) {
                    sv.registerSecretPlain(robotId, envType, secret);
                }
                if ("websocket".equalsIgnoreCase(method) && ws != null) {
                    if (!ws.isConnected(robotId, envType)) {
                        ws.registerRobot(robotId, envType, appId, secret, 0);
                        ws.start(robotId, envType);
                    } else {
                        log.info("[BotConfig] \u673a\u5668\u4eba {} \u5df2\u8fde\u63a5\uff0c\u8df3\u8fc7\u91cd\u590d\u542f\u52a8", (Object)robotId);
                    }
                }
                try {
                    repo.upsertBot(appId, secret, method, "true".equals(sandbox), "ONLINE", domain.isBlank() ? null : domain);
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    this.botInfoSync.ifAvailable(s -> s.syncBot(appId));
                }
                catch (Exception exception) {
                    // empty catch block
                }
                ++count;
            }
            HashSet<String> currentAppIds = new HashSet<String>();
            for (Map<String, Object> b : bots) {
                String a = (String)b.get("appId");
                if (a == null) continue;
                currentAppIds.add(a);
            }
            if (ws != null) {
                for (Map<String, Object> st : ws.getAllStatus()) {
                    String key = (String)st.get("key");
                    String rid = key.contains(":") ? key.substring(0, key.indexOf(":")) : key;
                    if (currentAppIds.contains(rid)) continue;
                    ws.stop(rid);
                }
            }
            for (String id : this.frameworkBotRepository.allInstanceIds()) {
                if (currentAppIds.contains(id)) continue;
                this.frameworkBotRepository.delete("qqbot", id);
            }
            try {
                for (Map<String, Object> b : bots) {
                    if (!"ONLINE".equalsIgnoreCase((String)b.getOrDefault("status", "ONLINE"))) continue;
                    this.frameworkBotRepository.upsert("qqbot", (String)b.get("appId"), "qqbot", "ONLINE");
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.robotProperties.reload();
            return Map.of("status", "ok", "updated", count, "msg", "\u5df2\u91cd\u65b0\u52a0\u8f7d " + count + " \u4e2a Bot");
        }
        catch (Exception e) {
            return Map.of("error", "\u91cd\u8f7d\u5931\u8d25\uff0c\u786e\u8ba4 xuanji-adapter-qqbot \u5df2\u52a0\u8f7d: " + e.getMessage());
        }
    }

    private static String str(Map<String, Object> row, String upperKey) {
        Object v = row.get(upperKey);
        if (v == null) {
            v = row.get(upperKey.toLowerCase());
        }
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean bool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            Boolean b = (Boolean)v;
            return b;
        }
        if (v instanceof Number) {
            Number n = (Number)v;
            return n.intValue() != 0;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static String defaultIfBlank(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}

