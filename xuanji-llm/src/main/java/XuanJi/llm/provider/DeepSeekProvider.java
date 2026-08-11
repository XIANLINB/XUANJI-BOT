package XuanJi.llm.provider;

import XuanJi.api.llm.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * DeepSeek 供应商 —— OpenAI 兼容协议实现。
 *
 * <p>走标准 {@code POST {baseUrl}/chat/completions} + Bearer 认证；
 * 同一个协议族（GLM/通义/Kimi 等）后续可复制本实现换 id/默认模型即接入。
 * 使用 Spring 内建 RestClient + JDK HttpClient，零额外依赖。
 */
@Component
public class DeepSeekProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    @Override
    public String id() {
        return "deepseek";
    }

    @Override
    public String displayName() {
        return "DeepSeek";
    }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT);
    }

    @Override
    public String defaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    public String chat(List<LlmMessage> messages, LlmChatOptions options, LlmCredentials credentials) {
        requireMessages(messages);
        RestClient client = buildClient(credentials);
        Map<String, Object> body = buildRequestBody(messages, options, resolveModel(options), false);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
        return extractContent(resp);
    }

    @Override
    public LlmChatResponse chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools,
                                         LlmChatOptions options, LlmCredentials credentials) {
        requireMessages(messages);
        RestClient client = buildClient(credentials);
        // 外部传入的 tools 优先并入选项
        List<LlmToolDefinition> effectiveTools = tools != null && !tools.isEmpty() ? tools
                : (options != null ? options.tools() : null);
        LlmChatOptions effective = new LlmChatOptions(
                options != null ? options.model() : null,
                options != null ? options.temperature() : null,
                options != null ? options.maxTokens() : null,
                effectiveTools);
        Map<String, Object> body = buildRequestBody(messages, effective, resolveModel(options), false);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
        return extractResponse(resp);
    }

    @Override
    public void chatStream(List<LlmMessage> messages, LlmChatOptions options,
                           LlmCredentials credentials, Consumer<String> onDelta) {
        requireMessages(messages);
        RestClient client = buildClient(credentials);
        Map<String, Object> body = buildRequestBody(messages, options, resolveModel(options), true);
        try {
            client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() >= 400) {
                            String err = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new IllegalStateException(
                                    "LLM 流式调用失败(HTTP " + response.getStatusCode().value() + "): " + err);
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;
                                String payload = line.substring("data:".length()).trim();
                                if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                                try {
                                    JsonNode node = MAPPER.readTree(payload);
                                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                                    if (!delta.isMissingNode() && !delta.isNull()) {
                                        String piece = delta.asText();
                                        if (!piece.isEmpty()) onDelta.accept(piece);
                                    }
                                } catch (Exception ignored) {
                                    // 忽略无法解析的 SSE 片段（如 keep-alive 注释）
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            throw new IllegalStateException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    private void requireMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("对话消息不能为空");
        }
    }

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

    private Map<String, Object> buildRequestBody(List<LlmMessage> messages, LlmChatOptions options,
                                                 String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(this::toProtocolMessage).toList());
        body.put("temperature", options != null && options.temperature() != null
                ? options.temperature() : 0.7);
        if (options != null && options.maxTokens() != null) {
            body.put("max_tokens", options.maxTokens());
        }
        if (options != null && options.tools() != null && !options.tools().isEmpty()) {
            body.put("tools", options.tools().stream().map(this::toProtocolTool).toList());
        }
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /** LlmMessage → OpenAI 协议消息；识别 [TOOL_CALLS]/[TOOL_RESULT] 编码还原 tool_calls / tool 角色。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toProtocolMessage(LlmMessage m) {
        String content = m.content();
        if (content != null && content.startsWith(LlmMessage.TOOL_CALLS_PREFIX)) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "assistant");
            msg.put("content", null);
            try {
                JsonNode arr = MAPPER.readTree(content.substring(LlmMessage.TOOL_CALLS_PREFIX.length()));
                List<Map<String, Object>> calls = new java.util.ArrayList<>();
                if (arr.isArray()) {
                    for (JsonNode c : arr) {
                        Map<String, Object> call = new LinkedHashMap<>();
                        call.put("id", c.path("id").asText());
                        call.put("type", "function");
                        Map<String, Object> fn = new LinkedHashMap<>();
                        fn.put("name", c.path("name").asText());
                        fn.put("arguments", c.path("arguments").toString());
                        call.put("function", fn);
                        calls.add(call);
                    }
                }
                msg.put("tool_calls", calls);
            } catch (Exception ignored) {
                // 解析失败按普通文本处理
            }
            return msg;
        }
        if (content != null && content.startsWith(LlmMessage.TOOL_RESULT_PREFIX)) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "tool");
            try {
                JsonNode obj = MAPPER.readTree(content.substring(LlmMessage.TOOL_RESULT_PREFIX.length()));
                msg.put("tool_call_id", obj.path("id").asText());
                msg.put("content", obj.path("result").asText());
            } catch (Exception ignored) {
                msg.put("content", content);
            }
            return msg;
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", m.role());
        msg.put("content", m.content());
        return msg;
    }

    /** LlmToolDefinition → OpenAI tools 协议函数。 */
    private Map<String, Object> toProtocolTool(LlmToolDefinition t) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("type", "function");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", t.name());
        func.put("description", t.description());
        func.put("parameters", t.parameters());
        fn.put("function", func);
        return fn;
    }

    /** 从 OpenAI 兼容响应中提取回复文本。 */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) {
            throw new IllegalStateException("LLM 响应为空");
        }
        Object choicesObj = resp.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object messageObj = choice.get("message");
                if (messageObj instanceof Map<?, ?> message) {
                    Object content = message.get("content");
                    if (content != null) {
                        return String.valueOf(content);
                    }
                }
            }
        }
        // 错误响应（如 key 无效）走 error.message
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) {
            throw new IllegalStateException("LLM 调用失败: " + em.get("message"));
        }
        throw new IllegalStateException("LLM 响应缺少 choices[0].message.content");
    }

    /** 提取结构化响应（正文 + 工具调用请求 + usage）。 */
    @SuppressWarnings("unchecked")
    private LlmChatResponse extractResponse(Map<String, Object> resp) {
        if (resp == null) {
            throw new IllegalStateException("LLM 响应为空");
        }
        Object choicesObj = resp.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object messageObj = choice.get("message");
                if (messageObj instanceof Map<?, ?> message) {
                    String content = message.get("content") != null ? String.valueOf(message.get("content")) : null;
                    List<LlmToolCall> calls = parseToolCalls(message.get("tool_calls"));
                    long[] usage = parseUsage(resp.get("usage"));
                    return new LlmChatResponse(content, calls, usage[0], usage[1]);
                }
            }
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) {
            throw new IllegalStateException("LLM 调用失败: " + em.get("message"));
        }
        throw new IllegalStateException("LLM 响应缺少 choices[0].message");
    }

    /** 解析 usage.prompt_tokens / completion_tokens；无则返回 {0,0}。 */
    private long[] parseUsage(Object usageObj) {
        if (usageObj instanceof Map<?, ?> usage) {
            long prompt = num(usage.get("prompt_tokens"));
            long completion = num(usage.get("completion_tokens"));
            return new long[]{prompt, completion};
        }
        return new long[]{0, 0};
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private List<LlmToolCall> parseToolCalls(Object toolCallsObj) {
        if (!(toolCallsObj instanceof List<?> list) || list.isEmpty()) return null;
        List<LlmToolCall> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> call)) continue;
            String id = call.get("id") != null ? String.valueOf(call.get("id")) : "";
            Object fnObj = call.get("function");
            String name = "";
            String args = "{}";
            if (fnObj instanceof Map<?, ?> fm) {
                name = fm.get("name") != null ? String.valueOf(fm.get("name")) : "";
                Object a = fm.get("arguments");
                if (a != null) args = String.valueOf(a);
            }
            out.add(new LlmToolCall(id, name, args));
        }
        return out.isEmpty() ? null : out;
    }

    private JdkClientHttpRequestFactory jdkFactory() {
        // connect 超时由 JDK HttpClient 承载；read 超时在工厂层设置
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(30));
        return f;
    }
}
