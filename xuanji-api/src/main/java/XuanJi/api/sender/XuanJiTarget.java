package XuanJi.api.sender;

/**
 * 消息发送目标。
 */
public sealed interface XuanJiTarget permits XuanJiTarget.Private, XuanJiTarget.Group, XuanJiTarget.Guild {

    record Private(String openid) implements XuanJiTarget {}
    record Group(String groupOpenid) implements XuanJiTarget {}
    record Guild(String channelId) implements XuanJiTarget {}
}
