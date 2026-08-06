package dev.xuanji.adapter.onebot.session;

import dev.xuanji.core.storage.FrameworkBotRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OneBotSessionRegistry#rebind} 回归测试 —— selfId 占位符回绑。
 *
 * <p>背景 bug：正向 WS 的会话 selfId 硬编码为占位符 {@code "forward"} 且从未赋值，
 * 反向 WS 缺 {@code X-Self-ID} 头时为 {@code "unknown"}。注册表以占位符作 key，
 * 于是按真实 QQ 号 {@code find("123456")} 永远查不到连接，发送时直接抛"无可用 OneBot 连接"
 * ——OneBot 侧根本发不出消息（单 bot 场景侥幸靠 {@code any()} 兜底掩盖了问题）。
 *
 * <p>修复：首条事件到达时用报文里真实的 {@code self_id} 回绑会话 key。
 */
class OneBotSessionRegistryRebindTest {

    /** 可回绑的假会话（模拟正向 WS 的 ClientSideSession）。 */
    static class FakeSession implements OneBotSession {
        private volatile String selfId;
        private final String direction;
        boolean closed = false;

        FakeSession(String selfId, String direction) {
            this.selfId = selfId;
            this.direction = direction;
        }

        @Override public String selfId()    { return selfId; }
        @Override public String direction() { return direction; }
        @Override public boolean isOpen()   { return !closed; }
        @Override public void sendText(String text) { /* 测试不发送 */ }
        @Override public void close()       { closed = true; }

        @Override
        public boolean rebindSelfId(String realSelfId) {
            this.selfId = realSelfId;
            return true;
        }
    }

    /** 不支持回绑的会话（走接口 default 实现返回 false）。 */
    static class RigidSession implements OneBotSession {
        @Override public String selfId()    { return OneBotSession.PLACEHOLDER_UNKNOWN; }
        @Override public String direction() { return "reverse"; }
        @Override public boolean isOpen()   { return true; }
        @Override public void sendText(String text) { }
        @Override public void close()       { }
    }

    /** 落库以 no-op 子类替身注入（单测不碰数据库，也避免 null 仓储刷 WARN 噪音）。 */
    private OneBotSessionRegistry newRegistry() {
        FrameworkBotRepository repo = new FrameworkBotRepository(null) {
            @Override public void upsert(String platform, String instanceId, String adapter, String status) { }
            @Override public void setStatus(String platform, String instanceId, String status) { }
        };
        return new OneBotSessionRegistry(repo);
    }

    @Test
    void placeholderKeyIsUnreachableByRealSelfIdBeforeRebind() {
        OneBotSessionRegistry registry = newRegistry();
        FakeSession s = new FakeSession(OneBotSession.PLACEHOLDER_FORWARD, "forward");
        registry.register(s);

        assertTrue(registry.find("123456").isEmpty(),
                "回绑前按真实 QQ 号查不到连接——这正是原 bug 的现场");
        assertTrue(registry.find(OneBotSession.PLACEHOLDER_FORWARD).isPresent());
    }

    @Test
    void rebindMovesSessionFromPlaceholderToRealSelfId() {
        OneBotSessionRegistry registry = newRegistry();
        FakeSession s = new FakeSession(OneBotSession.PLACEHOLDER_FORWARD, "forward");
        registry.register(s);

        registry.rebind(s, "123456");

        assertEquals("123456", s.selfId(), "会话自身的 selfId 应被改写");
        assertTrue(registry.find("123456").isPresent(), "按真实 QQ 号应能查到连接");
        assertTrue(registry.find(OneBotSession.PLACEHOLDER_FORWARD).isEmpty(), "占位 key 应被摘掉");
        assertEquals(1, registry.onlineCount(), "回绑不应产生重复会话");
        assertFalse(s.closed, "回绑的是同一条连接，不得被当作旧连接关闭");
    }

    @Test
    void reverseUnknownPlaceholderIsAlsoRebound() {
        OneBotSessionRegistry registry = newRegistry();
        FakeSession s = new FakeSession(OneBotSession.PLACEHOLDER_UNKNOWN, "reverse");
        registry.register(s);

        registry.rebind(s, "888888");

        assertTrue(registry.find("888888").isPresent());
        assertTrue(registry.find(OneBotSession.PLACEHOLDER_UNKNOWN).isEmpty());
    }

    @Test
    void alreadyBoundSessionIsNotRebound() {
        OneBotSessionRegistry registry = newRegistry();
        FakeSession s = new FakeSession("111111", "reverse");
        registry.register(s);

        registry.rebind(s, "222222");   // 同一连接报出不同 self_id：串号，应忽略

        assertEquals("111111", s.selfId(), "已绑定真实号的会话不得被改写");
        assertTrue(registry.find("111111").isPresent());
        assertTrue(registry.find("222222").isEmpty());
    }

    @Test
    void rebindWithSameIdOrBlankIsNoop() {
        OneBotSessionRegistry registry = newRegistry();
        FakeSession s = new FakeSession("123456", "forward");
        registry.register(s);

        registry.rebind(s, "123456");   // 常态快速路径
        registry.rebind(s, null);
        registry.rebind(s, "  ");
        registry.rebind(null, "999");

        assertEquals("123456", s.selfId());
        assertEquals(1, registry.onlineCount());
    }

    @Test
    void sessionRefusingRebindKeepsPlaceholderKey() {
        OneBotSessionRegistry registry = newRegistry();
        RigidSession s = new RigidSession();
        registry.register(s);

        registry.rebind(s, "123456");

        assertTrue(registry.find(OneBotSession.PLACEHOLDER_UNKNOWN).isPresent(),
                "不支持回绑的会话应原样保留在占位 key 下，不能凭空丢失连接");
        assertTrue(registry.find("123456").isEmpty());
    }

    @Test
    void placeholderDetection() {
        assertTrue(OneBotSession.isPlaceholderId(null));
        assertTrue(OneBotSession.isPlaceholderId("  "));
        assertTrue(OneBotSession.isPlaceholderId(OneBotSession.PLACEHOLDER_FORWARD));
        assertTrue(OneBotSession.isPlaceholderId(OneBotSession.PLACEHOLDER_UNKNOWN));
        assertFalse(OneBotSession.isPlaceholderId("123456"));
    }
}
