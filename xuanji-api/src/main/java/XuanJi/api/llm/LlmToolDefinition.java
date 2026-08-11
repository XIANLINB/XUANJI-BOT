package XuanJi.api.llm;

import java.util.Map;

/**
 * LLM 工具定义 —— 暴露给大模型做 Function Calling 的函数元数据（OpenAI tools 协议）。
 *
 * <p>{@link #parameters} 为 JSON Schema 风格的 {@code {"type":"object","properties":{...},"required":[...]}}，
 * 由 ToolRegistry 从方法签名与 {@link LlmToolParam} 自动生成。
 */
public record LlmToolDefinition(
        /** 工具名（模型调用标识，建议简短，如 get_time） */
        String name,

        /** 工具用途描述，模型据此决定何时调用 */
        String description,

        /** 中文释义（仅控制台展示，不发给模型） */
        String descriptionZh,

        /** 参数 JSON Schema（OpenAI 风格） */
        Map<String, Object> parameters,

        /** 是否要求用户显式确认后执行（危险操作 = true） */
        boolean confirm,

        /** 来源描述（内置 / 插件 xx / 模块名），用于前端工具清单展示 */
        String source
) {
}
