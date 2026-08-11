package XuanJi.starter;

import XuanJi.adapter.qqbot.model.Robot;
import XuanJi.adapter.qqbot.model.RobotEnvironment;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import XuanJi.adapter.qqbot.storage.BotInfoSync;
import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.adapter.qqbot.webhook.SignatureVerifier;
import XuanJi.adapter.qqbot.websocket.QqBotWsManager;
import XuanJi.console.service.AuditService;
import XuanJi.core.config.XuanJiRobotProperties;
import XuanJi.core.storage.BotDataSourceRegistry;
import XuanJi.core.storage.FrameworkBotRepository;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 机器人配置管理接口 — 控制台「机器人管理」页面的后端。
 *
 * <p>数据真相源是平台库 {@code qqbot_bot}；框架库 {@code xuanji_bot} 只做跨平台索引，两边同时写。
 * 删除是「彻底删除」语义：停 WS → 反注册 Registry → 摘签名密钥 → 关 per-bot 数据源 → 删两张表 →
 * 删 {@code data/&lt;platform&gt;/&lt;appId&gt;/} 数据目录。每一步独立 try-catch，任何一步失败都不阻断后续清理，
 * 避免半删状态残留。
 *
 * <p>QQ 适配器可能未启用，所以相关 Bean 一律通过 {@link ObjectProvider} / {@link ApplicationContext}
 * 惰性获取，取不到时降级返回错误信息而不是抛异常。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/bot-config")
public class BotConfigController {

    private final ApplicationContext ctx;
    private final XuanJiRobotProperties robotProperties;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final FrameworkBotRepository frameworkBotRepository;
    private final ObjectProvider<BotInfoSync> botInfoSync;
    private final AuditService auditService;
    private final XuanJi.core.storage.BotArchiveService botArchiveService;

    public BotConfigController(ApplicationContext ctx,
                               XuanJiRobotProperties robotProperties,
                               ObjectProvider<QqBotRepository> qqBotRepository,
                               FrameworkBotRepository frameworkBotRepository,
                               ObjectProvider<BotInfoSync> botInfoSync,
                               AuditService auditService,
                               XuanJi.core.storage.BotArchiveService botArchiveService) {
        this.ctx = ctx;
        this.robotProperties = robotProperties;
        this.qqBotRepository = qqBotRepository;
        this.frameworkBotRepository = frameworkBotRepository;
        this.botInfoSync = botInfoSync;
        this.auditService = auditService;
        this.botArchiveService = botArchiveService;
    }

