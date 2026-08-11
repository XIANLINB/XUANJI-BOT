package XuanJi.console.controller;

import XuanJi.core.storage.MessageEventRecorder;
import XuanJi.core.web.RealtimeEventPublisher;
import XuanJi.core.web.XuanJiApi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 控制台 · 实时事件流（SSE）。
 *
 * <p>路径经 {@link XuanJiApi} 前缀装配为 {@code /xuanji/api/v1/console/stream}。
 * 订阅 core 的 {@link RealtimeEventPublisher}，把 {@code event} / {@code log} / {@code heartbeat}
 * 三类实时事件扇出给所有已连接的 {@link SseEmitter}。SSE 控制器只能落此模块，因为 core 不引入 spring-web。
 *
 * <p>连接即推送一份当前事件快照 + 一次心跳，避免前端空窗；之后由 publisher 广播驱动。
 * 心跳用 {@link Scheduled} 每 30 秒一次，兼作保活（emitter 超时设 30 分钟，远长于心跳间隔）。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console")
@RequiredArgsConstructor
public class ConsoleStreamController {

    private final RealtimeEventPublisher publisher;
    private final MessageEventRecorder eventRecorder;

    /** 所有活跃 SSE 连接（写少读多，CopyOnWriteArrayList 合适）。 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    /** 应用生命周期内只订阅一次 publisher，向全部 emitter 广播。 */
    @PostConstruct
    public void init() {
        publisher.subscribe(this::onPublish);
    }

    @PreDestroy
    public void destroy() {
        publisher.unsubscribe(this::onPublish);
        emitters.forEach(e -> { try { e.complete(); } catch (Exception ignored) {} });
        emitters.clear();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // 30 分钟超时（心跳每 30 秒一次，远小于超时，连接可长期保持）
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitter.complete(); emitters.remove(emitter); });
        emitter.onError((e) -> emitters.remove(emitter));

        try {
            // 连接即推当前事件快照，前端立即可见历史活动
            for (Map<String, Object> e : eventRecorder.snapshot()) {
                emitter.send(SseEmitter.event()
                        .name("event")
                        .id(String.valueOf(seq.incrementAndGet()))
                        .data(e));
            }
            emitter.send(heartbeatEvent());
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    /** 心跳：每 30 秒向所有连接推送一次，兼作保活。 */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        broadcast(Map.of("type", "heartbeat",
                "time", new Date().toString(),
                "clients", emitters.size()));
    }

    /** publisher 回调：心跳由 @Scheduled 单独推送，这里跳过（避免双发）。 */
    private void onPublish(Map<String, Object> payload) {
        if ("heartbeat".equals(payload.get("type"))) return;
        broadcast(payload);
    }

    private void broadcast(Map<String, Object> payload) {
        String name = String.valueOf(payload.getOrDefault("type", "event"));
        String id = String.valueOf(seq.incrementAndGet());
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name(name).id(id).data(payload));
            } catch (Exception e) {
                dead.add(em); // 发送失败视为已断开，稍后清理
            }
        }
        if (!dead.isEmpty()) emitters.removeAll(dead);
    }

    private SseEmitter.SseEventBuilder heartbeatEvent() {
        return SseEmitter.event()
                .name("heartbeat")
                .id(String.valueOf(seq.incrementAndGet()))
                .data(Map.of("time", new Date().toString(), "clients", emitters.size()));
    }
}
