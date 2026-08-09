package dev.xuanji.adapter.qqbot.converter;

import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.json.Json;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锁定 webhook 事件 sender 解析契约（黑名单/限频依赖 sender.platformUserId）：
 * 真实 webhook 群消息报文（author.member_openid）必须解析出稳定 sender。
 */
class QqEventConverterWebhookSenderTest {

    /** 用户真实 webhook 群消息报文（author 含 member_openid，形如 d 字段）。 */
    static ObjectNode webhookGroupMessage() {
        ObjectNode d = Json.obj();
        d.put("id", "ROBOT1.0_xxx");
        d.put("content", "ping");
        d.put("timestamp", "2026-08-06T13:04:13+08:00");
        ObjectNode author = Json.obj();
        author.put("id", "UU_U1");
        author.put("username", "Yolo.H");
        author.put("bot", false);
        author.put("member_openid", "2A59D600A191EF936BDB13262C46FE7A");
        author.put("member_role", "member");
        author.put("union_openid", "");
        d.set("author", author);
        d.put("group_id", "B0E1469F5BA37505585E689DE3F5F7ED");
        d.put("group_openid", "B0E1469F5BA37505585E689DE3F5F7ED");
        d.put("message_type", 0);
        return d;
    }

    @Test
    void webhook群消息_必须解析出稳定sender() {
        Bot bot = new Bot("qq:102915166", "qq", "102915166", Bot.Status.ONLINE, Set.of());
        BotEvent be = QqEventConverter.convert(bot, "GROUP_MESSAGE_CREATE", "PRODUCTION",
                webhookGroupMessage(), "evt_1");
        assertNotNull(be.sender(), "webhook 事件 sender 不能为 null（黑名单/限频依赖）");
        assertEquals("2A59D600A191EF936BDB13262C46FE7A", be.sender().platformUserId());
    }

    @Test
    void 两次事件_sender稳定一致() {
        Bot bot = new Bot("qq:102915166", "qq", "102915166", Bot.Status.ONLINE, Set.of());
        BotEvent b1 = QqEventConverter.convert(bot, "GROUP_MESSAGE_CREATE", "PRODUCTION",
                webhookGroupMessage(), "evt_1");
        BotEvent b2 = QqEventConverter.convert(bot, "GROUP_MESSAGE_CREATE", "PRODUCTION",
                webhookGroupMessage(), "evt_2");
        assertEquals(b1.sender().platformUserId(), b2.sender().platformUserId());
    }
}
