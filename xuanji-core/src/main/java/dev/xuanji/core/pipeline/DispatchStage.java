package dev.xuanji.core.pipeline;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件分发阶段 — order=60。
 *
 * <p>先尝试 @Command 指令匹配；未匹配时桥接回旧 EventDispatcher 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchStage implements PipelineStage {

    private final EventDispatcher eventDispatcher;
    private final CommandRegistry commandRegistry;

    @Override public String name() { return "dispatch"; }
    @Override public int order() { return 60; }

    @Override
    public Result handle(BotEvent event, PipelineChain chain) {
        // 先尝试 @Command 指令匹配
        if (event.message() != null) {
            String text = event.message().plainText();
            String result = commandRegistry.execute(text);
            if (result != null) {
                log.info("[Pipeline] @Command: {} → {}", text, result);
                return Result.CONTINUE;
            }
        }

        // 未匹配 → 桥接到旧分发器
        if (event.platformData() instanceof ObjectNode data) {
            String eventType = data.path("_eventType").asText("");
            long robotId = data.path("_robotId").asLong();
            String envType = data.path("_envType").asText("PRODUCTION");
            eventDispatcher.dispatch(eventType, robotId, envType, data, event.eventId());
        }
        return Result.CONTINUE;
    }
}
