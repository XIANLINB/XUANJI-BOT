package dev.xuanji.adapter.onebot.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.sender.MessageSender;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.api.sender.Target;
import lombok.extern.slf4j.Slf4j;

/**
 * OneBot 消息发送器 — 统一 {@link MessageSender} 的 OneBot v11 实现。
 *
 * <p>与 QQ 官方 API 的关键差异：OneBot 没有"被动回复必须带 msg_id"的限制，
 * 但为了对齐引用语义，{@link #reply} 会自动在消息头插入 {@code reply} 段。
 *
 * <p>命名为 {@code oneBotMessageSender}，与 QQ 适配器的 Bean 并存不冲突；
 * 需要按平台路由时由调用方按 bot.platform() 选择。
 */
@Slf4j
public class OneBotMessageSenderImpl implements MessageSender {

    private final OneBotApiService api;

    public OneBotMessageSenderImpl(OneBotApiService api) {
        this.api = api;
    }

    @Override
    public SendReceipt reply(MessageChain chain) {
        BotEvent event = BotContext.current();
        String selfId = event.bot() != null ? event.bot().selfId() : null;

        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        // 自动引用原消息（OneBot 的 reply 段必须放在消息最前）
        if (event.replyToMsgId() != null && !event.replyToMsgId().isBlank() && !startsWithReply(segments)) {
            ObjectNode replySeg = Json.obj();
            replySeg.put("type", "reply");
            ObjectNode d = Json.obj();
            d.put("id", event.replyToMsgId());
            replySeg.set("data", d);
            segments.insert(0, replySeg);
        }

        if (event.isGroupEvent() && event.group() != null) {
            return sendGroup(selfId, event.group().groupId(), segments);
        }
        return sendPrivate(selfId, event.sender().platformUserId(), segments);
    }

    @Override
    public SendReceipt send(Target target, MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        return switch (target) {
            case Target.Private p -> sendPrivate(null, p.openid(), segments);
            case Target.Group g   -> sendGroup(null, g.groupOpenid(), segments);
            // OneBot v11 无频道概念（guild 为扩展），退化为群消息尝试
            case Target.Guild g   -> sendGroup(null, g.channelId(), segments);
        };
    }

    // ==================== 内部发送实现 ====================

