package dev.xuanji.adapter.qqbot.converter;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;

/**
 * QQ 消息转换器 — 统一 MessageChain → QQ 平台可发送报文。
 * <p>v2（2026-08-06 14:29）：框架层媒体自动下载——{@link #fromQqPayload(String, String)} 在构造
 * chain 时对 URL 形态 media 自动落盘（开关在运行设置，bot 级 &gt; 全局）。
 */
public final class QqMessageConverter {

    static {
        System.out.println("[媒体转换] QqMessageConverter v2 已加载（框架层自动下载）");
    }

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
        return fromQqPayload(raw, null);
    }

    /**
     * 解析 QQ 原始报文 → 消息链。下载/落盘按需在框架层完成（<b>无需插件调 resolveFile</b>）：
     * 若 {@code appId} 非空且媒体下载开关已开（全局 / 该 bot），URL 形态的 media 会<b>自动</b>
     * 下载到本地，构造时直接用 FILE_PATH（form 由字符串前缀自动识别）。
     * 下载失败/未启用/非法 URL → 保持 URL 形态兜底，绝不影响消息处理。
     *
     * @param raw   QQ 原始报文 JSON
     * @param appId 机器人 appId（用于 bot 级下载开关；null = 不做下载）
     */
    public static MessageChain fromQqPayload(String raw, String appId) {
        if (raw == null || raw.isBlank()) return MessageChain.EMPTY;
        try {
            return fromQqData((ObjectNode) Json.parse(raw), appId);
        } catch (Exception e) {
            // 字符串入口的兜底：JSON 都解析不了，至少把原文当文本留住，不让消息凭空消失
            return MessageChain.text(raw);
        }
    }

    /**
     * 解析 QQ 消息报文节点 → 消息链（{@link #fromQqPayload(String, String)} 的<b>结构化入口</b>）。
     *
     * <p>与字符串版能力完全一致（content 字符串 / 段数组、attachments 富媒体、按需自动下载），
     * 区别只在于免去 {@code toString() → parse()} 的序列化往返 —— 事件转换是每条消息必经的热路径，
     * 上游（{@code QqEventConverter}）手里本来就是 {@code ObjectNode}，没有理由再绕一圈。
     *
     * <p>兜底策略与字符串版<b>刻意不同</b>：字符串版解析失败会退化成 {@code Text(raw)} 保住原文；
     * 本方法入参已是结构化节点，真出异常说明是字段形态异常而非文本，此时返回
     * {@link MessageChain#EMPTY} 而不是把整坨 JSON 塞进消息文本（否则会污染命令匹配）。
     *
     * @param node  QQ 消息数据节点（webhook/WS 的 {@code d} 字段）
     * @param appId 机器人 appId（用于 bot 级媒体下载开关；null = 不下载，保持 URL 形态）
     * @return 消息链；无可解析内容时返回 {@link MessageChain#EMPTY}（不会返回 null）
     */
    public static MessageChain fromQqData(ObjectNode node, String appId) {
        if (node == null) return MessageChain.EMPTY;
        try {
            MessageChain.Builder b = MessageChain.builder();

            // 1. content：字符串或段数组
            if (node.has("content")) {
                var content = node.get("content");
                if (content.isTextual()) {
                    if (!content.asText().isEmpty()) b.text(content.asText());
                } else if (content.isArray()) {
                    for (var seg : content) {
                        if (seg instanceof ObjectNode so) parseSegment(b, so, appId);
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
                    // 框架层按需下载：URL → 尝试落盘，成功就改用 FILE_PATH 形态构造
                    String rawRef = autoDownload(url, kind.toMediaType(), appId);
                    switch (kind) {
                        case IMAGE -> b.add(new MessageElement.Image(rawRef, null, 0, 0, size));
                        case VOICE -> b.add(new MessageElement.Voice(rawRef, 0));
                        case VIDEO -> b.add(new MessageElement.Video(rawRef));
                        default -> b.add(new MessageElement.File(rawRef, filename, size));
                    }
                }
            }
            return b.build();
        } catch (Exception e) {
            return MessageChain.EMPTY;
        }
    }

    /** QQ 段数组（{type, data}）解析 — 防御式，未知段忽略。 */
    private static void parseSegment(MessageChain.Builder b, ObjectNode seg, String appId) {
        String type = seg.path("type").asText("");
        var data = seg.path("data");
        switch (type) {
            case "text" -> {
                String t = data.path("text").asText("");
                if (!t.isEmpty()) b.text(t);
            }
            case "image" -> {
                String url = data.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    String rawRef = autoDownload(url, dev.xuanji.api.annotation.MediaType.IMAGE, appId);
                    b.add(new MessageElement.Image(rawRef, null, 0, 0, 0));
                }
            }
            case "at" -> {
                String userId = data.path("user_openid").asText(data.path("id").asText(""));
                if (!userId.isBlank()) b.at(userId);
            }
            default -> { /* 未知段忽略，不崩 */ }
        }
    }

    /**
     * 框架层按需下载：URL → 尝试落盘，成功返回 FILE_PATH；失败/未启用/非 URL 返回原 URL。
     * 失败/异常静默吞掉（不影响消息处理，下载失败 = 拿 URL 形态，不阻断命令链）。
     */
    private static String autoDownload(String url, dev.xuanji.api.annotation.MediaType type, String appId) {
        if (appId == null || appId.isBlank() || type == null) {
            if (appId == null || appId.isBlank()) {
                System.out.println("[媒体转换] autoDownload 跳过: appId 为空（调用方未传？）url=" + (url != null && url.length() > 60 ? url.substring(0, 60) + "…" : url));
            }
            return url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url;
        try {
            var path = dev.xuanji.api.media.MediaFileDownloader.download(url, type, appId);
            return path != null ? path.toString() : url;
        } catch (Exception e) {
            return url;
        }
    }
}
