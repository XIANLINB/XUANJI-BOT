package dev.xuanji.adapter.qqbot.event.handler.c2c;

import dev.xuanji.adapter.qqbot.dto.C2cMessageEvent;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.adapter.qqbot.api.MessageSender;
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
    private final dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory;
    private final dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository;
    private final dev.xuanji.core.config.ConfigService configService;

    public C2cMessageHandler(MessageSender messageSender, CommandRegistry commandRegistry,
                             XuanjiRobotProperties robotProperties, JdbcTemplate jdbc,
                             dev.xuanji.core.storage.log.MessageLogService logSvc,
                             dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory,
                             dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository,
                             dev.xuanji.core.config.ConfigService configService) {
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
        this.robotProperties = robotProperties;
        this.jdbc = jdbc;
        this.logSvc = logSvc;
        this.configService = configService;
        this.botFactory = botFactory;
        this.qqBotRepository = qqBotRepository;
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
            boolean isBotAuthor = event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot());

            String content = event.getContent() != null ? event.getContent().trim() : "";
            String msgId = event.getId();
            String openid = event.getSenderId();

            log.info("[收到单聊消息] sender={}, content={}", event.getSenderName(), content);
            String botKey = robotProperties.findBotKeyByRobotId(robotId);
            if (botKey == null) botKey = "bot1";
            String appId = robotProperties.getRobots() != null && robotProperties.getRobots().get(botKey) != null
                    ? robotProperties.getRobots().get(botKey).getAppId() : String.valueOf(robotId);
            String inType = QqBotRepository.msgTypeLabel(
                    event.getMessageType(), firstAttachmentContentType(event), firstAttachmentFilename(event));
            // 纯文本但内容像 Markdown（QQ 可能以 text 段送达 markdown 文本）→ 归为 markdown
            if ("text".equals(inType)) inType = outboundType(content);
            // 机器人自消息（author.bot=true）已在发送侧由 MessageSender 落 OUT，回调不再重复记 IN
            if (!isBotAuthor) {
                dev.xuanji.core.storage.log.MessageLogger.c2cMessage("IN",
                        appId, openid, inType, content, data.toString());

                // 消息落库（per-bot qqbot_message）
                try {
                    qqBotRepository.insertMessage(appId, "c2c", null, openid,
                            "IN", inType, content, msgId, null, null, data.toString(),
                            dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
                } catch (Exception ex) {
                    log.debug("[消息落库] 失败: {}", ex.getMessage());
                }
            }

            // 机器人自消息：三级配置判定「忽略其他机器人消息」→ 只记录不做任何处理
            if (isBotAuthor) {
                boolean ignore = configService.isIgnoreBotMessages(botKey, null);
                if (ignore) {
                    log.info("[单聊消息] 忽略其他机器人消息（只记录不处理）: sender={}", openid);
                    return;
                }
            }

            // 自动同步用户（无则插入，有则标记未删除；机器人自消息跳过）
            if (!isBotAuthor) {
                autoSyncUser(appId, openid, event.getSenderName());
            }
            // 事件落库（per-bot qqbot_event，控制台事件流数据源）
            try {
                qqBotRepository.insertEvent(appId, "C2C_MESSAGE_CREATE", null, openid,
                        data.toString(), dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[事件落库] 失败: {}", ex.getMessage());
            }

            // 创建 Bot 实例（C2C 模式）
            var bot = botFactory.c2c(openid, msgId, appId);
            CommandRegistry.setContext("bot1", null, msgId, openid, null, bot, "qq");

            try {
                String result = commandRegistry.executePrivateMessage(content);
                if (result != null) {
                    // OUT 落库由 MessageSender 统一完成（bot.reply → sendC2cText/Markdown → 落库）
                    bot.reply(result);
                    dev.xuanji.core.storage.log.MessageLogger.c2cMessage("OUT",
                            appId, openid, outboundType(result), result, "");
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

    private void autoSyncUser(String appId, String userId, String nickname) {
        try {
            qqBotRepository.ensureUser(appId, userId, nickname);
        } catch (Exception ignored) {}
    }

    /** 取首条附件的 content_type（图片/语音/视频细分的依据）。 */
    private static String firstAttachmentContentType(C2cMessageEvent e) {
        if (e.getAttachments() == null || e.getAttachments().isEmpty()) return null;
        return e.getAttachments().get(0).getContentType();
    }

    /** 取首条附件的文件名（按扩展名兜底识别媒体类型）。 */
    private static String firstAttachmentFilename(C2cMessageEvent e) {
        if (e.getAttachments() == null || e.getAttachments().isEmpty()) return null;
        return e.getAttachments().get(0).getFilename();
    }

    /** OUT 方向消息类型：指令结果常是 Markdown，按内容粗略识别。 */
    private static String outboundType(String content) {
        if (content == null) return "text";
        String c = content.trim();
        if (c.startsWith("#") || c.contains("**") || c.contains("```")
                || c.contains("![") || (c.contains("[") && c.contains("]("))
                || c.startsWith("> ") || c.startsWith("- ") || c.startsWith("* ")) {
            return "markdown";
        }
        return "text";
    }
}
