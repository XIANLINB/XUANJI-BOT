package dev.xuanji.sdk.event;

import dev.xuanji.api.dto.C2cMessageEvent as ApiC2c;

/**
 * 单聊消息事件封装 — 类似 {@link GroupMessageEvent}，用于私聊场景。
 */
public class C2cMessageEvent {

    private final ApiC2c raw;

    public C2cMessageEvent(ApiC2c raw) { this.raw = raw; }

    public String getMessageId() { return raw.getId(); }
    public String getContent() { return raw.getContent(); }
    public String getSenderId() { return raw.getSenderId(); }
    public String getSenderName() { return raw.getSenderName(); }
    public int getMessageType() { return raw.getMessageType() != null ? raw.getMessageType() : 0; }
    public String getTimestamp() { return raw.getTimestamp(); }
    public boolean hasAttachments() { return raw.getAttachments() != null && !raw.getAttachments().isEmpty(); }
    public C2cMessageEvent raw() { return raw; }
}
