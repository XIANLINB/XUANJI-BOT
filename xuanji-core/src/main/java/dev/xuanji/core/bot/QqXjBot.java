package dev.xuanji.core.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.sdk.bot.Bot;

public class QqXjBot extends Bot {

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
        if (groupId != null) { sender.sendGroupText(groupId, text, msgId); record("text", text); }
    }

    @Override
    public void replyMarkdown(String md) { replyMarkdown(md, null); }

    @Override
    public void replyMarkdown(String md, String kbJson) {
        if (groupId == null) return;
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance);
        m.put("content", md);
        Object kb = null;
        if (kbJson != null && !kbJson.isBlank()) {
            try { kb = MAPPER.readTree(kbJson); } catch (Exception ignored) {}
        }
        sender.sendGroupMarkdown(groupId, m, kb, msgId);
        record("markdown", truncate(md));
    }

    @Override
    public void replyImage(String url) {
        if (groupId != null) { sender.sendGroupImage(groupId, url, msgId); record("image", url); }
    }

    @Override
    public void replyAudio(String url) {
        if (groupId != null) { sender.sendGroupAudio(groupId, url, msgId); record("audio", url); }
    }

    @Override
    public void replyVideo(String url) {
        if (groupId != null) { sender.sendGroupVideo(groupId, url, msgId); record("video", url); }
    }

    @Override
    public void replyArk(int templateId, String arkJson) {
        if (groupId == null) return;
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(arkJson);
            ObjectNode ark = (ObjectNode) root.get("ark");
            sender.sendGroupArk(groupId, ark, msgId);
            record("ark", "template=" + templateId);
        } catch (Exception e) {
            reply("Ark 消息解析失败: " + e.getMessage());
        }
    }

    @Override
    public void replyCard(String cardJson) {
        if (groupId == null) return;
        try {
            ObjectNode card = (ObjectNode) MAPPER.readTree(cardJson);
            sender.sendGroupCard(groupId, card, msgId);
            record("card", "tuwen");
        } catch (Exception e) {
            reply("Card 消息解析失败: " + e.getMessage());
        }
    }

    @Override
    public String uploadImage(String filePath) {
        return sender.uploadMedia(groupId, 1, filePath);  // file_type=1 图片
    }

    @Override
    public String uploadVideo(String filePath) {
        return sender.uploadMedia(groupId, 2, filePath);  // file_type=2 视频
    }

    @Override
    public String uploadAudio(String filePath) {
        return sender.uploadMedia(groupId, 3, filePath);  // file_type=3 语音
    }

    @Override
    public String uploadFile(String filePath) {
        return sender.uploadMedia(groupId, 4, filePath);  // file_type=4 文件
    }

    private void record(String type, String content) {
        dev.xuanji.core.storage.ConsoleApiController.recordEvent("OUT", type, "插件", groupId, content, "");
    }

    private static String truncate(String s) {
        return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
