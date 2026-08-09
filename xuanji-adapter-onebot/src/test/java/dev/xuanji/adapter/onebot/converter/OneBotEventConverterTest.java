package dev.xuanji.adapter.onebot.converter;

import tools.jackson.databind.JsonNode;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OneBot v11 事件转换器单测 —— 全部用假报文，不连任何真实 OneBot 实现。
 *
 * <p>覆盖 OneBot v11 标准 + Napcat/go-cqhttp 扩展的全部 post_type/sub_type：
 * message（群/私聊/频道）、notice（群文件上传/管理员/退群/入群/禁言/名片/好友添加/
 * 撤回/离线文件/notify 戳一戳·运气王·荣誉）、request（好友/加群申请/邀请）、
 * meta_event（生命周期/心跳）。
 */
class OneBotEventConverterTest {

    private static final Bot BOT =
            new Bot("onebot:10001", "onebot", "10001", Bot.Status.ONLINE, Set.of());

    private static BotEvent conv(String raw) {
        return OneBotEventConverter.convert(BOT, Json.parse(raw));
    }

    // ==================== message ====================

    @Test
    @DisplayName("群消息：数组格式 message 段应正确转为 MessageChain")
    void convertGroupMessage() {
        BotEvent e = conv("""
                {"time":1700000000,"self_id":10001,"post_type":"message","message_type":"group",
                 "sub_type":"normal","message_id":888,"group_id":20002,"user_id":30003,
                 "message":[{"type":"at","data":{"qq":"10001"}},{"type":"text","data":{"text":" 你好世界"}}],
                 "raw_message":"[CQ:at,qq=10001] 你好世界",
                 "sender":{"user_id":30003,"nickname":"小明","card":"群里的小明","role":"member"}}
                """);

        assertNotNull(e);
        assertEquals("message/group", e.type().fullName());
        assertEquals("message.group.normal", e.rawEventType());
        assertEquals("888", e.replyToMsgId());
        assertEquals("onebot:10001:888", e.eventId(), "事件ID应可用于幂等去重");
        assertNotNull(e.group());
        assertEquals("20002", e.group().groupId());
        assertEquals("30003", e.sender().platformUserId());
        assertEquals("群里的小明", e.sender().nickname(), "群名片优先于昵称");
        assertEquals(2, e.message().elements().size());
        assertInstanceOf(MessageElement.At.class, e.message().elements().get(0));
        assertEquals(" 你好世界", e.message().plainText());
        assertTrue(e.isGroupEvent());
        assertTrue(e.isMessageEvent());
    }

    @Test
    @DisplayName("私聊消息：group 应为 null，类型为 message/private")
    void convertPrivateMessage() {
        BotEvent e = conv("""
                {"time":1700000001,"self_id":10001,"post_type":"message","message_type":"private",
                 "sub_type":"friend","message_id":999,"user_id":30003,
                 "message":[{"type":"text","data":{"text":"在吗"}}],
                 "sender":{"user_id":30003,"nickname":"小明"}}
                """);
        assertEquals("message/private", e.type().fullName());
        assertNull(e.group());
        assertFalse(e.isGroupEvent());
        assertEquals("在吗", e.message().plainText());
        assertEquals("小明", e.sender().nickname(), "无群名片时回退昵称");
    }

    @Test
    @DisplayName("字符串格式（CQ 码）message 也应能解析")
    void convertCqStringMessage() {
        BotEvent e = conv("""
                {"time":1700000002,"self_id":10001,"post_type":"message","message_type":"group",
                 "message_id":1000,"group_id":20002,"user_id":30003,
                 "message":"早上好[CQ:at,qq=30003]天气不错",
                 "sender":{"user_id":30003,"nickname":"小红"}}
                """);
        assertNotNull(e.message());
        assertEquals(3, e.message().elements().size());
        assertEquals("早上好天气不错", e.message().plainText());
        MessageElement.At at = (MessageElement.At) e.message().elements().get(1);
        assertEquals("30003", at.userId());
    }

