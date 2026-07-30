package com.qunxing.qq_bot_xuanji.event;

import com.qunxing.qq_bot_xuanji.core.api.MessageSender;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件分发器 — 事件路由的核心组件
 *
 * <p>启动时自动扫描所有 {@link EventHandler} 实现，根据 {@link EventMapping} 注解
 * 建立事件类型到处理器的映射表。收到事件后根据事件类型路由到对应的 Handler 处理。
 *
 * <h3>机器人上下文</h3>
 * <p>分发事件前自动设置 {@link MessageSender} 的机器人上下文，
 * Handler 中使用 MessageSender 时无需手动传入 robotId/envType。
 */
@Slf4j
@Component
public class EventDispatcher {

    /** 事件类型 → 处理器 映射表 */
    private final Map<String, EventHandler> handlerMap = new HashMap<>();

    /**
     * 构造函数 — 自动扫描所有 Handler 并注册
     */
    public EventDispatcher(List<EventHandler> handlers) {
        for (EventHandler handler : handlers) {
            EventMapping mapping = handler.getClass().getAnnotation(EventMapping.class);
            if (mapping != null) {
                for (String eventType : mapping.value()) {
                    handlerMap.put(eventType, handler);
                    log.info("[事件分发器] 注册: {} → {}",
                            eventType, handler.getClass().getSimpleName());
                }
            }
        }

        log.info("[事件分发器] 初始化完成，共注册 {} 个事件类型", handlerMap.size());
    }

    /**
     * 分发事件到对应的处理器
     *
     * <p>分发前自动设置 MessageSender 的机器人上下文，
     * 处理完成后自动清理上下文。
     *
     * @param eventType 事件类型（如 C2C_MESSAGE_CREATE）
     * @param robotId   机器人 ID
     * @param envType   环境类型（SANDBOX / PRODUCTION）
     * @param data      事件数据（QQ 平台推送的 d 字段）
     * @param eventId   事件 ID（用于去重和日志）
     */
    public void dispatch(String eventType, Long robotId, String envType, JSONObject data, String eventId) {
        log.info("[事件分发] 收到事件: type={}, robotId={}, eventId={}", eventType, robotId, eventId);

        // 注入元数据
        data.put("_eventType", eventType);
        if (eventId != null && !eventId.isEmpty()) {
            data.put("_eventId", eventId);
        }

        // 设置机器人上下文（供 MessageSender 使用）
        MessageSender.setCurrentContext(robotId, envType);

        try {
            EventHandler handler = handlerMap.get(eventType);
            if (handler != null) {
                log.info("[事件分发] 找到处理器: type={}, handler={}", eventType, handler.getClass().getSimpleName());
                try {
                    handler.handle(robotId, envType, data);
                    log.info("[事件分发] 处理完成: type={}", eventType);
                } catch (Exception e) {
                    log.error("[事件分发] 处理异常: type={}, robotId={}, error={}",
                            eventType, robotId, e.getMessage(), e);
                }
            } else {
                log.warn("[事件分发] 未注册的事件类型: type={}", eventType);
            }
        } finally {
            // 清理机器人上下文
            MessageSender.clearCurrentContext();
        }
    }
}
