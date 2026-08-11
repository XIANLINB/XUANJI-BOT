package XuanJi.core.concurrent;

import XuanJi.core.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * 每 bot 出站执行器 — 并发池 + 节奏控制（P2-E）。
 *
 * <h3>两种用法（共享同一条 per-bot 时间线）</h3>
 * <ul>
 *   <li>{@link #submit(String, Runnable)} — 异步 fire-and-forget：进入 per-bot 并发池（线程数 = {@code tune.out_threads_per_bot}，默认 1），出队执行前补亏空睡眠，保证相邻出站间隔 ≥ 配置值</li>
 *   <li>{@link #awaitPace(String)} — 同步等待：当前调用线程补亏空睡眠后立即返回（用于群管/请求处理等有返回值、不能入队的动作）</li>
 * </ul>
 *
 * <p>节奏开关：{@code paceResolver} 按 key 返回节奏毫秒数，≤0 表示不节流（快速路径零开销）。
 * key 为空/空白统一归一为 {@code _default}，仍参与节流。
 *
 * <p>线程安全：异步队列与同步等待被不同线程调用，故 {@code lastSendNanos} 的读改写按 key 加锁互斥，
 * 否则并发下多线程读到同一 last 一起放行、节奏失效。
 */
@Slf4j
@Component
public class BotOutboundExecutor implements DisposableBean {

    private static final String DEFAULT_KEY = "_default";

    private final ToLongFunction<String> paceResolver;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private volatile ConfigService configService;

    /** 每 key 上次发送时刻（nano）。异步与同步调用共用此时间线。 */
    private final Map<String, Long> lastSendNanos = new ConcurrentHashMap<>();
    /** 每 key 节奏锁：让异步队列线程与任意同步调用线程互斥地读改写 lastSendNanos。 */
    private final Map<String, Object> paceLocks = new ConcurrentHashMap<>();
    /** 每 key 出站并发池（虚拟线程，线程数 = tune.out_threads_per_bot，默认 1）。 */
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    /** 每 bot 出站队列容量（有界），防任务无限堆积拖垮内存。 */
    private static final int OUTBOUND_QUEUE_CAPACITY = 1000;

    /**
     * 拒绝兜底：队列满载时降级为「调用线程同步执行」——既是背压（提交方越慢，入库越慢），又不丢任务；
     * 池已关闭时丢弃并告警。绝不抛异常中断提交方。
     */
    private static final RejectedExecutionHandler REJECT_HANDLER = (task, pool) -> {
        if (pool.isShutdown()) {
            log.warn("[Outbound] 出站池已关闭，任务丢弃: {}", task);
            return;
        }
        log.warn("[Outbound] 出站队列已满({})，降级为调用线程同步执行(背压): {}", OUTBOUND_QUEUE_CAPACITY, task);
        if (!Thread.currentThread().isInterrupted()) {
            task.run();
        }
    };

    /** 测试/裸用路径：默认不节流。 */
    public BotOutboundExecutor() {
        this(key -> 0L, System::nanoTime, Thread::sleep);
    }

    /** Spring 注入路径：paceResolver 由 ConfigService.getOutboundPaceMs(key) 派生。 */
    @Autowired
    public BotOutboundExecutor(ConfigService configService) {
        this(configService == null ? (key -> 0L) : (key -> configService.getOutboundPaceMs(key)),
                System::nanoTime, Thread::sleep);
        this.configService = configService;
        // 注册到监控：出站虚拟线程池（每 bot 一个并发池）
        ThreadPoolRegistry.register("出站虚拟线程池(每bot)", () -> {
            int n = executors.size();
            return new ThreadPoolRegistry.PoolInfo(
                    "出站虚拟线程池(每bot)", "VirtualThread(每bot并发池)",
                    n, n, -1, n, -1, -1,
                    "每 bot 一个并发池（线程数=tune.out_threads_per_bot）+ pace 节流；poolSize=已创建池数");
        });
    }

    /** 测试注入路径：假 Sleeper 睡眠时推进时钟，实现毫秒级确定性节奏验证。 */
    public BotOutboundExecutor(ToLongFunction<String> paceResolver, LongSupplier clock, Sleeper sleeper) {
        this.paceResolver = paceResolver != null ? paceResolver : key -> 0L;
        this.clock = clock != null ? clock : System::nanoTime;
        this.sleeper = sleeper != null ? sleeper : Thread::sleep;
        this.configService = null;
    }

    /** 异步提交：入 per-bot 并发池，出队执行前按 key 补亏空睡眠。 */
    public void submit(String botId, Runnable task) {
        String key = normalizeKey(botId);
        ExecutorService pool = executors.computeIfAbsent(key, this::newPool);
        pool.execute(() -> {
            try {
                pace(key);
                task.run();
            } catch (Exception e) {
                log.warn("[Outbound] 出站任务执行异常: key={}, err={}", key, e.getMessage());
            }
        });
    }

    /** 同步等待节奏：当前线程补亏空睡眠后返回；pace 未开启时零开销。 */
    public void awaitPace(String botId) {
        pace(normalizeKey(botId));
    }

    private ExecutorService newPool(String key) {
        // 每 bot 出站并发度：读全局 tune.out_threads_per_bot（默认 1 = 串行，与旧行为一致）
        int threads = 1;
        try {
            String v = configService != null ? configService.getGlobalConfig().get("tune.out_threads_per_bot") : null;
            if (v != null && !v.isBlank()) {
                int n = Integer.parseInt(v.trim());
                if (n >= 1) threads = n;
            }
        } catch (Exception ignored) { /* 非法值用默认 1 */ }
        final int t = threads;
        log.info("[Outbound] 创建出站池: key={}, threads={}, queue={}", key, t, OUTBOUND_QUEUE_CAPACITY);
        return new ThreadPoolExecutor(t, t, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY),
                Thread.ofVirtual().name("xuanji-out-" + key + "-" + seq.incrementAndGet()).factory(),
                REJECT_HANDLER);
    }

    private void pace(String key) {
        long paceMs = paceResolver.applyAsLong(key);
        if (paceMs <= 0) return;                       // 未开启节奏：零开销快速路径
        Object lock = paceLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            long now = clock.getAsLong();
            Long last = lastSendNanos.get(key);
            if (last == null) { lastSendNanos.put(key, now); return; }
            long deficitNanos = paceMs * 1_000_000L - (now - last);
            if (deficitNanos > 0) {
                try { sleeper.sleep(deficitNanos / 1_000_000L); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            lastSendNanos.put(key, clock.getAsLong());
        }
    }

    private static String normalizeKey(String botId) {
        return (botId == null || botId.isBlank()) ? DEFAULT_KEY : botId;
    }

    @Override
    public void destroy() {
        paceLocks.clear();
        executors.values().forEach(ExecutorService::shutdownNow);
        executors.clear();
    }

    /** 显式关闭（测试用）：等价 destroy。 */
    public void shutdown() {
        destroy();
    }
}
