package XuanJi.api.llm;

import java.util.List;
import java.util.Set;

/**
 * LLM 供应商 SPI —— 璇玑对「大模型服务商」的统一抽象。
 *
 * <p>实现方（DeepSeek / OpenAI / GLM / Ollama ...）各自注册为 Spring Bean，
 * 由 {@code LlmProviderRegistry} 按 {@link #id()} 索引，控制台与运行时按 id 选取。
 * 插件也可以实现本接口注册自定义供应商（SPI 放 api 层的目的）。
 *
 * <p>P0 只约定文本对话能力；图片/语音/Embedding 等通过 {@link #capabilities()}
 * 声明可用性，方法本身随阶段补充（保持接口演进不破坏实现方）。
 */
public interface LlmProvider {

    /** 供应商唯一 id，如 {@code deepseek} / {@code openai} / {@code ollama}，配置中存储该值。 */
    String id();

    /** 展示名，控制台下拉框显示。 */
    String displayName();

    /** 支持的能力位，用于控制台能力矩阵与功能可用性判断。 */
    Set<LlmCapability> capabilities();

    /** 供应商默认对话模型（如 deepseek-chat）。 */
    String defaultModel();

    /**
     * 一次性文本对话。
     *
     * @param messages    完整对话上下文（含 system 人格提示词）
     * @param options     生成参数；null 字段用供应商默认
     * @param credentials 接入凭据（baseUrl/apiKey），由调用方从配置组装
     * @return 模型回复文本
     */
    String chat(List<LlmMessage> messages, LlmChatOptions options, LlmCredentials credentials);

    /**
     * 流式对话 —— 逐段回调增量文本（用于控制台 SSE / 群聊分句）。
     *
     * <p>默认实现退回一次性 {@link #chat} 并整体回调一次；支持流式的供应商
     * （如 DeepSeek）应覆盖本方法实现真流式（SSE 解析）。
     *
     * @param onDelta 每个增量文本片段回调（调用线程保证串行）
     */
    default void chatStream(List<LlmMessage> messages, LlmChatOptions options,
                            LlmCredentials credentials, java.util.function.Consumer<String> onDelta) {
        String text = chat(messages, options, credentials);
        if (text != null) {
            onDelta.accept(text);
        }
    }

    /**
     * 带 Function Calling 的对话 —— 返回结构化结果（正文 + 工具调用请求）。
     *
     * <p>默认实现忽略 {@code tools}，直接调用 {@link #chat} 并返回纯文本响应。
     * 支持工具调用的供应商（如 DeepSeek tools 协议）应覆盖本方法：请求体带
     * {@code tools}，解析响应中的 {@code tool_calls}。
     *
     * @param messages    完整对话上下文（含 system / user / 已执行的 tool 结果）
     * @param tools       可用工具定义（null/空 = 无工具）
     * @param options     生成参数；null 字段用供应商默认
     * @param credentials 接入凭据（baseUrl/apiKey）
     * @return 结构化响应：{@link LlmChatResponse#content()} 为正文（可空），
     *         {@link LlmChatResponse#toolCalls()} 为模型要调用的工具列表
     */
    default LlmChatResponse chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools,
                                          LlmChatOptions options, LlmCredentials credentials) {
        return new LlmChatResponse(chat(messages, options, credentials), null);
    }

    /**
     * 图片理解（多模态）—— 输入图片 URL + 提问，返回模型对图片的描述/回答。
     *
     * <p>默认实现抛 {@link UnsupportedOperationException}；支持多模态的供应商
     * （如智谱 GLM-4V）应覆盖本方法。
     *
     * @param imageUrl    图片 URL（公网可访问）
     * @param prompt      关于图片的问题，如「描述这张图片的内容」
     * @param options     生成参数；null 用供应商默认
     * @param credentials 接入凭据（baseUrl/apiKey）
     * @return 模型返回的文本
     */
    default String vision(String imageUrl, String prompt, LlmChatOptions options, LlmCredentials credentials) {
        throw new UnsupportedOperationException("供应商 " + id() + " 不支持图片理解");
    }

    /**
     * 图片生成（文生图）—— 返回生成的图片公网 URL（或 base64 data URI）。
     *
     * <p>默认实现抛 {@link UnsupportedOperationException}；支持图像生成的供应商
     * （如智谱 CogView-3-Flash）应覆盖本方法。
     *
     * @param prompt      文生图描述
     * @param options     生成参数（模型名在 model 字段；null 用供应商默认）
     * @param credentials 接入凭据（baseUrl/apiKey）
     * @return 生成的图片公网 URL（或 data URI）
     */
    default String imageGen(String prompt, LlmChatOptions options, LlmCredentials credentials) {
        throw new UnsupportedOperationException("供应商 " + id() + " 不支持图片生成");
    }
}
