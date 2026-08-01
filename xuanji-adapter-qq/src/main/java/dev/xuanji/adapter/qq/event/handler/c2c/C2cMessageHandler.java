package dev.xuanji.adapter.qq.event.handler.c2c;

import dev.xuanji.adapter.qq.dto.C2cMessageEvent;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 单聊消息事件 — 接入 CommandRegistry 插件指令系统。
 */
@Slf4j
@Component
@EventMapping("C2C_MESSAGE_CREATE")
public class C2cMessageHandler implements EventHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;
    private final XuanjiRobotProperties robotProperties;
    private final JdbcTemplate jdbc;
    private final dev.xuanji.core.storage.log.MessageLogService logSvc;

    public C2cMessageHandler(MessageSender messageSender, CommandRegistry commandRegistry,
                             XuanjiRobotProperties robotProperties, JdbcTemplate jdbc,
                             dev.xuanji.core.storage.log.MessageLogService logSvc) {
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
        this.robotProperties = robotProperties;
        this.jdbc = jdbc;
        this.logSvc = logSvc;
    }

    @Override
    public String getEventType() { return "C2C_MESSAGE_EVENT"; }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        dev.xuanji.core.metrics.TraceContext.enter(data.has("_eventId") ? data.get("_eventId").asText() : "", String.valueOf(robotId));
        try {
            C2cMessageEvent event = objectMapper.readValue(data.toString(), C2cMessageEvent.class);
            if (event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot())) return;

            String content = event.getContent() != null ? event.getContent().trim() : "";
            String msgId = event.getId();
            String openid = event.getSenderId();

            log.info("[收到单聊消息] sender={}, content={}", event.getSenderName(), content);
            String botKey = robotProperties.findBotKeyByRobotId(robotId);
            if (botKey == null) botKey = "bot1";
            String appId = robotProperties.getRobots() != null && robotProperties.getRobots().get(botKey) != null
                    ? robotProperties.getRobots().get(botKey).getAppId() : String.valueOf(robotId);
            dev.xuanji.core.storage.log.MessageLogger.c2cMessage("IN",
                    appId, openid, "text", content, data.toString());

            // 自动同步用户（无则插入，有则标记未删除）
            autoSyncUser(appId, openid);

            // 创建 Bot 实例（C2C 模式）
            var bot = new dev.xuanji.adapter.qq.bot.C2cXjBot(messageSender, openid, msgId, appId, logSvc);
            CommandRegistry.setContext("bot1", null, msgId, openid, null, bot);

            try {
                String result = commandRegistry.executePrivateMessage(content);
                if (result != null) {
                    bot.reply(result);
                    dev.xuanji.core.storage.log.MessageLogger.c2cMessage("OUT",
                            appId, openid, "text", result, "");
                }
            } finally {
                CommandRegistry.clearContext();
            }

        } catch (Exception e) {
            log.error("[单聊消息] 异常: {}", e.getMessage(), e);
        } finally {
            dev.xuanji.core.metrics.TraceContext.exit();
        }
    }

    private void autoSyncUser(String appId, String userId) {
        try {
            jdbc.update("MERGE INTO xuanji_qqbot_user (bot_id, platform_user_id, is_deleted) KEY(bot_id,platform_user_id) VALUES (?,?,0)",
                    appId, userId);
        } catch (Exception ignored) {}
    }
}
