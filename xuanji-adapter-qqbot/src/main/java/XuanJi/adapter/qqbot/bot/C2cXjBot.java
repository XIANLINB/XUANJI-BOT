package XuanJi.adapter.qqbot.bot;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.adapter.qqbot.storage.BotDataQuery;
import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.core.concurrent.BotOutboundExecutor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import XuanJi.sdk.bot.Bot;

/**
 * Bot 的单聊实现 — 所有 reply/send 走 C2C API，出站经 {@link BotOutboundExecutor} 串行队列（P2-E 节奏控制）。
 */
public class C2cXjBot extends Bot {

    @Override public String selfId() { return appId; }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String openid;
    private final String msgId;
    private final String appId;
    private final QqBotRepository qqRepo;
    private final BotOutboundExecutor outbound;

    public C2cXjBot(MessageSender sender, String openid, String msgId, String appId,
                    QqBotRepository qqRepo, BotOutboundExecutor outbound) {
        this.sender = sender;
        this.openid = openid;
        this.msgId = msgId;
        this.appId = appId;
        this.qqRepo = qqRepo;
        this.outbound = outbound;
    }

    /** 异步出站（per-bot 串行队列）；executor 缺失时退化为同步直发。 */
    private void enqueue(Runnable task) {
        if (outbound == null) { sender.runWithRobotContext(appId, task); return; }
        // 出站线程显式设置 MessageSender 上下文（ThreadLocal 不跨线程，否则多机器人取错凭证 → 11255）
        outbound.submit(appId, () -> sender.runWithRobotContext(appId, task));
    }

    /** 有返回值动作：同步等待节奏后直发。 */
    private void pace() {
        if (outbound != null) outbound.awaitPace(appId);
    }

    @Override public void reply(String text) { enqueue(() -> sender.sendC2cText(openid, text, msgId)); }
    @Override public void replyMarkdown(String md) { replyMarkdown(md, null); }
    @Override public void replyMarkdown(String md, String kbJson) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        Object kb = parseKb(kbJson);
        enqueue(() -> sender.sendC2cMarkdown(openid, m, kb, msgId));
    }
    @Override public void replyImage(String url) { enqueue(() -> sender.sendC2cImage(openid, url, msgId)); }
    @Override public void replyAudio(String url) { enqueue(() -> sender.sendC2cAudio(openid, url, msgId)); }
    @Override public void replyVideo(String url) { enqueue(() -> sender.sendC2cVideo(openid, url, msgId)); }
    @Override public void replyArk(int templateId, String arkJson) {
        try {
            ObjectNode ark = (ObjectNode) MAPPER.readTree(arkJson).get("ark");
            enqueue(() -> sender.sendC2cArk(openid, ark, msgId));
        } catch (Exception ignored) {}
    }
    @Override public void replyCard(String cardJson) { /* 单聊不支持图文卡片，静默忽略 */ }

    @Override public void sendGroup(String gid, String text) {}
    @Override public void sendGroupMarkdown(String gid, String md) {}
    @Override public void sendGroupMarkdown(String gid, String md, String kbJson) {}
    @Override public void sendGroupImage(String gid, String url) {}
    @Override public void sendGroupAudio(String gid, String url) {}
    @Override public void sendGroupVideo(String gid, String url) {}
    @Override public void sendGroupArk(String gid, int id, String json) {}
    @Override public void sendGroupCard(String gid, String json) {}
    @Override public void sendPrivate(String uid, String text) { enqueue(() -> sender.sendC2cText(uid, text, null)); }
    @Override public void sendPrivateMarkdown(String uid, String md) {
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        enqueue(() -> sender.sendC2cMarkdown(uid, m, null, null));
    }
    @Override public void sendPrivateImage(String uid, String url) { enqueue(() -> sender.sendC2cImage(uid, url, null)); }
    @Override public void sendPrivateAudio(String uid, String url) { enqueue(() -> sender.sendC2cAudio(uid, url, null)); }

    @Override public String uploadImage(String p) { pace(); return null; }
    @Override public String uploadVideo(String p) { pace(); return null; }
    @Override public String uploadAudio(String p) { pace(); return null; }
    @Override public String uploadFile(String p) { pace(); return null; }

    private static Object parseKb(String kbJson) {
        if (kbJson == null || kbJson.isBlank()) return null;
        try { return MAPPER.readTree(kbJson); } catch (Exception e) { return null; }
    }
    private static String truncate(String s) { return s != null && s.length() > 60 ? s.substring(0, 57) + "..." : s; }

    @Override public int getGroupCount() { return BotDataQuery.groupCount(appId); }
    @Override public int getUserCount() { return BotDataQuery.userCount(appId); }
    @Override public java.util.Map<String, String> getBotInfo() { return BotDataQuery.botInfo(appId); }
    @Override public void retractGroupMessage(String messageId) { /* 单聊 Bot 不支持群聊撤回 */ }
    @Override public void retractC2cMessage(String messageId) { enqueue(() -> sender.retractC2cMessage(openid, messageId)); }
    @Override public int getTodayFriendAdd() { return BotDataQuery.todayFriendAdd(appId); }
    @Override public int getTodayFriendDel() { return BotDataQuery.todayFriendDel(appId); }
    @Override public int getTodayGroupAdd() { return BotDataQuery.todayGroupAdd(appId); }
    @Override public int getTodayGroupDel() { return BotDataQuery.todayGroupDel(appId); }
    @Override public int getTodayMemberAdd(String gid) { return BotDataQuery.todayGroupMemberAdd(appId, gid); }
    @Override public int getTodayMemberDel(String gid) { return BotDataQuery.todayGroupMemberDel(appId, gid); }
}
