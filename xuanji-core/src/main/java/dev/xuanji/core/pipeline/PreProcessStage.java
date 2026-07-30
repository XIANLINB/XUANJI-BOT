package dev.xuanji.core.pipeline;

import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预处理阶段 — order=50。
 *
 * <p>设置 BotContext（ScopedValue 事件上下���）；
 * 后续阶段和插件可通过 {@link BotContext#current()} 获取当前事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreProcessStage implements PipelineStage {

    @Override
    public String name() { return "pre-process"; }

    @Override
    public int order() { return 50; }

    @Override
    public Result handle(BotEvent event, PipelineChain chain) {
        return ScopedValue.where(BotContext.currentEvent, event)
                .call(() -> chain.proceed());
    }
}
