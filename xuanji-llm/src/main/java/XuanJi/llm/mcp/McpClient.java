package XuanJi.llm.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量 MCP 客户端 —— JSON-RPC 2.0 + streamable HTTP transport。
 *
 * <p>覆盖 MCP 核心方法：initialize（握手）/ notifications/initialized / tools/list / tools/call。
 * 零第三方依赖，响应兼容「纯 JSON」与「SSE 包裹」两种格式。
 */
@Slf4j
public class McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final RestClient rest;
    private final String url;
    private final AtomicInteger nextId = new AtomicInteger(1);

    public McpClient(String url) {
        this.url = url;
        this.rest = RestClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .requestFactory(jdkFactory())
                .build();
    }

    /** 握手并通知 initialized（每次调用前幂等执行，兼容无状态 server）。 */
    public void initialize() {
        JsonNode result = request("initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "xuanji", "version", "1.0")
        ));
        if (result == null) {
            throw new IllegalStateException("MCP initialize 失败: " + url);
        }
        // fire-and-forget initialized 通知
        try {
            rest.post().uri("").body(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized",
                    "params", Map.of()
            )).retrieve().body(String.class);
        } catch (Exception ignored) {
            // 部分实现不返回 initialized 的响应，忽略
        }
    }

    /** 拉取 MCP server 暴露的工具清单。 */
    public List<McpToolInfo> listTools() {
        initialize();
        JsonNode result = request("tools/list", Map.of());
        JsonNode tools = result != null ? result.path("tools") : null;
        List<McpToolInfo> out = new ArrayList<>();
        if (tools != null && tools.isArray()) {
            for (JsonNode t : tools) {
                out.add(new McpToolInfo(
                        t.path("name").asText(),
                        t.path("description").asText(null),
                        t.path("inputSchema").isObject() ? t.path("inputSchema") : null));
            }
        }
        return out;
    }

    /** 调用 MCP 工具，返回结果文本。 */
    public String callTool(String name, String argsJson) {
        JsonNode result = request("tools/call", Map.of("name", name, "arguments", parseArgs(argsJson)));
        if (result == null) {
            return "（MCP 调用无返回）";
        }
        // MCP 规范：content 为结构化内容数组，isError 标记错误
        if (result.path("isError").asBoolean(false)) {
            return "MCP 工具执行出错: " + result.path("content").toString();
        }
        StringBuilder sb = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode c : content) {
                String type = c.path("type").asText();
                if ("text".equals(type) || "resource".equals(type)) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(c.path("text").asText(""));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : result.toString();
    }

    // ──────────── JSON-RPC 底层 ────────────

    private JsonNode request(String method, Object params) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", nextId.getAndIncrement());
        body.put("method", method);
        body.put("params", params);
        try {
            String raw = rest.post()
                    .uri("")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode json = parse(raw);
            JsonNode error = json != null ? json.path("error") : null;
            if (error != null && !error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException("MCP " + method + " 错误: " + error);
            }
            return json != null ? json.path("result") : null;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MCP 请求失败: " + url + " " + method + " → " + e.getMessage());
        }
    }

    /** 解析响应：兼容纯 JSON 与 SSE（event: message + data: {...}）。 */
    private static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        try {
            if (t.startsWith("{")) {
                return MAPPER.readTree(t);
            }
            // SSE 逐行提取 data:
            StringBuilder data = new StringBuilder();
            for (String line : t.split("\n")) {
                String l = line.trim();
                if (l.startsWith("data:")) {
                    data.append(l.substring("data:".length()).trim());
                }
            }
            return data.length() > 0 ? MAPPER.readTree(data.toString()) : null;
        } catch (Exception e) {
            log.warn("[MCP] 响应解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static Object parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(argsJson, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(15));
        return f;
    }

    /** MCP 工具元数据。 */
    public record McpToolInfo(String name, String description, JsonNode inputSchema) {
    }
}
