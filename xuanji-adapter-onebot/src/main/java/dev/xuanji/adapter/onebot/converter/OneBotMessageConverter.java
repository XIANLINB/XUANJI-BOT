package dev.xuanji.adapter.onebot.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;

/**
 * OneBot v11 消息段 ↔ 统一 MessageChain 双向转换。
 *
 * <p>OneBot 的消息体有两种形态，均需支持：
 * <ul>
 *   <li><b>数组格式</b>（{@code message_format=array}，Napcat 默认）：
 *       {@code [{"type":"text","data":{"text":"hi"}}, {"type":"at","data":{"qq":"10001"}}]}</li>
 *   <li><b>字符串格式</b>（CQ 码）：{@code "hi[CQ:at,qq=10001]"}</li>
 * </ul>
 *
 * <p>发送方向统一输出数组格式（CQ 码在文本含 {@code [ ] ,} 时容易踩转义坑）。
 */
public final class OneBotMessageConverter {

    private OneBotMessageConverter() {}

    // ==================== 接收：OneBot → MessageChain ====================

    /**
     * 解析 OneBot 事件的 message 字段。
     *
     * @param message 数组格式的 ArrayNode，或字符串格式的 TextNode（CQ 码）
     */
    public static MessageChain toChain(JsonNode message) {
        if (message == null || message.isNull()) {
            return MessageChain.EMPTY;
        }
        if (message.isTextual()) {
            return parseCqString(message.asText());
        }
        if (!message.isArray()) {
            return MessageChain.EMPTY;
        }

        MessageChain.Builder builder = MessageChain.builder();
        for (JsonNode seg : message) {
            MessageElement el = toElement(seg);
            if (el != null) {
                builder.add(el);
            }
        }
        return builder.build();
    }

    /** 单个消息段 → MessageElement */
    static MessageElement toElement(JsonNode seg) {
        String type = seg.path("type").asText("");
        JsonNode d = seg.path("data");

        return switch (type) {
            case "text" -> new MessageElement.Text(d.path("text").asText(""));

            case "at" -> {
                String qq = d.path("qq").asText("");
                yield "all".equals(qq)
                        ? MessageElement.At.all()
                        : new MessageElement.At(qq, d.path("name").asText(""));
            }

            case "image" -> new MessageElement.Image(
                    firstNonBlank(d.path("url").asText(""), d.path("file").asText("")),
                    d.path("file").asText(""),
                    d.path("width").asInt(0),
                    d.path("height").asInt(0),
                    d.path("file_size").asLong(0));

            case "face" -> new MessageElement.Face(d.path("id").asInt(0));

            case "record" -> new MessageElement.Voice(
                    firstNonBlank(d.path("url").asText(""), d.path("file").asText("")),
                    d.path("duration").asInt(0));

            case "video" -> new MessageElement.Video(
                    firstNonBlank(d.path("url").asText(""), d.path("file").asText("")),
                    0, 0, 0);

            case "file" -> new MessageElement.File(
                    firstNonBlank(d.path("url").asText(""), d.path("file").asText("")),
                    d.path("name").asText(d.path("file").asText("")),
                    d.path("file_size").asLong(0));

            case "reply" -> new MessageElement.Reply(d.path("id").asText(""));

            // Napcat 扩展：原生 markdown 段 → 框架 Markdown 元素
            case "markdown" -> new MessageElement.Markdown(
                    d.path("content").asText(d.path("text").asText("")));

            case "json" -> new MessageElement.Passthrough("onebot", "json卡片", d.path("data").asText(""));

            case "forward" -> new MessageElement.Passthrough("onebot", "合并转发", d.path("id").asText(""));

            // ---- 以下 OneBot v11 段无通用元素映射，整段透传以保全信息 ----
            // 发送时再将原始段 JSON 原样还原（见 toPassthroughSegment），实现无损往返。
            case "rps"     -> passThrough(type, seg);
            case "dice"    -> passThrough(type, seg);
            case "shake"   -> passThrough(type, seg);
            case "poke"    -> passThrough(type, seg);
            case "anonymous" -> passThrough(type, seg);
            case "share"   -> passThrough(type, seg);
            case "contact" -> passThrough(type, seg);
            case "location" -> passThrough(type, seg);
            case "music"   -> passThrough(type, seg);
            case "node"    -> passThrough(type, seg);
            case "xml"     -> passThrough(type, seg);
            case "mface"   -> passThrough(type, seg);

            // 其余未知段同样整段透传，绝不丢信息
            default -> type.isEmpty() ? null : passThrough(type, seg);
        };
    }

