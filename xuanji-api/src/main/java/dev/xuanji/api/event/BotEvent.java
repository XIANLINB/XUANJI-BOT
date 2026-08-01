package dev.xuanji.api.event;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.message.MessageChain;

/**
 * 统一事件模型 — 全平台归一的事件数据对象。
 *
 * <p>适配器负责将平台报文转换为本对象；插件与核心调度层只认 BotEvent。
 * 非消息类事件（通知/请求）的 message 字段为 null，原始数据保留在 platformData 中。
 */
public record BotEvent(
        /** 全局唯一事件 ID（用于幂等去重） */
        String eventId,

        /** 标准化事件类型 */
        EventType type,

        /** 接收事件的机器人实例 */
        Bot bot,

        /** 事件发送者（统一用户档案，私聊=发送者，群聊=发送消息的成员） */
        XuanjiUser sender,

        /** 事件所属群组（私聊事件为 null） */
        XuanjiGroup group,

        /** 消息链（非消息事件为 null） */
        MessageChain message,

        /** 被动回复时引用的原始消息 ID */
        String replyToMsgId,

        /** 平台原生数据透传（身份组、附加字段等精细场景用） */
        JsonNode platformData,

        /** 平台原始事件类型字符串（如 GROUP_AT_MESSAGE_CREATE），用于分发路由 */
        String rawEventType,

        /** 环境类型（SANDBOX / PRODUCTION） */
        String envType
) {
    /** 是否为群聊事件 */
    public boolean isGroupEvent() {
        return group != null;
    }

    /** 是否为消息类事件 */
    public boolean isMessageEvent() {
        return message != null;
    }
}
