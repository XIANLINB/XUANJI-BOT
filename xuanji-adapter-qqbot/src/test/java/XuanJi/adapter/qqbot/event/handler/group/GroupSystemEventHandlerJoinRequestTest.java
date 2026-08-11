package XuanJi.adapter.qqbot.event.handler.group;

import tools.jackson.databind.node.ObjectNode;
import XuanJi.api.json.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锁定 GROUP_JOIN_REQUEST 入群申请解析契约：
 * 字段对照官方真实报文（group_openid / join_request_id / member_openid /
 * username / apply_source / apply_at / verify_info.verify_message），
 * 嵌套节点缺失/结构异常不崩。
 */
class GroupSystemEventHandlerJoinRequestTest {

    @Test
    void 真实报文结构_全部字段解析正确() {
        ObjectNode d = Json.obj()
                .put("group_openid", "B0E1469F5BA37505585E689DE3F5F7ED")
                .put("join_request_id", "REQ_ABC")
                .put("member_openid", "FC521B9A61658702576C9C78F836DAC0")
                .put("username", "借晚风叙旧")
                .put("apply_source", "self_apply")
                .put("apply_at", "2026-08-05T19:03:10+08:00")
                .set("verify_info", Json.obj()
                        .put("method", "verify_message")
                        .put("verify_message", "你好"));

        GroupSystemEventHandler.JoinRequestInfo jr =
                GroupSystemEventHandler.parseJoinRequest(d);
        assertEquals("B0E1469F5BA37505585E689DE3F5F7ED", jr.groupOpenid());
        assertEquals("REQ_ABC", jr.requestId());
        assertEquals("FC521B9A61658702576C9C78F836DAC0", jr.memberOpenid());
        assertEquals("借晚风叙旧", jr.username());
        assertEquals("self_apply", jr.applySource());
        assertEquals("2026-08-05T19:03:10+08:00", jr.applyAt());
        assertEquals("verify_message", jr.verifyMethod());
        assertEquals("你好", jr.verifyMessage());
    }

    @Test
    void verify_info缺失_不崩且留言为null() {
        ObjectNode d = Json.obj().put("group_openid", "G1");
        GroupSystemEventHandler.JoinRequestInfo jr =
                GroupSystemEventHandler.parseJoinRequest(d);
        assertEquals("G1", jr.groupOpenid());
        assertNull(jr.verifyMessage());
        assertNull(jr.verifyMethod());
    }

    @Test
    void 字段全缺失_全部为null且不崩() {
        ObjectNode d = Json.obj();
        GroupSystemEventHandler.JoinRequestInfo jr =
                GroupSystemEventHandler.parseJoinRequest(d);
        assertNull(jr.groupOpenid());
        assertNull(jr.requestId());
        assertNull(jr.memberOpenid());
        assertNull(jr.verifyMessage());
    }

    @Test
    void data为null_安全返回全null() {
        GroupSystemEventHandler.JoinRequestInfo jr =
                GroupSystemEventHandler.parseJoinRequest(null);
        assertNull(jr.groupOpenid());
        assertNull(jr.memberOpenid());
        assertNull(jr.verifyMessage());
    }
}
