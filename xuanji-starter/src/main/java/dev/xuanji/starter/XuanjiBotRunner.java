/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.adapter.qqbot.model.Robot
 *  dev.xuanji.adapter.qqbot.model.RobotEnvironment
 *  dev.xuanji.adapter.qqbot.registry.RobotRegistry
 *  dev.xuanji.adapter.qqbot.storage.QqBotRepository
 *  dev.xuanji.adapter.qqbot.webhook.SignatureVerifier
 *  dev.xuanji.adapter.qqbot.websocket.QqBotWsManager
 *  dev.xuanji.core.config.XuanjiRobotProperties
 *  dev.xuanji.core.config.XuanjiRobotProperties$RobotProperties
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.boot.CommandLineRunner
 *  org.springframework.stereotype.Component
 */
package dev.xuanji.starter;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.model.RobotEnvironment;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.adapter.qqbot.webhook.SignatureVerifier;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.FrameworkBotRepository;
import java.util.Map;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class XuanjiBotRunner
implements CommandLineRunner {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(XuanjiBotRunner.class);
    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<QqBotWsManager> wsManager;
    private final ObjectProvider<RobotRegistry> robotRegistry;
    private final FrameworkBotRepository frameworkBotRepository;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final ObjectProvider<SignatureVerifier> signatureVerifier;

    public void run(String ... args) {
        log.info("[\u542f\u52a8] ========================================");
        log.info("[\u542f\u52a8]   \u7487\u7391 QQ \u673a\u5668\u4eba\u6846\u67b6 - \u542f\u52a8\u4e2d...");
        log.info("[\u542f\u52a8] ========================================");
        this.robotProperties.reload();
        boolean isNewOpenBot = this.robotProperties.isNewOpenBot();
        this.wsManager.ifAvailable(ws -> ws.setNewOpenBot(isNewOpenBot));
        log.info("[\u914d\u7f6e] \u5f00\u653e\u5e73\u53f0\u7248\u672c: {}", (Object)(isNewOpenBot ? "\u65b0\u7248 (api.bot.qq.com)" : "\u8001\u7248 (api.sgroup.qq.com)"));
        // v3.3：数据库（qqbot_bot）是机器人注册真相源；yaml 缺失/未写时从库合并，重启后自动拉起
        this.qqBotRepository.ifAvailable(repo -> {
            int merged = 0;
            try {
                for (String appId : repo.listInstanceIds()) {
                    if (this.robotProperties.containsAppId(appId)) continue;
                    Map row = repo.getBotRow(appId);
                    if (row == null || row.isEmpty()) continue;
                    String status = row.get("STATUS") != null ? String.valueOf(row.get("STATUS")) : "";
                    if (!"ONLINE".equalsIgnoreCase(status)) continue;
                    String secret = row.get("BOT_CLIENTSECRET") != null ? String.valueOf(row.get("BOT_CLIENTSECRET")) : null;
                    if (secret == null || secret.isBlank()) continue;
                    boolean sandbox = "true".equalsIgnoreCase(String.valueOf(row.getOrDefault("IS_SANDBOX", "")));
                    String connMode = row.get("CONN_MODE") != null && !String.valueOf(row.get("CONN_MODE")).isBlank()
                            ? String.valueOf(row.get("CONN_MODE")) : "websocket";
                    this.robotProperties.registerRobot(appId, appId, secret, sandbox, connMode, "ONLINE");
                    ++merged;
                }
            }
            catch (Exception e) {
                log.warn("[\u914d\u7f6e] \u4ece\u6570\u636e\u5e93\u5408\u5e76\u673a\u5668\u4eba\u5931\u8d25: {}", (Object)e.getMessage());
            }
            if (merged > 0) {
                log.info("[\u914d\u7f6e] \u5df2\u4ece\u6570\u636e\u5e93\u5408\u5e76 {} \u4e2a\u673a\u5668\u4eba\uff08\u6ce8\u518c\u771f\u76f8\u6e90=qqbot_bot\uff09", (Object)merged);
            }
        });
        Map<String, XuanjiRobotProperties.RobotProperties> robots = this.robotProperties.getRobots();
        if (robots == null || robots.isEmpty()) {
            log.warn("[\u914d\u7f6e] \u672a\u53d1\u73b0\u4efb\u4f55\u673a\u5668\u4eba\uff0c\u8bf7\u5728\u63a7\u5236\u53f0\u300c\u673a\u5668\u4eba\u7ba1\u7406\u300d\u4e2d\u6dfb\u52a0\uff08\u6570\u636e\u843d\u5728 data/qqbot/{appId}/\uff09");
            return;
        }
        log.info("[\u914d\u7f6e] \u53d1\u73b0 {} \u4e2a\u673a\u5668\u4eba\u914d\u7f6e", (Object)robots.size());
        log.info("[\u542f\u52a8] ----------------------------------------");
        int successCount = 0;
        int failCount = 0;
        for (Map.Entry<String, XuanjiRobotProperties.RobotProperties> entry : robots.entrySet()) {
            String botKey = (String)entry.getKey();
            XuanjiRobotProperties.RobotProperties botConfig = (XuanjiRobotProperties.RobotProperties)entry.getValue();
            if (!"ONLINE".equalsIgnoreCase(botConfig.getStatus())) {
                log.info("[\u542f\u52a8] \u673a\u5668\u4eba {} \u5904\u4e8e\u505c\u7528\u72b6\u6001\uff08status={}\uff09\uff0c\u8df3\u8fc7\u542f\u52a8", (Object)botKey, (Object)botConfig.getStatus());
                continue;
            }
            try {
                this.registerAndStart(botKey, botConfig, isNewOpenBot);
                ++successCount;
            }
            catch (Exception e) {
                ++failCount;
                log.error("[\u542f\u52a8] \u673a\u5668\u4eba {} \u542f\u52a8\u5931\u8d25: {}", new Object[]{botKey, e.getMessage(), e});
            }
        }
        log.info("[\u542f\u52a8] ----------------------------------------");
        log.info("[\u542f\u52a8] \u5b8c\u6210: \u6210\u529f={}, \u5931\u8d25={}, \u603b\u8ba1={}", new Object[]{successCount, failCount, robots.size()});
        log.info("[\u542f\u52a8] ========================================");
    }

    private void registerAndStart(String botKey, XuanjiRobotProperties.RobotProperties config, boolean isNewOpenBot) {
        String appId = config.getAppId();
        String clientSecret = config.getClientSecret();
        boolean isSandbox = config.isSandbox();
        String connectionMethod = config.getConnectionMethod();
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("app-id \u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("client-secret \u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (connectionMethod == null || connectionMethod.isEmpty()) {
            throw new IllegalArgumentException("connection-method \u4e0d\u80fd\u4e3a\u7a7a");
        }
        String envType = isSandbox ? "SANDBOX" : "PRODUCTION";
        String robotId = appId;
        Robot robot = new Robot();
        robot.setId(robotId);
        robot.setAppId(appId);
        robot.setAppSecretEncrypted(clientSecret);
        robot.setRobotName(botKey);
        robot.setIsSandbox(Boolean.valueOf(isSandbox));
        robot.setConnectionMethod(connectionMethod);
        robot.setStatus(Integer.valueOf(1));
        robot.setActiveEnv(envType);
        this.robotRegistry.ifAvailable(r -> r.registerRobot(robot));
        log.info("[\u542f\u52a8] \u673a\u5668\u4eba {}: appId={}, env={}, method={}", new Object[]{botKey, appId, envType, connectionMethod});
        if ("websocket".equalsIgnoreCase(connectionMethod)) {
            this.wsManager.ifAvailable(ws -> {
                ws.registerRobot(robotId, envType, appId, clientSecret, 0);
                ws.start(robotId, envType);
            });
            log.info("[\u542f\u52a8] \u673a\u5668\u4eba {} WebSocket \u8fde\u63a5\u5df2\u542f\u52a8", (Object)botKey);
        } else if ("webhook".equalsIgnoreCase(connectionMethod)) {
            RobotEnvironment envObj = new RobotEnvironment();
            envObj.setRobotId(robotId);
            envObj.setEnvType(envType);
            envObj.setConnectMode("webhook");
            String webhookUrl = config.getWebhookUrl();
            QqBotRepository qqRepo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
            envObj.setWebhookUrl(webhookUrl == null || webhookUrl.isBlank() ? (qqRepo != null ? qqRepo.getWebhookUrl(appId) : null) : webhookUrl);
            this.robotRegistry.ifAvailable(r -> r.registerEnvironment(envObj));
            this.signatureVerifier.ifAvailable(sv -> {
                try {
                    sv.registerSecretPlain(robotId, envType, clientSecret);
                }
                catch (Exception e) {
                    log.warn("[\u542f\u52a8] \u6ce8\u518c Webhook \u7b7e\u540d\u5bc6\u94a5\u5931\u8d25(\u53ef\u5ffd\u7565): {}", (Object)e.getMessage());
                }
            });
            log.info("[\u542f\u52a8] \u673a\u5668\u4eba {} Webhook \u56de\u8c03\u5730\u5740: https://{}/webhook/{}", new Object[]{botKey, envObj.getWebhookUrl() == null ? "(\u672a\u914d\u7f6e)" : envObj.getWebhookUrl(), appId});
        } else {
            throw new IllegalArgumentException("\u4e0d\u652f\u6301\u7684\u8fde\u63a5\u65b9\u5f0f: " + connectionMethod);
        }
        try {
            this.frameworkBotRepository.upsert("qqbot", appId, "qqbot", "ONLINE");
            this.qqBotRepository.ifAvailable(repo -> repo.upsertBot(appId, clientSecret, connectionMethod, isSandbox, "ONLINE"));
        }
        catch (Exception e) {
            log.warn("[\u542f\u52a8] \u5199\u5165 Bot \u6ce8\u518c\u4fe1\u606f\u5931\u8d25: {}", (Object)e.getMessage());
        }
    }

    @Generated
    public XuanjiBotRunner(XuanjiRobotProperties robotProperties, ObjectProvider<QqBotWsManager> wsManager, ObjectProvider<RobotRegistry> robotRegistry, FrameworkBotRepository frameworkBotRepository, ObjectProvider<QqBotRepository> qqBotRepository, ObjectProvider<SignatureVerifier> signatureVerifier) {
        this.robotProperties = robotProperties;
        this.wsManager = wsManager;
        this.robotRegistry = robotRegistry;
        this.frameworkBotRepository = frameworkBotRepository;
        this.qqBotRepository = qqBotRepository;
        this.signatureVerifier = signatureVerifier;
    }
}

