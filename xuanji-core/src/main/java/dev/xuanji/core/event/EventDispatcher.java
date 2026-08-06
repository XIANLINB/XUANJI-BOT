package dev.xuanji.core.event;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.core.bot.BotContextManager;
import dev.xuanji.core.bot.DefaultBotContextManager;
import dev.xuanji.core.storage.MessageEventRecorder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EventDispatcher {

    private final Map<String, EventHandler> handlerMap = new HashMap<>();
    private final ObjectProvider<BotContextManager> botContextProvider;
    private final MessageEventRecorder eventRecorder;

    public EventDispatcher(List<EventHandler> handlers,
                           ObjectProvider<BotContextManager> botContextProvider,
                           MessageEventRecorder eventRecorder) {
        this.botContextProvider = botContextProvider;
        this.eventRecorder = eventRecorder;
        for (EventHandler handler : handlers) {
            EventMapping mapping = handler.getClass().getAnnotation(EventMapping.class);
            if (mapping != null) {
                for (String eventType : mapping.value()) {
                    handlerMap.put(eventType, handler);
                    log.info("[事件分发器] 注册: {} → {}", eventType, handler.getClass().getSimpleName());
                }
            }
        }
        log.info("[事件分发器] 初始化完成，共注册 {} 个事件类型", handlerMap.size());
    }

    /**
     * 将统一事件路由到对应的 Handler。
     *
     * <p>由 Pipeline 的 DispatchStage 调用（而非递归回灌 Pipeline）。
     * rawEventType 来自 BotEvent 一等字段；原始 data 仍透传供 Handler 处理平台细节。
     */
    public void dispatch(BotEvent event) {
        String eventType = event.rawEventType();
        String robotId = event.bot() != null ? event.bot().selfId() : "";
        String envType = event.envType() != null ? event.envType() : "PRODUCTION";
        if (!(event.platformData() instanceof ObjectNode data)) {
            log.warn("[事件分发] platformData 非 ObjectNode，丢弃: type={}", eventType);
            return;
        }
        log.info("[事件分发] 收到事件: type={}, robotId={}, eventId={}", eventType, robotId, event.eventId());
        eventRecorder.record(
                "LOG", "事件分发", "收到事件", robotId,
                eventType, "eventId=" + (event.eventId() != null ? event.eventId().substring(0, Math.min(20, event.eventId().length())) : ""));

        // 保留 _eventType/_eventId 元数据，兼容 Handler 内部基于 data 的读取
        data.put("_eventType", eventType);
        if (event.eventId() != null && !event.eventId().isEmpty()) data.put("_eventId", event.eventId());

        // 适配器可插拔：QQ 未启用时无 BotContextManager Bean，用无操作兜底
        BotContextManager botContext = botContextProvider.getIfAvailable(DefaultBotContextManager::new);
        botContext.setCurrentBot(robotId, envType);

        try {
            EventHandler handler = handlerMap.get(eventType);
            if (handler != null) {
                log.info("[事件分发] 找到处理器: type={}, handler={}", eventType, handler.getClass().getSimpleName());
                eventRecorder.record(
                        "LOG", "匹配处理器", handler.getClass().getSimpleName(),
                        robotId, eventType, "");
                try {
                    handler.handle(event);
                    log.info("[事件分发] 处理完成: type={}", eventType);
                    eventRecorder.record(
                            "LOG", "处理完成", eventType, robotId, "", "");
                } catch (Exception e) {
                    log.error("[事件分发] 处理异常: type={}, robotId={}, error={}", eventType, robotId, e.getMessage(), e);
                }
            } else {
                log.warn("[事件分发] 未注册的事件类型: type={}", eventType);
            }
        } finally {
            botContext.clearCurrentBot();
        }
    }
}
