package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

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

    public BotPipeline(List<PipelineStage> stages) {
        this.stages = stages.stream()
                .sorted(Comparator.comparingInt(PipelineStage::order))
                .toList();
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
        doProceed(event, 0);
    }

    private void doProceed(BotEvent event, int index) {
        if (index >= stages.size()) {
            return;
        }
        PipelineStage stage = stages.get(index);
        long start = System.currentTimeMillis();
        try {
            PipelineStage.Result result = stage.handle(event, () -> {
                doProceed(event, index + 1);
                return PipelineStage.Result.CONTINUE;
            });
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 100) {
                log.warn("[Pipeline] 慢阶段: {} ({}ms)", stage.name(), elapsed);
            }
            if (result == PipelineStage.Result.ABORT) {
                log.debug("[Pipeline] {} 中断流水线", stage.name());
            }
        } catch (Exception e) {
            log.error("[Pipeline] {} 执行异常: {}", stage.name(), e.getMessage(), e);
        }
    }
}
