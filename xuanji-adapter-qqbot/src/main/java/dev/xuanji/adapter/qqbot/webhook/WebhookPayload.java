package dev.xuanji.adapter.qqbot.webhook;

import lombok.Data;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;

/**
 * Webhook 接收到的 QQ 平台事件数据结构
 *
 * <p>对应 QQ 平台通过 HTTP POST 推送的 JSON 格式。
 * 与 {@link dev.xuanji.adapter.qqbot.websocket.WsPayload} 结构类似，
 * 但多了一个 {@code id} 字段（事件唯一标识，用于去重）。
 *
 * <p>JSON 结构：
 * <pre>
 * {
 *   "id": "event_id",       // 事件唯一标识（用于去重）
 *   "op": 0,                // OpCode: 0=业务事件, 13=回调地址验证
 *   "d": { ... },           // 事件数据（不同事件格式不同）
 *   "s": 42,                // 序列号（可选）
 *   "t": "EVENT_TYPE"       // 事件类型（仅 op=0 时有值）
 * }
 * </pre>
 *
 * <h3>OpCode 说明</h3>
 * <ul>
 *   <li><b>op=0</b> — 正常业务事件推送（消息、成员变动等），需要验签和分发</li>
 *   <li><b>op=13</b> — 回调地址验证请求，需要返回 Ed25519 签名</li>
 * </ul>
 *
 * @see WebhookServiceImpl#handleWebhook  解析和处理本结构
 * @see dev.xuanji.adapter.qqbot.websocket.WsPayload WebSocket 消息结构（对比参考）
 */
@Data
public class WebhookPayload {

    /** 事件唯一标识（UUID 格式），用于事件去重，防止同一事件被处理多次 */
    private String id;

    /**
     * OpCode — 消息类型
     * <ul>
     *   <li>0 — 正常业务事件推送</li>
     *   <li>13 — 回调地址验证请求</li>
     * </ul>
     */
    private int op;

    /** 事件数据（不同事件类型对应不同的 JSON 结构） */
    private ObjectNode d;

    /** 事件序列号（可选，用于事件排序和 Resume） */
    private Integer s;

    /** 事件类型（仅 op=0 时有值），如 "C2C_MESSAGE_CREATE"、"GUILD_CREATE" 等 */
    private String t;

    /**
     * 从 JSON 字符串解析为 WebhookPayload 对象
     *
     * <p>使用 org.json 库解析，字段缺失时使用默认值。
     * id 和 t 字段缺失时为 null，op 缺失时默认为 0。
     *
     * @param jsonStr 原始 JSON 字符串（来自 HTTP 请求体）
     * @return 解析后的 WebhookPayload 对象
     */
    public static WebhookPayload parse(String jsonStr) {
        ObjectNode json = Json.parseObj(jsonStr);
        WebhookPayload payload = new WebhookPayload();
        payload.setId(json.path("id").asText(null));
        payload.setOp(json.path("op").asInt(0));
        payload.setD(Json.getObj(json, "d"));
        payload.setS(json.has("s") ? json.path("s").asInt() : null);
        payload.setT(json.path("t").asText(null));
        return payload;
    }
}
