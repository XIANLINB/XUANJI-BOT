package XuanJi.api.event;

import java.util.Objects;

/**
 * 标准化事件类型 — 平台无关的层级命名（Koishi 规范）。
 *
 * <p>格式：{@code category/subType}，如 {@code message/group}、{@code notice/member_join}。
 * 使用 {@link #of(String)} 从完整名称解析。
 */
public record XuanJiEventType(String category, String subType) {

    public String fullName() {
        if (subType == null || subType.isEmpty()) {
            return category;
        }
        return category + "/" + subType;
    }

    /**
     * 从完整层级名称解析，如 "message/group" → category=message, subType=group。
     */
    public static XuanJiEventType of(String fullName) {
        Objects.requireNonNull(fullName, "event type must not be null");
        int i = fullName.indexOf('/');
        return i < 0
                ? new XuanJiEventType(fullName, "")
                : new XuanJiEventType(fullName.substring(0, i), fullName.substring(i + 1));
    }

    // ==================== 预定义常量 ====================

    /** 私聊消息 */
    public static final XuanJiEventType MESSAGE_PRIVATE        = new XuanJiEventType("message", "private");
    /** 群聊消息 */
    public static final XuanJiEventType MESSAGE_GROUP           = new XuanJiEventType("message", "group");
    /** 频道消息 */
    public static final XuanJiEventType MESSAGE_GUILD           = new XuanJiEventType("message", "guild");

    /** 频道消息（KOOK/Discord 等频道制平台） */
    public static final XuanJiEventType MESSAGE_CHANNEL          = new XuanJiEventType("message", "channel");

    /** 群成员加入 */
    public static final XuanJiEventType NOTICE_MEMBER_JOIN      = new XuanJiEventType("notice", "member_join");
    /** 群成员退出 */
    public static final XuanJiEventType NOTICE_MEMBER_LEAVE     = new XuanJiEventType("notice", "member_leave");
    /** 群设置变更 */
    public static final XuanJiEventType NOTICE_GROUP_SETTING    = new XuanJiEventType("notice", "group_setting");
    /** 群管理员变更（对应 OneBot group_admin） */
    public static final XuanJiEventType NOTICE_GROUP_ADMIN_CHANGE = new XuanJiEventType("notice", "group_admin_change");
    /** 群文件上传（对应 OneBot group_upload） */
    public static final XuanJiEventType NOTICE_FILE_UPLOAD      = new XuanJiEventType("notice", "file_upload");
    /** 群成员戳一戳（对应 OneBot notify/poke） */
    public static final XuanJiEventType NOTICE_GROUP_POKE       = new XuanJiEventType("notice", "group_poke");
    /** 好友添加 */
    public static final XuanJiEventType NOTICE_FRIEND_ADD       = new XuanJiEventType("notice", "friend_add");
    /** 好友删除 */
    public static final XuanJiEventType NOTICE_FRIEND_DELETE    = new XuanJiEventType("notice", "friend_delete");
    /** 频道成员加入 */
    public static final XuanJiEventType NOTICE_CHANNEL_JOIN     = new XuanJiEventType("notice", "channel_join");
    /** 频道成员退出 */
    public static final XuanJiEventType NOTICE_CHANNEL_LEAVE    = new XuanJiEventType("notice", "channel_leave");

    /** 入群邀请（机器人被邀请进群） */
    public static final XuanJiEventType REQUEST_GROUP_INVITE    = new XuanJiEventType("request", "group_invite");
    /** 加群申请（用户申请入群，区别于群邀请） */
    public static final XuanJiEventType REQUEST_GROUP_APPLY     = new XuanJiEventType("request", "group_apply");
    /** 好友请求 */
    public static final XuanJiEventType REQUEST_FRIEND_ADD      = new XuanJiEventType("request", "friend_add");

    /** 按钮交互 */
    public static final XuanJiEventType INTERACTION_BUTTON      = new XuanJiEventType("interaction", "button");
    /** 表单交互 */
    public static final XuanJiEventType INTERACTION_FORM        = new XuanJiEventType("interaction", "form");
}
