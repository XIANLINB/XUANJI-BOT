package dev.xuanji.adapter.qqbot.exception;

import dev.xuanji.core.metrics.TraceContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 适配器层统一异常处理器（与框架 {@code GlobalExceptionHandler} 分层）。
 *
 * <p>框架 {@code GlobalExceptionHandler} 只覆盖 HTTP/Controller 边界；而 WS 读取线程、
 * Webhook 消费线程、插件执行线程里的运行时异常不经过它。本类作为这些"运行时/事件"异常的
 * 统一落点：记录 traceId + eventType + botId，使一条异常能定位到"哪个 bot、哪类事件、
 * 哪个链路"，而非仅在局部 catch 里一行 {@code log.error}。</p>
 *
 * <p>同时做轻量计数：累计失败达阈值时输出告警，便于发现"上游依赖抖动/频控"等系统性问题。</p>
 */
@Slf4j
public final class AdapterExceptionHandler {

    /** 累计事件处理失败次数（用于超阈值告警）。 */
    private static final AtomicLong FAIL_COUNT = new AtomicLong(0);

    /** 阈值：每达到该倍数输出一次汇总告警。 */
    private static final long WARN_EVERY = 100;

    private AdapterExceptionHandler() {
    }

    /**
     * 记录一次事件处理失败。
     *
     * @param phase     处理阶段（如 "事件处理"、"群聊Handler"、"单聊Handler"）
     * @param eventType 事件类型（原始平台事件名）
     * @param botId     机器人标识（platform:selfId）
     * @param t         异常
     */
    public static void logEventFailure(String phase, String eventType, String botId, Throwable t) {
        long n = FAIL_COUNT.incrementAndGet();
        if (n % WARN_EVERY == 0) {
            log.warn("[适配器异常] 累计事件处理失败已达 {} 次，请排查上游依赖/频控", n);
        }
        log.error("[适配器异常][{}] eventType={}, botId={}, traceId={}, error={}",
                phase, eventType, botId, TraceContext.currentTraceId(), t.getMessage(), t);
    }
}
