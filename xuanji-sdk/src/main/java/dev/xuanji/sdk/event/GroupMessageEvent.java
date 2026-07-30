package dev.xuanji.sdk.event;

import dev.xuanji.api.dto.GroupMessageEvent;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 群聊消息事件封装 — 源自 {@link GroupMessageEvent}，为插件提供便捷 API。
 *
 * <p>使用方式：在 {@code @Command} 方法参数中声明即可，框架自动注入。
 *
 * <pre>
 * @Command("hi")
 * public String hi(GroupMessageEvent event, Bot bot) {
 *     bot.reply("你好 " + event.getSenderName());
 * }
 * </pre>
 */
public class GroupMessageEvent {

    private final GroupMessageEvent raw;

    public GroupMessageEvent(GroupMessageEvent raw) {
        this.raw = raw;
    }

    // ===== 基础字段 =====

    /** 消息 ID（全局唯一，可用于回复引用） */
    public String getMessageId() { return raw.getId(); }

    /** 原始内容（可能含 @标签） */
    public String getContent() { return raw.getContent(); }

    /** 纯文本内容（已去除 @标签） */
    public String getPlainText() { return raw.getPlainTextContent(); }

    /** 消息类型：0=文本，2=Markdown，7=富媒体 */
    public int getMessageType() { return raw.getMessageType() != null ? raw.getMessageType() : 0; }

    /** 时间戳 */
    public String getTimestamp() { return raw.getTimestamp(); }

    // ===== 群 =====

    /** 群 OpenID */
    public String getGroupId() { return raw.getGroupOpenid(); }

    // ===== 发送者 =====

    /** 发送者 member_openid（群聊标识） */
    public String getSenderId() { return raw.getSenderId(); }

    /** 发送者昵称 */
    public String getSenderName() {
        return raw.getAuthor() != null && raw.getAuthor().getUsername() != null
                ? raw.getAuthor().getUsername() : "未知";
    }

    /** 发送者群内角色：owner/admin/member */
    public String getSenderRole() {
        return raw.getAuthor() != null ? raw.getAuthor().getMemberRole() : null;
    }

    /** 是否 @了机器人 */
    public boolean isAtBot() { return raw.isAtBot(); }

    // ===== @列表 =====

    /** 被 @的用户 openid 列表 */
    public List<String> getMentionedUserIds() {
        if (raw.getMentions() == null) return List.of();
        return raw.getMentions().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsYou()))
                .map(m -> m.getMemberOpenid() != null ? m.getMemberOpenid() : m.getId())
                .collect(Collectors.toList());
    }

    // ===== 富媒体 =====

    /** 是否有附件 */
    public boolean hasAttachments() {
        return raw.getAttachments() != null && !raw.getAttachments().isEmpty();
    }

    /** 附件信息 */
    public List<GroupMessageEvent.Attachment> getAttachments() { return raw.getAttachments(); }

    // ===== 原始对象（高级场景） =====

    public GroupMessageEvent raw() { return raw; }
}
