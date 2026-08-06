/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  dev.xuanji.api.adapter.Bot
 *  dev.xuanji.api.event.BotEvent
 *  dev.xuanji.api.event.EventType
 *  dev.xuanji.api.event.XuanjiGroup
 *  dev.xuanji.api.event.XuanjiUser
 *  dev.xuanji.api.message.MessageChain
 */
package dev.xuanji.adapter.onebot.converter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.adapter.onebot.converter.OneBotEventTypes;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.message.MessageChain;
import java.time.Instant;
import java.util.UUID;

public final class OneBotEventConverter {
    private OneBotEventConverter() {
    }

    public static BotEvent convert(Bot bot, JsonNode raw) {
        String postType = raw.path("post_type").asText("");
        if (postType.isEmpty()) {
            return null;
        }
        String rawEventType = OneBotEventConverter.rawEventType(raw, postType);
        EventType type = OneBotEventConverter.mapEventType(raw, postType);
        XuanjiUser sender = OneBotEventConverter.extractUser(raw);
        XuanjiGroup group = OneBotEventConverter.extractGroup(raw);
        MessageChain message = "message".equals(postType) || "message_sent".equals(postType) ? OneBotMessageConverter.toChain(raw.path("message"), bot.selfId()) : null;
        String msgId = raw.hasNonNull("message_id") ? raw.get("message_id").asText() : null;
        return new BotEvent(OneBotEventConverter.buildEventId(raw), type, bot, sender, group, message != null && !message.elements().isEmpty() ? message : null, msgId, raw, rawEventType, "PRODUCTION");
    }

    static String buildEventId(JsonNode raw) {
        String selfId = raw.path("self_id").asText("");
        long time = raw.path("time").asLong(0L);
        if (raw.hasNonNull("message_id")) {
            return "onebot:" + selfId + ":" + raw.get("message_id").asText();
        }
        String postType = raw.path("post_type").asText("");
        String subType = raw.path("notice_type").asText(raw.path("request_type").asText(""));
        if (time > 0L && !subType.isEmpty()) {
            return "onebot:" + selfId + ":" + postType + ":" + subType + ":" + time + ":" + raw.path("user_id").asText("");
        }
        return "onebot:" + String.valueOf(UUID.randomUUID());
    }

    static String rawEventType(JsonNode raw, String postType) {
        String detail = switch (postType) {
            case "message", "message_sent" -> raw.path("message_type").asText("");
            case "notice" -> raw.path("notice_type").asText("");
            case "request" -> raw.path("request_type").asText("");
            case "meta_event" -> raw.path("meta_event_type").asText("");
            default -> "";
        };
        String sub = raw.path("sub_type").asText("");
        StringBuilder sb = new StringBuilder(postType);
        if (!detail.isEmpty()) {
            sb.append('.').append(detail);
        }
        if (!sub.isEmpty()) {
            sb.append('.').append(sub);
        }
        return sb.toString();
    }

