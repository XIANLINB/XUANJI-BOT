package XuanJi.api.llm;

import java.util.List;

/**
 * LLM 对话响应 —— 支持工具调用场景的结构化返回。
 *
 * <p>无工具调用时 {@link #toolCalls()} 为 null/空，{@link #content()} 即最终回复；
 * 有工具调用时 {@link #content()} 可能为 null，需把 toolCalls 逐一执行后再回填。
 *
 * <p>{@code promptTokens / completionTokens} 来自供应商响应的 {@code usage} 字段，
 * 供用量统计精确记录（非字符估算）。供应商未返回时为 0。
 */
public record LlmChatResponse(
        /** 模型生成的文本内容（可能为 null，当次只发起了工具调用） */
        String content,

        /** 模型发起的工具调用列表（无则为 null/空） */
        List<LlmToolCall> toolCalls,

        /** 本次请求消耗的 prompt token 数（usage.prompt_tokens；未知为 0） */
        long promptTokens,

        /** 本次请求消耗的 completion token 数（usage.completion_tokens；未知为 0） */
        long completionTokens
) {

    /** 兼容旧构造：无 usage 信息（用量统计回退估算）。 */
    public LlmChatResponse(String content, List<LlmToolCall> toolCalls) {
        this(content, toolCalls, 0, 0);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 总 token 数（可能为 0 = 未知）。 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
