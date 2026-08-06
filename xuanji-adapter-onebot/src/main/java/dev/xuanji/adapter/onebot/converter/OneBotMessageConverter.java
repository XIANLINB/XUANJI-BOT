/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  com.fasterxml.jackson.databind.node.ArrayNode
 *  com.fasterxml.jackson.databind.node.ObjectNode
 *  dev.xuanji.api.json.Json
 *  dev.xuanji.api.message.MessageChain
 *  dev.xuanji.api.message.MessageChain$Builder
 *  dev.xuanji.api.message.MessageElement
 *  dev.xuanji.api.message.MessageElement$Ark
 *  dev.xuanji.api.message.MessageElement$At
 *  dev.xuanji.api.message.MessageElement$Face
 *  dev.xuanji.api.message.MessageElement$File
 *  dev.xuanji.api.message.MessageElement$Image
 *  dev.xuanji.api.message.MessageElement$Keyboard
 *  dev.xuanji.api.message.MessageElement$Markdown
 *  dev.xuanji.api.message.MessageElement$Passthrough
 *  dev.xuanji.api.message.MessageElement$Quote
 *  dev.xuanji.api.message.MessageElement$Reply
 *  dev.xuanji.api.message.MessageElement$Text
 *  dev.xuanji.api.message.MessageElement$Video
 *  dev.xuanji.api.message.MessageElement$Voice
 */
package dev.xuanji.adapter.onebot.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import java.lang.runtime.SwitchBootstraps;
import java.util.Objects;

public final class OneBotMessageConverter {
    private OneBotMessageConverter() {
    }

    public static MessageChain toChain(JsonNode message) {
        return toChain(message, null);
    }

    /**
     * 解析 OneBot 消息段数组 → 消息链。{@code selfId} 非空时对 URL 形态的 image/record/video/file
     * <b>按需下载</b>到本地（开关在 xuanji_config/bot_setting），成功就用 FILE_PATH 形态。
     * 失败/未启用静默回退 URL，消息处理不受影响。
     */
    public static MessageChain toChain(JsonNode message, String selfId) {
        if (message == null || message.isNull()) {
            return MessageChain.EMPTY;
        }
        if (message.isTextual()) {
            return OneBotMessageConverter.parseCqString(message.asText());
        }
        if (!message.isArray()) {
            return MessageChain.EMPTY;
        }
        MessageChain.Builder builder = MessageChain.builder();
        for (JsonNode seg : message) {
            MessageElement el = OneBotMessageConverter.toElement(seg, selfId);
            if (el == null) continue;
            builder.add(el);
        }
        return builder.build();
    }

    static MessageElement toElement(JsonNode seg, String selfId) {
        String type = seg.path("type").asText("");
        JsonNode d = seg.path("data");
        return switch (type) {
            case "text" -> new MessageElement.Text(d.path("text").asText(""));
            case "at" -> {
                String qq = d.path("qq").asText("");
                if ("all".equals(qq)) {
                    yield MessageElement.At.all();
                }
                yield new MessageElement.At(qq, d.path("name").asText(""));
            }
            case "image" -> new MessageElement.Image(autoDownload(OneBotMessageConverter.firstNonBlank(d.path("url").asText(""), d.path("file").asText("")), dev.xuanji.api.annotation.MediaType.IMAGE, selfId), d.path("file").asText(""), d.path("width").asInt(0), d.path("height").asInt(0), d.path("file_size").asLong(0L));
            case "face" -> new MessageElement.Face(d.path("id").asInt(0));
            case "record" -> new MessageElement.Voice(autoDownload(OneBotMessageConverter.firstNonBlank(d.path("url").asText(""), d.path("file").asText("")), dev.xuanji.api.annotation.MediaType.VOICE, selfId), d.path("duration").asInt(0));
            case "video" -> new MessageElement.Video(autoDownload(OneBotMessageConverter.firstNonBlank(d.path("url").asText(""), d.path("file").asText("")), dev.xuanji.api.annotation.MediaType.VIDEO, selfId), 0, 0, 0);
            case "file" -> new MessageElement.File(autoDownload(OneBotMessageConverter.firstNonBlank(d.path("url").asText(""), d.path("file").asText("")), dev.xuanji.api.annotation.MediaType.FILE, selfId), d.path("name").asText(d.path("file").asText("")), d.path("file_size").asLong(0L));
            case "reply" -> new MessageElement.Reply(d.path("id").asText(""));
            case "markdown" -> new MessageElement.Markdown(d.path("content").asText(d.path("text").asText("")));
            case "json" -> new MessageElement.Passthrough("onebot", "json\u5361\u7247", (Object)d.path("data").asText(""));
            case "forward" -> new MessageElement.Passthrough("onebot", "\u5408\u5e76\u8f6c\u53d1", (Object)d.path("id").asText(""));
            case "rps" -> OneBotMessageConverter.passThrough(type, seg);
            case "dice" -> OneBotMessageConverter.passThrough(type, seg);
            case "shake" -> OneBotMessageConverter.passThrough(type, seg);
            case "poke" -> OneBotMessageConverter.passThrough(type, seg);
            case "anonymous" -> OneBotMessageConverter.passThrough(type, seg);
            case "share" -> OneBotMessageConverter.passThrough(type, seg);
            case "contact" -> OneBotMessageConverter.passThrough(type, seg);
            case "location" -> OneBotMessageConverter.passThrough(type, seg);
            case "music" -> OneBotMessageConverter.passThrough(type, seg);
            case "node" -> OneBotMessageConverter.passThrough(type, seg);
            case "xml" -> OneBotMessageConverter.passThrough(type, seg);
            case "mface" -> OneBotMessageConverter.passThrough(type, seg);
            default -> type.isEmpty() ? null : OneBotMessageConverter.passThrough(type, seg);
        };
    }

