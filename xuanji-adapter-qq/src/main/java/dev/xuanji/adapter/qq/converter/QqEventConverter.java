package dev.xuanji.adapter.qq.converter;

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
    public static BotEvent convert(Bot bot, String eventType, ObjectNode data, String eventId) {
        EventType type = mapEventType(eventType);
        XuanjiUser sender = extractUser(data, eventType);
        XuanjiGroup group = extractGroup(data, eventType);
        MessageChain message = extractMessage(data, eventType);
        String replyMsgId = data.path("id").asText(null);

        return new BotEvent(
                eventId != null ? eventId : UUID.randomUUID().toString(),
                type,
                bot,
                sender,
                group,
                message != null && !message.elements().isEmpty() ? message : null,
                replyMsgId,
                data  // 保留平台原生数据
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

    static MessageChain extractMessage(ObjectNode data, String eventType) {
        String content = data.path("content").asText(null);
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 简单提取纯文本（后续 P3 完整解析分段）
        MessageChain.Builder builder = MessageChain.builder();
        builder.text(content.trim());

        // @机器人 检测
        JsonNode mentions = data.path("mentions");
        if (mentions != null && mentions.isArray()) {
            for (JsonNode m : mentions) {
                String botFlag = m.path("bot").asText("false");
                if ("true".equals(botFlag)) {
                    // 在文本中检测到 @机器人
                    break;
                }
            }
        }

        return builder.build();
    }
}
