package dev.xuanji.api.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON 工具 — 框架内统一的 JSON 入口（Jackson）
 *
 * <p>替代原 org.json 的用法：{@code new JSONObject()} → {@code Json.obj()}，
 * {@code new JSONObject(str)} → {@code Json.parseObj(str)}。
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /** 共享 ObjectMapper（只读配置，线程安全） */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 新建空对象节点（替代 new JSONObject()） */
    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    /** 新建空数组节点（替代 new JSONArray()） */
    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    /** 解析 JSON 文本为树节点 */
    public static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** 解析 JSON 文本为对象节点（替代 new JSONObject(str)） */
    public static ObjectNode parseObj(String text) {
        JsonNode node = parse(text);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("JSON 文本不是对象结构");
        }
        return (ObjectNode) node;
    }

    /** 任意对象序列化为对象节点（替代 new JSONObject(Bean)） */
    public static ObjectNode toObj(Object bean) {
        return MAPPER.valueToTree(bean);
    }

    /** 取对象字段为 ObjectNode（替代 optJSONObject），缺失或不是对象时返回 null */
    public static ObjectNode getObj(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode node = parent.get(field);
        return node instanceof ObjectNode o ? o : null;
    }

    /** 取对象字段为 ArrayNode（替代 optJSONArray），缺失或不是数组时返回 null */
    public static ArrayNode getArr(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode node = parent.get(field);
        return node instanceof ArrayNode a ? a : null;
    }
}
