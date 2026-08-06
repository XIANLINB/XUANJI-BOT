package dev.xuanji.adapter.qqbot.converter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;

/**
 * QQ 消息转换器 — 统一 MessageChain → QQ 平台可发送报文。
 */
public final class QqMessageConverter {

    private QqMessageConverter() {}

    /**
     * 将消息链转为 QQ 发送请求体（直接可调 /v2/groups/{id}/messages 或 /v2/users/{id}/messages）。
     */
    public static ObjectNode toQqPayload(MessageChain chain) {
        ObjectNode body = Json.obj();

        // 遍历链中元素，按优先级取第一个可发送的元素类型
        for (MessageElement e : chain.elements()) {
            boolean matched = switch (e) {
                case MessageElement.Text text -> { body.put("content", text.content()); body.put("msg_type", 0); yield true; }
                case MessageElement.Markdown md -> {
                    ObjectNode mdNode = Json.obj();
                    if (md.nativePayload() instanceof String s) {
                        mdNode.put("content", s);
                    } else {
                        mdNode.put("content", md.content());
                    }
                    body.set("markdown", mdNode);
                    body.put("msg_type", 2);
                    yield true;
                }
                case MessageElement.Keyboard kb -> {
                    body.put("msg_type", 2);  // keyboard 附着于 markdown，仅标记类型
                    yield true;
                }
                case MessageElement.Ark ark -> {
                    body.set("ark", Json.obj().put("template_id", ark.templateId()));
                    body.put("msg_type", 3);
                    yield true;
                }
                case MessageElement.Image img -> {
                    ObjectNode media = Json.obj().put("file_info", img.url());
                    body.set("media", media);
                    body.put("msg_type", 7);
                    yield true;
                }
                case MessageElement.Voice v -> {
                    ObjectNode media = Json.obj().put("file_info", v.url());
                    body.set("media", media);
                    body.put("msg_type", 7);
                    yield true;
                }
                case MessageElement.Video v -> {
                    ObjectNode media = Json.obj().put("file_info", v.url());
                    body.set("media", media);
                    body.put("msg_type", 7);
                    yield true;
                }
                case MessageElement.At at -> { body.put("content", "@" + at.display() + " "); body.put("msg_type", 0); yield true; }
                case MessageElement.Reply reply -> { body.put("msg_id", reply.targetMsgId()); yield false; }
                default -> false;
            };
            if (matched) {
                break; // 取第一个可发送元素
            }
        }

        if (!body.has("msg_type")) {
            body.put("content", chain.plainText().isEmpty() ? "[不支持的消息类型]" : chain.plainText());
            body.put("msg_type", 0);
        }

        return body;
    }

    /**
     * 由 QQ 原始报文（JSON 字符串）解析消息链 — 兼容两种形态：
     * <pre>
     *   {"content":"纯文本"}                          → Text
     *   {"content":"","attachments":[{url,filename}]} → 媒体元素（按文件名判定类别）
     *   {"content":[{type:"text",data:{text:...}}]}   → 段数组
     * </pre>
     *
     * <p>媒体类别判定与日志摘要共用 {@link dev.xuanji.adapter.qqbot.util.MediaKind}，
     * 保证 {@code mediaTypes=IMAGE} 过滤器与日志口径一致（P1-D 铁律）。
     */
    public static MessageChain fromQqPayload(String raw) {
        if (raw == null || raw.isBlank()) return MessageChain.EMPTY;
        try {
            ObjectNode node = (ObjectNode) Json.parse(raw);
            MessageChain.Builder b = MessageChain.builder();

            // 1. content：字符串或段数组
            if (node.has("content")) {
                var content = node.get("content");
                if (content.isTextual()) {
                    if (!content.asText().isEmpty()) b.text(content.asText());
                } else if (content.isArray()) {
                    for (var seg : content) {
                        if (seg instanceof ObjectNode so) parseSegment(b, so);
                    }
                }
            }

            // 2. attachments：QQ 富媒体附件（content 常为空，仅文件名可辨）
            if (node.has("attachments") && node.get("attachments").isArray()) {
                for (var att : node.get("attachments")) {
                    if (!(att instanceof ObjectNode ao)) continue;
                    String url = ao.path("url").asText(null);
                    if (url == null || url.isBlank()) continue;
                    String filename = ao.path("filename").asText(null);
                    long size = ao.path("size").asLong(0);
                    var kind = dev.xuanji.adapter.qqbot.util.MediaKind.resolve(null, filename != null ? filename : url);
                    switch (kind) {
                        case IMAGE -> b.add(new MessageElement.Image(url, null, 0, 0, size));
                        case VOICE -> b.add(new MessageElement.Voice(url, 0));
                        case VIDEO -> b.add(new MessageElement.Video(url));
                        default -> b.add(new MessageElement.File(url, filename, size));
                    }
                }
            }
            return b.build();
        } catch (Exception e) {
            return MessageChain.text(raw);
        }
    }

    /** QQ 段数组（{type, data}）解析 — 防御式，未知段忽略。 */
    private static void parseSegment(MessageChain.Builder b, ObjectNode seg) {
        String type = seg.path("type").asText("");
        var data = seg.path("data");
        switch (type) {
            case "text" -> {
                String t = data.path("text").asText("");
                if (!t.isEmpty()) b.text(t);
            }
            case "image" -> {
                String url = data.path("url").asText(null);
                if (url != null && !url.isBlank()) b.add(new MessageElement.Image(url, null, 0, 0, 0));
            }
            case "at" -> {
                String userId = data.path("user_openid").asText(data.path("id").asText(""));
                if (!userId.isBlank()) b.at(userId);
            }
            default -> { /* 未知段忽略，不崩 */ }
        }
    }
}
