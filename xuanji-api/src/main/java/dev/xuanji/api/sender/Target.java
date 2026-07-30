package dev.xuanji.api.sender;

/**
 * 消息发送目标。
 */
public sealed interface Target permits Target.Private, Target.Group, Target.Guild {

    record Private(String openid) implements Target {}
    record Group(String groupOpenid) implements Target {}
    record Guild(String channelId) implements Target {}
}
