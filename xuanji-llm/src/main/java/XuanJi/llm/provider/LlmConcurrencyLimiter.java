package XuanJi.llm.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 下游调用并发护栏（背压）。
 *
 * <p>通过 {@link Semaphore} 限制同时进行的 LLM 出站调用数量，防止突发流量把供应商 API 打爆、
 * 或本地线程无限堆积导致 OOM。获取许可设置超时（{@link #ACQUIRE_TIMEOUT_SECONDS}），
 * 超时被限流而非无限阻塞，避免线程雪崩。
 *
 * <p><b>正向兼容</b>：默认上限 {@link #DEFAULT_MAX_CONCURRENT}=64，对正常流量几乎无感；
 * 旧部署配置中无该字段时取默认值，行为等价于改前（不限制）。可通过 LLM 配置
 * {@code maxConcurrency} 调小；resize 在调用间安全进行（acquire/release 绑定同一快照，无许可泄漏）。
 */
@Slf4j
@Component
public class LlmConcurrencyLimiter {

    /** 默认并发上限（宽松，正常流量不会触及）。 */
    public static final int DEFAULT_MAX_CONCURRENT = 64;

    /** 获取许可超时（秒）：超时即被限流，避免线程无限堆积。 */
    private static final int ACQUIRE_TIMEOUT_SECONDS = 30;

    /** 允许的最大上限（防误配成超大值）。 */
    private static final int HARD_CAP = 2000;

    private final AtomicReference<Semaphore> ref = new AtomicReference<>(new Semaphore(DEFAULT_MAX_CONCURRENT, true));
    private volatile int capacity = DEFAULT_MAX_CONCURRENT;
    private final Object lock = new Object();

    /** 应用新的并发上限；与当前值相同则无操作。仅在调用间隙安全 resize。 */
    public void configure(int requested) {
        int v = requested <= 0 ? DEFAULT_MAX_CONCURRENT : Math.min(requested, HARD_CAP);
        if (v == capacity) {
            return;
        }
        synchronized (lock) {
            if (v == capacity) {
                return;
            }
            Semaphore cur = ref.get();
            int held = capacity - cur.availablePermits();   // 当前在途（已借出）的许可数
            cur.acquireUninterruptibly(cur.availablePermits()); // 抽干当前可用许可
            Semaphore next = new Semaphore(v, true);
            next.acquireUninterruptibly(held);              // 为在途调用保留许可
            ref.set(next);
            capacity = v;
            log.info("[LLM-LIMIT] 并发护栏上限已调整: {} -> {}", this.capacity, v);
        }
    }

    /** 在并发许可保护下执行有返回值动作；许可获取/释放绑定同一快照，无泄漏。 */
    public <T> T run(java.util.function.Supplier<T> action) {
        Semaphore sem = ref.get();
        acquireOrThrow(sem);
        try {
            return action.get();
        } finally {
            sem.release();
        }
    }

    /** 在并发许可保护下执行无返回值动作。 */
    public void run(java.lang.Runnable action) {
        Semaphore sem = ref.get();
        acquireOrThrow(sem);
        try {
            action.run();
        } finally {
            sem.release();
        }
    }

    private void acquireOrThrow(Semaphore sem) {
        try {
            if (!sem.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "LLM 并发调用已达上限(" + capacity + ")，请求被限流，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 调用被中断", e);
        }
    }

    /** 当前上限（只读快照，供监控/日志使用）。 */
    public int maxConcurrent() {
        return capacity;
    }

    /** 当前可用许可数（只读快照）。 */
    public int available() {
        return ref.get().availablePermits();
    }
}
