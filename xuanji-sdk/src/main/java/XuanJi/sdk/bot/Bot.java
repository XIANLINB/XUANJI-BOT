package XuanJi.sdk.bot;

import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.BotGroupState;
import XuanJi.api.plugin.GroupBotRole;
import XuanJi.api.plugin.GroupInfo;
import XuanJi.api.plugin.GroupMember;
import XuanJi.api.plugin.GroupMuteStatus;
import XuanJi.api.plugin.JoinRequestList;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.UserInfo;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;

import java.util.List;

/**
 * 璇玑 Bot 统一门面 — 被动回复 + 主动发送 + 全部群管动作 + 查询 + 好友审批 + 用户信息。
 *
 * <h3>被动回复（带 msg_id，仅事件处理链内可用）</h3>
 * <pre>bot.reply("文本");
 * bot.replyMarkdown(md);
 * bot.replyImage(url);</pre>
 *
 * <h3>主动发送（不依赖当前事件，定时任务/推送等场景，需声明 {@code PROACTIVE_MESSAGE} 权限）</h3>
 * <pre>bot.sendGroup("群ID", "文本");
 * bot.sendGroupImage("群ID", url);
 * bot.sendPrivate("用户ID", "文本");
 * bot.sendToGroup("群ID", XuanJiMessage.builder().markdown(md).build());</pre>
 *
 * <h3>群管 / 查询 / 好友审批（返回类型化结果 OpResult / GroupInfo / …）</h3>
 * <pre>OpResult r = bot.muteGroupMembers(groupId, List.of(openid), 10);
 * GroupInfo info = bot.getGroupInfo(groupId);</pre>
 *
 * <p><b>PROACTIVE_MESSAGE 闸门</b>：所有主动发送方法（sendGroup / sendPrivate /
 * sendToGroup(chain) / sendToPrivate(chain)）受 {@code allowProactive} 运行时闸门控制，
 * 仅在插件 {@code @XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)} 声明后放行；
 * 被动回复（reply*）不受限。</p>
 *
 * <p><b>GROUP_ADMIN 闸门</b>：所有群管/撤回/审批写操作（muteGroupMembers / kickGroupMember /
 * approveGroupJoinRequest / recallGroupMessage 等）受 {@code allowGroupAdmin} 运行时闸门控制，
 * 仅在插件 {@code @XuanJiPlugin(permissions = Perm.GROUP_ADMIN)} 声明后放行；
 * 只读查询（getGroupInfo 等）不受限。未声明权限的插件调用将被拒绝（抛 IllegalStateException）。</p>
 */
public abstract class Bot {

    /** 平台端机器人账号 ID（robotId / appId）；子类可覆写，未知返回 null。 */
    public String selfId() { return null; }

    // ==================== 被动回复（reply* 系列） ====================

    public abstract void reply(String text);
    public abstract void replyMarkdown(String markdownContent);
    public abstract void replyMarkdown(String markdownContent, String keyboardJson);
    public abstract void replyImage(String url);
    public abstract void replyAudio(String url);
    public abstract void replyVideo(String url);
    public abstract void replyArk(int templateId, String arkJson);
    public abstract void replyCard(String cardJson);

    // ==================== 主动发送（send* 系列，需指定目标） ====================

    /** 主动发送群聊文本（受 PROACTIVE_MESSAGE 闸门控制）。 */
    public abstract void sendGroup(String groupId, String text);
    /** 主动发送群聊 Markdown。 */
    public abstract void sendGroupMarkdown(String groupId, String markdownContent);
    /** 主动发送群聊 Markdown + 键盘。 */
    public abstract void sendGroupMarkdown(String groupId, String markdownContent, String keyboardJson);
    /** 主动发送群聊图片。 */
    public abstract void sendGroupImage(String groupId, String url);
    /** 主动发送群聊语音。 */
    public abstract void sendGroupAudio(String groupId, String url);
    /** 主动发送群聊视频。 */
    public abstract void sendGroupVideo(String groupId, String url);
    /** 主动发送群聊 Ark。 */
    public abstract void sendGroupArk(String groupId, int templateId, String arkJson);
    /** 主动发送群聊图文卡片。 */
    public abstract void sendGroupCard(String groupId, String cardJson);

    /** 主动发送私聊文本。 */
    public abstract void sendPrivate(String userId, String text);
    /** 主动发送私聊 Markdown。 */
    public abstract void sendPrivateMarkdown(String userId, String markdownContent);
    /** 主动发送私聊图片。 */
    public abstract void sendPrivateImage(String userId, String url);
    /** 主动发送私聊语音。 */
    public abstract void sendPrivateAudio(String userId, String url);

    // ==================== 主动发送消息链（返回回执，受闸门控制） ====================

    /** 主动向群发送消息链（需声明 PROACTIVE_MESSAGE）。 */
    public abstract XuanJiSendReceipt sendToGroup(String groupId, XuanJiMessage chain);
    /** 主动向私聊发送消息链（需声明 PROACTIVE_MESSAGE）。 */
    public abstract XuanJiSendReceipt sendToPrivate(String userId, XuanJiMessage chain);

    // ==================== 媒体上传 ====================

    public abstract String uploadImage(String filePath);
    public abstract String uploadVideo(String filePath);
    public abstract String uploadAudio(String filePath);
    public abstract String uploadFile(String filePath);

    // ==================== 群管动作（返回 OpResult） ====================

