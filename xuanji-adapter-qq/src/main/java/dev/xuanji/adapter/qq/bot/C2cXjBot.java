package dev.xuanji.adapter.qq.bot;

import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.adapter.qq.storage.BotDataQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.sdk.bot.Bot;

/**
 * Bot 的单聊实现 — 所有 reply/send 走 C2C API。
 */
public class C2cXjBot extends Bot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String openid;
    private final String msgId;
    private final String appId;
    private final dev.xuanji.core.storage.log.MessageLogService logService;

    public C2cXjBot(MessageSender sender, String openid, String msgId, String appId,
                    dev.xuanji.core.storage.log.MessageLogService logService) {
        this.sender = sender;
        this.openid = openid;
        this.msgId = msgId;
        this.appId = appId;
        this.logService = logService;
    }

    @Override public void reply(String text) { sender.sendC2cText(openid, text, msgId); logOut("text", text); }
    @Override public void replyMarkdown(String md) { replyMarkdown(md, null); }
    @Override public void replyMarkdown(String md, String kbJson) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        Object kb = null;
        if (kbJson != null && !kbJson.isBlank()) try { kb = MAPPER.readTree(kbJson); } catch (Exception ignored) {}
        sender.sendC2cMarkdown(openid, m, kb, msgId);
        logOut("markdown", truncate(md));
    }
    @Override public void replyImage(String url) { sender.sendC2cImage(openid, url, msgId); logOut("image", url); }
    @Override public void replyAudio(String url) { sender.sendC2cAudio(openid, url, msgId); logOut("audio", url); }
    @Override public void replyVideo(String url) { sender.sendC2cVideo(openid, url, msgId); logOut("video", url); }
    @Override public void replyArk(int templateId, String arkJson) {
        try { sender.sendC2cArk(openid, (ObjectNode) MAPPER.readTree(arkJson).get("ark"), msgId); logOut("ark", "t=" + templateId); }
        catch (Exception ignored) {}
    }
    @Override public void replyCard(String cardJson) { reply("卡片暂不支持单聊"); }

    @Override public void sendGroup(String gid, String text) {}
    @Override public void sendGroupMarkdown(String gid, String md) {}
    @Override public void sendGroupMarkdown(String gid, String md, String kbJson) {}
    @Override public void sendGroupImage(String gid, String url) {}
    @Override public void sendGroupAudio(String gid, String url) {}
    @Override public void sendGroupVideo(String gid, String url) {}
    @Override public void sendGroupArk(String gid, int id, String json) {}
    @Override public void sendGroupCard(String gid, String json) {}
    @Override public void sendPrivate(String uid, String text) { sender.sendC2cText(uid, text, null); }
    @Override public void sendPrivateMarkdown(String uid, String md) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        sender.sendC2cMarkdown(uid, m, null, null);
    }
    @Override public void sendPrivateImage(String uid, String url) { sender.sendC2cImage(uid, url, null); }
    @Override public void sendPrivateAudio(String uid, String url) { sender.sendC2cAudio(uid, url, null); }

    @Override public String uploadImage(String p) { return null; }
    @Override public String uploadVideo(String p) { return null; }
    @Override public String uploadAudio(String p) { return null; }
    @Override public String uploadFile(String p) { return null; }

    @Override public int getGroupCount() { return dev.xuanji.adapter.qq.storage.BotDataQuery.groupCount(appId); }
    @Override public int getUserCount() { return dev.xuanji.adapter.qq.storage.BotDataQuery.userCount(appId); }
    @Override public java.util.Map<String, String> getBotInfo() { return dev.xuanji.adapter.qq.storage.BotDataQuery.botInfo(appId); }
    @Override public void retractGroupMessage(String messageId) { /* 单聊 Bot 不支持群聊撤回 */ }
    @Override public void retractC2cMessage(String messageId) { sender.retractC2cMessage(openid, messageId); }
    @Override public int getTodayFriendAdd() { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayFriendAdd(appId); }
    @Override public int getTodayFriendDel() { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayFriendDel(appId); }
    @Override public int getTodayGroupAdd() { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayGroupAdd(appId); }
    @Override public int getTodayGroupDel() { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayGroupDel(appId); }
    @Override public int getTodayMemberAdd(String gid) { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayGroupMemberAdd(appId, gid); }
    @Override public int getTodayMemberDel(String gid) { return dev.xuanji.adapter.qq.storage.BotDataQuery.todayGroupMemberDel(appId, gid); }

    private void logOut(String type, String content) {
        logService.logC2cOut(appId, openid, type, content, "");
    }
    private static String truncate(String s) { return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s; }
}
