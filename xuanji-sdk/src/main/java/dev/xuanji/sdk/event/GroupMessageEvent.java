package dev.xuanji.sdk.event;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 群聊消息事件封装 — 包装 QQ 的 GroupMessageEvent DTO，为插件提供便捷 API。
 */
public class GroupMessageEvent {

    private final dev.xuanji.api.dto.GroupMessageEvent raw;

    public GroupMessageEvent(dev.xuanji.api.dto.GroupMessageEvent raw) {
        this.raw = raw;
    }

    public String getMessageId() { return raw.getId(); }
    public String getContent() { return raw.getContent(); }
    public String getPlainText() { return raw.getPlainTextContent(); }
    public int getMessageType() { return raw.getMessageType() != null ? raw.getMessageType() : 0; }
    public String getTimestamp() { return raw.getTimestamp(); }
    public String getGroupId() { return raw.getGroupOpenid(); }
    public String getSenderId() { return raw.getSenderId(); }
    public String getSenderName() {
        return raw.getAuthor() != null && raw.getAuthor().getUsername() != null
                ? raw.getAuthor().getUsername() : "未知";
    }
    public String getSenderRole() {
        return raw.getAuthor() != null ? raw.getAuthor().getMemberRole() : null;
    }
    public boolean isAtBot() { return raw.isAtBot(); }

    public List<String> getMentionedUserIds() {
        if (raw.getMentions() == null) return List.of();
        return raw.getMentions().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsYou()))
                .map(m -> m.getMemberOpenid() != null ? m.getMemberOpenid() : m.getId())
                .collect(Collectors.toList());
    }

    public boolean hasAttachments() { return raw.getAttachments() != null && !raw.getAttachments().isEmpty(); }

    /** 返回原始 QQ DTO 对象（调试用） */
    public Object raw() { return raw; }
}