    @Test
    @DisplayName("匿名群消息应提取 anonymous.name 作为昵称")
    void convertAnonymousGroupMessage() {
        BotEvent e = conv("""
                {"time":1700000003,"self_id":10001,"post_type":"message","message_type":"group",
                 "message_id":1001,"group_id":20002,"user_id":80000001,"anonymous":{"id":80000001,"name":"匿名用户A"},
                 "message":[{"type":"text","data":{"text":"匿名说句话"}}],"sender":{}}
                """);
        assertEquals("匿名用户A", e.sender().nickname());
        assertEquals("80000001", e.sender().platformUserId());
    }

    // ==================== notice ====================

    @Test
    @DisplayName("notice：群文件上传")
    void noticeGroupUpload() {
        BotEvent e = conv("""
                {"time":1700000010,"self_id":10001,"post_type":"notice","notice_type":"group_upload",
                 "group_id":20002,"user_id":30003,
                 "file":{"id":"abc","name":"a.zip","size":1024,"busid":1}}
                """);
        assertEquals("notice/group_upload", e.type().fullName());
        assertEquals("notice.group_upload", e.rawEventType());
        assertNull(e.message());
        assertEquals("20002", e.group().groupId());
        assertEquals("a.zip", e.platformData().path("file").path("name").asText());
    }

    @Test
    @DisplayName("notice：管理员变更（set/unset）")
    void noticeGroupAdmin() {
        BotEvent e = conv("""
                {"time":1700000011,"self_id":10001,"post_type":"notice","notice_type":"group_admin",
                 "sub_type":"set","group_id":20002,"user_id":30003}
                """);
        assertEquals("notice/group_admin", e.type().fullName());
        assertEquals("notice.group_admin.set", e.rawEventType());
        assertEquals("30003", e.sender().platformUserId());
    }

    @Test
    @DisplayName("notice：成员减少（leave/kick/kick_me）")
    void noticeGroupDecrease() {
        BotEvent leave = conv("""
                {"time":1700000012,"self_id":10001,"post_type":"notice","notice_type":"group_decrease",
                 "sub_type":"leave","group_id":20002,"operator_id":50005,"user_id":50005}
                """);
        assertEquals("notice/group_decrease", leave.type().fullName());
        assertEquals("notice.group_decrease.leave", leave.rawEventType());

        BotEvent kick = conv("""
                {"time":1700000013,"self_id":10001,"post_type":"notice","notice_type":"group_decrease",
                 "sub_type":"kick","group_id":20002,"operator_id":40004,"user_id":50005}
                """);
        assertEquals("notice/group_decrease", kick.type().fullName());
        // operator_id 与原 user_id 分离，保留在 platformData
        assertEquals("50005", kick.sender().platformUserId());
        assertEquals("40004", kick.platformData().path("operator_id").asText());
    }

    @Test
    @DisplayName("notice：成员增加（add/invite/approve）")
    void noticeGroupIncrease() {
        BotEvent add = conv("""
                {"time":1700000014,"self_id":10001,"post_type":"notice","notice_type":"group_increase",
                 "sub_type":"add","group_id":20002,"operator_id":40004,"user_id":50005}
                """);
        assertEquals("notice/group_increase", add.type().fullName());
        assertEquals("notice.group_increase.add", add.rawEventType());
        assertEquals("50005", add.sender().platformUserId());
        assertEquals("40004", add.platformData().path("operator_id").asText());

        BotEvent invite = conv("""
                {"time":1700000015,"self_id":10001,"post_type":"notice","notice_type":"group_increase",
                 "sub_type":"invite","group_id":20002,"operator_id":40004,"user_id":50005}
                """);
        assertEquals("notice/group_increase", invite.type().fullName());
    }

