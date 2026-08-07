package dev.xuanji.adapter.qqbot.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * QQ 事件转换器 — QQ 平台报文 → 统一 BotEvent。
 */
public final class QqEventConverter {

    private QqEventConverter() {}

    /**
     * 将 QQ WS/Webhook 推送的数据转换为统一事件模型。
     *
     * @param bot       当前 bot 实例
     * @param eventType 原始事件类型字符串（如 GROUP_AT_MESSAGE_CREATE）
     * @param data      事件数据（d 字段，已注入 _eventType/_eventId 元数据）
     * @param eventId   事件 ID（平台推送或框架生成）
     */
    public static BotEvent convert(Bot bot, String rawEventType, String envType, ObjectNode data, String eventId) {
        EventType type = mapEventType(rawEventType);
        XuanjiUser sender = extractUser(data, rawEventType);
        XuanjiGroup group = extractGroup(data, rawEventType);
        MessageChain message = extractMessage(data, rawEventType, bot != null ? bot.selfId() : null);
        String replyMsgId = data.path("id").asText(null);

        return new BotEvent(
                eventId != null ? eventId : UUID.randomUUID().toString(),
                type,
                bot,
                sender,
                group,
                message != null && !message.elements().isEmpty() ? message : null,
                replyMsgId,
                data,  // 保留平台原生数据
                rawEventType,
                envType
        );
    }

    static EventType mapEventType(String qqEvent) {
        return switch (qqEvent) {
            case "C2C_MESSAGE_CREATE" -> EventType.MESSAGE_PRIVATE;
            case "GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE" -> EventType.MESSAGE_GROUP;
            case "GUILD_MESSAGE_CREATE" -> EventType.MESSAGE_GUILD;
            case "GROUP_MEMBER_JOIN" -> EventType.NOTICE_MEMBER_JOIN;
            case "GROUP_MEMBER_LEAVE" -> EventType.NOTICE_MEMBER_LEAVE;
            case "INTERACTION_CREATE" -> EventType.INTERACTION_BUTTON;
            default -> EventType.of("qq/" + qqEvent.replace('_', '/').toLowerCase());
        };
    }

    static XuanjiUser extractUser(ObjectNode data, String eventType) {
        ObjectNode author = Json.getObj(data, "author");
        String userId = "";
        String nickname = "";
        boolean botFlag = false;

        if (author != null) {
            userId = author.path("id").asText("");
            nickname = author.path("username").asText("");
            botFlag = author.path("bot").asBoolean(false);
        }

        // 群聊中 author 可能包含 member_openid，优先使用
        if (author != null) {
            String memberId = author.path("member_openid").asText(null);
            if (memberId != null && !memberId.isEmpty()) {
                userId = memberId;
            }
        }

        return new XuanjiUser(
                userId, userId, nickname.isEmpty() ? userId : nickname,
                null, 0, Instant.now()
        );
    }

    static XuanjiGroup extractGroup(ObjectNode data, String eventType) {
        String groupId = data.path("group_openid").asText(null);
        if (groupId == null) {
            groupId = data.path("guild_id").asText(null);
        }
        if (groupId == null) {
            return null;
        }
        return new XuanjiGroup(groupId, "", groupId, "", 0, Instant.now());
    }

    /**
     * 从 QQ 事件数据解析消息链。
     *
     * <p>正文/富媒体一律委托 {@link QqMessageConverter#fromQqData(ObjectNode, String)}，
     * 与 SDK 事件（{@code GroupMessageHandler.sdkEvent}）走同一套解析逻辑 ——
     * 管道侧（命令路由 / 权限 / 黑名单）和插件侧看到的消息链从此完全一致，
     * 不会再出现「插件能收到图片、命令匹配却只看得见空文本」的分叉。
     *
     * <p>本方法在此基础上补两类<b>事件层</b>字段（它们不在消息载荷里，转换器不该管）：
     * <ol>
     *   <li>{@code message_reference} → {@link MessageElement.Reply}，置于链首</li>
     *   <li>{@code mentions} → {@link MessageElement.At}，紧随其后</li>
     * </ol>
     * QQ 平台会把 {@code @机器人} 从 content 里剥离到 mentions 数组，位置信息已丢失；
     * 放在正文之前是对「@机器人 指令」这一实际语序的还原。
     *
     * @param data      事件数据（d 字段）
     * @param eventType 原始事件类型（保留给后续按类型分支，如频道消息形态差异）
     * @param appId     机器人 appId，用于媒体按需下载；null 则媒体保持 URL 形态
     * @return 消息链；无任何内容时返回 {@code null}（与 {@link #convert} 的空消息语义对齐）
     */
    static MessageChain extractMessage(ObjectNode data, String eventType, String appId) {
        MessageChain body = QqMessageConverter.fromQqData(data, appId);

        List<MessageElement> prefix = new ArrayList<>();

        // 1. 引用回复：{"message_reference":{"message_id":"xxx"}}
        String refId = data.path("message_reference").path("message_id").asText(null);
        if (refId != null && !refId.isBlank()) {
            prefix.add(new MessageElement.Reply(refId));
        }

        // 2. @ 提及：QQ 把 @ 从 content 剥离到 mentions 数组
        JsonNode mentions = data.path("mentions");
        if (mentions.isArray()) {
            for (JsonNode m : mentions) {
                // 群场景优先 member_openid（与 extractUser 的 userId 口径一致，便于插件比对）
                String userId = firstNonBlank(
                        m.path("member_openid").asText(null),
                        m.path("user_openid").asText(null),
                        m.path("id").asText(null));
                if (userId == null) continue;
                prefix.add(new MessageElement.At(userId, m.path("username").asText("")));
            }
        }

        if (prefix.isEmpty()) {
            return body.elements().isEmpty() ? null : body;
        }

        MessageChain.Builder builder = MessageChain.builder();
        prefix.forEach(builder::add);
        body.elements().forEach(builder::add);
        MessageChain merged = builder.build();
        return merged.elements().isEmpty() ? null : merged;
    }

    /** 返回第一个非空白值，全空返回 null。 */
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
