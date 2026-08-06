package dev.xuanji.adapter.onebot.bot;

import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.storage.FrameworkBotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OneBotXjBot} 出站收口回归测试（P2-E 对齐 QQ 侧）。
 *
 * <p>改造前 OneBot 的每一次 {@code reply/send} 都在事件线程上同步阻塞到 WS 响应
 * （正向 WS 每条入站消息一个虚拟线程 → 群内瞬时多消息会并发写同一条连接）。
 * 改造后：
 * <ul>
 *   <li>无返回值的发送投递到 per-bot 单线程队列——调用方立即返回、同 bot 串行保序；</li>
 *   <li>有返回值的群管动作留在调用线程同步执行，返回值语义不变。</li>
 * </ul>
 */
class OneBotXjBotOutboundTest {

    private BotOutboundExecutor outbound;

    @AfterEach
    void tearDown() {
        if (outbound != null) outbound.shutdown();
    }

    /** 记录调用的假发送器：拦住所有 action，不碰真实连接。 */
    static class RecordingSender extends OneBotMessageSenderImpl {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<String> threads = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;

        RecordingSender(OneBotApiService api, CountDownLatch latch) {
            super(api);
            this.latch = latch;
        }

        private SendReceipt record(String tag) {
            calls.add(tag);
            threads.add(Thread.currentThread().getName());
            if (latch != null) latch.countDown();
            return SendReceipt.ok("stub", 0L);
        }

        @Override public SendReceipt sendGroup(String selfId, String groupId, ArrayNode segments) {
            return record("sendGroup:" + groupId);
        }
        @Override public SendReceipt sendPrivate(String selfId, String userId, ArrayNode segments) {
            return record("sendPrivate:" + userId);
        }
        @Override public SendReceipt kickGroupMember(String selfId, String groupId, String userId, boolean rejectAdd) {
            return record("kick:" + userId);
        }
    }

    private OneBotApiService stubApi() {
        OneBotProperties props = new OneBotProperties();
        props.setApiTimeoutMs(200);
        FrameworkBotRepository repo = new FrameworkBotRepository(null) {
            @Override public void upsert(String platform, String instanceId, String adapter, String status) { }
            @Override public void setStatus(String platform, String instanceId, String status) { }
        };
        return new OneBotApiService(new OneBotSessionRegistry(repo), props);
    }

    @Test
    void voidSendsGoThroughOutboundQueueInOrder() throws InterruptedException {
        outbound = new BotOutboundExecutor();
        CountDownLatch done = new CountDownLatch(3);
        RecordingSender sender = new RecordingSender(stubApi(), done);
        OneBotXjBot bot = new OneBotXjBot(stubApi(), sender, outbound,
                "123456", "30003", "20002", "42");

        bot.sendGroup("g1", "一");
        bot.sendGroup("g2", "二");
        bot.reply("三");

        assertTrue(done.await(5, TimeUnit.SECONDS), "3 条出站应由出站线程完成");
        assertEquals(List.of("sendGroup:g1", "sendGroup:g2", "sendGroup:30003"), sender.calls,
                "同一 bot 的出站应串行且保持提交顺序");
        assertTrue(sender.threads.stream().allMatch(n -> n.startsWith("xuanji-out-")),
                "发送必须发生在 per-bot 出站线程上，而不是调用线程：" + sender.threads);
    }

    @Test
    void replyWithoutGroupFallsBackToPrivate() throws InterruptedException {
        outbound = new BotOutboundExecutor();
        CountDownLatch done = new CountDownLatch(1);
        RecordingSender sender = new RecordingSender(stubApi(), done);
        OneBotXjBot bot = new OneBotXjBot(stubApi(), sender, outbound,
                "123456", null, "20002", "42");

        bot.reply("私聊回复");

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("sendPrivate:20002"), sender.calls);
    }

    @Test
    void receiptActionsStaySynchronousOnCallerThread() {
        outbound = new BotOutboundExecutor();
        RecordingSender sender = new RecordingSender(stubApi(), null);
        OneBotXjBot bot = new OneBotXjBot(stubApi(), sender, outbound,
                "123456", "30003", "20002", "42");

        SendReceipt receipt = bot.kickGroupMember("20002", false);

        assertNotNull(receipt, "群管动作必须保留同步返回值");
        assertTrue(receipt.success());
        assertEquals(List.of("kick:20002"), sender.calls);
        assertEquals(Thread.currentThread().getName(), sender.threads.get(0),
                "有返回值的动作应留在调用线程，只做节奏等待、不切线程");
    }

    @Test
    void nullExecutorDegradesToSynchronousSend() {
        RecordingSender sender = new RecordingSender(stubApi(), null);
        OneBotXjBot bot = new OneBotXjBot(stubApi(), sender, null,
                "123456", "30003", "20002", "42");

        bot.sendGroup("g1", "裸用");

        assertEquals(List.of("sendGroup:g1"), sender.calls, "未装配执行器时应退化为同步直发");
        assertEquals(Thread.currentThread().getName(), sender.threads.get(0));
    }

    @Test
    void groupActionOutsidePrivateContextFailsFast() {
        outbound = new BotOutboundExecutor();
        RecordingSender sender = new RecordingSender(stubApi(), null);
        OneBotXjBot bot = new OneBotXjBot(stubApi(), sender, outbound,
                "123456", null, "20002", "42");

        assertThrows(IllegalStateException.class, () -> bot.kickGroupMember("20002", false),
                "私聊上下文执行群管动作应在节奏等待前就抛错");
        assertTrue(sender.calls.isEmpty());
    }
}
