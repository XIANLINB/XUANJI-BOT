package XuanJi.core.pipeline;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.pipeline.PipelineStage;
import XuanJi.core.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件分发阶段 — order=60。
 *
 * <p>将统一事件桥接到具体 Handler（不回灌 Pipeline，避免递归）。
 * rawEventType / envType 直接取自 XuanJiEvent 一等字段，不再依赖被污染的原始 data。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchStage implements PipelineStage {

    private final EventDispatcher eventDispatcher;

    @Override public String name() { return "dispatch"; }
    @Override public int order() { return 60; }

    @Override
    public Result handle(XuanJiEvent event, PipelineChain chain) {
        // 桥接：将统一事件路由到具体 Handler（不回灌 Pipeline，避免递归）
        eventDispatcher.dispatch(event);
        // 关键：必须继续后续阶段（result-decorate 70 / respond 80 / llm-chat 85 等）。
        // 若直接 return CONTINUE 不调 chain.proceed()，管线在 60 处截断，
        // 后续阶段将永不执行（LlmChatStage 即因此一直未触发）。
        return chain.proceed();
    }
}
