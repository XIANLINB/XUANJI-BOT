package dev.xuanji.adapter.onebot.event.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.bot.OneBotXjBot;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.core.storage.ConsoleApiController;
import dev.xuanji.core.storage.log.MessageLogger;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.event.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * OneBot 消息事件处理器 —— 把 OneBot 的群聊 / 私聊消息接入统一的 CommandRegistry 插件指令系统。
 *
 * <p>与 QQ 的 GroupMessageHandler/C2cMessageHandler 完全镜像：把平台报文转换为平台无关的
 * GroupMessageEvent/PrivateMessageEvent，设置线程上下文后调用 CommandRegistry。
 * 因此同一个 @GroupMessage 插件方法既能响应 QQ 官方消息，也能响应 Napcat(OneBot) 消息。
 *
 * <p>事件类型路由键（rawEventType）取自 OneBotEventConverter：
 * 群消息 message.group、私聊 message.private、频道 message.guild。
 */
@Slf4j
@EventMapping({"message.group", "message.group.normal", "message.group.anonymous", "message.group.notice",
               "message.private", "message.private.friend", "message.private.group", "message.private.other",
               "message.guild", "message.guild.normal"})
public class OneBotMessageHandler implements EventHandler {

    private final CommandRegistry commandRegistry;
    private final OneBotApiService api;
    private final OneBotMessageSenderImpl sender;
    private final JdbcTemplate jdbc;

    public OneBotMessageHandler(CommandRegistry commandRegistry, OneBotApiService api,
                                OneBotMessageSenderImpl sender, JdbcTemplate jdbc) {
        this.commandRegistry = commandRegistry;
        this.api = api;
        this.sender = sender;
        this.jdbc = jdbc;
    }

    @Override
    public String getEventType() {
        return "ONEBOT_MESSAGE";
    }

    @Override
    public void handle(BotEvent botEvent) {
        if (!(botEvent.platformData() instanceof ObjectNode data)) {
            return;
        }
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String raw = botEvent.rawEventType();
        String msgId = botEvent.replyToMsgId() != null ? botEvent.replyToMsgId() : "";
        MessageChain chain = botEvent.message();
        String plain = chain != null ? chain.plainText() : "";
        XuanjiUser senderU = botEvent.sender();
        XuanjiGroup group = botEvent.group();
        String senderId = senderU != null ? senderU.id() : "";
        String senderName = (senderU != null && senderU.nickname() != null) ? senderU.nickname() : senderId;
        String groupId = group != null ? group.groupId() : "";

        try {
            // rawEventType = post_type.detail.sub_type（如 message.group.normal / message.private.friend）
            // 用前缀匹配，兼容「带 sub_type」与「不带 sub_type」两种报文。
            if (raw != null && (raw.startsWith("message.group") || raw.startsWith("message.guild"))) {
                handleGroup(robotId, raw, msgId, chain, plain, senderId, senderName, groupId, data);
            } else if (raw != null && raw.startsWith("message.private")) {
                handlePrivate(robotId, msgId, plain, senderId, senderName);
            } else {
                log.debug("[OneBot消息] 未识别的消息路由键: raw={}", raw);
            }
        } catch (Exception e) {
            log.error("[OneBot消息] 处理异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }

    private void handleGroup(String robotId, String raw, String msgId, MessageChain chain,
                             String plain, String senderId, String senderName, String groupId, ObjectNode data) {
        String role = data.path("sender").path("role").asText("member");
        boolean atBot = chain != null && chain.elements().stream()
                .anyMatch(e -> e instanceof MessageElement.At a && robotId.equals(a.userId()));

        GroupMessageEvent sdk = new GroupMessageEvent.Builder()
                .messageId(msgId).content(plain).plainText(plain)
                .messageType(0).groupId(groupId).senderId(senderId)
                .senderName(senderName).senderRole(role).atBot(atBot).platform("onebot").build();

        // 自动同步：群 + 成员（与 QQ 适配器 autoSyncGroup 同构，落 XUANJI_ONEBOT_* 表）
        autoSyncGroup(robotId, groupId, senderId, role);
        // 群消息流水（日志库 xuanji_onebot_group_message）
        MessageLogger.onebotGroupMessage("IN", robotId, groupId, senderId, "text", plain, data.toString());

        OneBotXjBot bot = new OneBotXjBot(api, sender, robotId, groupId, senderId, msgId);
        CommandRegistry.setContext(robotId, groupId, msgId, senderId, sdk, bot, "onebot");
        try {
            ConsoleApiController.recordEvent("IN", "text", senderName, groupId, plain,
                    "onebot:" + raw + ":msgId=" + msgId);
            String result = commandRegistry.executeGroupMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                ConsoleApiController.recordEvent("OUT", "text", "bot", groupId, result, "onebot");
            } else if (atBot) {
                bot.reply("发送 \"帮助\" 查看可用命令");
            }
        } finally {
            CommandRegistry.clearContext();
        }
    }

    private void handlePrivate(String robotId, String msgId, String plain,
                               String senderId, String senderName) {
        PrivateMessageEvent sdk = new PrivateMessageEvent(msgId, plain, senderId, senderName, 0, "onebot");
        OneBotXjBot bot = new OneBotXjBot(api, sender, robotId, null, senderId, msgId);
        CommandRegistry.setContext(robotId, null, msgId, senderId, null, bot, "onebot");
        try {
            ConsoleApiController.recordEvent("IN", "text", senderName, "", plain,
                    "onebot:message.private:msgId=" + msgId);
            MessageLogger.onebotEvent("IN", robotId, "message.private", null, "私聊消息");
            String result = commandRegistry.executePrivateMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                ConsoleApiController.recordEvent("OUT", "text", "bot", "", result, "onebot");
            }
        } finally {
            CommandRegistry.clearContext();
        }
    }

    /** 把 OneBot 群与成员同步进 xuanji_onebot_group / xuanji_onebot_group_member（与 QQ autoSyncGroup 同构）。 */
    private void autoSyncGroup(String botId, String groupId, String memberId, String role) {
        if (groupId == null || groupId.isEmpty()) return;
        try {
            jdbc.update("MERGE INTO xuanji_onebot_group (bot_id, group_id, is_deleted) KEY(bot_id,group_id) VALUES (?,?,0)",
                    botId, groupId);
        } catch (Exception ignored) {}
        try {
            jdbc.update("MERGE INTO xuanji_onebot_group_member (bot_id, group_id, member_id, role) KEY(bot_id,group_id,member_id) VALUES (?,?,?,?)",
                    botId, groupId, memberId, role != null ? role : "member");
        } catch (Exception ignored) {}
    }
}