package dev.xuanji.adapter.onebot.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.sdk.bot.Bot;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * OneBot 平台的插件门面 Bot —— 实现 {@link dev.xuanji.sdk.bot.Bot} 抽象。
 *
 * <p>插件写 {@code bot.reply("hi")} 时无需知道底下是 QQ 官方 API 还是 Napcat，
 * 由框架按当前事件所属平台注入对应实现。
 *
 * <p>每条事件构造一个实例，携带该事件的 groupId / userId / msgId 上下文。
 */
@Slf4j
public class OneBotXjBot extends Bot {

    private final OneBotApiService api;
    private final OneBotMessageSenderImpl sender;
    /** 当前事件的 selfId（用于多 bot 场景路由到正确的连接） */
    private final String selfId;
    /** 当前事件的群号；私聊事件为 null */
    private final String groupId;
    /** 当前事件的发送者 QQ 号 */
    private final String userId;
    /** 当前事件的消息 ID（被动回复用） */
    private final String msgId;

    public OneBotXjBot(OneBotApiService api, OneBotMessageSenderImpl sender,
                       String selfId, String groupId, String userId, String msgId) {
        this.api = api;
        this.sender = sender;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.msgId = msgId;
    }

    // ==================== 被动回复 ====================

    @Override
    public void reply(String text) {
        replyChain(MessageChain.text(text));
    }

    @Override
    public void replyMarkdown(String markdownContent) {
        // OneBot v11 无原生 Markdown，降级为纯文本发送
        replyChain(MessageChain.builder().markdown(markdownContent).build());
    }

    @Override
    public void replyMarkdown(String markdownContent, String keyboardJson) {
        // 键盘为 QQ 官方特性，OneBot 侧无对应，忽略键盘参数
        replyMarkdown(markdownContent);
    }

    @Override
    public void replyImage(String url) {
        replyChain(MessageChain.builder().image(url).build());
    }

    @Override
    public void replyAudio(String url) {
        replyChain(MessageChain.builder()
                .add(new dev.xuanji.api.message.MessageElement.Voice(url, 0)).build());
    }

    @Override
    public void replyVideo(String url) {
        replyChain(MessageChain.builder()
                .add(new dev.xuanji.api.message.MessageElement.Video(url)).build());
    }

    @Override
    public void replyArk(int templateId, String arkJson) {
        replyChain(MessageChain.builder()
                .add(new dev.xuanji.api.message.MessageElement.Ark(templateId, arkJson)).build());
    }

    @Override
    public void replyCard(String cardJson) {
        // OneBot 用 json 段承载卡片
        replyChain(MessageChain.builder()
                .add(new dev.xuanji.api.message.MessageElement.Passthrough("onebot", "json卡片", cardJson))
                .build());
    }

    /** 统一的被动回复出口：自动带引用段，按群/私聊分流 */
    private void replyChain(MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        if (msgId != null && !msgId.isBlank()) {
            ObjectNode replySeg = Json.obj();
            replySeg.put("type", "reply");
            ObjectNode d = Json.obj();
            d.put("id", msgId);
            replySeg.set("data", d);
            segments.insert(0, replySeg);
        }
        if (groupId != null && !groupId.isBlank()) {
            sender.sendGroup(selfId, groupId, segments);
        } else if (userId != null && !userId.isBlank()) {
            sender.sendPrivate(selfId, userId, segments);
        } else {
            log.warn("[OneBot] reply 无有效目标: selfId={}", selfId);
        }
    }

    // ==================== 主动发送 ====================

