package XuanJi.llm.mcp;

import XuanJi.llm.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理 —— 注册/连接/断开外部 MCP server，并把其工具桥接进 {@link ToolRegistry}。
 *
 * <p>安全：非白名单（whitelist=false）的 MCP 工具注册为 {@code confirm=true}
 * （Agent 调用前需用户确认）；白名单 MCP 直接执行。断连时移除对应工具。
 */
@Slf4j
@Component
public class McpService {

    private final JdbcTemplate jdbc;
    private final ToolRegistry toolRegistry;

    public McpService(JdbcTemplate jdbc, ToolRegistry toolRegistry) {
        this.jdbc = jdbc;
        this.toolRegistry = toolRegistry;
    }

    /** 全部 MCP server 配置（botKey 为空 = 全部）。 */
    public List<Map<String, Object>> list(String botKey) {
        String sql = botKey == null || botKey.isBlank()
                ? "SELECT bot_key, name, url, description, whitelist, enabled, updated_at FROM xuanji_llm_mcp ORDER BY name"
                : "SELECT bot_key, name, url, description, whitelist, enabled, updated_at FROM xuanji_llm_mcp WHERE bot_key = ? ORDER BY name";
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("botKey", rs.getString("bot_key"));
            m.put("name", rs.getString("name"));
            m.put("url", rs.getString("url"));
            m.put("description", rs.getString("description"));
            m.put("whitelist", rs.getBoolean("whitelist"));
            m.put("enabled", rs.getBoolean("enabled"));
            m.put("updatedAt", rs.getObject("updated_at") != null ? String.valueOf(rs.getObject("updated_at")) : null);
            return m;
        }, botKey == null || botKey.isBlank() ? new Object[0] : new Object[]{botKey});
    }

    /** 注册/更新 MCP server。 */
    public void register(String botKey, String name, String url, String description, boolean whitelist, boolean enabled) {
        if (name == null || name.isBlank() || url == null || url.isBlank()) {
            throw new IllegalArgumentException("name/url 不能为空");
        }
        jdbc.update("""
            MERGE INTO xuanji_llm_mcp (bot_key, name, url, description, whitelist, enabled, updated_at)
            KEY (bot_key, name)
            VALUES (?,?,?,?,?,?, CURRENT_TIMESTAMP)
            """, botKey, name, url, description, whitelist, enabled);
        log.info("[MCP] 注册/更新: bot={} name={} url={} whitelist={} enabled={}", botKey, name, url, whitelist, enabled);
    }

    /** 删除 MCP server（同时移除其工具）。 */
    public void delete(String botKey, String name) {
        jdbc.update("DELETE FROM xuanji_llm_mcp WHERE bot_key = ? AND name = ?", botKey, name);
        unregisterTools(botKey, name);
    }

    /** 连接：initialize + tools/list → 注册工具到 ToolRegistry。返回工具数。 */
    public int connect(String botKey, String name) {
        Map<String, Object> cfg = get(botKey, name);
        if (cfg == null) throw new IllegalArgumentException("MCP server 不存在: " + name);
        McpClient client = new McpClient(String.valueOf(cfg.get("url")));
        List<McpClient.McpToolInfo> tools = client.listTools();
        boolean whitelist = Boolean.TRUE.equals(cfg.get("whitelist"));
        String source = "mcp:" + name;
        for (McpClient.McpToolInfo t : tools) {
            if (t.name() == null || t.name().isBlank()) continue;
            Map<String, Object> params = schemaOf(t.inputSchema());
            toolRegistry.registerDynamic(t.name(),
                    t.description() == null || t.description().isBlank() ? "（MCP 工具）" : t.description(),
                    "",
                    !whitelist, source, params,
                    (argsJson, ctx) -> client.callTool(t.name(), argsJson));
        }
        log.info("[MCP] 连接成功: bot={} name={} 注册 {} 个工具", botKey, name, tools.size());
        return tools.size();
    }

    /** 断开：移除该 server 的工具。 */
    public void disconnect(String botKey, String name) {
        unregisterTools(botKey, name);
        log.info("[MCP] 已断开: bot={} name={}", botKey, name);
    }

    /** 启动时连接所有 enabled 的 server。 */
    public void refreshAll() {
        for (Map<String, Object> cfg : list("")) {
            if (!Boolean.TRUE.equals(cfg.get("enabled"))) continue;
            String botKey = String.valueOf(cfg.get("botKey"));
            String name = String.valueOf(cfg.get("name"));
            try {
                connect(botKey, name);
            } catch (Exception e) {
                log.warn("[MCP] 启动连接失败: {} -> {}", name, e.getMessage());
            }
        }
    }

    // ──────────── 内部 ────────────

    private Map<String, Object> get(String botKey, String name) {
        return list(botKey).stream().filter(m -> name.equals(m.get("name"))).findFirst().orElse(null);
    }

    private void unregisterTools(String botKey, String name) {
        String source = "mcp:" + name;
        for (XuanJi.api.llm.LlmToolDefinition d : toolRegistry.definitions()) {
            if (source.equals(d.source())) {
                toolRegistry.unregister(d.name());
            }
        }
    }

    /** MCP inputSchema → 我们的参数 Map（已是 OpenAI 风格，直接透传）。非法时兜底为合法空 schema。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaOf(tools.jackson.databind.JsonNode inputSchema) {
        if (inputSchema != null && !inputSchema.isNull() && inputSchema.isObject()) {
            try {
                Object v = new tools.jackson.databind.ObjectMapper().convertValue(inputSchema, Object.class);
                if (v instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    if ("object".equals(map.get("type"))) return map;
                }
            } catch (Exception ignored) {
            }
        }
        // 兜底：DeepSeek 等要求 parameters 必须是 type:object 的合法 JSON Schema
        return Map.of("type", "object", "properties", Map.of(), "required", java.util.List.of());
    }
}
