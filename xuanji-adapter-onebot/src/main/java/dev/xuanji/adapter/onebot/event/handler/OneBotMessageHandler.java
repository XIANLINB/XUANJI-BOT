/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.node.ObjectNode
 *  dev.xuanji.api.event.BotEvent
 *  dev.xuanji.api.event.XuanjiGroup
 *  dev.xuanji.api.event.XuanjiUser
 *  dev.xuanji.api.message.MessageChain
 *  dev.xuanji.api.message.MessageElement$At
 *  dev.xuanji.core.command.CommandRegistry
 *  dev.xuanji.core.concurrent.BotOutboundExecutor
 *  dev.xuanji.core.event.EventHandler
 *  dev.xuanji.core.event.EventMapping
 *  dev.xuanji.core.storage.MessageEventRecorder
 *  dev.xuanji.core.util.TimeUtils
 *  dev.xuanji.sdk.bot.Bot
 *  dev.xuanji.sdk.event.GroupMessageEvent
 *  dev.xuanji.sdk.event.GroupMessageEvent$Builder
 *  dev.xuanji.sdk.event.MessageEvent
 *  dev.xuanji.sdk.event.PrivateMessageEvent
 *  dev.xuanji.sdk.event.PrivateMessageEvent$Builder
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.xuanji.adapter.onebot.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.bot.OneBotXjBot;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.storage.OneBotRepository;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.core.storage.MessageEventRecorder;
import dev.xuanji.core.util.TimeUtils;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.event.MessageEvent;
import dev.xuanji.sdk.event.PrivateMessageEvent;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventMapping(value={"message.group", "message.group.normal", "message.group.anonymous", "message.group.notice", "message.private", "message.private.friend", "message.private.group", "message.private.other", "message.guild", "message.guild.normal"})
public class OneBotMessageHandler
implements EventHandler {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotMessageHandler.class);
    private final CommandRegistry commandRegistry;
    private final OneBotApiService api;
    private final OneBotMessageSenderImpl sender;
    private final OneBotRepository repository;
    private final MessageEventRecorder eventRecorder;
    private final BotOutboundExecutor outbound;

    public OneBotMessageHandler(CommandRegistry commandRegistry, OneBotApiService api, OneBotMessageSenderImpl sender, OneBotRepository repository, MessageEventRecorder eventRecorder, BotOutboundExecutor outbound) {
        this.commandRegistry = commandRegistry;
        this.api = api;
        this.sender = sender;
        this.repository = repository;
        this.eventRecorder = eventRecorder;
        this.outbound = outbound;
    }

    public String getEventType() {
        return "ONEBOT_MESSAGE";
    }

    public void handle(BotEvent botEvent) {
        JsonNode jsonNode = botEvent.platformData();
        if (!(jsonNode instanceof ObjectNode)) {
            return;
        }
        ObjectNode data = (ObjectNode)jsonNode;
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String raw = botEvent.rawEventType();
        String msgId = botEvent.replyToMsgId() != null ? botEvent.replyToMsgId() : "";
        MessageChain chain = botEvent.message();
        String plain = chain != null ? chain.plainText() : "";
        XuanjiUser senderU = botEvent.sender();
        XuanjiGroup group = botEvent.group();
        String senderId = senderU != null ? senderU.id() : "";
        String senderName = senderU != null && senderU.nickname() != null ? senderU.nickname() : senderId;
        String groupId = group != null ? group.groupId() : "";
        try {
            if (raw != null && (raw.startsWith("message.group") || raw.startsWith("message.guild"))) {
                this.handleGroup(robotId, raw, msgId, chain, plain, senderId, senderName, groupId, data);
            } else if (raw != null && raw.startsWith("message.private")) {
                this.handlePrivate(robotId, msgId, chain, plain, senderId, senderName);
            } else {
                log.debug("[OneBot\u6d88\u606f] \u672a\u8bc6\u522b\u7684\u6d88\u606f\u8def\u7531\u952e: raw={}", (Object)raw);
            }
        }
        catch (Exception e) {
            log.error("[OneBot\u6d88\u606f] \u5904\u7406\u5f02\u5e38: robotId={}, error={}", new Object[]{robotId, e.getMessage(), e});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void handleGroup(String robotId, String raw, String msgId, MessageChain chain, String plain, String senderId, String senderName, String groupId, ObjectNode data) {
        String role = data.path("sender").path("role").asText("member");
        boolean atBot = chain != null && chain.elements().stream().anyMatch(e -> {
            MessageElement.At a;
            return e instanceof MessageElement.At && robotId.equals((a = (MessageElement.At)e).userId());
        });
        GroupMessageEvent sdk = new GroupMessageEvent.Builder().messageId(msgId).content(plain).plainText(plain).messageType(0).groupId(groupId).senderId(senderId).senderName(senderName).senderRole(role).atBot(atBot).platform("onebot").chain(chain).hasAttachments(chain != null && chain.hasMedia()).build();
        if (this.repository != null) {
            this.autoSyncGroup(robotId, groupId, senderId, role, senderName);
            this.repository.insertMessage(robotId, "GROUP", groupId, senderId, "IN", "text", plain, msgId, null, null, data.toString(), TimeUtils.nowEpochSeconds());
        }
        OneBotXjBot bot = new OneBotXjBot(this.api, this.sender, this.outbound, robotId, groupId, senderId, msgId);
        CommandRegistry.setContext((String)robotId, (String)groupId, (String)msgId, (String)senderId, (MessageEvent)sdk, (Bot)bot, (String)"onebot");
        try {
            if (this.eventRecorder != null) {
                this.eventRecorder.record("IN", "text", senderName, groupId, plain, "onebot:" + raw + ":msgId=" + msgId);
            }
            String result = this.commandRegistry.executeGroupMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                if (this.eventRecorder != null) {
                    this.eventRecorder.record("OUT", "text", "bot", groupId, result, "onebot");
                }
            } else if (atBot) {
                bot.reply("\u53d1\u9001 \"\u5e2e\u52a9\" \u67e5\u770b\u53ef\u7528\u547d\u4ee4");
            }
        }
        finally {
            CommandRegistry.clearContext();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void handlePrivate(String robotId, String msgId, MessageChain chain, String plain, String senderId, String senderName) {
        PrivateMessageEvent sdk = new PrivateMessageEvent.Builder().messageId(msgId).content(plain).senderId(senderId).senderName(senderName).messageType(0).platform("onebot").chain(chain).hasAttachments(chain != null && chain.hasMedia()).build();
        if (this.repository != null) {
            this.repository.ensureUser(robotId, senderId);
            this.repository.upsertUser(robotId, senderId, senderName, null);
            this.repository.insertMessage(robotId, "C2C", null, senderId, "IN", "text", plain, msgId, null, null, null, TimeUtils.nowEpochSeconds());
        }
        OneBotXjBot bot = new OneBotXjBot(this.api, this.sender, this.outbound, robotId, null, senderId, msgId);
        CommandRegistry.setContext((String)robotId, null, (String)msgId, (String)senderId, null, (Bot)bot, (String)"onebot");
        try {
            if (this.eventRecorder != null) {
                this.eventRecorder.record("IN", "text", senderName, "", plain, "onebot:message.private:msgId=" + msgId);
            }
            String result = this.commandRegistry.executePrivateMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                if (this.eventRecorder != null) {
                    this.eventRecorder.record("OUT", "text", "bot", "", result, "onebot");
                }
            }
        }
        finally {
            CommandRegistry.clearContext();
        }
    }

    private void autoSyncGroup(String selfId, String groupId, String memberId, String role, String nickname) {
        if (groupId == null || groupId.isEmpty() || this.repository == null) {
            return;
        }
        this.repository.ensureGroup(selfId, groupId);
        this.repository.ensureGroupMember(selfId, groupId, memberId, role);
        this.repository.upsertGroupMember(selfId, groupId, memberId, role, nickname, null);
    }
}

