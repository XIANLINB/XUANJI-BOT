package XuanJi.api.llm;

import XuanJi.api.event.XuanJiEvent;

/**
 * LLM 兜底接管判定 —— 供平台事件处理器判断「这条消息是否会被 LLM 回复」。
 *
 * <p>场景：群消息 @机器人 且未命中任何命令时，框架默认会提示「发送帮助查看可用命令」。
 * 若 LLM 功能对该群开启（且满足触发条件），应由 LLM 接管而非生硬提示帮助。
 * 实现放 llm 模块（读取 LlmConfig 判定），平台 handler 仅依赖本接口，无反向依赖。
 */
public interface LlmAvailability {

    /** 当前事件是否会被 LLM 兜底接管（enabled + 群白名单 + @ 触发 + 冷却，不含命令命中判断）。 */
    boolean available(XuanJiEvent event);
}
