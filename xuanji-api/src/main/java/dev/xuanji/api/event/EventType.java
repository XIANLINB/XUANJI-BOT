package dev.xuanji.api.event;

import java.util.Objects;

/**
 * 标准化事件类型 — 平台无关的层级命名（Koishi 规范）。
 *
 * <p>格式：{@code category/subType}，如 {@code message/group}、{@code notice/member_join}。
 * 使用 {@link #of(String)} 从完整名称解析。
 */
public record EventType(String category, String subType) {

    public String fullName() {
        if (subType == null || subType.isEmpty()) {
            return category;
        }
        return category + "/" + subType;
    }

    /**
     * 从完整层级名称解析，如 "message/group" → category=message, subType=group。
     */
    public static EventType of(String fullName) {
        Objects.requireNonNull(fullName, "event type must not be null");
        int i = fullName.indexOf('/');
        return i < 0
                ? new EventType(fullName, "")
                : new EventType(fullName.substring(0, i), fullName.substring(i + 1));
    }

    // ==================== 预定义常量 ====================

    /** 私聊消息 */
    public static final EventType MESSAGE_PRIVATE        = new EventType("message", "private");
    /** 群聊消息 */
    public static final EventType MESSAGE_GROUP           = new EventType("message", "group");
    /** 频道消息 */
    public static final EventType MESSAGE_GUILD           = new EventType("message", "guild");

    /** 群成员加入 */
    public static final EventType NOTICE_MEMBER_JOIN      = new EventType("notice", "member_join");
    /** 群成员退出 */
    public static final EventType NOTICE_MEMBER_LEAVE     = new EventType("notice", "member_leave");
    /** 群设置变更 */
    public static final EventType NOTICE_GROUP_SETTING    = new EventType("notice", "group_setting");

    /** 入群请求 */
    public static final EventType REQUEST_GROUP_INVITE    = new EventType("request", "group_invite");
    /** 好友请求 */
    public static final EventType REQUEST_FRIEND_ADD      = new EventType("request", "friend_add");

    /** 按钮交互 */
    public static final EventType INTERACTION_BUTTON      = new EventType("interaction", "button");
    /** 表单交互 */
    public static final EventType INTERACTION_FORM        = new EventType("interaction", "form");
}
