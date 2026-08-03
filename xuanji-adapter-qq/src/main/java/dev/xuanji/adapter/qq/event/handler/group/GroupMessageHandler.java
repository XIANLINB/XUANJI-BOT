package dev.xuanji.adapter.qq.event.handler.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.adapter.qq.dto.GroupMessageEvent;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import dev.xuanji.core.command.CommandRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 群聊消息事件处理器
 *
 * <h3>测试命令（@机器人后发送）</h3>
 * <ul>
 *   <li>文本 — 文本消息</li>
 *   <li>markdown — Markdown 消息</li>
 *   <li>按钮 — Markdown + 键盘按钮</li>
 *   <li>ark23 — Ark 链接+文本列表模板</li>
 *   <li>ark24 — Ark 文本+缩略图模板</li>
 *   <li>ark37 — Ark 大图模板</li>
 *   <li>图片 — 图片消息</li>
 *   <li>语音 — 语音消息</li>
 *   <li>视频 — 视频消息</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE"})
public class GroupMessageHandler implements EventHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final XuanjiRobotProperties robotProperties;
    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;
    private final JdbcTemplate jdbc;
    private final dev.xuanji.core.storage.log.MessageLogService logSvc;

    private final Timer handleTimer;
    private final Timer e2eTimer;

    public GroupMessageHandler(XuanjiRobotProperties robotProperties, MessageSender messageSender,
                               CommandRegistry commandRegistry,
                               JdbcTemplate jdbc, dev.xuanji.core.storage.log.MessageLogService logSvc,
                               MeterRegistry meterRegistry) {
        this.robotProperties = robotProperties;
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
        this.jdbc = jdbc;
        this.logSvc = logSvc;
        this.handleTimer = Timer.builder("xuanji.message.handle")
                .description("插件指令处理耗时").register(meterRegistry);
        this.e2eTimer = Timer.builder("xuanji.message.e2e")
                .description("消息端到端耗时").register(meterRegistry);
    }

    @Override
    public String getEventType() {
        return "GROUP_MESSAGE_EVENT";
    }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        dev.xuanji.core.metrics.TraceContext.enter(data.has("_eventId") ? data.get("_eventId").asText() : "", String.valueOf(robotId));
        Timer.Sample handleSample = Timer.start();
        try {
            GroupMessageEvent event = objectMapper.readValue(data.toString(), GroupMessageEvent.class);

            // 检查是否为机器人发送的消息
            if (event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot())) {
                if (robotProperties.isIgnoreBotMessages()) {
                    return;
                }
            }

            String content = event.getPlainTextContent().trim();
            String msgId = event.getId();
            String groupOpenid = event.getGroupOpenid();

            // 提取 member_openid（QQ 平台不用 QQ 号，每 bot 独立）
            String memberOpenid = "";
            if (event.getAuthor() != null) {
                memberOpenid = event.getAuthor().getMemberOpenid();
                if (memberOpenid == null) memberOpenid = event.getAuthor().getId();
            }

            log.info("[收到群聊消息][群{}] sender={}, memberOpenId={}, content={}",
                    groupOpenid, event.getAuthor().getUsername(), memberOpenid, content);

            // 解析 botKey / appId
            String botKey = robotProperties.findBotKeyByRobotId(robotId);
            if (botKey == null) botKey = "bot1";
            String appId = robotProperties.getRobots() != null && robotProperties.getRobots().get(botKey) != null
                    ? robotProperties.getRobots().get(botKey).getAppId() : String.valueOf(robotId);

            // 自动同步：群 + 成员记录（无则插入，有则更新 role）
            autoSyncGroup(appId, groupOpenid, memberOpenid, role(event));

            // 记录到控制台消息流水
            dev.xuanji.core.storage.ConsoleApiController.recordEvent(
                    "IN", "text", event.getAuthor().getUsername(),
                    groupOpenid, content, "msgId=" + msgId);

            // 记录到日志库（用真实 appId 而非 hashCode）
            dev.xuanji.core.storage.log.MessageLogger.groupMessage("IN",
                    appId, groupOpenid, memberOpenid,
                    "text", content, data.toString());

            // 事件注解指令调度
            var xjBot = new dev.xuanji.adapter.qq.bot.QqXjBot(messageSender, groupOpenid, msgId, appId, logSvc);
                CommandRegistry.setContext(botKey, groupOpenid, msgId, memberOpenid,
                        sdkEvent(event), xjBot, "qq");
            try {
                // 权限已在 Pipeline 的 WhitelistStage(20) 统一裁决，此处不再内联检查
                String cmdResult = commandRegistry.executeGroupMessage(content);
                if (cmdResult != null) {
                    messageSender.sendGroupText(groupOpenid, cmdResult, msgId);
                    dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT",
                            appId, groupOpenid, memberOpenid,
                            "text", cmdResult, "");
                    return;
                }
            } finally {
                CommandRegistry.clearContext();
            }

            // 未匹配 → 提示帮助
            if (event.isAtBot()) {
                String helpText = "发送\"帮助\"查看可用命令";
                messageSender.sendGroupText(groupOpenid, helpText, msgId);
                dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT", appId, groupOpenid, memberOpenid, "text", helpText, "");
            }

        } catch (Exception e) {
            log.error("[群聊消息] 解析异常: robotId={}, error={}", robotId, e.getMessage(), e);
        } finally {
            handleSample.stop(handleTimer);
            dev.xuanji.core.metrics.TraceContext.exit();
        }
    }

    private void autoSyncGroup(String appId, String groupId, String memberId, String role) {
        try {
            jdbc.update("MERGE INTO xuanji_qqbot_group (bot_id, group_id, is_deleted) KEY(bot_id,group_id) VALUES (?,?,0)",
                    appId, groupId);
        } catch (Exception ignored) {}
        try {
            jdbc.update("MERGE INTO xuanji_qqbot_group_member (bot_id, group_id, member_id, role) KEY(bot_id,group_id,member_id) VALUES (?,?,?,?)",
                    appId, groupId, memberId, role != null ? role : "member");
        } catch (Exception ignored) {}
    }

    private static String role(GroupMessageEvent e) {
        try {
            return e.getAuthor() != null ? e.getAuthor().getMemberRole() : null;
        } catch (Exception ex) { return null; }
    }

    private static dev.xuanji.sdk.event.GroupMessageEvent sdkEvent(GroupMessageEvent raw) {
        return new dev.xuanji.sdk.event.GroupMessageEvent.Builder()
                .messageId(raw.getId())
                .content(raw.getContent())
                .plainText(raw.getPlainTextContent())
                .messageType(raw.getMessageType() != null ? raw.getMessageType() : 0)
                .groupId(raw.getGroupOpenid())
                .senderId(raw.getSenderId())
                .senderName(raw.getAuthor() != null ? raw.getAuthor().getUsername() : "?")
                .senderRole(raw.getAuthor() != null ? raw.getAuthor().getMemberRole() : null)
                .atBot(raw.isAtBot())
                .platform("qq")
                .build();
    }
}
