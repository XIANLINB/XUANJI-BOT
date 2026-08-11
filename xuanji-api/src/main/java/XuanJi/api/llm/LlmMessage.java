package XuanJi.api.llm;

/**
 * LLM 对话消息 —— 与 OpenAI 兼容协议的消息模型一一对应。
 *
 * <p>role 取值沿用协议惯例：{@code system}（系统提示词/人格）、{@code user}、
 * {@code assistant}、{@code tool}（工具调用结果，P2 Agent 启用）。
 */
public record LlmMessage(String role, String content) {

    /** 工具调用协议前缀：assistant 消息 content 以该前缀开头表示"模型请求调用工具"，后接 JSON 数组 */
    public static final String TOOL_CALLS_PREFIX = "[TOOL_CALLS]";
    /** 工具结果协议前缀：tool 消息 content 以该前缀开头，后接 JSON 对象 {"id":"调用id","result":"执行结果"} */
    public static final String TOOL_RESULT_PREFIX = "[TOOL_RESULT]";

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }

    /** 工具调用结果（P2 起使用）。 */
    public static LlmMessage tool(String content) {
        return new LlmMessage("tool", content);
    }
}
