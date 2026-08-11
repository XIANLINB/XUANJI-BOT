package XuanJi.api.pipeline;

import XuanJi.api.event.XuanJiEvent;

/**
 * 流水线阶段 — 消息处理的洋葱模型单元。
 *
 * <p>每个阶段实现 {@code Ordered} 接口决定执行顺序，并返回 {@link Result} 控制流转。
 *
 * <pre>
 *   preHandle  ← 进入阶段前的前置逻辑
 *      ↓
 *   handler  ← 实际处理（插件分发 / 指令执行）
 *      ↓
 *   postHandle ← 阶段完成后的后置逻辑
 * </pre>
 *
 * <p>返回 {@link Result#ABORT} 可中断后续所有阶段。
 */
public interface PipelineStage {

    /** 阶段名称 */
    String name();

    /** 阶段优先级（数值越小越先执行） */
    int order();

    /**
     * 执行本阶段。
     *
     * @param event 当前事件
     * @param chain 继续调用后续阶段的回调
     * @return 处理结果
     */
    Result handle(XuanJiEvent event, PipelineChain chain);

    /** 阶段执行结果 */
    enum Result {
        /** 继续执行后续阶段 */
        CONTINUE,
        /** 中断流水线（不再执行后续阶段） */
        ABORT
    }

    /** 提供给阶段继续调用后续的链式回调 */
    @FunctionalInterface
    interface PipelineChain {
        Result proceed();
    }
}
