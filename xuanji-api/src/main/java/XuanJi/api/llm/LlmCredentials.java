package XuanJi.api.llm;

/**
 * 供应商接入凭据 —— 调用 {@link LlmProvider} 时由框架（LlmService）组装传入。
 *
 * <p>设计目的：Provider 保持无状态 Bean，不持有配置；baseUrl/apiKey 来自
 * {@code xuanji_llm_config}（或未来 bot/group 级覆盖），由 LlmService 在调用时传入。
 * 插件实现的第三方 Provider 同样遵循本签名，无需感知框架配置存储。
 */
public record LlmCredentials(
        /** 兼容协议基地址，如 https://api.deepseek.com */
        String baseUrl,
        /** API Key（Bearer 认证） */
        String apiKey
) {
}
