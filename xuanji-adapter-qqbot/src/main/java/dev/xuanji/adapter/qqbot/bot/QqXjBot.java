package dev.xuanji.adapter.qqbot.bot;

import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.adapter.qqbot.storage.BotDataQuery;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.storage.MessageEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.sdk.bot.Bot;

/**
 * Bot 的群聊实现 — 出站统一经 {@link BotOutboundExecutor} 串行队列（P2-E 节奏控制），
 * 实时事件面板经 {@link MessageEventRecorder} 上报。
 */
public class QqXjBot extends Bot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String groupId;
    private final String msgId;
    private final String appId;
    private final QqBotRepository qqRepo;
    private final BotOutboundExecutor outbound;
    private final MessageEventRecorder eventRecorder;

    public QqXjBot(MessageSender sender, String groupId, String msgId, String appId,
                   QqBotRepository qqRepo, BotOutboundExecutor outbound,
                   MessageEventRecorder eventRecorder) {
        this.sender = sender;
        this.groupId = groupId;
        this.msgId = msgId;
        this.appId = appId;
        this.qqRepo = qqRepo;
        this.outbound = outbound;
        this.eventRecorder = eventRecorder;
    }

    /** 异步出站（per-bot 串行队列，出队前补亏空睡眠）；executor 缺失时退化为同步直发。 */
    private void enqueue(Runnable task) {
        if (outbound == null) { sender.runWithRobotContext(appId, task); return; }
        // 出站线程显式设置 MessageSender 上下文（ThreadLocal 不跨线程，否则多机器人取错凭证 → 11255）
        outbound.submit(appId, () -> sender.runWithRobotContext(appId, task));
    }

    /** 有返回值动作：同步等待节奏后直发。 */
    private void pace() {
        if (outbound != null) outbound.awaitPace(appId);
    }

    // ==================== 被动回复 ====================

    @Override public void reply(String text) {
        if (groupId != null) { enqueue(() -> sender.sendGroupText(groupId, text, msgId)); record("text", text); }
    }
    @Override public void replyMarkdown(String md) { replyMarkdown(md, null); }
    @Override
    public void replyMarkdown(String md, String kbJson) {
        if (groupId == null) return;
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance);
        m.put("content", md);
        Object kb = parseKb(kbJson);
        enqueue(() -> sender.sendGroupMarkdown(groupId, m, kb, msgId));
        record("markdown", truncate(md));
    }
    @Override public void replyImage(String url)  { if (groupId != null) { enqueue(() -> sender.sendGroupImage(groupId, url, msgId)); record("image", url); } }
    @Override public void replyAudio(String url)  { if (groupId != null) { enqueue(() -> sender.sendGroupAudio(groupId, url, msgId)); record("audio", url); } }
    @Override public void replyVideo(String url)  { if (groupId != null) { enqueue(() -> sender.sendGroupVideo(groupId, url, msgId)); record("video", url); } }
    @Override
    public void replyArk(int templateId, String arkJson) {
        if (groupId == null) return;
        try {
            ObjectNode ark = (ObjectNode) MAPPER.readTree(arkJson).get("ark");
            enqueue(() -> sender.sendGroupArk(groupId, ark, msgId));
            record("ark", "t=" + templateId);
        } catch (Exception e) { reply("Ark 错误: " + e.getMessage()); }
    }
    @Override
    public void replyCard(String cardJson) {
        if (groupId == null) return;
        try {
            var card = MAPPER.readTree(cardJson);
            enqueue(() -> sender.sendGroupCard(groupId, card, msgId));
            record("card", "tuwen");
        } catch (Exception e) { reply("Card 错误: " + e.getMessage()); }
    }

    // ==================== 主动发送（不带 msg_id） ====================

    @Override public void sendGroup(String gid, String text) { enqueue(() -> sender.sendGroupText(gid, text, null)); }
    @Override
    public void sendGroupMarkdown(String gid, String md) { sendGroupMarkdown(gid, md, null); }
    @Override
    public void sendGroupMarkdown(String gid, String md, String kbJson) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        Object kb = parseKb(kbJson);
        enqueue(() -> sender.sendGroupMarkdown(gid, m, kb, null));
    }
    @Override public void sendGroupImage(String gid, String url)  { enqueue(() -> sender.sendGroupImage(gid, url, null)); }
    @Override public void sendGroupAudio(String gid, String url)  { enqueue(() -> sender.sendGroupAudio(gid, url, null)); }
    @Override public void sendGroupVideo(String gid, String url)  { enqueue(() -> sender.sendGroupVideo(gid, url, null)); }
    @Override
    public void sendGroupArk(String gid, int templateId, String arkJson) {
        try {
            ObjectNode ark = (ObjectNode) MAPPER.readTree(arkJson).get("ark");
            enqueue(() -> sender.sendGroupArk(gid, ark, null));
        } catch (Exception ignored) {}
    }
    @Override
    public void sendGroupCard(String gid, String cardJson) {
        try {
            var card = MAPPER.readTree(cardJson);
            enqueue(() -> sender.sendGroupCard(gid, card, null));
        } catch (Exception ignored) {}
    }
    @Override public void sendPrivate(String uid, String text)      { enqueue(() -> sender.sendC2cText(uid, text, null)); }
    @Override public void sendPrivateMarkdown(String uid, String md) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        enqueue(() -> sender.sendC2cMarkdown(uid, m, null, null));
    }
    @Override public void sendPrivateImage(String uid, String url)  { enqueue(() -> sender.sendC2cImage(uid, url, null)); }
    @Override public void sendPrivateAudio(String uid, String url)  { enqueue(() -> sender.sendC2cAudio(uid, url, null)); }

    // ==================== 媒体上传（有返回值：同步走节奏） ====================

    @Override public String uploadImage(String path) { pace(); return sender.uploadMedia(groupId, 1, path); }
    @Override public String uploadVideo(String path) { pace(); return sender.uploadMedia(groupId, 2, path); }
    @Override public String uploadAudio(String path) { pace(); return sender.uploadMedia(groupId, 3, path); }
    @Override public String uploadFile(String path)  { pace(); return sender.uploadMedia(groupId, 4, path); }

    private void record(String type, String content) {
        eventRecorder.record("OUT", type, "插件", groupId, content, "");
    }
    private static String truncate(String s) { return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s; }

    private static Object parseKb(String kbJson) {
        if (kbJson == null || kbJson.isBlank()) return null;
        try { return MAPPER.readTree(kbJson); } catch (Exception e) { return null; }
    }

    @Override public int getGroupCount() { return BotDataQuery.groupCount(appId); }
    @Override public int getUserCount() { return BotDataQuery.userCount(appId); }
    @Override public java.util.Map<String, String> getBotInfo() { return BotDataQuery.botInfo(appId); }
    @Override public void retractGroupMessage(String messageId) { enqueue(() -> sender.retractGroupMessage(groupId, messageId)); }
    @Override public void retractC2cMessage(String messageId) { /* 群聊 Bot 不支持单聊撤回 */ }
    @Override public int getTodayFriendAdd() { return BotDataQuery.todayFriendAdd(appId); }
    @Override public int getTodayFriendDel() { return BotDataQuery.todayFriendDel(appId); }
    @Override public int getTodayGroupAdd() { return BotDataQuery.todayGroupAdd(appId); }
    @Override public int getTodayGroupDel() { return BotDataQuery.todayGroupDel(appId); }
    @Override public int getTodayMemberAdd(String gid) { return BotDataQuery.todayGroupMemberAdd(appId, gid); }
    @Override public int getTodayMemberDel(String gid) { return BotDataQuery.todayGroupMemberDel(appId, gid); }
}