    @Test
    @DisplayName("notice：群禁言（ban/lift_ban，含 duration）")
    void noticeGroupBan() {
        BotEvent ban = conv("""
                {"time":1700000016,"self_id":10001,"post_type":"notice","notice_type":"group_ban",
                 "sub_type":"ban","group_id":20002,"operator_id":40004,"user_id":50005,"duration":600}
                """);
        assertEquals("notice/group_ban", ban.type().fullName());
        assertEquals("notice.group_ban.ban", ban.rawEventType());
        assertEquals(600, ban.platformData().path("duration").asInt());

        BotEvent lift = conv("""
                {"time":1700000017,"self_id":10001,"post_type":"notice","notice_type":"group_ban",
                 "sub_type":"lift_ban","group_id":20002,"user_id":50005}
                """);
        assertEquals("notice/group_ban", lift.type().fullName());
    }

    @Test
    @DisplayName("notice：群名片变更")
    void noticeGroupCard() {
        BotEvent e = conv("""
                {"time":1700000018,"self_id":10001,"post_type":"notice","notice_type":"group_card",
                 "group_id":20002,"user_id":30003,"card_new":"新名片"}
                """);
        assertEquals("notice/group_card", e.type().fullName());
        assertEquals("新名片", e.platformData().path("card_new").asText());
    }

    @Test
    @DisplayName("notice：好友添加 / 群消息撤回 / 好友消息撤回 / 离线文件")
    void noticeMisc() {
        assertEquals("notice/friend_add", conv("""
                {"time":1700000019,"self_id":10001,"post_type":"notice","notice_type":"friend_add","user_id":30003}
                """).type().fullName());

        BotEvent recall = conv("""
                {"time":1700000020,"self_id":10001,"post_type":"notice","notice_type":"group_recall",
                 "group_id":20002,"operator_id":40004,"user_id":30003,"message_id":777}
                """);
        assertEquals("notice/group_recall", recall.type().fullName());
        assertEquals("777", recall.platformData().path("message_id").asText());

        assertEquals("notice/friend_recall", conv("""
                {"time":1700000021,"self_id":10001,"post_type":"notice","notice_type":"friend_recall",
                 "user_id":30003,"message_id":778}
                """).type().fullName());

        BotEvent offline = conv("""
                {"time":1700000022,"self_id":10001,"post_type":"notice","notice_type":"offline_file",
                 "user_id":30003,"file":{"name":"x.zip","size":100}}
                """);
        assertEquals("notice/offline_file", offline.type().fullName());
    }

    @Test
    @DisplayName("notice.notify：戳一戳 / 运气王 / 群荣誉")
    void noticeNotify() {
        BotEvent poke = conv("""
                {"time":1700000023,"self_id":10001,"post_type":"notice","notice_type":"notify","sub_type":"poke",
                 "group_id":20002,"user_id":30003,"target_id":10001,"action":"戳了戳","suffix":"脸"}
                """);
        assertEquals("notice/notify_poke", poke.type().fullName());
        assertEquals("10001", poke.platformData().path("target_id").asText());

        BotEvent king = conv("""
                {"time":1700000024,"self_id":10001,"post_type":"notice","notice_type":"notify","sub_type":"lucky_king",
                 "group_id":20002,"user_id":30003,"target_id":50005}
                """);
        assertEquals("notice/notify_lucky_king", king.type().fullName());

        BotEvent honor = conv("""
                {"time":1700000025,"self_id":10001,"post_type":"notice","notice_type":"notify","sub_type":"honor",
                 "group_id":20002,"user_id":30003,"honor_type":"talkative"}
                """);
        assertEquals("notice/notify_honor", honor.type().fullName());
        assertEquals("talkative", honor.platformData().path("honor_type").asText());
    }

    // ==================== request ====================

