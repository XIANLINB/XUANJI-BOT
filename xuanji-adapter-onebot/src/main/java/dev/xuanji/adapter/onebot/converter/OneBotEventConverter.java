package dev.xuanji.adapter.onebot.converter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.message.MessageChain;

import java.time.Instant;
import java.util.UUID;

/**
 * OneBot v11 事件转换器 — 平台报文 → 统一 {@link BotEvent}。
 *
 * <p>覆盖 OneBot v11 的四类 post_type：
 * <ul>
 *   <li>{@code message} — 群聊 / 私聊消息</li>
 *   <li>{@code notice}  — 群成员增减、禁言、撤回、戳一戳等</li>
 *   <li>{@code request} — 好友请求、加群/邀请请求</li>
 *   <li>{@code meta_event} — 心跳、生命周期（不进业务流水线，由接入层过滤）</li>
 * </ul>
 *
 * <p>未能映射到框架标准类型的事件统一落到 {@code onebot/xxx} 命名空间，
 * 插件仍可通过 rawEventType + platformData 精细处理。
 */
public final class OneBotEventConverter {

    private OneBotEventConverter() {}

    /**
     * 转换一条 OneBot 上报事件。
     *
     * @param bot 当前 bot 实例（由接入层按 self_id 构造）
     * @param raw OneBot 原始事件 JSON
     * @return 统一事件；meta_event 等无需业务处理的返回 null
     */
    public static BotEvent convert(Bot bot, JsonNode raw) {
        String postType = raw.path("post_type").asText("");
        if (postType.isEmpty()) {
            return null;
        }

        String rawEventType = rawEventType(raw, postType);
        EventType type = mapEventType(raw, postType);
        XuanjiUser sender = extractUser(raw);
        XuanjiGroup group = extractGroup(raw);
        MessageChain message = "message".equals(postType) || "message_sent".equals(postType)
                ? OneBotMessageConverter.toChain(raw.path("message"))
                : null;

        // OneBot 的 message_id 为整数，被动回复时需原样回传
        String msgId = raw.hasNonNull("message_id") ? raw.get("message_id").asText() : null;

        return new BotEvent(
                buildEventId(raw),
                type,
                bot,
                sender,
                group,
                message != null && !message.elements().isEmpty() ? message : null,
                msgId,
                raw,
                rawEventType,
                "PRODUCTION"   // OneBot 实现无沙箱概念，统一按正式环境处理
        );
    }

    /**
     * 事件 ID —— 用于框架幂等去重。
     *
     * <p>OneBot 不提供全局事件 ID，用 {@code self_id:time:message_id} 组合；
     * 非消息事件退化为 UUID（通知类重复上报概率低）。
     */
    static String buildEventId(JsonNode raw) {
        String selfId = raw.path("self_id").asText("");
        long time = raw.path("time").asLong(0);
        if (raw.hasNonNull("message_id")) {
            return "onebot:" + selfId + ":" + raw.get("message_id").asText();
        }
        String postType = raw.path("post_type").asText("");
        String subType = raw.path("notice_type").asText(raw.path("request_type").asText(""));
        if (time > 0 && !subType.isEmpty()) {
            return "onebot:" + selfId + ":" + postType + ":" + subType + ":" + time
                    + ":" + raw.path("user_id").asText("");
        }
        return "onebot:" + UUID.randomUUID();
    }

    /** 平台原始事件类型字符串，形如 {@code message.group.normal} / {@code notice.group_increase} */
    static String rawEventType(JsonNode raw, String postType) {
        String detail = switch (postType) {
            case "message", "message_sent" -> raw.path("message_type").asText("");
            case "notice"     -> raw.path("notice_type").asText("");
            case "request"    -> raw.path("request_type").asText("");
            case "meta_event" -> raw.path("meta_event_type").asText("");
            default           -> "";
        };
        String sub = raw.path("sub_type").asText("");
        StringBuilder sb = new StringBuilder(postType);
        if (!detail.isEmpty()) sb.append('.').append(detail);
        if (!sub.isEmpty())    sb.append('.').append(sub);
        return sb.toString();
    }

