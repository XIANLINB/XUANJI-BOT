package dev.xuanji.sdk.event;

import java.util.*;

/**
 * 群聊消息事件 — SDK 封装，平台无关。
 */
public class GroupMessageEvent {
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
    public boolean hasAttachments() { return false; }
    public Object raw() { return this; }

    public static class Builder {
        String messageId, content, plainText, groupId, senderId, senderName, senderRole;
        int messageType;
        boolean atBot;
        List<String> mentionedUserIds;

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
        public GroupMessageEvent build() { return new GroupMessageEvent(this); }
    }
}