    @Test
    @DisplayName("request：好友请求 / 加群申请(add) / 加群邀请(invite)")
    void convertRequestEvents() {
        BotEvent friend = conv("""
                {"time":1700000026,"self_id":10001,"post_type":"request","request_type":"friend",
                 "user_id":50005,"comment":"你好","flag":"abc"}
                """);
        assertEquals("request/friend_add", friend.type().fullName());
        assertEquals("abc", friend.platformData().path("flag").asText());
        assertEquals("你好", friend.platformData().path("comment").asText());

        BotEvent add = conv("""
                {"time":1700000027,"self_id":10001,"post_type":"request","request_type":"group","sub_type":"add",
                 "group_id":20002,"user_id":50005,"comment":"求拉","flag":"def"}
                """);
        assertEquals("request/group_add", add.type().fullName());
        assertEquals("request.group.add", add.rawEventType());

        BotEvent invite = conv("""
                {"time":1700000028,"self_id":10001,"post_type":"request","request_type":"group","sub_type":"invite",
                 "group_id":20002,"user_id":50005,"flag":"ghi"}
                """);
        assertEquals("request/group_invite", invite.type().fullName());
        assertEquals("request.group.invite", invite.rawEventType());
    }

    // ==================== meta_event ====================

    @Test
    @DisplayName("元事件应被识别，不进入业务流水线")
    void detectMetaEvent() {
        JsonNode heartbeat = Json.parse("""
                {"time":1700000029,"self_id":10001,"post_type":"meta_event",
                 "meta_event_type":"heartbeat","status":{"online":true,"good":true},"interval":5000}
                """);
        assertTrue(OneBotEventConverter.isMetaEvent(heartbeat));

        JsonNode lifecycle = Json.parse("""
                {"time":1700000030,"self_id":10001,"post_type":"meta_event",
                 "meta_event_type":"lifecycle","sub_type":"connect"}
                """);
        assertTrue(OneBotEventConverter.isMetaEvent(lifecycle));

        JsonNode msg = Json.parse("""
                {"post_type":"message","message_type":"private","self_id":10001}
                """);
        assertFalse(OneBotEventConverter.isMetaEvent(msg));

        // convert 对 meta_event 也会给出类型（实际由分发器在调用前用 isMetaEvent 拦截，不进业务流水线）
        BotEvent meta = OneBotEventConverter.convert(BOT, heartbeat);
        assertEquals("meta/heartbeat", meta.type().fullName());
    }

    @Test
    @DisplayName("API 响应报文应与事件上报区分开")
    void detectApiResponse() {
        JsonNode resp = Json.parse("""
                {"status":"ok","retcode":0,"data":{"message_id":123},"echo":"uuid-1"}
                """);
        assertTrue(OneBotEventConverter.isApiResponse(resp));

        JsonNode event = Json.parse("""
                {"post_type":"message","self_id":10001,"message_id":123}
                """);
        assertFalse(OneBotEventConverter.isApiResponse(event));
    }

    // ==================== 兜底 ====================

    @Test
    @DisplayName("未知通知类型应落到命名空间而非丢弃，原始报文透传")
    void unknownNoticeFallsBackToNamespace() {
        BotEvent e = conv("""
                {"time":1700000031,"self_id":10001,"post_type":"notice",
                 "notice_type":"some_new_notice","group_id":20002,"user_id":50005}
                """);
        assertEquals("notice/some_new_notice", e.type().fullName());
        assertNotNull(e.platformData(), "原始报文必须透传，插件可精细处理");
    }

    @Test
    @DisplayName("EventType 常量与其 fullName 一致（校验 OneBotEventTypes 定义）")
    void eventTypeConstantsConsistency() {
        assertEquals("message/group", OneBotEventTypes.MESSAGE_GROUP.fullName());
        assertEquals("notice/group_upload", OneBotEventTypes.NOTICE_GROUP_UPLOAD.fullName());
        assertEquals("notice/group_ban", OneBotEventTypes.NOTICE_GROUP_BAN.fullName());
        assertEquals("notice/notify_poke", OneBotEventTypes.NOTICE_NOTIFY_POKE.fullName());
        assertEquals("request/group_add", OneBotEventTypes.REQUEST_GROUP_ADD.fullName());
        assertEquals("request/group_invite", OneBotEventTypes.REQUEST_GROUP_INVITE.fullName());
        assertEquals("meta/lifecycle", OneBotEventTypes.META_LIFECYCLE.fullName());
        assertEquals("meta/heartbeat", OneBotEventTypes.META_HEARTBEAT.fullName());
    }
}
