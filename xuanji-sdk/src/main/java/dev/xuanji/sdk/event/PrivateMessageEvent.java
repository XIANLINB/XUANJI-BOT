package dev.xuanji.sdk.event;

import dev.xuanji.api.message.MessageChain;

/**
 * 单聊/私聊消息事件 — SDK 封装，平台无关。
 */
public class PrivateMessageEvent implements MessageEvent {
    private final String messageId;
    private final String content;
    private final String senderId;
    private final String senderName;
    private final int messageType;
    private final String platform;
    private final MessageChain chain;
    private final boolean hasAttachments;

    public PrivateMessageEvent(String messageId, String content, String senderId, String senderName, int messageType, String platform) {
        this(messageId, content, senderId, senderName, messageType, platform, null, false);
    }

    private PrivateMessageEvent(String messageId, String content, String senderId, String senderName, int messageType,
                                String platform, MessageChain chain, boolean hasAttachments) {
        this.messageId = messageId;
        this.content = content;
        this.senderId = senderId;
        this.senderName = senderName;
        this.messageType = messageType;
        this.platform = platform;
        this.chain = chain;
        this.hasAttachments = hasAttachments;
    }

    public String getMessageId() { return messageId; }
    public String getContent() { return content; }
    @Override public String getPlainText() { return content; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public int getMessageType() { return messageType; }
    public String getPlatform() { return platform; }
    /** 已解析消息链（OneBot 直塞；其余平台为 null 时调用方自行解析）。 */
    public MessageChain chain() { return chain; }
    @Override public MessageChain getChain() { return chain; }
    @Override public Stripped getStripped() {
        return new Stripped(content, "", false, false, false);
    }
    @Override public String getBotKey() { return null; }
    @Override public String getUnifiedMsgOrigin() { return null; }
    public boolean hasAttachments() { return hasAttachments; }
    public Object raw() { return this; }

    public static class Builder {
        String messageId, content, senderId, senderName, platform;
        int messageType;
        MessageChain chain;
        boolean hasAttachments;

        public Builder messageId(String v) { messageId = v; return this; }
        public Builder content(String v) { content = v; return this; }
        public Builder senderId(String v) { senderId = v; return this; }
        public Builder senderName(String v) { senderName = v; return this; }
        public Builder messageType(int v) { messageType = v; return this; }
        public Builder platform(String v) { platform = v; return this; }
        public Builder chain(MessageChain v) { chain = v; return this; }
        public Builder hasAttachments(boolean v) { hasAttachments = v; return this; }
        public PrivateMessageEvent build() {
            return new PrivateMessageEvent(messageId, content, senderId, senderName, messageType, platform, chain, hasAttachments);
        }
    }
}
