package dev.xuanji.sdk.event;

import dev.xuanji.api.message.MessageChain;

import java.util.*;

/**
 * 群聊消息事件 — SDK 封装，平台无关。
 */
public class GroupMessageEvent implements MessageEvent {
    private final String messageId;
    private final String content;
    private final String plainText;
    private final int messageType;
    private final String groupId;
    private final String senderId;
    private final String senderName;
    private final String senderRole;
    private final boolean atBot;
    private final List<String> mentionedUserIds;
    private final String platform;
    private final MessageChain chain;
    private final boolean hasAttachments;

    private GroupMessageEvent(Builder b) {
        this.messageId = b.messageId;
        this.content = b.content;
        this.plainText = b.plainText;
        this.messageType = b.messageType;
        this.groupId = b.groupId;
        this.senderId = b.senderId;
        this.senderName = b.senderName;
        this.senderRole = b.senderRole;
        this.atBot = b.atBot;
        this.mentionedUserIds = b.mentionedUserIds != null ? b.mentionedUserIds : List.of();
        this.platform = b.platform;
        this.chain = b.chain;
        this.hasAttachments = b.hasAttachments;
    }

    public String getMessageId() { return messageId; }
    public String getContent() { return content; }
    public String getPlainText() { return plainText; }
    public int getMessageType() { return messageType; }
    public String getGroupId() { return groupId; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderRole() { return senderRole; }
    public boolean isAtBot() { return atBot; }
    public List<String> getMentionedUserIds() { return mentionedUserIds; }
    public String getPlatform() { return platform; }
    /** 已解析消息链（OneBot 直塞；QQ 侧为 null 时调用方自行解析）。 */
    public MessageChain chain() { return chain; }
    @Override public MessageChain getChain() { return chain; }
    @Override public Stripped getStripped() {
        return new Stripped(plainText, "", atBot, false, atBot);
    }
    @Override public String getBotKey() { return null; }
    @Override public String getUnifiedMsgOrigin() { return null; }
    public boolean hasAttachments() { return hasAttachments; }
    public Object raw() { return this; }

    public static class Builder {
        String messageId, content, plainText, groupId, senderId, senderName, senderRole, platform;
        int messageType;
        boolean atBot;
        List<String> mentionedUserIds;
        MessageChain chain;
        boolean hasAttachments;

        public Builder messageId(String v) { messageId = v; return this; }
        public Builder content(String v) { content = v; return this; }
        public Builder plainText(String v) { plainText = v; return this; }
        public Builder messageType(int v) { messageType = v; return this; }
        public Builder groupId(String v) { groupId = v; return this; }
        public Builder senderId(String v) { senderId = v; return this; }
        public Builder senderName(String v) { senderName = v; return this; }
        public Builder senderRole(String v) { senderRole = v; return this; }
        public Builder atBot(boolean v) { atBot = v; return this; }
        public Builder mentionedUserIds(List<String> v) { mentionedUserIds = v; return this; }
        public Builder platform(String v) { platform = v; return this; }
        /** 直塞已解析消息链（避免 SDK 事件无 converter/rawJson 时 getChain 恒空）。 */
        public Builder chain(MessageChain v) { chain = v; return this; }
        public Builder hasAttachments(boolean v) { hasAttachments = v; return this; }
        public GroupMessageEvent build() { return new GroupMessageEvent(this); }
    }
}
