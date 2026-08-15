package XuanJi.adapter.qqbot.bot;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.adapter.qqbot.storage.BotDataQuery;
import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.BotGroupState;
import XuanJi.api.plugin.GroupBotRole;
import XuanJi.api.plugin.GroupInfo;
import XuanJi.api.plugin.GroupMember;
import XuanJi.api.plugin.GroupMuteStatus;
import XuanJi.api.plugin.JoinRequestList;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.UserInfo;
import XuanJi.api.sender.XuanJiMessageSender;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;
import XuanJi.core.concurrent.BotOutboundExecutor;
import XuanJi.core.storage.MessageEventRecorder;
import XuanJi.sdk.bot.Bot;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Bot 的群聊实现 — 出站统一经 {@link BotOutboundExecutor} 串行队列（P2-E 节奏控制），
 * 实时事件面板经 {@link MessageEventRecorder} 上报。
 *
 * <p>群管/查询/好友审批/撤回统一动作经 {@link BotActionSupport} 走 {@link PlatformActionHub}
 * （中枢自动剥 {@code qq:} 前缀并绑定机器人上下文）；主动发送消息链经
 * {@link XuanJiMessageSender}（运行时 PROACTIVE_MESSAGE 闸门控制）。
 */
@Slf4j
public class QqXjBot extends Bot {

    @Override public String selfId() { return appId; }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageSender sender;
    private final String groupId;
    private final String msgId;
    private final String appId;
    private final QqBotRepository qqRepo;
    private final BotOutboundExecutor outbound;
    private final MessageEventRecorder eventRecorder;
    private final XuanJiMessageSender messageSender;
    private final BotActionSupport actions;