    static EventType mapEventType(JsonNode raw, String postType) {
        return switch (postType) {
            case "message", "message_sent" -> {
                switch (raw.path("message_type").asText("")) {
                    case "group": {
                        yield OneBotEventTypes.MESSAGE_GROUP;
                    }
                    case "private": {
                        yield OneBotEventTypes.MESSAGE_PRIVATE;
                    }
                    case "guild": {
                        yield OneBotEventTypes.MESSAGE_GUILD;
                    }
                }
                yield OneBotEventTypes.MESSAGE_PRIVATE;
            }
            case "notice" -> {
                switch (raw.path("notice_type").asText("")) {
                    case "group_upload": {
                        yield OneBotEventTypes.NOTICE_GROUP_UPLOAD;
                    }
                    case "group_admin": {
                        yield OneBotEventTypes.NOTICE_GROUP_ADMIN;
                    }
                    case "group_decrease": {
                        yield OneBotEventTypes.NOTICE_GROUP_DECREASE;
                    }
                    case "group_increase": {
                        yield OneBotEventTypes.NOTICE_GROUP_INCREASE;
                    }
                    case "group_ban": {
                        yield OneBotEventTypes.NOTICE_GROUP_BAN;
                    }
                    case "group_card": {
                        yield OneBotEventTypes.NOTICE_GROUP_CARD;
                    }
                    case "friend_add": {
                        yield OneBotEventTypes.NOTICE_FRIEND_ADD;
                    }
                    case "group_recall": {
                        yield OneBotEventTypes.NOTICE_GROUP_RECALL;
                    }
                    case "friend_recall": {
                        yield OneBotEventTypes.NOTICE_FRIEND_RECALL;
                    }
                    case "offline_file": {
                        yield OneBotEventTypes.NOTICE_OFFLINE_FILE;
                    }
                    case "notify": {
                        switch (raw.path("sub_type").asText("")) {
                            case "poke": {
                                yield OneBotEventTypes.NOTICE_NOTIFY_POKE;
                            }
                            case "lucky_king": {
                                yield OneBotEventTypes.NOTICE_NOTIFY_LUCKY_KING;
                            }
                            case "honor": {
                                yield OneBotEventTypes.NOTICE_NOTIFY_HONOR;
                            }
                        }
                        yield EventType.of((String)("notice/notify_" + raw.path("sub_type").asText("notify")));
                    }
                }
                yield EventType.of((String)("notice/" + raw.path("notice_type").asText("unknown")));
            }
            case "request" -> {
                switch (raw.path("request_type").asText("")) {
                    case "friend": {
                        yield OneBotEventTypes.REQUEST_FRIEND_ADD;
                    }
                    case "group": {
                        switch (raw.path("sub_type").asText("")) {
                            case "add": {
                                yield OneBotEventTypes.REQUEST_GROUP_ADD;
                            }
                            case "invite": {
                                yield OneBotEventTypes.REQUEST_GROUP_INVITE;
                            }
                        }
                        yield OneBotEventTypes.REQUEST_GROUP_INVITE;
                    }
                }
                yield EventType.of((String)("request/" + raw.path("request_type").asText("unknown")));
            }
            case "meta_event" -> {
                switch (raw.path("meta_event_type").asText("")) {
                    case "lifecycle": {
                        yield OneBotEventTypes.META_LIFECYCLE;
                    }
                    case "heartbeat": {
                        yield OneBotEventTypes.META_HEARTBEAT;
                    }
                }
                yield EventType.of((String)("meta/" + raw.path("meta_event_type").asText("unknown")));
            }
            default -> EventType.of((String)("onebot/" + postType));
        };
    }

    static XuanjiUser extractUser(JsonNode raw) {
        String userId = raw.path("user_id").asText("");
        JsonNode s = raw.path("sender");
        String nickname = "";
        if (s.isObject()) {
            String card = s.path("card").asText("");
            String string = nickname = !card.isBlank() ? card : s.path("nickname").asText("");
            if (userId.isEmpty()) {
                userId = s.path("user_id").asText("");
            }
        }
        if (nickname.isBlank() && raw.hasNonNull("anonymous") && raw.get("anonymous").hasNonNull("name")) {
            nickname = raw.get("anonymous").get("name").asText("");
            if (userId.isEmpty()) {
                userId = raw.get("anonymous").path("id").asText("");
            }
        }
        if (nickname.isBlank()) {
            nickname = userId;
        }
        return new XuanjiUser(userId, userId, nickname, null, 0, Instant.now());
    }

    static XuanjiGroup extractGroup(JsonNode raw) {
        String groupId;
        String string = groupId = raw.hasNonNull("group_id") ? raw.get("group_id").asText() : null;
        if (groupId == null || groupId.isBlank() || "0".equals(groupId)) {
            return null;
        }
        return new XuanjiGroup(groupId, "", groupId, "", 0, Instant.now());
    }

    public static boolean isMetaEvent(JsonNode raw) {
        return "meta_event".equals(raw.path("post_type").asText(""));
    }

    public static boolean isApiResponse(JsonNode raw) {
        return raw.has("echo") && (raw.has("retcode") || raw.has("status"));
    }
}

