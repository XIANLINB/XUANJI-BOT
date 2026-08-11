package XuanJi.core.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link RealtimeEventPublisher} 的进程内默认实现（零 Web 依赖）。
 *
 * <p>用 {@link CopyOnWriteArrayList} 存订阅者，保证 publish 时的读遍历无锁、线程安全；
 * 订阅/退订为低频操作，写开销可接受。任一订阅者抛异常不会影响其他订阅者。
 */
@Slf4j
@Component
public class InMemoryRealtimePublisher implements RealtimeEventPublisher {

    private final CopyOnWriteArrayList<Consumer<Map<String, Object>>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Map<String, Object> payload) {
        if (payload == null) return;
        for (Consumer<Map<String, Object>> listener : listeners) {
            try {
                listener.accept(payload);
            } catch (Exception e) {
                log.warn("[实时事件] 订阅者处理异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean subscribe(Consumer<Map<String, Object>> listener) {
        if (listener == null) return false;
        return listeners.addIfAbsent(listener);
    }

    @Override
    public boolean unsubscribe(Consumer<Map<String, Object>> listener) {
        if (listener == null) return false;
        return listeners.remove(listener);
    }
}
