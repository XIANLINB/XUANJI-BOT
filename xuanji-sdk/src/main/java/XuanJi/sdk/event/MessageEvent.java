package XuanJi.sdk.event;

import XuanJi.api.message.XuanJiMessage;

/**
 * 消息事件公共接口 — {@link GroupMessageEvent} / {@link PrivateMessageEvent} 的统一抽象。
 *
 * <p>插件方法可按 {@code MessageEvent} 类型注入，无需区分群聊/私聊即可读取
 * 消息链、剥离结果、归属机器人与统一会话标识（P0-A 回灌能力）。
 */
public interface MessageEvent {
    String getMessageId();
    String getContent();
    String getPlainText();
    String getPlatform();
    XuanJiMessage getChain();
    Stripped getStripped();
    String getBotKey();
    String getUnifiedMsgOrigin();
    boolean hasAttachments();
    Object raw();
}