    /** 极简 CQ 码解析（字符串格式兜底，主流实现默认走数组格式） */
    static MessageChain parseCqString(String raw) {
        MessageChain.Builder builder = MessageChain.builder();
        int i = 0;
        while (i < raw.length()) {
            int start = raw.indexOf("[CQ:", i);
            if (start < 0) {
                appendText(builder, raw.substring(i));
                break;
            }
            if (start > i) {
                appendText(builder, raw.substring(i, start));
            }
            int end = raw.indexOf(']', start);
            if (end < 0) {
                appendText(builder, raw.substring(start));
                break;
            }
            MessageElement el = parseCqSegment(raw.substring(start + 4, end));
            if (el != null) {
                builder.add(el);
            }
            i = end + 1;
        }
        return builder.build();
    }

    private static void appendText(MessageChain.Builder builder, String text) {
        String t = unescapeCq(text);
        if (!t.isEmpty()) {
            builder.text(t);
        }
    }

    /** body 形如 {@code at,qq=10001} */
    private static MessageElement parseCqSegment(String body) {
        String[] parts = body.split(",");
        if (parts.length == 0) {
            return null;
        }
        ObjectNode data = Json.obj();
        for (int k = 1; k < parts.length; k++) {
            int eq = parts[k].indexOf('=');
            if (eq > 0) {
                data.put(parts[k].substring(0, eq), unescapeCq(parts[k].substring(eq + 1)));
            }
        }
        ObjectNode seg = Json.obj();
        seg.put("type", parts[0]);
        seg.set("data", data);
        return toElement(seg);
    }

    private static String unescapeCq(String s) {
        return s.replace("&#44;", ",").replace("&#91;", "[").replace("&#93;", "]").replace("&amp;", "&");
    }

    // ==================== 发送：MessageChain → OneBot ====================

    /**
     * 消息链 → OneBot 数组格式消息段。
     *
     * <p>无法映射到 OneBot v11 的元素（Markdown / Keyboard / 外平台 Passthrough）
     * 按"降级为文本或丢弃"处理，保证发送不会因元素不兼容而整条失败。
     */
    public static ArrayNode toSegments(MessageChain chain) {
        ArrayNode arr = Json.arr();
        if (chain == null) {
            return arr;
        }
        for (MessageElement el : chain.elements()) {
            ObjectNode seg = toSegment(el);
            if (seg != null) {
                arr.add(seg);
            }
        }
        return arr;
    }

    static ObjectNode toSegment(MessageElement el) {
        return switch (el) {
            case MessageElement.Text t -> seg("text", "text", t.content());

            case MessageElement.At at -> seg("at", "qq", "all".equals(at.userId()) ? "all" : at.userId());

            case MessageElement.Image img -> seg("image", "file", img.url());

            case MessageElement.Face f -> seg("face", "id", String.valueOf(f.faceId()));

            case MessageElement.Voice v -> seg("record", "file", v.url());

            case MessageElement.Video v -> seg("video", "file", v.url());

            case MessageElement.File f -> {
                ObjectNode s = seg("file", "file", f.url());
                ((ObjectNode) s.get("data")).put("name", f.name());
                yield s;
            }

            case MessageElement.Reply r -> seg("reply", "id", r.targetMsgId());

            case MessageElement.Quote q -> seg("reply", "id", q.msgId());

            // OneBot v11 标准无 Markdown，但 Napcat 支持原生 markdown 段，原样输出
            case MessageElement.Markdown md -> seg("markdown", "content", md.content());

            // 键盘为 QQ 官方特性，OneBot 侧无对应，丢弃
            case MessageElement.Keyboard ignored -> null;

            // Ark → OneBot json 卡片段
            case MessageElement.Ark ark -> ark.nativePayload() == null
                    ? null
                    : seg("json", "data", String.valueOf(ark.nativePayload()));

            case MessageElement.Passthrough p -> toPassthroughSegment(p);
        };
    }

    /** 本平台透传原样还原为消息段；外平台透传降级为描述文本 */
    private static ObjectNode toPassthroughSegment(MessageElement.Passthrough p) {
        if (!"onebot".equals(p.platform())) {
            return null;
        }
        Object payload = p.nativePayload();
        if (payload == null) {
            return null;
        }
        String raw = String.valueOf(payload);
        if ("json卡片".equals(p.description())) {
            return seg("json", "data", raw);
        }
        if ("合并转发".equals(p.description())) {
            return seg("forward", "id", raw);
        }
        // 其余情况：nativePayload 保存的是完整段 JSON，原样解析回去
        try {
            JsonNode node = Json.parse(raw);
            if (node.isObject() && node.has("type")) {
                return (ObjectNode) node;
            }
        } catch (Exception ignored) {
            // 解析失败则丢弃，不影响其它段
        }
        return null;
    }

    private static ObjectNode seg(String type, String key, String value) {
        ObjectNode s = Json.obj();
        s.put("type", type);
        ObjectNode d = Json.obj();
        d.put(key, value == null ? "" : value);
        s.set("data", d);
        return s;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    /** 整段透传：保存原始段 JSON，发送时可原样还原（见 toPassthroughSegment） */
    private static MessageElement.Passthrough passThrough(String type, JsonNode seg) {
        return new MessageElement.Passthrough("onebot", type, seg.toString());
    }
}
