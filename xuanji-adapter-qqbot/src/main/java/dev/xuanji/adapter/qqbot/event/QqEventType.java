package dev.xuanji.adapter.qqbot.event;

/**
 * QQ 机器人平台事件类型枚举。
 *
 * <p>覆盖 QQ 开放平台推送的全部事件类型。{@code qqbot_event.event_type} 列以 VARCHAR 存储原始字符串，
 * 本枚举用于类型安全的解析与判断；未知类型统一返回 {@link #UNKNOWN}。
 *
 * @see <a href="https://bot.q.qq.com/wiki/develop/api-v2/event/">QQ 事件文档</a>
 */
public enum QqEventType {

    // ===== 群消息 =====
    GROUP_AT_MESSAGE_CREATE,
    GROUP_MESSAGE_CREATE,

    // ===== 群系统事件 =====
    GROUP_ADD_ROBOT,
    GROUP_DEL_ROBOT,
    GROUP_MSG_REJECT,
    GROUP_MSG_RECEIVE,
    GROUP_MEMBER_ADD,
    GROUP_MEMBER_REMOVE,
    GROUP_JOIN_REQUEST,

    // ===== 单聊消息 =====
    C2C_MESSAGE_CREATE,

    // ===== 单聊系统事件 =====
    FRIEND_ADD,
    FRIEND_DEL,
    C2C_MSG_REJECT,
    C2C_MSG_RECEIVE,

    // ===== 互动 / 按钮回调 =====
    INTERACTION_CREATE,

    // ===== 频道（可选）=====
    AT_MESSAGE_CREATE,
    MESSAGE_CREATE,
    DIRECT_MESSAGE_CREATE,
    MESSAGE_DELETE,
    PUBLIC_MESSAGE_DELETE,
    DIRECT_MESSAGE_DELETE,

    // ===== 频道系统事件 =====
    GUILD_CREATE,
    GUILD_UPDATE,
    GUILD_DELETE,
    CHANNEL_CREATE,
    CHANNEL_UPDATE,
    CHANNEL_DELETE,
    GUILD_MEMBER_ADD,
    GUILD_MEMBER_UPDATE,
    GUILD_MEMBER_REMOVE,
    AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER,
    AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT,

    // ===== 消息审核 / 表情表态 =====
    MESSAGE_AUDIT_PASS,
    MESSAGE_AUDIT_REJECT,
    MESSAGE_REACTION_ADD,
    MESSAGE_REACTION_REMOVE,

    // ===== 未知 / 未枚举 =====
    UNKNOWN;

    /**
     * 从平台原始事件类型字符串解析枚举。
     *
     * @param type 平台推送的 t 字段 / eventType 字符串
     * @return 匹配的枚举值；为空或无法识别时返回 {@link #UNKNOWN}
     */
    public static QqEventType of(String type) {
        if (type == null || type.isEmpty()) return UNKNOWN;
        // QQ 官方入群申请事件 t 字段为 GROUP_ADD_REQUEST，统一映射到 GROUP_JOIN_REQUEST
        if ("GROUP_ADD_REQUEST".equalsIgnoreCase(type.trim())) return GROUP_JOIN_REQUEST;
        try {
            return QqEventType.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** 是否为消息类事件（需要落 qqbot_message）。 */
    public boolean isMessage() {
        return this == GROUP_AT_MESSAGE_CREATE
                || this == GROUP_MESSAGE_CREATE
                || this == C2C_MESSAGE_CREATE
                || this == AT_MESSAGE_CREATE
                || this == MESSAGE_CREATE
                || this == DIRECT_MESSAGE_CREATE;
    }

    /** 是否为频道域事件（GUILDS intent 1<<0 覆盖）。 */
    public boolean isGuild() {
        return switch (this) {
            case GUILD_CREATE, GUILD_UPDATE, GUILD_DELETE,
                 CHANNEL_CREATE, CHANNEL_UPDATE, CHANNEL_DELETE,
                 GUILD_MEMBER_ADD, GUILD_MEMBER_UPDATE, GUILD_MEMBER_REMOVE,
                 AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER, AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT -> true;
            default -> false;
        };
    }
}
