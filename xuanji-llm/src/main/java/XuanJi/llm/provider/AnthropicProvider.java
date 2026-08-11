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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Anthropic 兼容供应商 —— 标准 Anthropic Messages API（Claude）实现。
 *
 * <p>协议要点：
 * <ul>
 *   <li>端点 {@code POST {baseUrl}/v1/messages}（兼容已带 /v1 的 baseUrl）</li>
 *   <li>认证 {@code x-api-key} + {@code anthropic-version: 2023-06-01}</li>
 *   <li>system 提示词放请求顶层 {@code system} 字段（非 messages 内）</li>
 *   <li>工具调用：请求 {@code tools:[{name,input_schema}]}；响应 content 里
 *       {@code type:"tool_use"}（id/name/input）；工具结果以 {@code tool_result}
 *       content block 回填到 user 消息</li>
 *   <li>用量：{@code usage.input_tokens / output_tokens}</li>
 * </ul>
 */
@Component
public class AnthropicProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public String displayName() {
        return "Anthropic 兼容";
    }

    @Override
    public Set<LlmCapability> capabilities() {
        return Set.of(LlmCapability.CHAT);
    }

    @Override
    public String defaultModel() {
        return "";
    }

    @Override
    public String chat(List<LlmMessage> messages, LlmChatOptions options, LlmCredentials credentials) {
        requireMessages(messages);
        RestClient client = buildClient(credentials);
        Map<String, Object> body = buildRequestBody(messages, options, resolveModel(options), null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post()
                .uri(endpoint(credentials))
                .body(body)
                .retrieve()
                .body(Map.class);
        return extractText(resp);
    }

    @Override
    public LlmChatResponse chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools,
                                         LlmChatOptions options, LlmCredentials credentials) {
        requireMessages(messages);
        RestClient client = buildClient(credentials);
        List<LlmToolDefinition> effectiveTools = tools != null && !tools.isEmpty() ? tools
                : (options != null ? options.tools() : null);
        Map<String, Object> body = buildRequestBody(messages, options, resolveModel(options), effectiveTools, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post()
                .uri(endpoint(credentials))
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
        Map<String, Object> body = buildRequestBody(messages, options, resolveModel(options), null, true);
        try {
            client.post()
                    .uri(endpoint(credentials))
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
                                    JsonNode type = node.path("type");
                                    if ("content_block_delta".equals(type.asText())) {
                                        JsonNode delta = node.path("delta");
                                        if ("text_delta".equals(delta.path("type").asText())) {
                                            String piece = delta.path("text").asText("");
                                            if (!piece.isEmpty()) onDelta.accept(piece);
                                        }
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

    // ──────────── 私有：请求组装 ────────────

    private void requireMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("对话消息不能为空");
        }
    }

    private String resolveModel(LlmChatOptions options) {
        String model = options != null && options.model() != null && !options.model().isBlank()
                ? options.model() : defaultModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("Anthropic 兼容供应商未指定模型：请到「能力选择」给 CHAT 绑定模型");
        }
        return model;
    }

    private String endpoint(LlmCredentials credentials) {
        String baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? DEFAULT_BASE_URL : credentials.baseUrl().trim();
        return baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
                ? "/messages" : "/v1/messages";
    }

    private RestClient buildClient(LlmCredentials credentials) {
        String baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? DEFAULT_BASE_URL : credentials.baseUrl();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", credentials.apiKey() == null ? "" : credentials.apiKey())
                .defaultHeader("anthropic-version", API_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(jdkFactory())
                .build();
    }

    private Map<String, Object> buildRequestBody(List<LlmMessage> messages, LlmChatOptions options,
                                                 String model, List<LlmToolDefinition> tools, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        // max_tokens 必填（Anthropic 强制）
        body.put("max_tokens", options != null && options.maxTokens() != null
                ? options.maxTokens() : 2048);
        if (options != null && options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        String system = collectSystem(messages);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.put("messages", toProtocolMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::toProtocolTool).toList());
        }
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /** 收集所有 system 消息拼接为顶层 system 字段（Anthropic 协议要求）。 */
    private String collectSystem(List<LlmMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (LlmMessage m : messages) {
            if ("system".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(m.content().trim());
            }
        }
        return sb.toString();
    }

    /** LlmMessage → Anthropic messages（含 tool_use / tool_result content block 还原）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toProtocolMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LlmMessage m : messages) {
            String role = m.role();
            if ("system".equals(role)) continue; // system 已合并到顶层
            String content = m.content();
            if (content != null && content.startsWith(LlmMessage.TOOL_CALLS_PREFIX)) {
                // assistant 工具调用请求 → tool_use blocks
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "assistant");
                List<Map<String, Object>> blocks = new ArrayList<>();
                try {
                    JsonNode arr = MAPPER.readTree(content.substring(LlmMessage.TOOL_CALLS_PREFIX.length()));
                    if (arr.isArray()) {
                        for (JsonNode c : arr) {
                            Map<String, Object> block = new LinkedHashMap<>();
                            block.put("type", "tool_use");
                            block.put("id", c.path("id").asText());
                            block.put("name", c.path("name").asText());
                            try {
                                JsonNode args = MAPPER.readTree(c.path("arguments").asText("{}"));
                                block.put("input", args);
                            } catch (Exception ignored) {
                                block.put("input", Map.of());
                            }
                            blocks.add(block);
                        }
                    }
                } catch (Exception ignored) {
                    // 解析失败按普通文本
                }
                if (blocks.isEmpty()) {
                    msg.put("content", content);
                } else {
                    msg.put("content", blocks);
                }
                out.add(msg);
                continue;
            }
            if (content != null && content.startsWith(LlmMessage.TOOL_RESULT_PREFIX)) {
                // 工具执行结果 → user + tool_result block
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "user");
                try {
                    JsonNode obj = MAPPER.readTree(content.substring(LlmMessage.TOOL_RESULT_PREFIX.length()));
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", obj.path("id").asText());
                    block.put("content", obj.path("result").asText());
                    msg.put("content", List.of(block));
                } catch (Exception ignored) {
                    msg.put("content", content);
                }
                out.add(msg);
                continue;
            }
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role);
            msg.put("content", content == null ? "" : content);
            out.add(msg);
        }
        return out;
    }

    /** LlmToolDefinition → Anthropic tools 协议。 */
    private Map<String, Object> toProtocolTool(LlmToolDefinition t) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", t.name());
        tool.put("description", t.description());
        // Anthropic 用 input_schema（OpenAI 的 parameters 结构一致）
        tool.put("input_schema", t.parameters());
        return tool;
    }

    // ──────────── 私有：响应解析 ────────────

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("LLM 响应为空");
        Object contentObj = resp.get("content");
        if (contentObj instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> block && "text".equals(block.get("type"))) {
                    Object text = block.get("text");
                    if (text != null) return String.valueOf(text);
                }
            }
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> em) {
            throw new IllegalStateException("LLM 调用失败: " + em.get("message"));
        }
        throw new IllegalStateException("LLM 响应缺少 content[].text");
    }

    /** 提取结构化响应（正文 + tool_use 调用 + usage）。 */
    @SuppressWarnings("unchecked")
    private LlmChatResponse extractResponse(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("LLM 响应为空");
        List<LlmToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Object contentObj = resp.get("content");
        if (contentObj instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (!(b instanceof Map<?, ?> block)) continue;
                String type = block.get("type") == null ? "" : String.valueOf(block.get("type"));
                if ("text".equals(type)) {
                    Object t = block.get("text");
                    if (t != null) text.append(t);
                } else if ("tool_use".equals(type)) {
                    String id = block.get("id") == null ? "" : String.valueOf(block.get("id"));
                    String name = block.get("name") == null ? "" : String.valueOf(block.get("name"));
                    String args = block.get("input") == null ? "{}" : String.valueOf(block.get("input"));
                    calls.add(new LlmToolCall(id, name, args));
                }
            }
        }
        if (calls.isEmpty()) {
            Object error = resp.get("error");
            if (error instanceof Map<?, ?> em) {
                throw new IllegalStateException("LLM 调用失败: " + em.get("message"));
            }
        }
        long[] usage = parseUsage(resp.get("usage"));
        return new LlmChatResponse(text.length() == 0 ? null : text.toString(),
                calls.isEmpty() ? null : calls, usage[0], usage[1]);
    }

    private long[] parseUsage(Object usageObj) {
        if (usageObj instanceof Map<?, ?> usage) {
            long input = num(usage.get("input_tokens"));
            long output = num(usage.get("output_tokens"));
            return new long[]{input, output};
        }
        return new long[]{0, 0};
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private JdkClientHttpRequestFactory jdkFactory() {
        java.net.http.HttpClient http = LlmHttpClient.shared();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(30));
        return f;
    }
}
