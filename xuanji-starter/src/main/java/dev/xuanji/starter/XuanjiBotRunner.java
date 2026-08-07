package dev.xuanji.starter;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.model.RobotEnvironment;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.adapter.qqbot.webhook.SignatureVerifier;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.FrameworkBotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动期机器人拉起器 — 读取配置、注册到 RobotRegistry、按连接方式建立 WebSocket 或注册 Webhook。
 *
 * <p>注册真相源是数据库表 {@code qqbot_bot}（v3.3 起）：yaml 里没写的机器人会从库里合并进来，
 * 保证控制台新增的 Bot 重启后能自动拉起。yaml 已声明的 appId 优先，不会被库覆盖。
 *
 * <p>QQ 适配器相关依赖全部用 {@link ObjectProvider} 注入，这样 {@code xuanji.qqbot.enabled=false}
 * 时本类依然能正常加载，不会因缺 Bean 而中断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XuanjiBotRunner implements CommandLineRunner {

    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<QqBotWsManager> wsManager;
    private final ObjectProvider<RobotRegistry> robotRegistry;
    private final FrameworkBotRepository frameworkBotRepository;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final ObjectProvider<SignatureVerifier> signatureVerifier;
    private final ObjectProvider<dev.xuanji.core.config.ConfigService> configService;

    @Override
    public void run(String... args) {
        log.info("[启动] ========================================");
        log.info("[启动]   璇玑 QQ 机器人框架 - 启动中...");
        log.info("[启动] ========================================");

        robotProperties.reload();
        // 开放平台版本：优先运行时全局配置（运行设置可改，默认 new=新统一地址），回退 yml 的 isNewOpenBot
        boolean isNewOpenBot = resolveNewOpenBot();
        wsManager.ifAvailable(ws -> ws.setNewOpenBot(isNewOpenBot));
        log.info("[配置] 开放平台版本: {}", isNewOpenBot ? "新版 (api.bot.qq.com)" : "老版 (api.sgroup.qq.com)");
        log.info("[配置] 提示: 运行设置「QQ 开放平台 API 基地址」可在运行中切换（30s 内生效，WebSocket 连接需重启）");

        mergeRobotsFromDatabase();

        Map<String, XuanjiRobotProperties.RobotProperties> robots = robotProperties.getRobots();
        if (robots == null || robots.isEmpty()) {
            log.warn("[配置] 未发现任何机器人，请在控制台「机器人管理」中添加（数据落在 data/qqbot/{appId}/）");
            return;
        }

        log.info("[配置] 发现 {} 个机器人配置", robots.size());
        log.info("[启动] ----------------------------------------");

        int successCount = 0;
        int failCount = 0;
        for (Map.Entry<String, XuanjiRobotProperties.RobotProperties> entry : robots.entrySet()) {
            String botKey = entry.getKey();
            XuanjiRobotProperties.RobotProperties botConfig = entry.getValue();
            if (!"ONLINE".equalsIgnoreCase(botConfig.getStatus())) {
                log.info("[启动] 机器人 {} 处于停用状态（status={}），跳过启动", botKey, botConfig.getStatus());
                continue;
            }
            try {
                registerAndStart(botKey, botConfig, isNewOpenBot);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[启动] 机器人 {} 启动失败: {}", botKey, e.getMessage(), e);
            }
        }

        log.info("[启动] ----------------------------------------");
        log.info("[启动] 完成: 成功={}, 失败={}, 总计={}", successCount, failCount, robots.size());
        log.info("[启动] ========================================");
    }

    /**
     * v3.3：数据库（qqbot_bot）是机器人注册真相源；yaml 缺失/未写时从库合并，重启后自动拉起。
     *
     * <p>只合并 status=ONLINE 且 secret 非空的记录，避免把停用的 Bot 误拉起来。
     */
    private void mergeRobotsFromDatabase() {
        qqBotRepository.ifAvailable(repo -> {
            int merged = 0;
            try {
                for (String appId : repo.listInstanceIds()) {
                    if (robotProperties.containsAppId(appId)) {
                        continue;
                    }
                    Map<String, Object> row = repo.getBotRow(appId);
                    if (row == null || row.isEmpty()) {
                        continue;
                    }
                    String status = row.get("STATUS") != null ? String.valueOf(row.get("STATUS")) : "";
                    if (!"ONLINE".equalsIgnoreCase(status)) {
                        continue;
                    }
                    String secret = row.get("BOT_CLIENTSECRET") != null
                            ? String.valueOf(row.get("BOT_CLIENTSECRET")) : null;
                    if (secret == null || secret.isBlank()) {
                        continue;
                    }
                    boolean sandbox = "true".equalsIgnoreCase(String.valueOf(row.getOrDefault("IS_SANDBOX", "")));
                    String connMode = row.get("CONN_MODE") != null && !String.valueOf(row.get("CONN_MODE")).isBlank()
                            ? String.valueOf(row.get("CONN_MODE"))
                            : "websocket";
                    robotProperties.registerRobot(appId, appId, secret, sandbox, connMode, "ONLINE");
                    merged++;
                }
            } catch (Exception e) {
                log.warn("[配置] 从数据库合并机器人失败: {}", e.getMessage());
            }
            if (merged > 0) {
                log.info("[配置] 已从数据库合并 {} 个机器人（注册真相源=qqbot_bot）", merged);
            }
        });
    }

    /** 解析开放平台版本：优先运行时全局配置 framework.qqbot.api_base_mode（new=新统一地址），
     * 未配置时默认 new（用户要求：默认新统一地址 api.bot.qq.com）；仅显式 legacy 才用老平台。 */
    private boolean resolveNewOpenBot() {
        try {
            dev.xuanji.core.config.ConfigService cs = configService.getIfAvailable();
            if (cs != null) {
                String mode = cs.getGlobalConfig().get("framework.qqbot.api_base_mode");
                if (mode != null && !mode.isBlank()) {
                    return !"legacy".equalsIgnoreCase(mode.trim());
                }
            }
        } catch (Exception ignored) { /* 配置不可用 */ }
        return true; // 默认新统一地址（用户要求）
    }

    /**
     * 注册单个机器人并按连接方式拉起。
     *
     * <p>robotId 与 appId 保持一致——全框架都以 appId 作为 QQ 侧机器人的唯一标识。
     */
    private void registerAndStart(String botKey, XuanjiRobotProperties.RobotProperties config, boolean isNewOpenBot) {
        String appId = config.getAppId();
        String clientSecret = config.getClientSecret();
        boolean isSandbox = config.isSandbox();
        String connectionMethod = config.getConnectionMethod();

        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("app-id 不能为空");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("client-secret 不能为空");
        }
        if (connectionMethod == null || connectionMethod.isEmpty()) {
            throw new IllegalArgumentException("connection-method 不能为空");
        }

        String envType = isSandbox ? "SANDBOX" : "PRODUCTION";
        String robotId = appId;

        Robot robot = new Robot();
        robot.setId(robotId);
        robot.setAppId(appId);
        robot.setAppSecretEncrypted(clientSecret);
        robot.setRobotName(botKey);
        robot.setIsSandbox(isSandbox);
        robot.setConnectionMethod(connectionMethod);
        robot.setStatus(1);
        robot.setActiveEnv(envType);
        robotRegistry.ifAvailable(r -> r.registerRobot(robot));

        log.info("[启动] 机器人 {}: appId={}, env={}, method={}", botKey, appId, envType, connectionMethod);

        if ("websocket".equalsIgnoreCase(connectionMethod)) {
            wsManager.ifAvailable(ws -> {
                ws.registerRobot(robotId, envType, appId, clientSecret, 0);
                ws.start(robotId, envType);
            });
            log.info("[启动] 机器人 {} WebSocket 连接已启动", botKey);
        } else if ("webhook".equalsIgnoreCase(connectionMethod)) {
            RobotEnvironment envObj = new RobotEnvironment();
            envObj.setRobotId(robotId);
            envObj.setEnvType(envType);
            envObj.setConnectMode("webhook");

            String webhookUrl = config.getWebhookUrl();
            QqBotRepository qqRepo = qqBotRepository.getIfAvailable();
            envObj.setWebhookUrl(webhookUrl == null || webhookUrl.isBlank()
                    ? (qqRepo != null ? qqRepo.getWebhookUrl(appId) : null)
                    : webhookUrl);

            robotRegistry.ifAvailable(r -> r.registerEnvironment(envObj));
            signatureVerifier.ifAvailable(sv -> {
                try {
                    sv.registerSecretPlain(robotId, envType, clientSecret);
                } catch (Exception e) {
                    log.warn("[启动] 注册 Webhook 签名密钥失败(可忽略): {}", e.getMessage());
                }
            });

            log.info("[启动] 机器人 {} Webhook 回调地址: https://{}/webhook/{}",
                    botKey, envObj.getWebhookUrl() == null ? "(未配置)" : envObj.getWebhookUrl(), appId);
        } else {
            throw new IllegalArgumentException("不支持的连接方式: " + connectionMethod);
        }

        try {
            frameworkBotRepository.upsert("qqbot", appId, "qqbot", "ONLINE");
            qqBotRepository.ifAvailable(repo ->
                    repo.upsertBot(appId, clientSecret, connectionMethod, isSandbox, "ONLINE"));
        } catch (Exception e) {
            log.warn("[启动] 写入 Bot 注册信息失败: {}", e.getMessage());
        }
    }
}
