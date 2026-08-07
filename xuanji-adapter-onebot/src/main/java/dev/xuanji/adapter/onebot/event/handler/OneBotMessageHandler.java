package dev.xuanji.adapter.onebot.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.bot.OneBotXjBot;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.storage.OneBotRepository;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
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
                this.handlePrivate(robotId, msgId, chain, plain, senderId, senderName, data);
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
        // 机器人自己发出的消息可能被 OneBot 回显成入站事件：不作为「收到」计入，避免方向误判 / 重复处理
        if (senderId != null && senderId.equals(robotId)) {
            return;
        }
        String role = data.path("sender").path("role").asText("member");
        boolean atBot = chain != null && chain.elements().stream().anyMatch(e -> {
            MessageElement.At a;
            return e instanceof MessageElement.At && robotId.equals((a = (MessageElement.At)e).userId());
        });
        GroupMessageEvent sdk = new GroupMessageEvent.Builder().messageId(msgId).content(plain).plainText(plain).messageType(0).groupId(groupId).senderId(senderId).senderName(senderName).senderRole(role).atBot(atBot).platform("onebot").chain(chain).hasAttachments(chain != null && chain.hasMedia()).build();
        String inType = deriveType(chain);
        if (this.repository != null) {
            this.autoSyncGroup(robotId, groupId, senderId, role, senderName);
            this.repository.insertMessage(robotId, "GROUP", groupId, senderId, "IN", inType, plain, msgId, null, null, data.toString(), TimeUtils.nowEpochSeconds());
            // 系统事件落库（修复 #26：原先从不写 onebot_event，导致控制台「系统事件」页为空且无报错）
            try {
                this.repository.insertEvent(robotId, raw, groupId, senderId, data.toString(), TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OneBot 事件落库] 失败: {}", ex.getMessage());
            }
        }
        OneBotXjBot bot = new OneBotXjBot(this.api, this.sender, this.outbound, robotId, groupId, senderId, msgId);
        CommandRegistry.setContext((String)robotId, (String)groupId, (String)msgId, (String)senderId, (MessageEvent)sdk, (Bot)bot, (String)"onebot");
        try {
            if (this.eventRecorder != null) {
                this.eventRecorder.record("IN", inType, senderName, groupId, plain, "onebot:" + raw + ":msgId=" + msgId);
            }
            String result = this.commandRegistry.executeGroupMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                String outType = outboundType(result);
                String outRaw = outboundRaw(result);
                if (this.repository != null) {
                    try {
                        this.repository.insertMessage(robotId, "GROUP", groupId, senderId, "OUT", outType, result, null, null, null, outRaw, TimeUtils.nowEpochSeconds());
                    } catch (Exception ex) {
                        log.debug("[OneBot OUT落库] 失败: {}", ex.getMessage());
                    }
                }
                if (this.eventRecorder != null) {
                    this.eventRecorder.record("OUT", outType, "bot", groupId, result, "onebot");
                }
            } else if (atBot) {
                String help = "\u53d1\u9001 \"\u5e2e\u52a9\" \u67e5\u770b\u53ef\u7528\u547d\u4ee4";
                bot.reply(help);
                if (this.repository != null) {
                    try {
                        this.repository.insertMessage(robotId, "GROUP", groupId, senderId, "OUT", "text", help, null, null, null, outboundRaw(help), TimeUtils.nowEpochSeconds());
                    } catch (Exception ex) {
                        log.debug("[OneBot OUT落库] 失败: {}", ex.getMessage());
                    }
                }
            }
        }
        finally {
            CommandRegistry.clearContext();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void handlePrivate(String robotId, String msgId, MessageChain chain, String plain, String senderId, String senderName, ObjectNode data) {
        // 机器人自己发出的消息可能被 OneBot 回显成入站事件：不作为「收到」计入
        if (senderId != null && senderId.equals(robotId)) {
            return;
        }
        PrivateMessageEvent sdk = new PrivateMessageEvent.Builder().messageId(msgId).content(plain).senderId(senderId).senderName(senderName).messageType(0).platform("onebot").chain(chain).hasAttachments(chain != null && chain.hasMedia()).build();
        String inType = deriveType(chain);
        if (this.repository != null) {
            this.repository.ensureUser(robotId, senderId);
            this.repository.upsertUser(robotId, senderId, senderName, null);
            this.repository.insertMessage(robotId, "C2C", null, senderId, "IN", inType, plain, msgId, null, null, data != null ? data.toString() : null, TimeUtils.nowEpochSeconds());
            // 系统事件落库（修复 #26：单聊消息也写入 onebot_event）
            try {
                this.repository.insertEvent(robotId, "message.private", null, senderId, data != null ? data.toString() : null, TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OneBot 事件落库] 失败: {}", ex.getMessage());
            }
        }
        OneBotXjBot bot = new OneBotXjBot(this.api, this.sender, this.outbound, robotId, null, senderId, msgId);
        CommandRegistry.setContext((String)robotId, null, (String)msgId, (String)senderId, null, (Bot)bot, (String)"onebot");
        try {
            if (this.eventRecorder != null) {
                this.eventRecorder.record("IN", inType, senderName, "", plain, "onebot:message.private:msgId=" + msgId);
            }
            String result = this.commandRegistry.executePrivateMessage(plain.trim());
            if (result != null) {
                bot.reply(result);
                String outType = outboundType(result);
                String outRaw = outboundRaw(result);
                if (this.repository != null) {
                    try {
                        this.repository.insertMessage(robotId, "C2C", null, senderId, "OUT", outType, result, null, null, null, outRaw, TimeUtils.nowEpochSeconds());
                    } catch (Exception ex) {
                        log.debug("[OneBot OUT落库] 失败: {}", ex.getMessage());
                    }
                }
                if (this.eventRecorder != null) {
                    this.eventRecorder.record("OUT", outType, "bot", "", result, "onebot");
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

    /** 由消息链推导消息类型（图片/语音/视频/文件/Markdown/文本）。
     *  优先看元素类型；纯文本但内容像 Markdown（OneBot 常把 markdown 当 text 段下发）→ 归为 markdown，修复「markdown 显示成 text」。 */
    private static String deriveType(MessageChain chain) {
        if (chain == null) {
            return "text";
        }
        for (MessageElement e : chain.elements()) {
            if (e instanceof MessageElement.Image) {
                return "image";
            }
            if (e instanceof MessageElement.Voice) {
                return "voice";
            }
            if (e instanceof MessageElement.Video) {
                return "video";
            }
            if (e instanceof MessageElement.File) {
                return "file";
            }
            if (e instanceof MessageElement.Markdown) {
                return "markdown";
            }
        }
        String plain = chain.plainText();
        if (plain != null && looksLikeMarkdown(plain)) {
            return "markdown";
        }
        return "text";
    }

    /** OUT 方向消息类型：reply 走文本传输，但指令结果常为 Markdown，按内容粗略识别。 */
    private static String outboundType(String content) {
        return (content != null && looksLikeMarkdown(content)) ? "markdown" : "text";
    }

    /** 粗略判断文本是否像 Markdown（标题/加粗/代码块/图片/链接/引用/列表）。 */
    private static boolean looksLikeMarkdown(String c) {
        if (c == null || c.isBlank()) {
            return false;
        }
        String t = c.trim();
        if (t.startsWith("#") || t.startsWith("> ") || t.startsWith("- ") || t.startsWith("* ")
                || t.startsWith("+ ") || t.startsWith("1. ") || t.contains("**") || t.contains("__")
                || t.contains("```") || t.contains("![") || (t.contains("[") && t.contains("]("))) {
            return true;
        }
        return false;
    }

    /** OUT 出站载荷（OneBot 协议段）作为「原始数据」落库，便于消息监控查看发送内容。 */
    private static String outboundRaw(String content) {
        try {
            ArrayNode segments = OneBotMessageConverter.toSegments(MessageChain.text(content));
            return segments.toString();
        } catch (Exception ex) {
            return Json.obj().put("content", content).toString();
        }
    }
}

