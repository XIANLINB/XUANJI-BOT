package XuanJi.api.llm;

/**
 * LLM 工具调用请求 —— 模型返回的"要调用某个工具"的指令。
 */
public record LlmToolCall(
        /** 调用 id（OpenAI 协议 tool_call.id，回填结果时原样带回） */
        String id,

        /** 工具名（对应 {@link LlmToolDefinition#name()}） */
        String name,

        /** 参数字符串（JSON），由调用方解析 */
        String arguments
) {
}