    /** 群成员禁言（单目标）；minutes&lt;=0 解除禁言。 */
    public abstract OpResult muteGroupMember(String groupId, String memberOpenid, int minutes);
    /** 批量群成员禁言（每个目标独立执行，返回汇总）。 */
    public abstract OpResult muteGroupMembers(String groupId, List<String> memberOpenids, int minutes);
    /** 解除群成员禁言（minutes=0 便捷重载）。 */
    public OpResult unmuteGroupMembers(String groupId, List<String> memberOpenids) {
        return muteGroupMembers(groupId, memberOpenids, 0);
    }
    /** 踢出群成员（平台不支持时返回 fail）。 */
    public abstract OpResult kickGroupMember(String groupId, String memberOpenid);
    /** 设置群成员名片（平台不支持时返回 fail）。 */
    public abstract OpResult setGroupCard(String groupId, String memberOpenid, String card);
    /** 设置/取消群管理员（平台不支持时返回 fail）。 */
    public abstract OpResult setGroupAdmin(String groupId, String memberOpenid, boolean setAdmin);
    /** 入群申请审批。 */
    public abstract OpResult approveGroupJoinRequest(String groupId, String memberOpenid, String joinRequestId,
                                                     boolean approve, String reason);
    /** 好友申请审批（平台不支持时返回 fail）。 */
    public abstract OpResult approveFriendRequest(String openid, boolean approve, String reason);

    // ==================== 撤回（返回 OpResult） ====================

    /** 撤回群消息。 */
    public abstract OpResult recallGroupMessage(String groupId, String messageId);
    /** 撤回群内某成员最近 N 条消息。 */
    public abstract OpResult recallRecentMessages(String groupId, String memberOpenid, int count);
    /** 撤回群内某成员最近 1 条消息。 */
    public OpResult recallRecentMessages(String groupId, String memberOpenid) {
        return recallRecentMessages(groupId, memberOpenid, 1);
    }
    /** 撤回单聊消息。 */
    public abstract OpResult recallPrivateMessage(String openid, String messageId);

    // ==================== 平台信息查询（返回类型化结果） ====================

    /** 群基本信息（远程平台接口）。平台不支持/失败返回 null。 */
    public abstract GroupInfo getGroupInfo(String groupId);
    /** 群本地档案（查库，免限频）。found=false 表示本地无档案。 */
    public abstract GroupInfo getLocalGroupInfo(String groupId);
    /** 机器人在群内的状态。平台不支持/失败返回 null。 */
    public abstract BotGroupState getBotGroupState(String groupId);
    /** 群禁言状态。平台不支持/失败返回 null。 */
    public abstract GroupMuteStatus getGroupMuteStatus(String groupId);
    /** 入群申请列表（含 next_cursor）；平台不支持/失败返回空列表。 */
    public abstract JoinRequestList listGroupJoinRequests(String groupId);
    /** 群成员列表（查本地库）。平台不支持/失败返回空列表。 */
    public abstract List<GroupMember> listGroupMembers(String groupId);
    /** 机器人所在群列表（查本地库）。平台不支持/失败返回空列表。 */
    public abstract List<GroupInfo> listGroups();
    /** 机器人在群内的角色（查本地库）。平台不支持/失败返回 null。 */
    public abstract GroupBotRole getGroupBotRole(String groupId);
    /** 单聊用户列表（查本地库）。平台不支持/失败返回空列表。 */
    public abstract List<UserInfo> listUsers();
    /** 单用户资料（远程平台接口）。平台不支持/失败返回 null。 */
    public abstract UserInfo getUserInfo(String openid);

    // ==================== Bot 信息查询 ====================

    public abstract int getGroupCount();
    public abstract int getUserCount();
    public abstract java.util.Map<String, String> getBotInfo();
    /** 今日新增/删除好友数 */
    public abstract int getTodayFriendAdd();
    public abstract int getTodayFriendDel();
    /** 今日新增/退群数 */
    public abstract int getTodayGroupAdd();
    public abstract int getTodayGroupDel();
    /** 某群今日加入/退出人数 */
    public abstract int getTodayMemberAdd(String groupId);
    public abstract int getTodayMemberDel(String groupId);

    // ==================== PROACTIVE_MESSAGE 运行时闸门 ====================

    /** 主动发送是否放行（由框架按插件 @XuanJiPlugin(permissions) 注入）。默认关闭。 */
    protected boolean allowProactive = false;

    /** 由框架在注入 Bot 时设置（事件上下文/定时上下文按插件权限决定）。 */
    public void setProactiveAllowed(boolean allowed) { this.allowProactive = allowed; }

    /**
     * 主动发送前置校验：未声明 PROACTIVE_MESSAGE 权限时抛异常，阻断未授权主动发送。
     * 被动回复（reply*）不调用本方法，始终放行。
     */
    protected void ensureProactive() {
        if (!allowProactive) {
            throw new IllegalStateException(
                    "插件未声明 PROACTIVE_MESSAGE 权限，禁止主动发送。请在 @XuanJiPlugin 的 permissions 中加入 Perm.PROACTIVE_MESSAGE");
        }
    }

    /** 群管/撤回/审批动作是否放行（由框架按插件 @XuanJiPlugin(permissions) 注入）。默认关闭。 */
    protected boolean allowGroupAdmin = false;

    /** 由框架在注入 Bot 时设置（事件上下文/定时上下文按插件权限决定）。 */
    public void setGroupAdminAllowed(boolean allowed) { this.allowGroupAdmin = allowed; }

    /**
     * 群管/撤回/审批前置校验：未声明 GROUP_ADMIN 权限时抛异常，阻断未授权的群管操作。
     * 只读查询（getGroupInfo 等）与被动回复（reply*）不调用本方法，始终放行。
     */
    protected void ensureGroupAdmin() {
        if (!allowGroupAdmin) {
            throw new IllegalStateException(
                    "插件未声明 GROUP_ADMIN 权限，禁止群管/撤回/审批操作。请在 @XuanJiPlugin 的 permissions 中加入 Perm.GROUP_ADMIN");
        }
    }
}
