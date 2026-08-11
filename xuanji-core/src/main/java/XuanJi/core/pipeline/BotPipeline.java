package XuanJi.core.pipeline;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.pipeline.PipelineStage;
import XuanJi.core.config.ConfigService;
import XuanJi.core.concurrent.ThreadPoolRegistry;
import XuanJi.core.metric.QpsMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BotPipeline — 按 Ordered 顺序执行所有注册的阶段。
 *
 * <p><b>并发模型（P2-1）</b>：{@link #proceed} 从「同步串行链」改为「每 bot 异步并发」——
 * 每个 bot 一个独立的虚拟线程池，池大小读全局配置 {@code tune.bot_concurrency}（默认 1 = 串行，
 * 保证与旧行为一致）。事件到达后立即提交到该 bot 的池，不阻塞 WS/Webhook 接收线程。
 *
 * <p>并发度（经济 2 / 运动 4 / 性能 8）由 {@code xuanji_config.tune.bot_concurrency} 控制，
 * 需配合性能模板保存使用；模板只写入配置，BotPipeline 懒建池时读取（重启后生效）。
 *
 * <p>阶段通过构造器注入（Spring 自动收集所有 PipelineStage Bean），
 * 按 {@code order()} 升序排列后依次执行。任一阶段返回 ABORT 则停止。
 */
@Slf4j
@Component
public class BotPipeline implements DisposableBean {

    private final List<PipelineStage> stages;
    private final ConfigService configService;
    private final AtomicLong processedCount = new AtomicLong();
    private final Map<String, AtomicInteger> slowStageCounts = new ConcurrentHashMap<>();
    private static final long SLOW_THRESHOLD_MS = 100;

    // ── 每 bot 虚拟线程并发池 ──
    /** key = "platform:selfId"，value = 该 bot 的并发池。 */
    private final Map<String, ExecutorService> botPools = new ConcurrentHashMap<>();
    private static final String DEFAULT_KEY = "_default";
    private static final AtomicInteger POOL_SEQ = new AtomicInteger();

    public BotPipeline(List<PipelineStage> stages, ConfigService configService) {
        this.stages = stages.stream()
                .sorted(Comparator.comparingInt(PipelineStage::order))
                .toList();
        this.configService = configService;
        // 慢阶段计数预初始化全部阶段为 0（前端期望全量阶段展示，未慢过也显示 0）
        for (PipelineStage s : this.stages) {
            slowStageCounts.putIfAbsent(s.name(), new AtomicInteger());
        }
        // 注册到监控：Pipeline 并发池实时状态
        ThreadPoolRegistry.register("Pipeline事件池(每bot)", () -> {
            int n = botPools.size();
            return new ThreadPoolRegistry.PoolInfo(
                    "Pipeline事件池(每bot)", "VirtualThread(每bot并发池)",
                    n, n, -1, n, -1, -1,
                    "每 bot 一个并发池（大小=tune.bot_concurrency）；poolSize=已创建池数");
        });
        log.info("[Pipeline] 初始化完成，阶段链: {}",
                this.stages.stream().map(s -> s.order() + ":" + s.name()).toList());
    }

    /**
     * 将事件送入流水线处理（异步）。
     *
     * <p>提交到该 bot 的虚拟线程并发池执行，接收线程立即返回（不阻塞 WS/Webhook）。
     * 事件处理异常在池内吞掉并记日志，不影响其它事件。
     *
     * @param event 已转换的统一事件
     */
    public void proceed(XuanJiEvent event) {
        processedCount.incrementAndGet();
        QpsMeter.hit();
        ExecutorService pool = poolFor(event);
        pool.execute(() -> {
            try {
                doProceed(event, 0);
            } catch (Throwable t) {
                log.error("[Pipeline] 事件处理未捕获异常: bot={}, type={}, err={}",
                        event.bot() != null ? event.bot().selfId() : "?", event.rawEventType(), t.getMessage(), t);
            }
        });
    }

    /** 取/建该 bot 的并发池（大小 = tune.bot_concurrency，默认 1=串行）。 */
    private ExecutorService poolFor(XuanJiEvent event) {
        String key = DEFAULT_KEY;
        if (event.bot() != null && event.bot().selfId() != null && !event.bot().selfId().isBlank()) {
            key = event.bot().platform() + ":" + event.bot().selfId();
        }
        return botPools.computeIfAbsent(key, this::newPool);
    }

    /** 建池：从全局配置读并发度（默认 1 = 串行）。 */
    private ExecutorService newPool(String key) {
        int concurrency = 1;
        try {
            String v = configService.getGlobalConfig().get("tune.bot_concurrency");
            if (v != null && !v.isBlank()) {
                int n = Integer.parseInt(v.trim());
                if (n >= 1) concurrency = n;
            }
        } catch (Exception ignored) { /* 非法值用默认 1 */ }
        final int n = concurrency;
        log.info("[Pipeline] 创建事件并发池: bot={}, concurrency={}", key, n);
        return Executors.newFixedThreadPool(n, r -> {
            Thread t = Thread.ofVirtual()
                    .name("xuanji-pipeline-" + key.replaceAll("[^a-zA-Z0-9]", "-") + "-" + POOL_SEQ.incrementAndGet())
                    .factory().newThread(r);
            t.setDaemon(true);
            return t;
        });
    }

    private void doProceed(XuanJiEvent event, int index) {
        if (index >= stages.size()) {
            return;
        }
        PipelineStage stage = stages.get(index);
        // 计时修正：next() 链式同步执行后续阶段，若按 handle 总耗时计，
        // 每个阶段都会显示同样的总耗时（误导）。用 nextBegin 记录「调用 next」的时刻，
        // 阶段自身耗时 = handle 开始 → 调 next（或 handle 返回）之间。
        long start = System.nanoTime();
        AtomicLong nextBegin = new AtomicLong(-1);
        try {
            PipelineStage.Result result = stage.handle(event, () -> {
                nextBegin.set(System.nanoTime());
                doProceed(event, index + 1);
                return PipelineStage.Result.CONTINUE;
            });
            long end = nextBegin.get() > 0 ? nextBegin.get() : System.nanoTime();
            long elapsedMs = (end - start) / 1_000_000L;
            if (elapsedMs > SLOW_THRESHOLD_MS) {
                log.warn("[Pipeline] 慢阶段: {} ({}ms)", stage.name(), elapsedMs);
                slowStageCounts.computeIfAbsent(stage.name(), k -> new AtomicInteger()).incrementAndGet();
            }
            if (result == PipelineStage.Result.ABORT) {
                log.debug("[Pipeline] {} 中断流水线", stage.name());
            }
        } catch (Exception e) {
            log.error("[Pipeline] {} 执行异常: {}", stage.name(), e.getMessage(), e);
        }
    }

    /** 去重统计（控制台 /console/health 的 dedup 键）。 */
    public Map<String, Object> getDedupStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("processed", processedCount.get());
        m.put("stages", stages.size());
        m.put("slowThresholdMs", SLOW_THRESHOLD_MS);
        // 聚合 DedupStage 的 DB 命中 / 降级本地计数（前端 /health 卡片字段）
        for (PipelineStage s : stages) {
            if (s instanceof DedupStage dd) {
                m.putAll(dd.stats());
            }
        }
        return m;
    }

    /** 框架级限流命中统计（风控中心概览：RateLimitStage 拦截次数）。 */
    public Map<String, Object> getRateLimitStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (PipelineStage s : stages) {
            if (s instanceof RateLimitStage rl) {
                m.putAll(rl.stats());
            }
        }
        return m;
    }

    /** 白名单/黑名单拦截统计（风控中心概览：WhitelistStage 黑名单拦截次数）。 */
    public Map<String, Object> getWhitelistStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (PipelineStage s : stages) {
            if (s instanceof WhitelistStage ws) {
                m.putAll(ws.stats());
            }
        }
        return m;
    }

    /** 慢阶段计数（控制台 /console/health 的 pipelineSlowStages 键）。 */
    public Map<String, Object> getSlowStageCounts() {
        Map<String, Object> m = new LinkedHashMap<>();
        slowStageCounts.forEach((k, v) -> m.put(k, v.get()));
        return m;
    }

    /** 每 bot 并发池当前大小（供前端展示）。 */
    public Map<String, Integer> getPoolSizes() {
        Map<String, Integer> m = new LinkedHashMap<>();
        botPools.forEach((k, pool) -> m.put(k, ((java.util.concurrent.ThreadPoolExecutor) pool).getCorePoolSize()));
        return m;
    }

    @Override
    public void destroy() {
        botPools.values().forEach(ExecutorService::shutdownNow);
        botPools.clear();
        log.info("[Pipeline] 已关闭全部事件并发池");
    }
}