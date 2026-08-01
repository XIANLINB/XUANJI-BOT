package dev.xuanji.sdk.event;

/**
 * 单聊/私聊消息事件 — SDK 封装，平台无关。
 */
public class PrivateMessageEvent {
    private final String messageId;
    private final String content;
    private final String senderId;
    private final String senderName;
    private final int messageType;

    public PrivateMessageEvent(String messageId, String content, String senderId, String senderName, int messageType) {
        this.messageId = messageId;
        this.content = content;
        this.senderId = senderId;
        this.senderName = senderName;
        this.messageType = messageType;
    }

    public String getMessageId() { return messageId; }
    public String getContent() { return content; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public int getMessageType() { return messageType; }
    public boolean hasAttachments() { return false; }
    public Object raw() { return this; }
}
