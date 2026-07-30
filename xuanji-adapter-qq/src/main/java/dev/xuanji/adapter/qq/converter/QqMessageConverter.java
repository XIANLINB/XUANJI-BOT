package dev.xuanji.adapter.qq.converter;

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
}
