package dev.xuanji.adapter.onebot.converter;

import dev.xuanji.api.event.EventType;

/**
 * OneBot v11 全量事件类型常量 —— 适配器内统一的事件类型来源。
 *
 * <p>清单对齐 OneBot v11 标准事件 + Napcat / go-cqhttp 扩展：
 * <ul>
 *   <li>{@code message}：私聊 / 群聊（OneBot v11 无原生频道，guild 仅作兼容）</li>
 *   <li>{@code notice}：群文件上传、管理员变更、退群/入群、禁言、名片变更、
 *       好友添加、群/好友消息撤回、离线文件、notify（戳一戳/运气王/群荣誉）</li>
 *   <li>{@code request}：好友请求、加群请求（add）/ 邀请（invite）</li>
 *   <li>{@code meta_event}：生命周期、心跳</li>
 * </ul>
 *
 * <p>所有常量均为 {@link EventType} 值对象，未命中标准清单的事件仍由转换器兜底为
 * {@code category/subType} 命名空间，不会丢失。
 */
public final class OneBotEventTypes {

    private OneBotEventTypes() {}

    // ==================== message ====================

    /** 私聊消息 */
    public static final EventType MESSAGE_PRIVATE = EventType.MESSAGE_PRIVATE;
    /** 群聊消息 */
    public static final EventType MESSAGE_GROUP    = EventType.MESSAGE_GROUP;
    /** 频道消息（兼容扩展，标准 OneBot v11 不产生） */
    public static final EventType MESSAGE_GUILD    = EventType.MESSAGE_GUILD;

    // ==================== notice ====================

    /** 群文件上传 */
    public static final EventType NOTICE_GROUP_UPLOAD     = EventType.of("notice/group_upload");
    /** 群管理员变更（set/unset） */
    public static final EventType NOTICE_GROUP_ADMIN      = EventType.of("notice/group_admin");
    /** 群成员减少（leave/kick/kick_me） */
    public static final EventType NOTICE_GROUP_DECREASE   = EventType.of("notice/group_decrease");
    /** 群成员增加（add/invite/approve） */
    public static final EventType NOTICE_GROUP_INCREASE   = EventType.of("notice/group_increase");
    /** 群禁言（ban/lift_ban） */
    public static final EventType NOTICE_GROUP_BAN        = EventType.of("notice/group_ban");
    /** 群名片变更 */
    public static final EventType NOTICE_GROUP_CARD       = EventType.of("notice/group_card");
    /** 好友添加 */
    public static final EventType NOTICE_FRIEND_ADD       = EventType.of("notice/friend_add");
    /** 群消息撤回 */
    public static final EventType NOTICE_GROUP_RECALL     = EventType.of("notice/group_recall");
    /** 好友消息撤回 */
    public static final EventType NOTICE_FRIEND_RECALL    = EventType.of("notice/friend_recall");
    /** 离线文件（go-cqhttp 扩展） */
    public static final EventType NOTICE_OFFLINE_FILE     = EventType.of("notice/offline_file");
    /** 戳一戳 */
    public static final EventType NOTICE_NOTIFY_POKE      = EventType.of("notice/notify_poke");
    /** 群红包运气王 */
    public static final EventType NOTICE_NOTIFY_LUCKY_KING = EventType.of("notice/notify_lucky_king");
    /** 群荣誉（龙王/群聊之火/快乐源泉） */
    public static final EventType NOTICE_NOTIFY_HONOR     = EventType.of("notice/notify_honor");

    // ==================== request ====================

    /** 好友添加请求 */
    public static final EventType REQUEST_FRIEND_ADD   = EventType.REQUEST_FRIEND_ADD;
    /** 加群请求（用户申请） */
    public static final EventType REQUEST_GROUP_ADD    = EventType.of("request/group_add");
    /** 加群邀请（管理员/群主邀请） */
    public static final EventType REQUEST_GROUP_INVITE = EventType.REQUEST_GROUP_INVITE;

    // ==================== meta_event ====================

    /** 生命周期（connect/disable/enable/client_disable） */
    public static final EventType META_LIFECYCLE = EventType.of("meta/lifecycle");
    /** 心跳 */
    public static final EventType META_HEARTBEAT = EventType.of("meta/heartbeat");
}
