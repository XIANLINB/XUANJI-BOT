package XuanJi.llm.provider;

import XuanJi.api.llm.LlmCapability;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmCredentials;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI 通用兼容供应商 —— 修复「前端可配 openai 类型但运行时不认识」的问题。
 *
 * <p>任何标准 OpenAI 协议服务商（OpenAI / 智谱 GLM 兼容 / 通义千问兼容 / Kimi /
 * Ollama 网关 / 本地代理等）都可以用本类型，配好 baseUrl + apiKey + 模型即可对话
 * （含 Function Calling / 工具调用，复用 {@link DeepSeekProvider} 的完整实现）。
 *
 * <p>额外开放 {@link LlmCapability#IMAGE_UNDERSTAND}：OpenAI 兼容协议原生支持
 * {@code content:[{type:image_url}]} 多模态，因此把 GLM 等视觉模型挂在本类型下也能
 * 正常图理解（之前因本类型未实现 vision() 而统一抛「供应商不支持图片理解」）。
 * 具体能否成功取决于该端点背后的模型是否真的支持视觉，由运行时 API 如实反馈。
 */
@Component
public class OpenAiCompatibleProvider extends DeepSeekProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String displayName() {
        return "OpenAI 兼容";
    }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT, LlmCapability.IMAGE_UNDERSTAND);
    }

    @Override
    public String defaultModel() {
        // 通用兼容供应商无固定默认模型：用户必须在能力选择里绑定具体模型
        return "";
    }

    /**
     * 图片理解：OpenAI 兼容协议 {@code content:[{type:text},{type:image_url}]}，
     * 与 {@code GlmProvider} 同协议族。
     */
    @Override
    public String vision(String imageUrl, String prompt, LlmChatOptions options, LlmCredentials credentials) {
        RestClient client = buildClient(credentials);
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt == null || prompt.isBlank() ? "描述这张图片" : prompt));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(options));
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
        return extractContent(resp);
    }

    private String resolveModel(LlmChatOptions options) {
        return options != null && options.model() != null && !options.model().isBlank()
                ? options.model() : "";
    }

    private RestClient buildClient(LlmCredentials credentials) {
        String baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? DEFAULT_BASE_URL : credentials.baseUrl();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(jdkFactory())
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("LLM 响应为空");
        Object choices = resp.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg && msg.get("content") != null) {
                    return String.valueOf(msg.get("content"));
                }
            }
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) throw new IllegalStateException("LLM 调用失败: " + em.get("message"));
        throw new IllegalStateException("LLM 响应缺少 choices[0].message.content");
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(30));
        return f;
    }
}
