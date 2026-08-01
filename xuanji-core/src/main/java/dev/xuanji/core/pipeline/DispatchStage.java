package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件分发阶段 — order=60。
 *
 * <p>将统一事件桥接到具体 Handler（不回灌 Pipeline，避免递归）。
 * rawEventType / envType 直接取自 BotEvent 一等字段，不再依赖被污染的原始 data。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchStage implements PipelineStage {

    private final EventDispatcher eventDispatcher;

    @Override public String name() { return "dispatch"; }
    @Override public int order() { return 60; }

    @Override
    public Result handle(BotEvent event, PipelineChain chain) {
        // 桥接：将统一事件路由到具体 Handler（不回灌 Pipeline，避免递归）
        eventDispatcher.dispatch(event);
        return Result.CONTINUE;
    }
}