    /** 列出全部机器人配置；QQ 适配器未启用时返回空列表而非报错。 */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        QqBotRepository repo = qqBotRepository.getIfAvailable();
        if (repo == null) {
            return result;
        }
        for (Map.Entry<String, Map<String, Object>> entry : repo.loadAllBotRows().entrySet()) {
            String appId = entry.getKey();
            Map<String, Object> row = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", appId);
            item.put("appId", appId);
            item.put("clientSecret", str(row, "BOT_CLIENTSECRET"));
            item.put("sandbox", String.valueOf(bool(row.get("IS_SANDBOX"))));
            item.put("connectionMethod", defaultIfBlank(str(row, "CONN_MODE"), "websocket"));
            item.put("domain", str(row, "WEBHOOK_URL"));
            item.put("status", defaultIfBlank(str(row, "STATUS"), "OFFLINE"));
            result.add(item);
        }
        return result;
    }

    /** 新增或更新机器人配置；已存在时沿用原 status，不会因保存把在线机器人改成离线。 */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        String sandbox = body.getOrDefault("sandbox", "false");
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");

        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            return Map.of("error", "AppSecret 不能为空");
        }

        try {
            QqBotRepository repo = qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ 适配器未启用 (xuanji.qqbot.enabled=false)");
            }
            Map<String, Object> existing = repo.getBotRow(appId);
            boolean isNew = existing.isEmpty();
            String status = defaultIfBlank(str(existing, "STATUS"), "ONLINE");
            String webhookUrl = "webhook".equalsIgnoreCase(method) && !domain.isBlank() ? domain : null;
            repo.upsertBot(appId, secret, method, "true".equals(sandbox), status, webhookUrl);

            try {
                frameworkBotRepository.upsert("qqbot", appId, "qqbot", status);
            } catch (Exception e) {
                log.warn("[BotConfig] 写入 xuanji_bot 失败: appId={}, {}", appId, e.getMessage());
            }

            robotProperties.reload();
            log.info("[BotConfig] 已保存机器人配置: appId={}, mode={}", appId, method);
            auditService.record(isNew ? "BOT_ADD" : "BOT_UPDATE",
                    (isNew ? "新增机器人 appId=" : "更新机器人 appId=") + appId
                            + " mode=" + method + " sandbox=" + sandbox, req);
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.error("[BotConfig] 保存机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 删除并归档机器人：先归档（数据/日志目录移入 data/_archive + 登记 30 天可恢复），
     * 再断连接 → 清注册 → 清密钥 → 关数据源 → 删两张表。
     *
     * <p>归档失败则<b>中止删除</b>（防误删兜底）；归档成功后数据已在 _archive，
     * 即使后续清理步骤有缺失也不影响数据保留。
     */
    @DeleteMapping("/{appId}")
    public Map<String, Object> delete(@PathVariable String appId, HttpServletRequest req) {
        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID 不能为空");
        }
        try {
            // ── 第 0 步：归档（防误删）──
            String platform = resolvePlatform(appId);
            if (platform == null) {
                platform = "qqbot";
            }
            String botName = botNameOf(platform, appId);
            String extraJson = collectArchiveExtraJson(platform, appId);
            long archiveId = botArchiveService.archive(platform, appId, botName, extraJson);
            if (archiveId < 0) {
                log.error("[BotConfig] 归档失败，中止删除: appId={}", appId);
                return Map.of("error", "归档失败，删除已中止（防误删）");
            }
            log.info("[BotConfig] 已归档机器人: platform={} appId={} archiveId={}", platform, appId, archiveId);

            try {
                QqBotWsManager ws = ctx.getBeanProvider(QqBotWsManager.class).getIfAvailable();
                if (ws != null) {
                    ws.stop(appId);
                }
            } catch (Exception e) {
                log.warn("[BotConfig] 停止 WS 连接失败(可忽略): appId={}, {}", appId, e.getMessage());
            }

            try {
                RobotRegistry registry = ctx.getBeanProvider(RobotRegistry.class).getIfAvailable();
                if (registry != null) {
                    registry.unregisterRobot(appId);
                }
            } catch (Exception e) {
                log.warn("[BotConfig] 反注册 RobotRegistry 失败(可忽略): appId={}, {}", appId, e.getMessage());
            }

            try {
                SignatureVerifier sv = ctx.getBeanProvider(SignatureVerifier.class).getIfAvailable();
                if (sv != null) {
                    sv.unregister(appId);
                }
            } catch (Exception e) {
                log.warn("[BotConfig] 移除签名密钥失败(可忽略): appId={}, {}", appId, e.getMessage());
            }

            try {
                BotDataSourceRegistry dsr = ctx.getBean(BotDataSourceRegistry.class);
                dsr.closeInstance("qqbot", appId);
                dsr.closeInstance("onebot", appId);
            } catch (Exception e) {
                log.warn("[BotConfig] 关闭 bot 数据源失败(可忽略): appId={}, {}", appId, e.getMessage());
            }

            try {
                frameworkBotRepository.delete("qqbot", appId);
                log.info("[BotConfig] 已删除 xuanji_bot 记录: appId={}", appId);
            } catch (Exception e) {
                log.warn("[BotConfig] 删除 xuanji_bot 记录失败: appId={}, {}", appId, e.getMessage());
            }

            try {
                QqBotRepository repo = qqBotRepository.getIfAvailable();
                if (repo != null) {
                    repo.deleteBot(appId);
                    log.info("[BotConfig] 已删除平台库档案: appId={}", appId);
                }
            } catch (Exception e) {
                log.warn("[BotConfig] 删除平台库档案失败(可忽略): appId={}, {}", appId, e.getMessage());
            }

            deleteBotDataDirs(appId);

            robotProperties.reload();
            log.info("[BotConfig] 已删除并归档机器人: appId={}, archiveId={}", appId, archiveId);
            auditService.record("BOT_DELETE", "删除并归档机器人 appId=" + appId + " archiveId=" + archiveId, req);
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.error("[BotConfig] 删除机器人失败: appId={}, {}", appId, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ──────────────── 归档（防误删，30 天可恢复） ────────────────

    /** 归档列表（未过期未恢复）。 */
    @GetMapping("/archives")
    public List<Map<String, Object>> archives() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> rec : botArchiveService.listActive()) {
            out.add(XuanJi.core.storage.BotArchiveService.toView(rec));
        }
        return out;
    }

    /**
     * 恢复归档机器人：移回数据/日志目录 → 重建平台库档案 → 重建框架索引 → 重载配置。
     */
    @PostMapping("/archives/{id}/restore")
    public Map<String, Object> restore(@PathVariable long id, HttpServletRequest req) {
        try {
            Map<String, Object> rec = botArchiveService.get(id);
            if (rec == null || !"ACTIVE".equals(String.valueOf(rec.get("status")))) {
                return Map.of("error", "归档不存在或已过期/已恢复");
            }
            String platform = String.valueOf(rec.get("platform"));
            String appId = String.valueOf(rec.get("instance_id"));
            if (!botArchiveService.restoreFiles(id)) {
                return Map.of("error", "恢复数据目录失败");
            }
            // 重建平台库档案（qqbot_bot / onebot_bot 行，依据归档快照）
            String extraJson = String.valueOf(rec.get("extra_json"));
            if (extraJson != null && !extraJson.isBlank() && !"null".equals(extraJson)) {
                restorePlatformRow(platform, appId, extraJson);
            }
            // 重建框架索引记录
            frameworkBotRepository.upsert(platform, appId, appId, "OFFLINE");
            robotProperties.reload();
            auditService.record("BOT_RESTORE", "恢复归档机器人 " + platform + "/" + appId, req);
            log.info("[BotConfig] 已恢复归档机器人: platform={} appId={} archiveId={}", platform, appId, id);
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.error("[BotConfig] 恢复归档机器人失败: id={}, {}", id, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /** 识别 appId 属于哪个平台（扫描 data/{qqbot,onebot}/{appId} 目录）。 */
    private String resolvePlatform(String appId) {
        Path dataRoot = Paths.get("data");
        if (!Files.isDirectory(dataRoot)) {
            return null;
        }
        for (String p : new String[]{"qqbot", "onebot"}) {
            if (Files.isDirectory(dataRoot.resolve(p).resolve(appId))) {
                return p;
            }
        }
        return null;
    }

    /** 机器人显示名（优先 botinfo，其次 appId）。 */
    private String botNameOf(String platform, String appId) {
        try {
            if ("qqbot".equals(platform)) {
                QqBotRepository repo = qqBotRepository.getIfAvailable();
                if (repo != null) {
                    Map<String, Object> info = repo.getBotInfo(appId);
                    if (info != null && info.get("NAME") != null) {
                        return String.valueOf(info.get("NAME"));
                    }
                }
            }
        } catch (Exception ignored) {}
        return appId;
    }

    /** 收集平台档案 JSON 快照（归档记录 extra_json，恢复时重建档案行）。 */
    private String collectArchiveExtraJson(String platform, String appId) {
        try {
            if ("qqbot".equals(platform)) {
                QqBotRepository repo = qqBotRepository.getIfAvailable();
                if (repo != null) {
                    Map<String, Object> row = repo.getBotRow(appId);
                    if (row != null && !row.isEmpty()) {
                        return XuanJi.api.json.Json.mapper().writeValueAsString(row);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[BotConfig] 收集平台档案快照失败(可忽略): {}", e.getMessage());
        }
        return "";
    }

    /** 依据归档快照重建平台库档案行。 */
    private void restorePlatformRow(String platform, String appId, String extraJson) {
        try {
            if ("qqbot".equals(platform)) {
                QqBotRepository repo = qqBotRepository.getIfAvailable();
                if (repo != null) {
                    tools.jackson.databind.JsonNode node = XuanJi.api.json.Json.parseObj(extraJson);
                    if (node != null && node.isObject()) {
                        String secret = node.path("bot_clientSecret").asText(null);
                        String connMode = node.path("conn_mode").asText("websocket");
                        boolean sandbox = node.path("is_sandbox").asBoolean(false);
                        String status = node.path("status").asText("OFFLINE");
                        String webhook = node.path("webhook_url").asText(null);
                        repo.upsertBot(appId, secret, connMode, sandbox, status, webhook);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[BotConfig] 重建平台库档案失败(可忽略): {}", e.getMessage());
        }
    }

    /** 扫描 {@code data/} 下所有平台目录，删除该 appId 的 per-bot 数据目录。 */
    private void deleteBotDataDirs(String appId) {
        try {
            Path dataRoot = Paths.get("data");
            if (!Files.exists(dataRoot)) {
                return;
            }
            try (Stream<Path> platforms = Files.list(dataRoot)) {
                platforms.forEach(platform -> {
                    Path botDir = platform.resolve(appId);
                    if (!Files.exists(botDir)) {
                        return;
                    }
                    deleteRecursively(botDir);
                    if (Files.exists(botDir)) {
                        log.warn("[BotConfig] per-bot 数据目录仍残留（可能文件被占用）: {}", botDir);
                    } else {
                        log.info("[BotConfig] 已删除 per-bot 数据目录: {}", botDir);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[BotConfig] 删除 per-bot 数据目录失败: appId={}, {}", appId, e.getMessage());
        }
    }

    /** 深度优先删除：按路径层级倒序保证先删子节点再删父目录。 */
    private void deleteRecursively(Path target) {
        try {
            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 单个文件被占用时跳过，外层会检测残留并告警
                        }
                    });
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("[BotConfig] 递归删除失败: {} - {}", target, e.getMessage());
        }
    }

    /**
     * 热重载全部机器人：按最新配置重建注册与连接，并回收已被移除的机器人。
     *
     * <p>已连接的 WS 会跳过重复启动；配置里不存在的 robotId 会被停连接并从框架库清掉。
     */
    @PostMapping("/reload")
    public Map<String, Object> reload(HttpServletRequest req) {
        List<Map<String, Object>> bots = list();
        if (bots.isEmpty()) {
            return Map.of("error", "无 XuanJiBot 配置");
        }
        try {
            QqBotWsManager ws = ctx.getBeanProvider(QqBotWsManager.class).getIfAvailable();
            RobotRegistry registry = ctx.getBeanProvider(RobotRegistry.class).getIfAvailable();
            SignatureVerifier sv = ctx.getBeanProvider(SignatureVerifier.class).getIfAvailable();
            QqBotRepository repo = qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ 适配器未启用 (xuanji.qqbot.enabled=false)");
            }

            int count = 0;
            for (Map<String, Object> b : bots) {
                String appId = (String) b.get("appId");
                String secret = (String) b.get("clientSecret");
                String sandbox = (String) b.getOrDefault("sandbox", "false");
                if (appId == null || secret == null || secret.isBlank()) {
                    continue;
                }
                if (!"ONLINE".equalsIgnoreCase((String) b.getOrDefault("status", "ONLINE"))) {
                    log.info("[BotConfig] 机器人 {} 处于停用状态，跳过热重载启动", appId);
                    continue;
                }

                String robotId = appId;
                String envType = "true".equals(sandbox) ? "SANDBOX" : "PRODUCTION";
                String method = (String) b.getOrDefault("connectionMethod", "websocket");
                String domain = (String) b.getOrDefault("domain", "");

                Robot robot = new Robot();
                robot.setId(robotId);
                robot.setAppId(appId);
                robot.setAppSecretEncrypted(secret);
                robot.setRobotName(appId);
                robot.setIsSandbox("true".equals(sandbox));
                robot.setConnectionMethod(method);
                robot.setStatus(1);
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
                        log.info("[BotConfig] 机器人 {} 已连接，跳过重复启动", robotId);
                    }
                }

                try {
                    repo.upsertBot(appId, secret, method, "true".equals(sandbox), "ONLINE",
                            domain.isBlank() ? null : domain);
                } catch (Exception ignored) {
                    // 落库失败不影响本次热重载，下次保存会补上
                }
                try {
                    botInfoSync.ifAvailable(s -> s.syncBot(appId));
                } catch (Exception ignored) {
                    // XuanJiBot 资料同步是锦上添花，失败静默
                }
                count++;
            }

            // 回收：配置里已不存在的机器人，停连接并从框架库摘掉
            Set<String> currentAppIds = new HashSet<>();
            for (Map<String, Object> b : bots) {
                String appId = (String) b.get("appId");
                if (appId != null) {
                    currentAppIds.add(appId);
                }
            }
            if (ws != null) {
                for (Map<String, Object> st : ws.getAllStatus()) {
                    String key = (String) st.get("key");
                    String rid = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
                    if (!currentAppIds.contains(rid)) {
                        ws.stop(rid);
                    }
                }
            }
            for (String id : frameworkBotRepository.allInstanceIds()) {
                if (!currentAppIds.contains(id)) {
                    frameworkBotRepository.delete("qqbot", id);
                }
            }

            try {
                for (Map<String, Object> b : bots) {
                    if ("ONLINE".equalsIgnoreCase((String) b.getOrDefault("status", "ONLINE"))) {
                        frameworkBotRepository.upsert("qqbot", (String) b.get("appId"), "qqbot", "ONLINE");
                    }
                }
            } catch (Exception ignored) {
                // 框架库索引回写失败不影响连接本身
            }

            robotProperties.reload();
            auditService.record("BOT_RELOAD", "热重载全部机器人配置，更新 " + count + " 个", req);
            return Map.of("status", "ok", "updated", count, "msg", "已重新加载 " + count + " 个 XuanJiBot");
        } catch (Exception e) {
            return Map.of("error", "重载失败，确认 xuanji-adapter-qqbot 已加载: " + e.getMessage());
        }
    }

    /** 取行字段，兼容 H2 大写列名与小写列名两种返回。 */
    private static String str(Map<String, Object> row, String upperKey) {
        Object v = row.get(upperKey);
        if (v == null) {
            v = row.get(upperKey.toLowerCase());
        }
        return v == null ? "" : String.valueOf(v);
    }

    /** 宽松布尔解析：Boolean / Number / "true" / "1" 都算真。 */
    private static boolean bool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static String defaultIfBlank(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