    static MessageChain parseCqString(String raw) {
        MessageChain.Builder builder = MessageChain.builder();
        int i = 0;
        while (i < raw.length()) {
            int end;
            int start = raw.indexOf("[CQ:", i);
            if (start < 0) {
                OneBotMessageConverter.appendText(builder, raw.substring(i));
                break;
            }
            if (start > i) {
                OneBotMessageConverter.appendText(builder, raw.substring(i, start));
            }
            if ((end = raw.indexOf(93, start)) < 0) {
                OneBotMessageConverter.appendText(builder, raw.substring(start));
                break;
            }
            MessageElement el = OneBotMessageConverter.parseCqSegment(raw.substring(start + 4, end));
            if (el != null) {
                builder.add(el);
            }
            i = end + 1;
        }
        return builder.build();
    }

    private static void appendText(MessageChain.Builder builder, String text) {
        String t = OneBotMessageConverter.unescapeCq(text);
        if (!t.isEmpty()) {
            builder.text(t);
        }
    }

    private static MessageElement parseCqSegment(String body) {
        String[] parts = body.split(",");
        if (parts.length == 0) {
            return null;
        }
        ObjectNode data = Json.obj();
        for (int k = 1; k < parts.length; ++k) {
            int eq = parts[k].indexOf(61);
            if (eq <= 0) continue;
            data.put(parts[k].substring(0, eq), OneBotMessageConverter.unescapeCq(parts[k].substring(eq + 1)));
        }
        ObjectNode seg = Json.obj();
        seg.put("type", parts[0]);
        seg.set("data", (JsonNode)data);
        return OneBotMessageConverter.toElement((JsonNode)seg, null);
    }

    private static String unescapeCq(String s) {
        return s.replace("&#44;", ",").replace("&#91;", "[").replace("&#93;", "]").replace("&amp;", "&");
    }

    public static ArrayNode toSegments(MessageChain chain) {
        ArrayNode arr = Json.arr();
        if (chain == null) {
            return arr;
        }
        for (MessageElement el : chain.elements()) {
            ObjectNode seg = OneBotMessageConverter.toSegment(el);
            if (seg == null) continue;
            arr.add((JsonNode)seg);
        }
        return arr;
    }

    static ObjectNode toSegment(MessageElement el) {
        if (el == null) return null;
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
            case MessageElement.Markdown md -> seg("markdown", "content", md.content());
            case MessageElement.Keyboard ignored -> null;
            case MessageElement.Ark ark -> ark.nativePayload() == null
                    ? null : seg("json", "data", String.valueOf(ark.nativePayload()));
            case MessageElement.Passthrough p -> toPassthroughSegment(p);
            default -> null;
        };
    }

    private static ObjectNode toPassthroughSegment(MessageElement.Passthrough p) {
        if (!"onebot".equals(p.platform())) {
            return null;
        }
        Object payload = p.nativePayload();
        if (payload == null) {
            return null;
        }
        String raw = String.valueOf(payload);
        if ("json\u5361\u7247".equals(p.description())) {
            return OneBotMessageConverter.seg("json", "data", raw);
        }
        if ("\u5408\u5e76\u8f6c\u53d1".equals(p.description())) {
            return OneBotMessageConverter.seg("forward", "id", raw);
        }
        try {
            JsonNode node = Json.parse((String)raw);
            if (node.isObject() && node.has("type")) {
                return (ObjectNode)node;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static ObjectNode seg(String type, String key, String value) {
        ObjectNode s = Json.obj();
        s.put("type", type);
        ObjectNode d = Json.obj();
        d.put(key, value == null ? "" : value);
        s.set("data", (JsonNode)d);
        return s;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static MessageElement.Passthrough passThrough(String type, JsonNode seg) {
        return new MessageElement.Passthrough("onebot", type, (Object)seg.toString());
    }

    /** 框架层按需下载：URL → 尝试落盘，成功返回 FILE_PATH；失败/未启用/非 URL 返回原 ref。 */
    private static String autoDownload(String ref, dev.xuanji.api.annotation.MediaType type, String selfId) {
        if (selfId == null || selfId.isBlank() || ref == null || ref.isBlank()) return ref;
        if (!ref.startsWith("http://") && !ref.startsWith("https://")) return ref;
        try {
            var path = dev.xuanji.api.media.MediaFileDownloader.download(ref, type, selfId);
            return path != null ? path.toString() : ref;
        } catch (Exception e) {
            return ref;
        }
    }
}

