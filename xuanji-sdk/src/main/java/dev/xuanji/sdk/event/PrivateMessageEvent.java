package dev.xuanji.sdk.event;

/**
 * 单聊消息事件封装 — 包装 QQ 的 C2cMessageEvent DTO，用于私聊场景。
 */
public class PrivateMessageEvent {

    private final dev.xuanji.api.dto.C2cMessageEvent raw;

    public PrivateMessageEvent(dev.xuanji.api.dto.C2cMessageEvent raw) { this.raw = raw; }

    public String getMessageId() { return raw.getId(); }
    public String getContent() { return raw.getContent(); }
    public String getSenderId() { return raw.getSenderId(); }
    public String getSenderName() { return raw.getSenderName(); }
    public int getMessageType() { return raw.getMessageType() != null ? raw.getMessageType() : 0; }
    public String getTimestamp() { return raw.getTimestamp(); }
    public boolean hasAttachments() { return raw.getAttachments() != null && !raw.getAttachments().isEmpty(); }
    public dev.xuanji.api.dto.C2cMessageEvent raw() { return raw; }
}
