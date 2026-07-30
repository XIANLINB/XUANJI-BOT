package dev.xuanji.api.capability;

/**
 * LLM 服务接口 — 核心无 LLM 依赖，实现由插件提供（如 xuanji-plugin-llm）。
 */
public interface LlmService {

    /** 发起一次聊天请求 */
    ChatResponse chat(ChatRequest request);

    /** 注册一个 LLM 工具（Function Calling） */
    void registerTool(ToolDefinition tool);

    record ChatRequest(String prompt, String model, String systemPrompt) {}
    record ChatResponse(String content, int tokensUsed) {}
    record ToolDefinition(String name, String description, String schema) {}
}
