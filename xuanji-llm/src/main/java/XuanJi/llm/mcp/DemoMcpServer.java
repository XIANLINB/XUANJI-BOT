package XuanJi.llm.mcp;

import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 内置演示 MCP server —— 以 MCP 协议（JSON-RPC 2.0 + streamable HTTP）暴露几个演示工具，
 * 供 {@link McpClient} 连接验收「连接 → 工具清单 → Agent 调用」闭环，无需外部服务。
 *
 * <p>URL：{@code POST /xuanji/api/v1/console/llm/mcp-demo}
 * 工具：demo_time（当前时间）/ demo_random（随机数）/ demo_echo（回声）。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console/llm/mcp-demo")
public class DemoMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PostMapping(produces = "application/json")
    public JsonNode handle(@RequestBody JsonNode req) {
        if (req == null) return error(0, -32700, "无效请求");
        String method = req.path("method").asText("");
        int id = req.path("id").asInt(0);
        return switch (method) {
            case "initialize" -> result(id, obj()
                    .put("protocolVersion", "2025-03-26")
                    .set("capabilities", obj().set("tools", obj()))
                    .set("serverInfo", obj().put("name", "xuanji-demo").put("version", "1.0")));
            case "tools/list" -> result(id, listTools());
            case "tools/call" -> callTool(req.path("params"));
            case "notifications/initialized" -> null; // 通知类：无响应体
            default -> error(id, -32601, "方法不存在: " + method);
        };
    }

    // ──────────── 工具实现 ────────────

    private ObjectNode listTools() {
        ObjectNode r = obj();
        ArrayNode tools = r.putArray("tools");

        ObjectNode t1 = obj();
        t1.put("name", "demo_time");
        t1.put("description", "获取当前服务器时间（演示 MCP 工具）");
        t1.set("inputSchema", emptySchema());
        tools.add(t1);

        ObjectNode t2 = obj();
        t2.put("name", "demo_random");
        t2.put("description", "生成一个 1-100 的随机数（演示 MCP 工具）");
        t2.set("inputSchema", emptySchema());
        tools.add(t2);

        ObjectNode t3 = obj();
        t3.put("name", "demo_echo");
        t3.put("description", "原样返回输入文本（演示 MCP 工具）");
        ObjectNode props = obj();
        props.set("text", obj().put("type", "string").put("description", "要回显的文本"));
        ObjectNode schema = obj();
        schema.put("type", "object");
        schema.set("properties", props);
        ArrayNode req = schema.putArray("required");
        req.add("text");
        t3.set("inputSchema", schema);
        tools.add(t3);

        return r;
    }

    /** 空参数工具的标准 JSON Schema（type=object，properties/required 空）。 */
    private static ObjectNode emptySchema() {
        ObjectNode s = obj();
        s.put("type", "object");
        s.set("properties", obj());
        s.putArray("required");
        return s;
    }

    private JsonNode callTool(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        String text;
        try {
            switch (name) {
                case "demo_time" -> text = "当前时间：" + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                case "demo_random" -> text = "随机数：" + ThreadLocalRandom.current().nextInt(1, 101);
                case "demo_echo" -> text = "回声：" + (args != null ? args.path("text").asText("") : "");
                default -> {
                    return result(0, obj().put("isError", true).set("content",
                            textArray("未知工具: " + name)));
                }
            }
        } catch (Exception e) {
            return result(0, obj().put("isError", true).set("content",
                    textArray("执行失败: " + e.getMessage())));
        }
        return result(0, obj().put("isError", false).set("content", textArray(text)));
    }

    private static ArrayNode textArray(String text) {
        ArrayNode arr = MAPPER.createArrayNode();
        arr.add(obj().put("type", "text").put("text", text));
        return arr;
    }

    // ──────────── JSON-RPC 包装 ────────────

    private static ObjectNode result(int id, ObjectNode resultNode) {
        ObjectNode r = obj();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.set("result", resultNode);
        return r;
    }

    private static ObjectNode error(int id, int code, String message) {
        ObjectNode r = obj();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        ObjectNode err = obj();
        err.put("code", code);
        err.put("message", message);
        r.set("error", err);
        return r;
    }

    private static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }
}
