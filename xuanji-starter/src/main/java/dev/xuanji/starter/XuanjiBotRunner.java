package dev.xuanji.starter;

import dev.xuanji.adapter.qq.websocket.QqBotWsManager;
import dev.xuanji.adapter.qq.registry.RobotRegistry;
import dev.xuanji.adapter.qq.model.Robot;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 璇玑框架启动器
 *
 * <p>Spring Boot 启动后自动执行，完成以下初始化工作：
 * <ol>
 *   <li>读取 xuanji-robots.yml 中的机器人配置</li>
 *   <li>设置全局配置（是否使用新开放平台）</li>
 *   <li>注册机器人到 WebSocket 管理器</li>
 *   <li>启动 WebSocket 连接</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XuanjiBotRunner implements CommandLineRunner {

    private final XuanjiRobotProperties robotProperties;
    private final QqBotWsManager wsManager;
    private final RobotRegistry robotRegistry;

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("  璇玑 QQ 机器人框架 - 启动中...");
        log.info("========================================");

        // 设置全局配置
        boolean isNewOpenBot = robotProperties.isNewOpenBot();
        wsManager.setNewOpenBot(isNewOpenBot);
        log.info("[配置] 开放平台版本: {}", isNewOpenBot ? "新版 (api.bot.qq.com)" : "老版 (api.sgroup.qq.com)");

        // 遍历并注册机器人
        var robots = robotProperties.getRobots();
        if (robots == null || robots.isEmpty()) {
            log.warn("[配置] 未发现任何机器人配置，请检查 xuanji-robots.yml");
            return;
        }

        log.info("[配置] 发现 {} 个机器人配置", robots.size());
        log.info("----------------------------------------");

        int successCount = 0;
        int failCount = 0;

        for (var entry : robots.entrySet()) {
            String botKey = entry.getKey();
            XuanjiRobotProperties.RobotProperties botConfig = entry.getValue();

            try {
                registerAndStart(botKey, botConfig, isNewOpenBot);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[启动] 机器人 {} 启动失败: {}", botKey, e.getMessage(), e);
            }
        }

        log.info("----------------------------------------");
        log.info("[启动] 完成: 成功={}, 失败={}, 总计={}", successCount, failCount, robots.size());
        log.info("========================================");
    }

    /**
     * 注册并启动单个机器人
     */
    private void registerAndStart(String botKey, XuanjiRobotProperties.RobotProperties config,
                                   boolean isNewOpenBot) {
        String appId = config.getAppId();
        String clientSecret = config.getClientSecret();
        boolean isSandbox = config.isSandbox();
        String connectionMethod = config.getConnectionMethod();

        // 验证必填配置
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("app-id 不能为空");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("client-secret 不能为空");
        }
        if (connectionMethod == null || connectionMethod.isEmpty()) {
            throw new IllegalArgumentException("connection-method 不能为空");
        }

        // 确定环境类型
        String envType = isSandbox ? "SANDBOX" : "PRODUCTION";

        // 使用 appId 的哈希值作为 robotId（简化处理）
        Long robotId = (long) appId.hashCode();

        // 注册到 RobotRegistry
        Robot robot = new Robot();
        robot.setId(robotId);
        robot.setAppId(appId);
        robot.setAppSecretEncrypted(clientSecret); // 框架模式下存储明文
        robot.setRobotName(botKey);
        robot.setIsSandbox(isSandbox);
        robot.setConnectionMethod(connectionMethod);
        robot.setStatus(1);
        robotRegistry.registerRobot(robot);

        log.info("[启动] 机器人 {}: appId={}, env={}, method={}",
                botKey, appId, envType, connectionMethod);

        // 根据连接方式启动
        if ("websocket".equalsIgnoreCase(connectionMethod)) {
            // 注册到 WebSocket 管理器（使用默认 intents）
            wsManager.registerRobot(robotId, envType, appId, clientSecret, 0);
            // 启动 WebSocket 连接
            wsManager.start(robotId, envType);
            log.info("[启动] 机器人 {} WebSocket 连接已启动", botKey);
        } else if ("webhook".equalsIgnoreCase(connectionMethod)) {
            // Webhook 模式不需要主动连接，等待 QQ 平台推送
            String webhookUrl = robotProperties.getWebhookUrl();
            log.info("[启动] 机器人 {} Webhook 回调地址: https://{}/webhook/{}",
                    botKey, webhookUrl, appId);
        } else {
            throw new IllegalArgumentException("不支持的连接方式: " + connectionMethod);
        }
    }
}
