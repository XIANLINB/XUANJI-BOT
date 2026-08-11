package XuanJi.llm.provider;

import XuanJi.api.llm.*;
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
 * 智谱 GLM 供应商 —— OpenAI 兼容协议 + 多模态（图片理解）。
 *
 * <p>GLM-4V-Flash 为智谱官方<b>免费</b>视觉模型，用于 P4 图片理解。
 * chat 能力与 DeepSeek 同协议族（可互相复用）；vision 走 OpenAI 兼容
 * {@code content: [{type:text},{type:image_url}]} 格式。
 */
@Component
public class GlmProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String DEFAULT_MODEL = "glm-4.6v-flash";

    @Override public String id() { return "glm"; }
    @Override public String displayName() { return "智谱 GLM"; }
    @Override public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT, LlmCapability.IMAGE_UNDERSTAND, LlmCapability.IMAGE_GEN);
    }
    @Override public String defaultModel() { return DEFAULT_MODEL; }

    @Override
    public String chat(List<LlmMessage> messages, LlmChatOptions options, LlmCredentials credentials) {
        RestClient client = buildClient(credentials);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(options));
        body.put("messages", messages.stream().map(m -> {
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", m.role());
            msg.put("content", m.content());
            return msg;
        }).toList());
        body.put("temperature", options != null && options.temperature() != null ? options.temperature() : 0.7);
        if (options != null && options.maxTokens() != null) body.put("max_tokens", options.maxTokens());
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
        return extractContent(resp);
    }

    @Override
    public String vision(String imageUrl, String prompt, LlmChatOptions options, LlmCredentials credentials) {
        RestClient client = buildClient(credentials);
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt == null ? "描述这张图片" : prompt));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(options));
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
        return extractContent(resp);
    }

    /**
     * 文生图 —— 智谱 CogView-3-Flash（免费）。
     * OpenAI 兼容 POST /images/generations：{model, prompt, size} → data[0].url（公网临时 URL）。
     */
    @Override
    @SuppressWarnings("unchecked")
    public String imageGen(String prompt, LlmChatOptions options, LlmCredentials credentials) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("图片生成描述不能为空");
        }
        RestClient client = buildClient(credentials);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(options));
        body.put("prompt", prompt);
        body.put("size", "1024x1024");
        Map<String, Object> resp = client.post().uri("/images/generations").body(body).retrieve().body(Map.class);
        if (resp == null) throw new IllegalStateException("图像生成响应为空");
        Object dataObj = resp.get("data");
        if (dataObj instanceof List<?> data && !data.isEmpty()) {
            Object first = data.get(0);
            if (first instanceof Map<?, ?> m) {
                Object url = m.get("url");
                if (url != null) {
                    return String.valueOf(url);
                }
            }
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) throw new IllegalStateException("图像生成失败: " + em.get("message"));
        throw new IllegalStateException("图像生成响应缺少 data[0].url");
    }

    // ──────────── 公共 ────────────

    private String resolveModel(LlmChatOptions options) {
        return options != null && options.model() != null && !options.model().isBlank()
                ? options.model() : DEFAULT_MODEL;
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
