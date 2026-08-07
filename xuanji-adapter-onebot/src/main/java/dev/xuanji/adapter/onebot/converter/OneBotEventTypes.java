package dev.xuanji.adapter.onebot.converter;

import dev.xuanji.api.event.EventType;

public final class OneBotEventTypes {
    public static final EventType MESSAGE_PRIVATE = EventType.MESSAGE_PRIVATE;
    public static final EventType MESSAGE_GROUP = EventType.MESSAGE_GROUP;
    public static final EventType MESSAGE_GUILD = EventType.MESSAGE_GUILD;
    public static final EventType NOTICE_GROUP_UPLOAD = EventType.of((String)"notice/group_upload");
    public static final EventType NOTICE_GROUP_ADMIN = EventType.of((String)"notice/group_admin");
    public static final EventType NOTICE_GROUP_DECREASE = EventType.of((String)"notice/group_decrease");
    public static final EventType NOTICE_GROUP_INCREASE = EventType.of((String)"notice/group_increase");
    public static final EventType NOTICE_GROUP_BAN = EventType.of((String)"notice/group_ban");
    public static final EventType NOTICE_GROUP_CARD = EventType.of((String)"notice/group_card");
    public static final EventType NOTICE_FRIEND_ADD = EventType.of((String)"notice/friend_add");
    public static final EventType NOTICE_GROUP_RECALL = EventType.of((String)"notice/group_recall");
    public static final EventType NOTICE_FRIEND_RECALL = EventType.of((String)"notice/friend_recall");
    public static final EventType NOTICE_OFFLINE_FILE = EventType.of((String)"notice/offline_file");
    public static final EventType NOTICE_NOTIFY_POKE = EventType.of((String)"notice/notify_poke");
    public static final EventType NOTICE_NOTIFY_LUCKY_KING = EventType.of((String)"notice/notify_lucky_king");
    public static final EventType NOTICE_NOTIFY_HONOR = EventType.of((String)"notice/notify_honor");
    public static final EventType REQUEST_FRIEND_ADD = EventType.REQUEST_FRIEND_ADD;
    public static final EventType REQUEST_GROUP_ADD = EventType.of((String)"request/group_add");
    public static final EventType REQUEST_GROUP_INVITE = EventType.REQUEST_GROUP_INVITE;
    public static final EventType META_LIFECYCLE = EventType.of((String)"meta/lifecycle");
    public static final EventType META_HEARTBEAT = EventType.of((String)"meta/heartbeat");

    private OneBotEventTypes() {
    }
}