    /**
     * OneBot 事件 → 框架标准 EventType（穷尽 OneBot v11 全部 post_type/sub_type）。
     *
     * <p>标准清单见 {@link OneBotEventTypes}；未命中的事件退化为
     * {@code category/subType} 命名空间（如 {@code notice/some_new_notice}），
     * 原始报文始终保留在 platformData，插件可精细处理，绝不丢弃。
     */
    static EventType mapEventType(JsonNode raw, String postType) {
        return switch (postType) {
            case "message", "message_sent" -> switch (raw.path("message_type").asText("")) {
                case "group"   -> OneBotEventTypes.MESSAGE_GROUP;
                case "private" -> OneBotEventTypes.MESSAGE_PRIVATE;
                case "guild"   -> OneBotEventTypes.MESSAGE_GUILD;
                default        -> OneBotEventTypes.MESSAGE_PRIVATE;
            };

            case "notice" -> switch (raw.path("notice_type").asText("")) {
                case "group_upload"   -> OneBotEventTypes.NOTICE_GROUP_UPLOAD;
                case "group_admin"    -> OneBotEventTypes.NOTICE_GROUP_ADMIN;
                case "group_decrease" -> OneBotEventTypes.NOTICE_GROUP_DECREASE;
                case "group_increase" -> OneBotEventTypes.NOTICE_GROUP_INCREASE;
                case "group_ban"      -> OneBotEventTypes.NOTICE_GROUP_BAN;
                case "group_card"     -> OneBotEventTypes.NOTICE_GROUP_CARD;
                case "friend_add"     -> OneBotEventTypes.NOTICE_FRIEND_ADD;
                case "group_recall"   -> OneBotEventTypes.NOTICE_GROUP_RECALL;
                case "friend_recall"  -> OneBotEventTypes.NOTICE_FRIEND_RECALL;
                case "offline_file"   -> OneBotEventTypes.NOTICE_OFFLINE_FILE;
                case "notify" -> switch (raw.path("sub_type").asText("")) {
                    case "poke"       -> OneBotEventTypes.NOTICE_NOTIFY_POKE;
                    case "lucky_king" -> OneBotEventTypes.NOTICE_NOTIFY_LUCKY_KING;
                    case "honor"      -> OneBotEventTypes.NOTICE_NOTIFY_HONOR;
                    default           -> EventType.of("notice/notify_" + raw.path("sub_type").asText("notify"));
                };
                default -> EventType.of("notice/" + raw.path("notice_type").asText("unknown"));
            };

            case "request" -> switch (raw.path("request_type").asText("")) {
                case "friend" -> OneBotEventTypes.REQUEST_FRIEND_ADD;
                case "group"  -> switch (raw.path("sub_type").asText("")) {
                    case "add"    -> OneBotEventTypes.REQUEST_GROUP_ADD;
                    case "invite" -> OneBotEventTypes.REQUEST_GROUP_INVITE;
                    default       -> OneBotEventTypes.REQUEST_GROUP_INVITE;
                };
                default -> EventType.of("request/" + raw.path("request_type").asText("unknown"));
            };

            case "meta_event" -> switch (raw.path("meta_event_type").asText("")) {
                case "lifecycle" -> OneBotEventTypes.META_LIFECYCLE;
                case "heartbeat" -> OneBotEventTypes.META_HEARTBEAT;
                default          -> EventType.of("meta/" + raw.path("meta_event_type").asText("unknown"));
            };

            default -> EventType.of("onebot/" + postType);
        };
    }

    /**
     * 提取发送者档案（事件主体 user_id，而非 operator_id）。
     *
     * <p>群聊优先取群名片（card），为空回退昵称；匿名消息取 anonymous.name；
     * OneBot 的 role 字段（owner/admin/member）落到 platformData，由权限层 L1 使用。
     * 操作者（operator_id，如禁言/踢人/撤回的执行者）保存在 platformData 原报文中，
     * 插件可通过 {@code event.platformData().get("operator_id")} 读取。
     */
    static XuanjiUser extractUser(JsonNode raw) {
        String userId = raw.path("user_id").asText("");
        JsonNode s = raw.path("sender");

        String nickname = "";
        if (s.isObject()) {
            String card = s.path("card").asText("");
            nickname = !card.isBlank() ? card : s.path("nickname").asText("");
            if (userId.isEmpty()) {
                userId = s.path("user_id").asText("");
            }
        }
        // 匿名群消息（go-cqhttp 在 sender 为空时给出 anonymous 对象）
        if (nickname.isBlank() && raw.hasNonNull("anonymous")
                && raw.get("anonymous").hasNonNull("name")) {
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

    /** 提取群档案；私聊事件返回 null */
    static XuanjiGroup extractGroup(JsonNode raw) {
        String groupId = raw.hasNonNull("group_id") ? raw.get("group_id").asText() : null;
        if (groupId == null || groupId.isBlank() || "0".equals(groupId)) {
            return null;
        }
        return new XuanjiGroup(groupId, "", groupId, "", 0, Instant.now());
    }

    /** 是否为无需进业务流水线的元事件（心跳 / 生命周期） */
    public static boolean isMetaEvent(JsonNode raw) {
        return "meta_event".equals(raw.path("post_type").asText(""));
    }

    /** 是否为 API 调用的响应报文（含 echo + status/retcode，不是事件上报） */
    public static boolean isApiResponse(JsonNode raw) {
        return raw.has("echo") && (raw.has("retcode") || raw.has("status"));
    }
}
