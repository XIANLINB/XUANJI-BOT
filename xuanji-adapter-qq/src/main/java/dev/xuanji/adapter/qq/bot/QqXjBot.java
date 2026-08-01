package dev.xuanji.adapter.qq.bot;

import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.adapter.qq.storage.BotDataQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.sdk.bot.Bot;

public class QqXjBot extends Bot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String groupId;
    private final String msgId;
    private final String appId;
    private final dev.xuanji.core.storage.log.MessageLogService logService;

    public QqXjBot(MessageSender sender, String groupId, String msgId, String appId,
                   dev.xuanji.core.storage.log.MessageLogService logService) {
        this.sender = sender;
        this.groupId = groupId;
        this.msgId = msgId;
        this.appId = appId;
        this.logService = logService;
    }

    // ==================== 被动回复 ====================

    @Override public void reply(String text) {
        if (groupId != null) { sender.sendGroupText(groupId, text, msgId); record("text", text); }
    }
    @Override public void replyMarkdown(String md) { replyMarkdown(md, null); }
    @Override
    public void replyMarkdown(String md, String kbJson) {
        if (groupId == null) return;
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance);
        m.put("content", md);
        Object kb = null;
        if (kbJson != null && !kbJson.isBlank()) try { kb = MAPPER.readTree(kbJson); } catch (Exception ignored) {}
        sender.sendGroupMarkdown(groupId, m, kb, msgId);
        record("markdown", truncate(md));
    }
    @Override public void replyImage(String url)  { if (groupId != null) { sender.sendGroupImage(groupId, url, msgId); record("image", url); } }
    @Override public void replyAudio(String url)  { if (groupId != null) { sender.sendGroupAudio(groupId, url, msgId); record("audio", url); } }
    @Override public void replyVideo(String url)  { if (groupId != null) { sender.sendGroupVideo(groupId, url, msgId); record("video", url); } }
    @Override
    public void replyArk(int templateId, String arkJson) {
        if (groupId == null) return;
        try { sender.sendGroupArk(groupId, (ObjectNode) MAPPER.readTree(arkJson).get("ark"), msgId); record("ark", "t=" + templateId); }
        catch (Exception e) { reply("Ark 错误: " + e.getMessage()); }
    }
    @Override
    public void replyCard(String cardJson) {
        if (groupId == null) return;
        try { sender.sendGroupCard(groupId, MAPPER.readTree(cardJson), msgId); record("card", "tuwen"); }
        catch (Exception e) { reply("Card 错误: " + e.getMessage()); }
    }

    // ==================== 主动发送（不带 msg_id） ====================

    @Override public void sendGroup(String gid, String text) { sender.sendGroupText(gid, text, null); }
    @Override
    public void sendGroupMarkdown(String gid, String md) { sendGroupMarkdown(gid, md, null); }
    @Override
    public void sendGroupMarkdown(String gid, String md, String kbJson) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        Object kb = null;
        if (kbJson != null && !kbJson.isBlank()) try { kb = MAPPER.readTree(kbJson); } catch (Exception ignored) {}
        sender.sendGroupMarkdown(gid, m, kb, null);
    }
    @Override public void sendGroupImage(String gid, String url)  { sender.sendGroupImage(gid, url, null); }
    @Override public void sendGroupAudio(String gid, String url)  { sender.sendGroupAudio(gid, url, null); }
    @Override public void sendGroupVideo(String gid, String url)  { sender.sendGroupVideo(gid, url, null); }
    @Override
    public void sendGroupArk(String gid, int templateId, String arkJson) {
        try { sender.sendGroupArk(gid, (ObjectNode) MAPPER.readTree(arkJson).get("ark"), null); }
        catch (Exception ignored) {}
    }
    @Override
    public void sendGroupCard(String gid, String cardJson) {
        try { sender.sendGroupCard(gid, MAPPER.readTree(cardJson), null); } catch (Exception ignored) {}
    }
    @Override public void sendPrivate(String uid, String text)      { sender.sendC2cText(uid, text, null); }
    @Override public void sendPrivateMarkdown(String uid, String md) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        sender.sendC2cMarkdown(uid, m, null, null);
    }
    @Override public void sendPrivateImage(String uid, String url)  { sender.sendC2cImage(uid, url, null); }
    @Override public void sendPrivateAudio(String uid, String url)  { sender.sendC2cAudio(uid, url, null); }

    // ==================== 媒体上传 ====================

    @Override public String uploadImage(String path) { return sender.uploadMedia(groupId, 1, path); }
    @Override public String uploadVideo(String path) { return sender.uploadMedia(groupId, 2, path); }
    @Override public String uploadAudio(String path) { return sender.uploadMedia(groupId, 3, path); }
    @Override public String uploadFile(String path)  { return sender.uploadMedia(groupId, 4, path); }

    private void record(String type, String content) {
        dev.xuanji.core.storage.ConsoleApiController.recordEvent("OUT", type, "插件", groupId, content, "");
        logService.logGroupOut(appId, groupId, "bot", type, content, "");
    }
    private static String truncate(String s) { return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s; }

    @Override public int getGroupCount() { return dev.xuanji.adapter.qq.storage.BotDataQuery.groupCount(appId); }
    @Override public int getUserCount() { return dev.xuanji.adapter.qq.storage.BotDataQuery.userCount(appId); }
    @Override public java.util.Map<String, String> getBotInfo() { return dev.xuanji.adapter.qq.storage.BotDataQuery.botInfo(appId); }
    @Override public void retractGroupMessage(String messageId) { sender.retractGroupMessage(groupId, messageId); }
    @Override public void retractC2cMessage(String messageId) { /* 群聊 Bot 不支持单聊撤回 */ }
    @Override public int getTodayFriendAdd() { return BotDataQuery.todayFriendAdd(appId); }
    @Override public int getTodayFriendDel() { return BotDataQuery.todayFriendDel(appId); }
    @Override public int getTodayGroupAdd() { return BotDataQuery.todayGroupAdd(appId); }
    @Override public int getTodayGroupDel() { return BotDataQuery.todayGroupDel(appId); }
    @Override public int getTodayMemberAdd(String gid) { return BotDataQuery.todayGroupMemberAdd(appId, gid); }
    @Override public int getTodayMemberDel(String gid) { return BotDataQuery.todayGroupMemberDel(appId, gid); }
}
