package dev.xuanji.core.bot;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.sdk.bot.XjBot;

/**
 * XjBot 的 QQ 平台实现 — 将 SDK 抽象调用翻译为 QQ Bot API。
 *
 * <p>由 CommandRegistry 在每次请求时创建，通过 ThreadLocal 传递给插件。
 */
public class QqXjBot extends XjBot {

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
        if (groupId != null) sender.sendGroupText(groupId, text, msgId);
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
        ObjectNode kb = null;
        if (keyboardJson != null && !keyboardJson.isBlank()) {
            kb = new ObjectNode(JsonNodeFactory.instance);
            // 键盘 JSON 已由 XjKeyboard 构建好，直接设 content
        }
        sender.sendGroupMarkdown(groupId, md, kb, msgId);
        // TODO: keyboard 解析
    }

    @Override
    public void replyImage(String url) {
        if (groupId != null) sender.sendGroupImage(groupId, url, msgId);
    }

    @Override
    public void replyAudio(String url) {
        if (groupId != null) sender.sendGroupAudio(groupId, url, msgId);
    }

    @Override
    public void replyVideo(String url) {
        if (groupId != null) sender.sendGroupVideo(groupId, url, msgId);
    }

    @Override
    public void replyArk(String templateId, String kvJson) {
        if (groupId == null) return;
        // TODO: 解析 kvJson
        ObjectNode ark = new ObjectNode(JsonNodeFactory.instance);
        ark.put("template_id", Integer.parseInt(templateId));
        sender.sendGroupArk(groupId, ark, msgId);
    }
}
