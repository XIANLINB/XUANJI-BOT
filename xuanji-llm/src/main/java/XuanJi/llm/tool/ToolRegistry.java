package XuanJi.llm.tool;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolDefinition;
import XuanJi.api.llm.LlmToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 工具注册表 —— 扫描 Spring 容器中所有标注 {@link LlmTool} 的方法，
 * 生成 OpenAI 风格的函数定义（{@link LlmToolDefinition}），并负责按模型传来的
 * JSON 参数执行对应方法（参数绑定 + 类型转换）。
 *
 * <p>扫描范围包含插件方法（llm 模块不感知插件来源，只按注解注册），
 * 因此插件标了 {@code @LlmTool} 的方法也能被 AI 调用。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext ctx;
    private final ObjectMapper mapper;
    private final ToolLearnService toolLearn;

    /** 工具名 → 执行入口 */
    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    public ToolRegistry(ApplicationContext ctx, ObjectMapper mapper, ToolLearnService toolLearn) {
        this.ctx = ctx;
        this.mapper = mapper;
        this.toolLearn = toolLearn;
    }

    @PostConstruct
    void scan() {
        String[] beanNames = ctx.getBeanDefinitionNames();
        int registered = 0;
        for (String beanName : beanNames) {
            Class<?> type;
            try {
                type = ctx.getType(beanName);
            } catch (Exception e) {
                continue;
            }
            if (type == null) continue;
            Object bean = null;
            for (Method m : type.getDeclaredMethods()) {
                LlmTool ann = m.getAnnotation(LlmTool.class);
                if (ann == null) continue;
                if (bean == null) {
                    try {
                        bean = ctx.getBean(beanName);
                    } catch (Exception e) {
                        log.warn("[TOOL] 获取 bean 失败，跳过 {}#{}: {}", type.getSimpleName(), m.getName(), e.getMessage());
                        break;
                    }
                }
                register(bean, m, ann);
                registered++;
            }
        }
        log.info("[TOOL] 已注册 {} 个 LLM 工具: {}", registered, tools.keySet());
    }

    private void register(Object bean, Method m, LlmTool ann) {
        String name = ann.name() == null || ann.name().isBlank() ? m.getName() : ann.name().trim();
        if (tools.containsKey(name)) {
            log.warn("[TOOL] 工具名冲突，忽略重复注册: {}（{}#{})", name, bean.getClass().getSimpleName(), m.getName());
            return;
        }
        LlmToolDefinition def = new LlmToolDefinition(
                name,
                ann.description(),
                ann.descriptionZh(),
                buildParameters(m),
                ann.confirm(),
                bean.getClass().getSimpleName());
        tools.put(name, new ToolEntry(def, bean, m, null));
    }

    /** 全部工具定义（前端工具清单 + 模型 tools 参数）。 */
    public List<LlmToolDefinition> definitions() {
        return new ArrayList<>(tools.values().stream().map(e -> e.definition).toList());
    }

    /** 是否存在指定工具。 */
    public boolean exists(String name) {
        return tools.containsKey(name);
    }

    /** 是否要求确认。 */
    public boolean requiresConfirm(String name) {
        ToolEntry e = tools.get(name);
        return e != null && e.definition.confirm();
    }

    /** 动态工具执行回调（MCP 桥接等运行时注册的工具）。 */
    @FunctionalInterface
    public interface ToolInvoker {
        String invoke(String argsJson, LlmToolContext context) throws Exception;
    }

    /**
     * 运行时注册动态工具（无 @LlmTool 注解，如 MCP 桥接）。同名工具直接覆盖。
     *
     * @param parameters OpenAI 风格参数 JSON Schema（可为空 Map）
     */
    public void registerDynamic(String name, String description, String descriptionZh, boolean confirm, String source,
                                Map<String, Object> parameters, ToolInvoker invoker) {
        LlmToolDefinition def = new LlmToolDefinition(
                name, description, descriptionZh,
                parameters == null ? Map.of() : parameters,
                confirm, source);
        tools.put(name, new ToolEntry(def, null, null, invoker));
        log.info("[TOOL] 动态注册工具: {} (source={}, confirm={})", name, source, confirm);
    }

    /** 移除动态工具（MCP server 断开时）。 */
    public void unregister(String name) {
        tools.remove(name);
    }

    /**
     * 执行工具（无上下文版本）。
     *
     * @throws IllegalArgumentException 工具不存在 / 参数绑定失败
     * @throws Exception                工具方法本身抛出的异常
     */
    public String execute(String name, String argsJson) throws Exception {
        return execute(name, argsJson, null);
    }

    /**
     * 执行工具。
     *
     * @param name        工具名
     * @param argsJson    模型传入的参数 JSON 字符串（可为 "{}" / null）
     * @param context     会话上下文；工具方法声明 {@link LlmToolContext} 参数时自动注入
     * @return 执行结果（String / Map 序列化为 JSON），供模型回填
     */
    public String execute(String name, String argsJson, LlmToolContext context) throws Exception {
        ToolEntry e = tools.get(name);
        if (e == null) {
            toolLearn.record(name, argsJson, false, "工具不存在");
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        try {
            String result = doExecute(e, name, argsJson, context);
            toolLearn.record(name, argsJson, true, null);
            return result;
        } catch (Exception ex) {
            // P1-D 经验库：失败埋点（记录 error，同类错误累积后生成 fix_hint）
            toolLearn.record(name, argsJson, false, ex.getMessage());
            throw ex;
        }
    }

    private String doExecute(ToolEntry e, String name, String argsJson, LlmToolContext context) throws Exception {
        // 动态工具（MCP 桥接等）：走回调
        if (e.invoker != null) {
            return e.invoker.invoke(argsJson, context);
        }
        ObjectNode args = parseArgs(argsJson);
        Parameter[] params = e.method.getParameters();
        Object[] callArgs = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> ptype = params[i].getType();
            if (ptype == LlmToolContext.class) {
                callArgs[i] = context;
                continue;
            }
            String paramName = paramName(params[i]);
            JsonNode node = args.get(paramName);
            if (node == null || node.isNull()) {
                LlmToolParam pa = params[i].getAnnotation(LlmToolParam.class);
                if (pa != null && !pa.required()) {
                    callArgs[i] = null;
                    continue;
                }
                throw new IllegalArgumentException("工具 " + name + " 缺少参数: " + paramName);
            }
            callArgs[i] = mapper.convertValue(node, ptype);
        }
        Object result = e.method.invoke(e.bean, callArgs);
        return toResultText(result);
    }

    private ObjectNode parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank() || argsJson.equals("{}")) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode n = mapper.readTree(argsJson);
            return n.isObject() ? (ObjectNode) n : mapper.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalArgumentException("工具参数解析失败: " + ex.getMessage());
        }
    }

    private String toResultText(Object result) {
        if (result == null) return "（无返回值）";
        if (result instanceof String s) return s;
        if (result instanceof Number || result instanceof Boolean) return String.valueOf(result);
        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            return String.valueOf(result);
        }
    }

    // ──────────── 参数 JSON Schema 生成 ────────────

    private Map<String, Object> buildParameters(Method m) {
        ObjectNode props = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();
        for (Parameter p : m.getParameters()) {
            // 上下文参数自动注入，不暴露给模型
            if (p.getType() == LlmToolContext.class) continue;
            String name = paramName(p);
            LlmToolParam pa = p.getAnnotation(LlmToolParam.class);
            ObjectNode def = props.putObject(name);
            def.put("type", mapType(p.getType()));
            if (pa != null && !pa.value().isBlank()) {
                def.put("description", pa.value());
            }
            if (pa == null || pa.required()) {
                required.add(name);
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", required);
        return schema;
    }

    private static String paramName(Parameter p) {
        LlmToolParam pa = p.getAnnotation(LlmToolParam.class);
        if (pa != null && !pa.name().isBlank()) {
            return pa.name().trim();
        }
        return p.getName();
    }

    private static String mapType(Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || List.class.isAssignableFrom(type)) return "array";
        return "string";
    }

    private record ToolEntry(LlmToolDefinition definition, Object bean, Method method, ToolInvoker invoker) {
    }
}
