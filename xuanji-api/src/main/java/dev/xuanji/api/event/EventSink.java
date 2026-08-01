package dev.xuanji.api.event;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 事件入口 — 接入层（适配器）向框架核心投递事件的通道
 *
 * <p>核心模块提供实现（事件分发器），适配器只依赖本接口，
 * 以此打破 适配器 ↔ 核心 的模块循环依赖。
 *
 * <p>后续版本演进：事件对象将由 ObjectNode 替换为统一的 BotEvent 模型（P1 抽象）。
 */
public interface EventSink {

    /**
     * 投递一个平台事件
     *
     * @param eventType 平台事件类型（如 GROUP_MESSAGE_CREATE）
     * @param robotId   机器人 ID
     * @param envType   环境类型（SANDBOX / PRODUCTION）
     * @param data      事件数据（平台推送的 d 字段）
     * @param eventId   事件 ID（用于去重和日志），可为 null
     */
    void dispatch(String eventType, String robotId, String envType, ObjectNode data, String eventId);
}