    /** 发送群消息 —— action: send_group_msg */
    public SendReceipt sendGroup(String selfId, String groupId, ArrayNode segments) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        params.set("message", segments);
        return doCall(selfId, "send_group_msg", params, "group=" + groupId);
    }

    /** 发送私聊消息 —— action: send_private_msg */
    public SendReceipt sendPrivate(String selfId, String userId, ArrayNode segments) {
        ObjectNode params = Json.obj();
        putId(params, "user_id", userId);
        params.set("message", segments);
        return doCall(selfId, "send_private_msg", params, "user=" + userId);
    }

    /** 撤回消息 —— action: delete_msg */
    public SendReceipt recall(String selfId, String messageId) {
        ObjectNode params = Json.obj();
        putId(params, "message_id", messageId);
        return doCall(selfId, "delete_msg", params, "recall=" + messageId);
    }

    // ==================== 请求处理（好友 / 加群） ====================

    /** 处理好友添加请求 —— action: set_friend_add_request */
    public SendReceipt handleFriendRequest(String selfId, String flag, boolean approve, String remark) {
        ObjectNode params = Json.obj();
        params.put("flag", flag);
        params.put("approve", approve);
        if (remark != null) params.put("remark", remark);
        return doCall(selfId, "set_friend_add_request", params, "flag=" + flag);
    }

    /** 处理加群请求 / 邀请 —— action: set_group_add_request */
    public SendReceipt handleGroupRequest(String selfId, String flag, String subType,
                                           boolean approve, String reason) {
        ObjectNode params = Json.obj();
        params.put("flag", flag);
        params.put("sub_type", "add".equals(subType) ? "add" : "invite");
        params.put("approve", approve);
        if (reason != null) params.put("reason", reason);
        return doCall(selfId, "set_group_add_request", params, "flag=" + flag);
    }

    // ==================== 群管动作 ====================

    /** 群踢人 —— action: set_group_kick */
    public SendReceipt kickGroupMember(String selfId, String groupId, String userId,
                                       boolean rejectAddRequest) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        putId(params, "user_id", userId);
        params.put("reject_add_request", rejectAddRequest);
        return doCall(selfId, "set_group_kick", params, "group=" + groupId + ",user=" + userId);
    }

    /** 群单人禁言 —— action: set_group_ban（duration=0 表示解禁） */
    public SendReceipt banGroupMember(String selfId, String groupId, String userId, long durationSec) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        putId(params, "user_id", userId);
        params.put("duration", durationSec);
        return doCall(selfId, "set_group_ban", params, "group=" + groupId + ",user=" + userId);
    }

    /** 群组全员禁言 —— action: set_group_whole_ban */
    public SendReceipt setGroupWholeBan(String selfId, String groupId, boolean enable) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        params.put("enable", enable);
        return doCall(selfId, "set_group_whole_ban", params, "group=" + groupId);
    }

    /** 设置群管理员 —— action: set_group_admin */
    public SendReceipt setGroupAdmin(String selfId, String groupId, String userId, boolean enable) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        putId(params, "user_id", userId);
        params.put("enable", enable);
        return doCall(selfId, "set_group_admin", params, "group=" + groupId + ",user=" + userId);
    }

    /** 设置群名片（群备注）—— action: set_group_card */
    public SendReceipt setGroupCard(String selfId, String groupId, String userId, String card) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        putId(params, "user_id", userId);
        params.put("card", card == null ? "" : card);
        return doCall(selfId, "set_group_card", params, "group=" + groupId + ",user=" + userId);
    }

    /** 设置群名 —— action: set_group_name */
    public SendReceipt setGroupName(String selfId, String groupId, String name) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        params.put("group_name", name == null ? "" : name);
        return doCall(selfId, "set_group_name", params, "group=" + groupId);
    }

    /** 退出群组 —— action: set_group_leave（dismiss=true 仅群主可解散） */
    public SendReceipt leaveGroup(String selfId, String groupId, boolean dismiss) {
        ObjectNode params = Json.obj();
        putId(params, "group_id", groupId);
        params.put("is_dismiss", dismiss);
        return doCall(selfId, "set_group_leave", params, "group=" + groupId);
    }

    /** 删除好友 —— action: delete_friend（go-cqhttp 扩展） */
    public SendReceipt deleteFriend(String selfId, String userId) {
        ObjectNode params = Json.obj();
        putId(params, "user_id", userId);
        return doCall(selfId, "delete_friend", params, "user=" + userId);
    }

    /** 发送好友赞 —— action: send_like */
    public SendReceipt sendLike(String selfId, String userId, int times) {
        ObjectNode params = Json.obj();
        putId(params, "user_id", userId);
        params.put("times", Math.max(1, times));
        return doCall(selfId, "send_like", params, "user=" + userId);
    }

    private SendReceipt doCall(String selfId, String action, ObjectNode params, String desc) {
        long start = System.currentTimeMillis();
        try {
            JsonNode data = api.call(selfId, action, params);
            long elapsed = System.currentTimeMillis() - start;
            String msgId = data != null && data.hasNonNull("message_id")
                    ? data.get("message_id").asText() : "";
            log.debug("[OneBot消息] {} 成功: {}, msgId={}, {}ms", action, desc, msgId, elapsed);
            return SendReceipt.ok(msgId, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[OneBot消息] {} 失败: {}, error={}", action, desc, e.getMessage());
            return SendReceipt.fail(e.getMessage(), elapsed);
        }
    }

    private static boolean startsWithReply(ArrayNode segments) {
        return !segments.isEmpty() && "reply".equals(segments.get(0).path("type").asText(""));
    }

    /**
     * OneBot 的 group_id / user_id / message_id 标准类型为 int64。
     * 数字串按 long 上送，非数字（部分实现用字符串 ID）原样上送字符串。
     */
    static void putId(ObjectNode node, String field, String id) {
        if (id == null) {
            node.putNull(field);
            return;
        }
        try {
            node.put(field, Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            node.put(field, id);
        }
    }
}