    public QqXjBot(MessageSender sender, String groupId, String msgId, String appId,
                   QqBotRepository qqRepo, BotOutboundExecutor outbound,
                   MessageEventRecorder eventRecorder, PlatformActionHub actionHub,
                   XuanJiMessageSender messageSender) {
        this.sender = sender;
        this.groupId = groupId;
        this.msgId = msgId;
        this.appId = appId;
        this.qqRepo = qqRepo;
        this.outbound = outbound;
        this.eventRecorder = eventRecorder;
        this.messageSender = messageSender;
        this.actions = new BotActionSupport(actionHub, appId);
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
        } catch (Exception e) { log.warn("[QQ群Bot] Ark 构造失败，不发送: {}", e.getMessage()); }
    }
    @Override
    public void replyCard(String cardJson) {
        if (groupId == null) return;
        try {
            var card = MAPPER.readTree(cardJson);
            enqueue(() -> sender.sendGroupCard(groupId, card, msgId));
            record("card", "tuwen");
        } catch (Exception e) { log.warn("[QQ群Bot] Card 构造失败，不发送: {}", e.getMessage()); }
    }

    // ==================== 主动发送（不带 msg_id，受 PROACTIVE_MESSAGE 闸门） ====================

    @Override public void sendGroup(String gid, String text) { ensureProactive(); enqueue(() -> sender.sendGroupText(gid, text, null)); }
    @Override
    public void sendGroupMarkdown(String gid, String md) { ensureProactive(); sendGroupMarkdown(gid, md, null); }
    @Override
    public void sendGroupMarkdown(String gid, String md, String kbJson) {
        ensureProactive();
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        Object kb = parseKb(kbJson);
        enqueue(() -> sender.sendGroupMarkdown(gid, m, kb, null));
    }
    @Override public void sendGroupImage(String gid, String url)  { ensureProactive(); enqueue(() -> sender.sendGroupImage(gid, url, null)); }
    @Override public void sendGroupAudio(String gid, String url)  { ensureProactive(); enqueue(() -> sender.sendGroupAudio(gid, url, null)); }
    @Override public void sendGroupVideo(String gid, String url)  { ensureProactive(); enqueue(() -> sender.sendGroupVideo(gid, url, null)); }
    @Override
    public void sendGroupArk(String gid, int templateId, String arkJson) {
        ensureProactive();
        try {
            ObjectNode ark = (ObjectNode) MAPPER.readTree(arkJson).get("ark");
            enqueue(() -> sender.sendGroupArk(gid, ark, null));
        } catch (Exception ignored) {}
    }
    @Override
    public void sendGroupCard(String gid, String cardJson) {
        ensureProactive();
        try {
            var card = MAPPER.readTree(cardJson);
            enqueue(() -> sender.sendGroupCard(gid, card, null));
        } catch (Exception ignored) {}
    }
    @Override public void sendPrivate(String uid, String text)      { ensureProactive(); enqueue(() -> sender.sendC2cText(uid, text, null)); }
    @Override public void sendPrivateMarkdown(String uid, String md) {
        ensureProactive();
        ObjectNode m = new ObjectNode(JsonNodeFactory.instance); m.put("content", md);
        enqueue(() -> sender.sendC2cMarkdown(uid, m, null, null));
    }
    @Override public void sendPrivateImage(String uid, String url)  { ensureProactive(); enqueue(() -> sender.sendC2cImage(uid, url, null)); }
    @Override public void sendPrivateAudio(String uid, String url)  { ensureProactive(); enqueue(() -> sender.sendC2cAudio(uid, url, null)); }

    // ==================== 主动发送消息链（返回回执，受闸门控制） ====================

    @Override
    public XuanJiSendReceipt sendToGroup(String gid, XuanJiMessage chain) {
        ensureProactive();
        XuanJiSendReceipt[] box = new XuanJiSendReceipt[1];
        sender.runWithRobotContext(appId, () -> box[0] = messageSender.send(new XuanJiTarget.Group(gid), chain));
        return box[0] != null ? box[0] : XuanJiSendReceipt.fail("发送无回执", 0);
    }
    @Override
    public XuanJiSendReceipt sendToPrivate(String uid, XuanJiMessage chain) {
        ensureProactive();
        XuanJiSendReceipt[] box = new XuanJiSendReceipt[1];
        sender.runWithRobotContext(appId, () -> box[0] = messageSender.send(new XuanJiTarget.Private(uid), chain));
        return box[0] != null ? box[0] : XuanJiSendReceipt.fail("发送无回执", 0);
    }

    // ==================== 群管动作（返回 OpResult） ====================

    @Override public OpResult muteGroupMember(String g, String m, int min) { ensureGroupAdmin(); return actions.muteGroupMember(g, m, min); }
    @Override public OpResult muteGroupMembers(String g, List<String> ms, int min) { ensureGroupAdmin(); return actions.muteGroupMembers(g, ms, min); }
    @Override public OpResult kickGroupMember(String g, String m) { ensureGroupAdmin(); return actions.kickGroupMember(g, m); }
    @Override public OpResult setGroupCard(String g, String m, String card) { ensureGroupAdmin(); return actions.setGroupCard(g, m, card); }
    @Override public OpResult setGroupAdmin(String g, String m, boolean admin) { ensureGroupAdmin(); return actions.setGroupAdmin(g, m, admin); }
    @Override public OpResult approveGroupJoinRequest(String g, String m, String rid, boolean a, String r) {
        ensureGroupAdmin();
        return actions.approveGroupJoinRequest(g, m, rid, a, r);
    }
    @Override public OpResult approveFriendRequest(String o, boolean a, String r) { ensureGroupAdmin(); return actions.approveFriendRequest(o, a, r); }

    // ==================== 撤回（返回 OpResult） ====================

    @Override public OpResult recallGroupMessage(String groupId, String messageId) { ensureGroupAdmin(); return actions.recallGroupMessage(groupId, messageId); }
    @Override public OpResult recallRecentMessages(String groupId, String memberOpenid, int count) {
        ensureGroupAdmin();
        return actions.recallRecentMessages(groupId, memberOpenid, count);
    }
    @Override public OpResult recallPrivateMessage(String openid, String messageId) { ensureGroupAdmin(); return actions.recallPrivateMessage(openid, messageId); }

    // ==================== 平台信息查询（返回类型化结果） ====================

    @Override public GroupInfo getGroupInfo(String g) { return actions.getGroupInfo(g); }
    @Override public GroupInfo getLocalGroupInfo(String g) { return actions.getLocalGroupInfo(g); }
    @Override public BotGroupState getBotGroupState(String g) { return actions.getBotGroupState(g); }
    @Override public GroupMuteStatus getGroupMuteStatus(String g) { return actions.getGroupMuteStatus(g); }
    @Override public JoinRequestList listGroupJoinRequests(String g) { return actions.listGroupJoinRequests(g); }
    @Override public List<GroupMember> listGroupMembers(String g) { return actions.listGroupMembers(g); }
    @Override public List<GroupInfo> listGroups() { return actions.listGroups(); }
    @Override public GroupBotRole getGroupBotRole(String g) { return actions.getGroupBotRole(g); }
    @Override public List<UserInfo> listUsers() { return actions.listUsers(); }
    @Override public UserInfo getUserInfo(String o) { return actions.getUserInfo(o); }

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
    @Override public Map<String, String> getBotInfo() { return BotDataQuery.botInfo(appId); }
    @Override public int getTodayFriendAdd() { return BotDataQuery.todayFriendAdd(appId); }
    @Override public int getTodayFriendDel() { return BotDataQuery.todayFriendDel(appId); }
    @Override public int getTodayGroupAdd() { return BotDataQuery.todayGroupAdd(appId); }
    @Override public int getTodayGroupDel() { return BotDataQuery.todayGroupDel(appId); }
    @Override public int getTodayMemberAdd(String gid) { return BotDataQuery.todayGroupMemberAdd(appId, gid); }
    @Override public int getTodayMemberDel(String gid) { return BotDataQuery.todayGroupMemberDel(appId, gid); }
}
