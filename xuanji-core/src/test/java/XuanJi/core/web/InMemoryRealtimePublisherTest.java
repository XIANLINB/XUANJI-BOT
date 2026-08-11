package XuanJi.core.web;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InMemoryRealtimePublisher} 行为测试：发布/订阅/退订、异常隔离。
 */
class InMemoryRealtimePublisherTest {

    @Test
    void publishDeliversToAllSubscribers() {
        InMemoryRealtimePublisher pub = new InMemoryRealtimePublisher();
        List<Map<String, Object>> received = new ArrayList<>();
        pub.subscribe(received::add);

        Map<String, Object> evt = Map.of("type", "event", "level", "INFO");
        pub.publish(evt);

        assertEquals(1, received.size());
        assertEquals("event", received.get(0).get("type"));
    }

    @Test
    void unsubscribeStopsDelivery() {
        InMemoryRealtimePublisher pub = new InMemoryRealtimePublisher();
        AtomicInteger count = new AtomicInteger();
        // 退订需同一个监听器引用（与生产中 this::onPublish 这类稳定方法引用一致）
        Consumer<Map<String, Object>> listener = e -> count.incrementAndGet();
        assertTrue(pub.subscribe(listener));
        pub.publish(Map.of("type", "heartbeat"));
        assertEquals(1, count.get());

        assertTrue(pub.unsubscribe(listener));
        pub.publish(Map.of("type", "heartbeat"));
        assertEquals(1, count.get()); // 退订后不再收到
    }

    @Test
    void publishNullIsIgnored() {
        InMemoryRealtimePublisher pub = new InMemoryRealtimePublisher();
        AtomicInteger count = new AtomicInteger();
        pub.subscribe(e -> count.incrementAndGet());
        pub.publish(null);
        assertEquals(0, count.get());
    }

    @Test
    void subscriberExceptionIsolated() {
        InMemoryRealtimePublisher pub = new InMemoryRealtimePublisher();
        List<Map<String, Object>> good = new ArrayList<>();
        pub.subscribe(e -> { throw new RuntimeException("boom"); });
        pub.subscribe(good::add);

        pub.publish(Map.of("type", "event"));
        // 坏订阅者抛异常不应影响其他订阅者
        assertEquals(1, good.size());
    }
}
