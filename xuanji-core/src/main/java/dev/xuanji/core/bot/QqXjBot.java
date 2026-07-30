package dev.xuanji.core.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.sdk.bot.XjBot;

public class QqXjBot extends XjBot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String groupId;
    private final String msgId;

    public QqXjBot(MessageSender sender, String groupId, String msgId) {
        this.sender = sender;
        this.groupId = groupId;
        this.msgId = msgId;
    }

    @Override
    public void reply(String text) {
        if (groupId != null) {
            sender.sendGroupText(groupId, text, msgId);
            recordOut("text", text, text);
        }
    }

    @Override
    public void replyMarkdown(String markdownContent) {
        replyMarkdown(markdownContent, null);
    }

    @Override
    public void replyMarkdown(String markdownContent, String keyboardJson) {
        if (groupId == null) return;
        ObjectNode md = new ObjectNode(JsonNodeFactory.instance);
        md.put("content", markdownContent);
        Object kb = null;
        if (keyboardJson != null && !keyboardJson.isBlank()) {
            try { kb = MAPPER.readTree(keyboardJson); } catch (Exception ignored) {}
        }
        sender.sendGroupMarkdown(groupId, md, kb, msgId);
        recordOut("markdown", truncate(markdownContent), "kb=" + (kb != null));
    }

    @Override
    public void replyImage(String url) {
        if (groupId != null) {
            sender.sendGroupImage(groupId, url, msgId);
            recordOut("image", truncate(url), url);
        }
    }

    @Override
    public void replyAudio(String url) {
        if (groupId != null) {
            sender.sendGroupAudio(groupId, url, msgId);
            recordOut("audio", truncate(url), url);
        }
    }

    @Override
    public void replyVideo(String url) {
        if (groupId != null) {
            sender.sendGroupVideo(groupId, url, msgId);
            recordOut("video", truncate(url), url);
        }
    }

    @Override
    public void replyArk(String templateId, String kvJson) {
        if (groupId == null) return;
        ObjectNode ark = new ObjectNode(JsonNodeFactory.instance);
        ark.put("template_id", Integer.parseInt(templateId));
        sender.sendGroupArk(groupId, ark, msgId);
        recordOut("ark", "template=" + templateId, kvJson);
    }

    private void recordOut(String type, String content, String detail) {
        dev.xuanji.core.storage.ConsoleApiController.recordEvent(
                "OUT", type, "插件", groupId, content, detail);
    }

    private static String truncate(String s) {
        return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
