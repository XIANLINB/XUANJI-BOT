package dev.xuanji.adapter.onebot.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.sdk.bot.Bot;
import java.util.HashMap;
import java.util.Map;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotXjBot
extends Bot {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotXjBot.class);
    private final OneBotApiService api;
    private final OneBotMessageSenderImpl sender;
    private final BotOutboundExecutor outbound;
    private final String selfId;
    private final String groupId;
    private final String userId;
    private final String msgId;

    public OneBotXjBot(OneBotApiService api, OneBotMessageSenderImpl sender, BotOutboundExecutor outbound, String selfId, String groupId, String userId, String msgId) {
        this.api = api;
        this.sender = sender;
        this.outbound = outbound;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.msgId = msgId;
    }

    private void enqueue(Runnable task) {
        if (this.outbound == null) {
            task.run();
            return;
        }
        this.outbound.submit(this.selfId, task);
    }

    private void pace() {
        if (this.outbound != null) {
            this.outbound.awaitPace(this.selfId);
        }
    }

    public void reply(String text) {
        this.replyChain(MessageChain.text((String)text));
    }

    public void replyMarkdown(String markdownContent) {
        this.replyChain(MessageChain.builder().markdown(markdownContent).build());
    }

    public void replyMarkdown(String markdownContent, String keyboardJson) {
        this.replyMarkdown(markdownContent);
    }

    public void replyImage(String url) {
        this.replyChain(MessageChain.builder().image(url).build());
    }

    public void replyAudio(String url) {
        this.replyChain(MessageChain.builder().add((MessageElement)new MessageElement.Voice(url, 0)).build());
    }

    public void replyVideo(String url) {
        this.replyChain(MessageChain.builder().add((MessageElement)new MessageElement.Video(url)).build());
    }

    public void replyArk(int templateId, String arkJson) {
        this.replyChain(MessageChain.builder().add((MessageElement)new MessageElement.Ark(templateId, (Object)arkJson)).build());
    }

    public void replyCard(String cardJson) {
        this.replyChain(MessageChain.builder().add((MessageElement)new MessageElement.Passthrough("onebot", "json\u5361\u7247", (Object)cardJson)).build());
    }

    private void replyChain(MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        if (this.msgId != null && !this.msgId.isBlank()) {
            ObjectNode replySeg = Json.obj();
            replySeg.put("type", "reply");
            ObjectNode d = Json.obj();
            d.put("id", this.msgId);
            replySeg.set("data", (JsonNode)d);
            segments.insert(0, (JsonNode)replySeg);
        }
        ArrayNode payload = segments;
        if (this.groupId != null && !this.groupId.isBlank()) {
            this.enqueue(() -> this.sender.sendGroup(this.selfId, this.groupId, payload));
        } else if (this.userId != null && !this.userId.isBlank()) {
            this.enqueue(() -> this.sender.sendPrivate(this.selfId, this.userId, payload));
        } else {
            log.warn("[OneBot] reply \u65e0\u6709\u6548\u76ee\u6807: selfId={}", (Object)this.selfId);
        }
    }

    public void sendGroup(String gid, String text) {
        this.pushGroup(gid, MessageChain.text((String)text));
    }

    public void sendGroupMarkdown(String gid, String markdownContent) {
        this.pushGroup(gid, MessageChain.builder().markdown(markdownContent).build());
    }

    public void sendGroupMarkdown(String gid, String markdownContent, String keyboardJson) {
        this.sendGroupMarkdown(gid, markdownContent);
    }

    public void sendGroupImage(String gid, String url) {
        this.pushGroup(gid, MessageChain.builder().image(url).build());
    }

    public void sendGroupAudio(String gid, String url) {
        this.pushGroup(gid, MessageChain.builder().add((MessageElement)new MessageElement.Voice(url, 0)).build());
    }

    public void sendGroupVideo(String gid, String url) {
        this.pushGroup(gid, MessageChain.builder().add((MessageElement)new MessageElement.Video(url)).build());
    }

    public void sendGroupArk(String gid, int templateId, String arkJson) {
        this.pushGroup(gid, MessageChain.builder().add((MessageElement)new MessageElement.Ark(templateId, (Object)arkJson)).build());
    }

    public void sendGroupCard(String gid, String cardJson) {
        this.pushGroup(gid, MessageChain.builder().add((MessageElement)new MessageElement.Passthrough("onebot", "json\u5361\u7247", (Object)cardJson)).build());
    }

    public void sendPrivate(String uid, String text) {
        this.pushPrivate(uid, MessageChain.text((String)text));
    }

    public void sendPrivateMarkdown(String uid, String markdownContent) {
        this.pushPrivate(uid, MessageChain.builder().markdown(markdownContent).build());
    }

    public void sendPrivateImage(String uid, String url) {
        this.pushPrivate(uid, MessageChain.builder().image(url).build());
    }

    public void sendPrivateAudio(String uid, String url) {
        this.pushPrivate(uid, MessageChain.builder().add((MessageElement)new MessageElement.Voice(url, 0)).build());
    }

    private void pushGroup(String gid, MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        this.enqueue(() -> this.sender.sendGroup(this.selfId, gid, segments));
    }

    private void pushPrivate(String uid, MessageChain chain) {
        ArrayNode segments = OneBotMessageConverter.toSegments(chain);
        this.enqueue(() -> this.sender.sendPrivate(this.selfId, uid, segments));
    }

    public String uploadImage(String filePath) {
        return OneBotXjBot.toFileUri(filePath);
    }

    public String uploadVideo(String filePath) {
        return OneBotXjBot.toFileUri(filePath);
    }

    public String uploadAudio(String filePath) {
        return OneBotXjBot.toFileUri(filePath);
    }

    public String uploadFile(String filePath) {
        return OneBotXjBot.toFileUri(filePath);
    }

    private static String toFileUri(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String p = filePath.trim();
        if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file://") || p.startsWith("base64://")) {
            return p;
        }
        return "file:///" + p.replace('\\', '/');
    }

    public void retractGroupMessage(String messageId) {
        this.enqueue(() -> this.sender.recall(this.selfId, messageId));
    }

    public void retractC2cMessage(String messageId) {
        this.enqueue(() -> this.sender.recall(this.selfId, messageId));
    }

    public SendReceipt approveFriendRequest(String flag, String remark) {
        this.pace();
        return this.sender.handleFriendRequest(this.selfId, flag, true, remark);
    }

    public SendReceipt rejectFriendRequest(String flag, String remark) {
        this.pace();
        return this.sender.handleFriendRequest(this.selfId, flag, false, remark);
    }

    public SendReceipt approveGroupAddRequest(String flag, String reason) {
        this.pace();
        return this.sender.handleGroupRequest(this.selfId, flag, "add", true, reason);
    }

    public SendReceipt rejectGroupAddRequest(String flag, String reason) {
        this.pace();
        return this.sender.handleGroupRequest(this.selfId, flag, "add", false, reason);
    }

    public SendReceipt approveGroupInvite(String flag) {
        this.pace();
        return this.sender.handleGroupRequest(this.selfId, flag, "invite", true, null);
    }

    public SendReceipt rejectGroupInvite(String flag, String reason) {
        this.pace();
        return this.sender.handleGroupRequest(this.selfId, flag, "invite", false, reason);
    }

    public SendReceipt kickGroupMember(String userId, boolean rejectAdd) {
        this.requireGroup();
        this.pace();
        return this.sender.kickGroupMember(this.selfId, this.groupId, userId, rejectAdd);
    }

    public SendReceipt banGroupMember(String userId, long durationSec) {
        this.requireGroup();
        this.pace();
        return this.sender.banGroupMember(this.selfId, this.groupId, userId, durationSec);
    }

    public SendReceipt unbanGroupMember(String userId) {
        return this.banGroupMember(userId, 0L);
    }

    public SendReceipt setGroupWholeBan(boolean enable) {
        this.requireGroup();
        this.pace();
        return this.sender.setGroupWholeBan(this.selfId, this.groupId, enable);
    }

    public SendReceipt setGroupAdmin(String userId, boolean enable) {
        this.requireGroup();
        this.pace();
        return this.sender.setGroupAdmin(this.selfId, this.groupId, userId, enable);
    }

    public SendReceipt setGroupCard(String userId, String card) {
        this.requireGroup();
        this.pace();
        return this.sender.setGroupCard(this.selfId, this.groupId, userId, card);
    }

    public SendReceipt setGroupName(String name) {
        this.requireGroup();
        this.pace();
        return this.sender.setGroupName(this.selfId, this.groupId, name);
    }

    public SendReceipt leaveGroup(boolean dismiss) {
        this.requireGroup();
        this.pace();
        return this.sender.leaveGroup(this.selfId, this.groupId, dismiss);
    }

    public SendReceipt deleteFriend(String userId) {
        this.pace();
        return this.sender.deleteFriend(this.selfId, userId);
    }

    public SendReceipt sendLike(String userId, int times) {
        this.pace();
        return this.sender.sendLike(this.selfId, userId, times);
    }

    private void requireGroup() {
        if (this.groupId == null || this.groupId.isBlank()) {
            throw new IllegalStateException("[OneBot] \u5f53\u524d\u4e8b\u4ef6\u975e\u7fa4\u804a\uff0c\u65e0\u6cd5\u6267\u884c\u7fa4\u7ba1\u52a8\u4f5c");
        }
    }

    public int getGroupCount() {
        try {
            this.pace();
            JsonNode data = this.api.call(this.selfId, "get_group_list", Json.obj());
            return data != null && data.isArray() ? data.size() : 0;
        }
        catch (Exception e) {
            log.warn("[OneBot] get_group_list \u5931\u8d25: {}", (Object)e.getMessage());
            return 0;
        }
    }

    public int getUserCount() {
        try {
            this.pace();
            JsonNode data = this.api.call(this.selfId, "get_friend_list", Json.obj());
            return data != null && data.isArray() ? data.size() : 0;
        }
        catch (Exception e) {
            log.warn("[OneBot] get_friend_list \u5931\u8d25: {}", (Object)e.getMessage());
            return 0;
        }
    }

    public Map<String, String> getBotInfo() {
        HashMap<String, String> info = new HashMap<String, String>();
        info.put("platform", "onebot");
        info.put("selfId", this.selfId == null ? "" : this.selfId);
        try {
            this.pace();
            JsonNode data = this.api.call(this.selfId, "get_login_info", Json.obj());
            if (data != null) {
                info.put("userId", data.path("user_id").asText(""));
                info.put("nickname", data.path("nickname").asText(""));
            }
        }
        catch (Exception e) {
            log.warn("[OneBot] get_login_info \u5931\u8d25: {}", (Object)e.getMessage());
        }
        return info;
    }

    public int getTodayFriendAdd() {
        return 0;
    }

    public int getTodayFriendDel() {
        return 0;
    }

    public int getTodayGroupAdd() {
        return 0;
    }

    public int getTodayGroupDel() {
        return 0;
    }

    public int getTodayMemberAdd(String groupId) {
        return 0;
    }

    public int getTodayMemberDel(String groupId) {
        return 0;
    }
}

