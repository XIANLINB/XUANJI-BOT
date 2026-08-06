package dev.xuanji.adapter.onebot.event.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.converter.OneBotEventTypes;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.annotation.GroupMessage;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.storage.MessageEventRecorder;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.api.adapter.Bot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OneBotMessageHandlerTest {

    String captured = null;

    @GroupMessage
    public void onMsg(GroupMessageEvent e) {
        captured = e.getPlainText();
    }

    @Test
    void groupMessageWithSubTypeRoutesToCommandRegistry() {
        captured = null;
        OneBotSessionRegistry registry = new OneBotSessionRegistry(null);
        OneBotProperties props = new OneBotProperties();
        props.setApiTimeoutMs(500);
        OneBotApiService api = new OneBotApiService(registry, props);
        OneBotMessageSenderImpl sender = new OneBotMessageSenderImpl(api);
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(this, "test");
        OneBotMessageHandler handler = new OneBotMessageHandler(cr, api, sender, null,
                new MessageEventRecorder(), new BotOutboundExecutor());

        ObjectNode data = Json.obj();
        data.set("sender", Json.obj().put("role", "member"));
        // BotEvent.bot() 是 dev.xuanji.api.adapter.Bot（实例元数据记录），selfId 故意设为与 @at 的
        // "10001" 不同，使 atBot=false，从而不触发被动回包中的 sendGroup（测试不依赖真实连接）。
        Bot bot = new Bot("onebot:10001:42", "onebot", "99999", Bot.Status.ONLINE, java.util.Set.of());
        BotEvent evt = new BotEvent(
                "onebot:10001:42",
                OneBotEventTypes.MESSAGE_GROUP,
                bot,
                new XuanjiUser("20002", "20002", "Tester", null, 0, Instant.now()),
                new XuanjiGroup("30003", "", "30003", "", 0, Instant.now()),
                MessageChain.builder().at("10001").text("你好").build(),
                "42",
                data,
                "message.group.normal",
                "PRODUCTION");

        handler.handle(evt);

        assertNotNull(captured, "@GroupMessage 插件方法应被调用");
        assertEquals("你好", captured);
    }
}