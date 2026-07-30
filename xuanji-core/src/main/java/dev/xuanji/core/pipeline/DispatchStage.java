package dev.xuanji.core.pipeline;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件分发阶段 — order=60，桥接到现有 EventDispatcher（P3 过渡）。
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
        if (event.platformData() instanceof ObjectNode data) {
            // 从 QqBotWsClient 预注入的元数据中读取
            String eventType = data.path("_eventType").asText("");
            long robotId = data.path("_robotId").asLong();
            String envType = data.path("_envType").asText("PRODUCTION");
            String eventId = event.eventId();

            // 桥接到旧分发器（旧 handlers 正常工作）
            eventDispatcher.dispatch(eventType, robotId, envType, data, eventId);
        }
        return Result.CONTINUE;
    }
}
