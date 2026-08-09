package dev.xuanji.adapter.onebot.sender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.sender.MessageSender;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.api.sender.Target;
import java.lang.runtime.SwitchBootstraps;
import java.util.Objects;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotMessageSenderImpl
implements MessageSender {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotMessageSenderImpl.class);
    private final OneBotApiService api;

    public OneBotMessageSenderImpl(OneBotApiService api) {
        this.api = api;
    }

    public SendReceipt reply(MessageChain chain) {
        BotEvent event = BotContext.current();
        String selfId = event.bot() != null ? event.bot().selfId() : null;
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        if (event.replyToMsgId() != null && !event.replyToMsgId().isBlank() && !OneBotMessageSenderImpl.startsWithReply(segments)) {
            ObjectNode replySeg = Json.obj();
            replySeg.put("type", "reply");
            ObjectNode d = Json.obj();
            d.put("id", event.replyToMsgId());
            replySeg.set("data", (JsonNode)d);
            segments.insert(0, (JsonNode)replySeg);
        }
        if (event.isGroupEvent() && event.group() != null) {
            return this.sendGroup(selfId, event.group().groupId(), segments);
        }
        return this.sendPrivate(selfId, event.sender().platformUserId(), segments);
    }

    public SendReceipt send(Target target, MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        if (target == null) {
            return SendReceipt.fail("目标为空", 0L);
        }
        return switch (target) {
            case Target.Private p -> sendPrivate(null, p.openid(), segments);
            case Target.Group g -> sendGroup(null, g.groupOpenid(), segments);
            case Target.Guild g -> sendGroup(null, g.channelId(), segments);
            default -> SendReceipt.fail("不支持的目标类型: " + target.getClass().getSimpleName(), 0L);
        };
    }

    public SendReceipt sendGroup(String selfId, String groupId, ArrayNode segments) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        params.set("message", (JsonNode)segments);
        return this.doCall(selfId, "send_group_msg", params, "group=" + groupId);
    }

    public SendReceipt sendPrivate(String selfId, String userId, ArrayNode segments) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.set("message", (JsonNode)segments);
        return this.doCall(selfId, "send_private_msg", params, "user=" + userId);
    }

    public SendReceipt recall(String selfId, String messageId) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "message_id", messageId);
        return this.doCall(selfId, "delete_msg", params, "recall=" + messageId);
    }

    public SendReceipt handleFriendRequest(String selfId, String flag, boolean approve, String remark) {
        ObjectNode params = Json.obj();
        params.put("flag", flag);
        params.put("approve", approve);
        if (remark != null) {
            params.put("remark", remark);
        }
        return this.doCall(selfId, "set_friend_add_request", params, "flag=" + flag);
    }

    public SendReceipt handleGroupRequest(String selfId, String flag, String subType, boolean approve, String reason) {
        ObjectNode params = Json.obj();
        params.put("flag", flag);
        params.put("sub_type", "add".equals(subType) ? "add" : "invite");
        params.put("approve", approve);
        if (reason != null) {
            params.put("reason", reason);
        }
        return this.doCall(selfId, "set_group_add_request", params, "flag=" + flag);
    }

    public SendReceipt kickGroupMember(String selfId, String groupId, String userId, boolean rejectAddRequest) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.put("reject_add_request", rejectAddRequest);
        return this.doCall(selfId, "set_group_kick", params, "group=" + groupId + ",user=" + userId);
    }

    public SendReceipt banGroupMember(String selfId, String groupId, String userId, long durationSec) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.put("duration", durationSec);
        return this.doCall(selfId, "set_group_ban", params, "group=" + groupId + ",user=" + userId);
    }

    public SendReceipt setGroupWholeBan(String selfId, String groupId, boolean enable) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        params.put("enable", enable);
        return this.doCall(selfId, "set_group_whole_ban", params, "group=" + groupId);
    }

    public SendReceipt setGroupAdmin(String selfId, String groupId, String userId, boolean enable) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.put("enable", enable);
        return this.doCall(selfId, "set_group_admin", params, "group=" + groupId + ",user=" + userId);
    }

    public SendReceipt setGroupCard(String selfId, String groupId, String userId, String card) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.put("card", card == null ? "" : card);
        return this.doCall(selfId, "set_group_card", params, "group=" + groupId + ",user=" + userId);
    }

    public SendReceipt setGroupName(String selfId, String groupId, String name) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        params.put("group_name", name == null ? "" : name);
        return this.doCall(selfId, "set_group_name", params, "group=" + groupId);
    }

    public SendReceipt leaveGroup(String selfId, String groupId, boolean dismiss) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "group_id", groupId);
        params.put("is_dismiss", dismiss);
        return this.doCall(selfId, "set_group_leave", params, "group=" + groupId);
    }

    public SendReceipt deleteFriend(String selfId, String userId) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        return this.doCall(selfId, "delete_friend", params, "user=" + userId);
    }

    public SendReceipt sendLike(String selfId, String userId, int times) {
        ObjectNode params = Json.obj();
        OneBotMessageSenderImpl.putId(params, "user_id", userId);
        params.put("times", Math.max(1, times));
        return this.doCall(selfId, "send_like", params, "user=" + userId);
    }

    private SendReceipt doCall(String selfId, String action, ObjectNode params, String desc) {
        long start = System.currentTimeMillis();
        try {
            JsonNode data = this.api.call(selfId, action, params);
            long elapsed = System.currentTimeMillis() - start;
            String msgId = data != null && data.hasNonNull("message_id") ? data.get("message_id").asText() : "";
            log.debug("[OneBot\u6d88\u606f] {} \u6210\u529f: {}, msgId={}, {}ms", new Object[]{action, desc, msgId, elapsed});
            return SendReceipt.ok((String)msgId, (long)elapsed);
        }
        catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[OneBot\u6d88\u606f] {} \u5931\u8d25: {}, error={}", new Object[]{action, desc, e.getMessage()});
            return SendReceipt.fail((String)e.getMessage(), (long)elapsed);
        }
    }

    private static boolean startsWithReply(ArrayNode segments) {
        return !segments.isEmpty() && "reply".equals(segments.get(0).path("type").asText(""));
    }

    static void putId(ObjectNode node, String field, String id) {
        if (id == null) {
            node.putNull(field);
            return;
        }
        try {
            node.put(field, Long.parseLong(id.trim()));
        }
        catch (NumberFormatException e) {
            node.put(field, id);
        }
    }
}

