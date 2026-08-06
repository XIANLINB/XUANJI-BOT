package dev.xuanji.adapter.qqbot.util;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ Markdown 消息模板注册中心（借鉴 ElainaBot 的 MARKDOWN_TEMPLATES 设计）。
 *
 * <p>把在 QQ 开放平台申请并审核通过的 Markdown 模板集中注册（模板 ID + 参数占位符顺序），
 * 插件只需按模板名引用、按顺序填值，无需记忆模板 ID 与占位符：
 * <pre>{@code
 * // 1. 启动时注册（或注入后在 @PostConstruct 里注册）
 * markdownTemplates.register("notice", "101993071_1658748972", "title", "content", "link");
 *
 * // 2. 插件中使用：按 params 顺序填值 → 得到 {custom_template_id, params:[...]} 负载
 * ObjectNode payload = markdownTemplates.render("notice", "系统公告", "今晚 22:00 维护", "https://qq.com");
 * sender.sendGroupMessage("群 openid", SendMessageRequest.activeMarkdown(payload, null));
 * }</pre>
 *
 * <h3>反渲染拆分（借鉴 ElainaBot _split_markdown_to_values）</h3>
 * 模板参数值若直接含 markdown 语法（如 {@code **bold**}、`` `code` ``、{@code [text](url)}），
 * QQ 渲染器可能把参数值本身当作格式指令渲染，破坏模板布局。
 * {@link #render} 默认对每个值做<b>语法拆分</b>（把完整语法拆成多个 value 片段），
 * 让占位符内按字面显示；需要原样渲染格式时用 {@link #renderRaw}。
 */
@Slf4j
@Component
@ConditionalOnQqbotEnabled
public class QqMarkdownTemplates {

    /** 模板定义：模板 ID + 参数占位符顺序。 */
    public record MarkdownTemplate(String templateId, List<String> params) {}

    /** 模板注册表：模板名 → 定义。 */
    private final Map<String, MarkdownTemplate> templates = new ConcurrentHashMap<>();

    /**
     * 注册模板。
     *
     * @param name       模板别名（插件引用名）
     * @param templateId QQ 开放平台模板 ID（格式 {appid}_{template_id}）
     * @param params     模板内占位符参数名（顺序与 {@link #render} 的值顺序一致）
     */
    public void register(String name, String templateId, String... params) {
        if (name == null || name.isBlank()) return;
        templates.put(name, new MarkdownTemplate(templateId, List.of(params == null ? new String[0] : params)));
        log.info("[Markdown模板] 已注册: {} → {}", name, templateId);
    }

    /** 批量注册。 */
    public void registerAll(Map<String, MarkdownTemplate> templates) {
        if (templates != null) templates.forEach(this::register);
    }

    private void register(String name, MarkdownTemplate t) {
        if (t == null) return;
        templates.put(name, t);
    }

    /** 是否已注册。 */
    public boolean contains(String name) {
        return templates.containsKey(name);
    }

    /** 已注册的模板名集合。 */
    public java.util.Set<String> names() {
        return templates.keySet();
    }

    /**
     * 渲染模板（值顺序对应注册时的 params；每个值做反渲染拆分）。
     *
     * @param name   模板别名
     * @param values 参数值（按 params 顺序）
     * @return {@code {custom_template_id, params:[{key, values:[...]}]}}；模板未注册返回 null
     */
    public ObjectNode render(String name, String... values) {
        return render(name, values == null ? List.of() : List.of(values), true);
    }

    /**
     * 渲染模板（不拆分，值原样作为单元素 values）。
     *
     * <p>适用于参数值确实想以 markdown 格式渲染的场景。
     */
    public ObjectNode renderRaw(String name, String... values) {
        return render(name, values == null ? List.of() : List.of(values), false);
    }

    private ObjectNode render(String name, List<String> values, boolean split) {
        MarkdownTemplate tpl = templates.get(name);
        if (tpl == null) {
            log.warn("[Markdown模板] 未注册的模板名: {}（已注册: {}）", name, templates.keySet());
            return null;
        }
        ObjectNode payload = Json.obj();
        payload.put("custom_template_id", tpl.templateId());
        ArrayNode params = Json.arr();
        for (int i = 0; i < tpl.params().size(); i++) {
            String key = tpl.params().get(i);
            String value = i < values.size() && values.get(i) != null ? values.get(i) : "";
            ObjectNode param = Json.obj();
            param.put("key", key);
            ArrayNode vals = Json.arr();
            if (split) {
                for (String part : splitMarkdownSyntax(value)) vals.add(part);
            } else {
                vals.add(value);
            }
            param.set("values", vals);
            params.add(param);
        }
        payload.set("params", params);
        return payload;
    }

    // ==================== 反渲染拆分（借鉴 ElainaBot _split_markdown_to_values） ====================

    /**
     * 把参数值中的 markdown 语法符号拆分，防止 QQ 把参数值当作格式指令渲染。
     *
     * <p>策略（简化自 ElainaBot 三段式）：
     * <ul>
     *   <li>识别 {@code **bold**}、{@code _italic_}、{@code *italic*}、{@code ~~del~~}、
     *       {@code `code`}、{@code [text](url)} 等成对语法</li>
     *   <li>将语法符号（如 {@code **}、{@code `}、{@code [}、{@code ](url)}）拆成独立片段，
     *       使占位符内按字面展示，不被渲染</li>
     * </ul>
     *
     * @param value 原始参数值
     * @return 拆分后的片段列表（无语法时为单元素列表）
     */
    public static List<String> splitMarkdownSyntax(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? List.of("") : List.of(value);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int n = value.length();
        while (i < n) {
            String token = leadingToken(value, i);
            if (token != null) {
                if (!cur.isEmpty()) {
                    parts.add(cur.toString());
                    cur = new StringBuilder();
                }
                parts.add(token);
                i += token.length();
            } else {
                cur.append(value.charAt(i));
                i++;
            }
        }
        if (!cur.isEmpty()) parts.add(cur.toString());
        return parts;
    }

    /** 取位置 i 处开头的 markdown 语法 token（无则 null）。 */
    private static String leadingToken(String s, int i) {
        int n = s.length();
        if (i + 1 < n && s.charAt(i) == '*' && s.charAt(i + 1) == '*') {
            return "**"; // ** 加粗（含 *** 场景的前两个）
        }
        if (s.charAt(i) == '_' || s.charAt(i) == '*' || s.charAt(i) == '`'
                || s.charAt(i) == '~') {
            // ~~ 删除线取两个；单符号斜体/行内代码取一个
            if (s.charAt(i) == '~' && i + 1 < n && s.charAt(i + 1) == '~') return "~~";
            return String.valueOf(s.charAt(i));
        }
        if (s.charAt(i) == '[') return "[";
        if (s.charAt(i) == ']') {
            // ](url) 整体拆出，防止生成链接
            if (i + 1 < n && s.charAt(i + 1) == '(') {
                int close = s.indexOf(')', i + 2);
                if (close > 0) return s.substring(i, close + 1);
            }
            return "]";
        }
        return null;
    }
}
