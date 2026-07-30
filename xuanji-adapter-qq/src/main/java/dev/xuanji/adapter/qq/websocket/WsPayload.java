package dev.xuanji.adapter.qq.websocket;

import lombok.Data;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;

/**
 * QQ Bot WebSocket 消息帧数据结构
 *
 * <p>对应 QQ 开放平台 WebSocket 协议的消息格式。
 * 每条 WebSocket 消息都是一个 JSON 对象，包含以下字段：
 *
 * <pre>
 * {
 *   "op": 0,            // OpCode — 消息类型（见下方常量说明）
 *   "s": 42,            // 序列号 — 仅 op=0（Dispatch）时有值，用于 Resume
 *   "t": "EVENT_TYPE",  // 事件类型 — 仅 op=0 时有值，如 "C2C_MESSAGE_CREATE"
 *   "d": { ... }        // 数据 — 不同 OpCode 对应不同的数据结构
 * }
 * </pre>
 *
 * <h3>OpCode 说明</h3>
 * <ul>
 *   <li><b>0 — Dispatch</b> — 服务端推送的业务事件（如消息、成员变动等）</li>
 *   <li><b>1 — Heartbeat</b> — 心跳请求/响应</li>
 *   <li><b>2 — Identify</b> — 客户端发送的鉴权消息</li>
 *   <li><b>6 — Resume</b> — 客户端发送的恢复会话消息</li>
 *   <li><b>7 — Reconnect</b> — 服务端要求客户端重新连接</li>
 *   <li><b>9 — Invalid Session</b> — 鉴权失败，会话无效</li>
 *   <li><b>10 — Hello</b> — 服务端握手消息，包含心跳间隔</li>
 *   <li><b>11 — Heartbeat ACK</b> — 心跳确认（服务端回复）</li>
 * </ul>
 *
 * @see QqBotWsClient#handleMessage(String) 消息处理入口
 */
@Data
public class WsPayload {

    /**
     * OpCode — 消息类型
     * <p>取值范围：0, 1, 2, 6, 7, 9, 10, 11
     */
    private int op;

    /**
     * 事件类型（仅 op=0 时有值）
     * <p>如 "C2C_MESSAGE_CREATE"、"GROUP_AT_MESSAGE_CREATE"、"READY" 等
     */
    private String t;

    /**
     * 消息数据
     * <p>不同 OpCode 对应不同的 JSON 结构：
     * <ul>
     *   <li>op=0  — 事件数据（包含 id、author、content 等）</li>
     *   <li>op=10 — 包含 heartbeat_interval 字段</li>
     *   <li>op=9  — 可能包含错误信息</li>
     * </ul>
     */
    private ObjectNode d;

    /**
     * 事件序列号（仅 op=0 时有值）
     * <p>单调递增的整数，用于 Resume 恢复会话时告知服务端从哪个事件开始重放。
     * 为 null 表示该消息不包含序列号。
     */
    private Integer s;

    /**
     * 从 JSON 字符串解析为 WsPayload 对象
     *
     * <p>使用 org.json 库解析，字段缺失时使用默认值（op=-1, t=null, s=null）。
     *
     * @param json 原始 JSON 字符串
     * @return 解析后的 WsPayload 对象
     */
    public static WsPayload parse(String json) {
        ObjectNode obj = Json.parseObj(json);
        WsPayload p = new WsPayload();
        p.setOp(obj.path("op").asInt(-1));
        p.setT(obj.path("t").asText(null));
        p.setD(Json.getObj(obj, "d"));
        p.setS(obj.has("s") ? obj.path("s").asInt() : null);
        return p;
    }
}
