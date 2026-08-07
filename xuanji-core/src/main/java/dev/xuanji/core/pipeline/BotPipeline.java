package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.metric.QpsMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BotPipeline — 按 Ordered 顺序执行所有注册的阶段。
 *
 * <p>阶段通过构造器注入（Spring 自动收集所有 PipelineStage Bean），
 * 按 {@code order()} 升序排列后依次执行。任一阶段返回 ABORT 则停止。
 */
@Slf4j
@Component
public class BotPipeline {

    private final List<PipelineStage> stages;
    private final AtomicLong processedCount = new AtomicLong();
    private final Map<String, AtomicInteger> slowStageCounts = new ConcurrentHashMap<>();
    private static final long SLOW_THRESHOLD_MS = 100;

    public BotPipeline(List<PipelineStage> stages) {
        this.stages = stages.stream()
                .sorted(Comparator.comparingInt(PipelineStage::order))
                .toList();
        // 慢阶段计数预初始化全部阶段为 0（前端期望全量阶段展示，未慢过也显示 0）
        for (PipelineStage s : this.stages) {
            slowStageCounts.putIfAbsent(s.name(), new AtomicInteger());
        }
        log.info("[Pipeline] 初始化完成，阶段链: {}",
                this.stages.stream().map(s -> s.order() + ":" + s.name()).toList());
    }

    /**
     * 将事件送入流水线处理。
     *
     * @param event 已转换的统一事件
     * @return 整个流水线执行的结果（目前仅用于日志）
     */
    public void proceed(BotEvent event) {
        processedCount.incrementAndGet();
        QpsMeter.hit();
        doProceed(event, 0);
    }

    private void doProceed(BotEvent event, int index) {
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

    /** 慢阶段计数（控制台 /console/health 的 pipelineSlowStages 键）。 */
    public Map<String, Object> getSlowStageCounts() {
        Map<String, Object> m = new LinkedHashMap<>();
        slowStageCounts.forEach((k, v) -> m.put(k, v.get()));
        return m;
    }
}