    @Override
    public void sendGroup(String gid, String text) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(MessageChain.text(text)));
    }

    @Override
    public void sendGroupMarkdown(String gid, String markdownContent) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder().markdown(markdownContent).build()));
    }

    @Override
    public void sendGroupMarkdown(String gid, String markdownContent, String keyboardJson) {
        sendGroupMarkdown(gid, markdownContent);
    }

    @Override
    public void sendGroupImage(String gid, String url) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder().image(url).build()));
    }

    @Override
    public void sendGroupAudio(String gid, String url) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder().add(new dev.xuanji.api.message.MessageElement.Voice(url, 0)).build()));
    }

    @Override
    public void sendGroupVideo(String gid, String url) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder().add(new dev.xuanji.api.message.MessageElement.Video(url)).build()));
    }

    @Override
    public void sendGroupArk(String gid, int templateId, String arkJson) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder()
                        .add(new dev.xuanji.api.message.MessageElement.Ark(templateId, arkJson)).build()));
    }

    @Override
    public void sendGroupCard(String gid, String cardJson) {
        sender.sendGroup(selfId, gid, OneBotMessageConverter.toSegments(
                MessageChain.builder()
                        .add(new dev.xuanji.api.message.MessageElement.Passthrough("onebot", "json卡片", cardJson))
                        .build()));
    }

    @Override
    public void sendPrivate(String uid, String text) {
        sender.sendPrivate(selfId, uid, OneBotMessageConverter.toSegments(MessageChain.text(text)));
    }

    @Override
    public void sendPrivateMarkdown(String uid, String markdownContent) {
        sender.sendPrivate(selfId, uid, OneBotMessageConverter.toSegments(
                MessageChain.builder().markdown(markdownContent).build()));
    }

    @Override
    public void sendPrivateImage(String uid, String url) {
        sender.sendPrivate(selfId, uid, OneBotMessageConverter.toSegments(
                MessageChain.builder().image(url).build()));
    }

    @Override
    public void sendPrivateAudio(String uid, String url) {
        sender.sendPrivate(selfId, uid, OneBotMessageConverter.toSegments(
                MessageChain.builder().add(new dev.xuanji.api.message.MessageElement.Voice(url, 0)).build()));
    }

    // ==================== 媒体上传 ====================
    // OneBot v11 没有"先上传拿 file_id 再发送"的两段式协议：
    // 本地文件用 file:// 前缀、网络图用 http(s)://、二进制用 base64://，
    // 由 OneBot 实现自行取用。此处统一转成 file:// URI 直接返回。

    @Override
    public String uploadImage(String filePath) {
        return toFileUri(filePath);
    }

    @Override
    public String uploadVideo(String filePath) {
        return toFileUri(filePath);
    }

    @Override
    public String uploadAudio(String filePath) {
        return toFileUri(filePath);
    }

    @Override
    public String uploadFile(String filePath) {
        return toFileUri(filePath);
    }

    private static String toFileUri(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String p = filePath.trim();
        if (p.startsWith("http://") || p.startsWith("https://")
                || p.startsWith("file://") || p.startsWith("base64://")) {
            return p;
        }
        return "file:///" + p.replace('\\', '/');
    }

    // ==================== 消息撤回 ====================

    @Override
    public void retractGroupMessage(String messageId) {
        sender.recall(selfId, messageId);
    }

    @Override
    public void retractC2cMessage(String messageId) {
        // OneBot 的 delete_msg 不区分群聊/私聊
        sender.recall(selfId, messageId);
    }

    // ==================== 请求处理（好友 / 加群） ====================
    // flag 取自事件 platformData：event.platformData().get("flag").asText()

    /** 通过好友请求 */
    public SendReceipt approveFriendRequest(String flag, String remark) {
        return sender.handleFriendRequest(selfId, flag, true, remark);
    }

    /** 拒绝好友请求 */
    public SendReceipt rejectFriendRequest(String flag, String remark) {
        return sender.handleFriendRequest(selfId, flag, false, remark);
    }

    /** 通过加群申请（request_type=group, sub_type=add） */
    public SendReceipt approveGroupAddRequest(String flag, String reason) {
        return sender.handleGroupRequest(selfId, flag, "add", true, reason);
    }

    /** 拒绝加群申请 */
    public SendReceipt rejectGroupAddRequest(String flag, String reason) {
        return sender.handleGroupRequest(selfId, flag, "add", false, reason);
    }

    /** 通过加群邀请（request_type=group, sub_type=invite） */
    public SendReceipt approveGroupInvite(String flag) {
        return sender.handleGroupRequest(selfId, flag, "invite", true, null);
    }

    /** 拒绝加群邀请 */
    public SendReceipt rejectGroupInvite(String flag, String reason) {
        return sender.handleGroupRequest(selfId, flag, "invite", false, reason);
    }

    // ==================== 群管动作（基于当前事件所在群） ====================

    /** 群踢人；rejectAdd=true 拒绝其再次申请 */
    public SendReceipt kickGroupMember(String userId, boolean rejectAdd) {
        requireGroup();
        return sender.kickGroupMember(selfId, groupId, userId, rejectAdd);
    }

    /** 群单人禁言（durationSec 秒；0 表示解禁） */
    public SendReceipt banGroupMember(String userId, long durationSec) {
        requireGroup();
        return sender.banGroupMember(selfId, groupId, userId, durationSec);
    }

    /** 解除单人禁言 */
    public SendReceipt unbanGroupMember(String userId) {
        return banGroupMember(userId, 0);
    }

    /** 群组全员禁言开关 */
    public SendReceipt setGroupWholeBan(boolean enable) {
        requireGroup();
        return sender.setGroupWholeBan(selfId, groupId, enable);
    }

    /** 设置/取消群管理员 */
    public SendReceipt setGroupAdmin(String userId, boolean enable) {
        requireGroup();
        return sender.setGroupAdmin(selfId, groupId, userId, enable);
    }

    /** 设置群名片（群备注） */
    public SendReceipt setGroupCard(String userId, String card) {
        requireGroup();
        return sender.setGroupCard(selfId, groupId, userId, card);
    }

    /** 设置群名 */
    public SendReceipt setGroupName(String name) {
        requireGroup();
        return sender.setGroupName(selfId, groupId, name);
    }

    /** 退出当前群（dismiss=true 仅群主可解散） */
    public SendReceipt leaveGroup(boolean dismiss) {
        requireGroup();
        return sender.leaveGroup(selfId, groupId, dismiss);
    }

    /** 删除好友（go-cqhttp 扩展） */
    public SendReceipt deleteFriend(String userId) {
        return sender.deleteFriend(selfId, userId);
    }

    /** 发送好友赞 */
    public SendReceipt sendLike(String userId, int times) {
        return sender.sendLike(selfId, userId, times);
    }

    private void requireGroup() {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalStateException("[OneBot] 当前事件非群聊，无法执行群管动作");
        }
    }

    // ==================== Bot 信息查询 ====================

    @Override
    public int getGroupCount() {
        try {
            JsonNode data = api.call(selfId, "get_group_list", Json.obj());
            return data != null && data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            log.warn("[OneBot] get_group_list 失败: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public int getUserCount() {
        try {
            JsonNode data = api.call(selfId, "get_friend_list", Json.obj());
            return data != null && data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            log.warn("[OneBot] get_friend_list 失败: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public Map<String, String> getBotInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("platform", "onebot");
        info.put("selfId", selfId == null ? "" : selfId);
        try {
            JsonNode data = api.call(selfId, "get_login_info", Json.obj());
            if (data != null) {
                info.put("userId", data.path("user_id").asText(""));
                info.put("nickname", data.path("nickname").asText(""));
            }
        } catch (Exception e) {
            log.warn("[OneBot] get_login_info 失败: {}", e.getMessage());
        }
        return info;
    }

    // ==================== 统计口径（OneBot 无对应协议，由框架自建日志表提供） ====================
    // 这些指标依赖框架侧的事件流水统计，P4 数据域落库后接入；当前返回 0 而非抛异常，
    // 保证插件调用不炸。

    @Override public int getTodayFriendAdd() { return 0; }
    @Override public int getTodayFriendDel() { return 0; }
    @Override public int getTodayGroupAdd()  { return 0; }
    @Override public int getTodayGroupDel()  { return 0; }
    @Override public int getTodayMemberAdd(String groupId) { return 0; }
    @Override public int getTodayMemberDel(String groupId) { return 0; }
}
