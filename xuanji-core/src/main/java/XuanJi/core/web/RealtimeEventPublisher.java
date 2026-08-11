package XuanJi.core.web;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 零 Web 依赖的实时事件广播接口。
 *
 * <p>core 模块<b>不引入</b> spring-web（只有 spring-boot-starter），因此 SSE 控制器只能落在
 * {@code xuanji-console-server}；core 侧只能定义这个纯 JDK 的发布/订阅接口，由 console-server 的 SSE
 * 控制器实现并订阅。{@link XuanJi.core.storage.MessageEventRecorder} 在每次
 * {@code record()} 时通过本接口广播一条实时事件。
 *
 * <p>事件载荷统一为 {@code Map<String,Object>}，且<b>必须</b>带 {@code type} 字段
 * （如 {@code event} / {@code log} / {@code heartbeat}），便于 SSE 端按类型分发。
 * 订阅回调在<b>发布线程</b>上同步执行，订阅者需保证轻量、不抛异常。
 */
public interface RealtimeEventPublisher {

    /** 广播一条实时事件。payload 为 null 时静默忽略。 */
    void publish(Map<String, Object> payload);

    /**
     * 注册订阅者。回调在发布线程上同步执行。
     * @return true 表示注册成功（首次添加）
     */
    boolean subscribe(Consumer<Map<String, Object>> listener);

    /**
     * 取消订阅。
     * @return true 表示成功移除
     */
    boolean unsubscribe(Consumer<Map<String, Object>> listener);
}
