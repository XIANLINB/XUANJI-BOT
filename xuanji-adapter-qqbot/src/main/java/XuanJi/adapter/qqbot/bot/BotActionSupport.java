package XuanJi.adapter.qqbot.bot;

import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import XuanJi.api.plugin.BotGroupState;
import XuanJi.api.plugin.GroupBotRole;
import XuanJi.api.plugin.GroupInfo;
import XuanJi.api.plugin.GroupMember;
import XuanJi.api.plugin.GroupMuteStatus;
import XuanJi.api.plugin.JoinRequestList;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.UserInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QQ Bot 门面动作支撑 — 把群管/查询/撤回等统一动作协议封装为类型化结果，
 * 供 {@link QqXjBot} / {@link C2cXjBot} 复用，避免两处重复分发逻辑。
 *
 * <p>所有动作统一走 {@link PlatformActionHub#dispatch(String, String, Map)}，
 * botKey 用 {@code "qq:" + appId}：中枢自动剥前缀并绑定机器人上下文。</p>
 */
@Slf4j
class BotActionSupport {

    private final PlatformActionHub actionHub;
    private final String appId;

    BotActionSupport(PlatformActionHub actionHub, String appId) {
        this.actionHub = actionHub;
        this.appId = appId;
    }

    // ──────────── 分发与结果转换 ────────────

    private Map<String, Object> act(String action, Map<String, Object> params) {
        return actionHub.dispatch("qq:" + appId, action, params);
    }

    private static OpResult opResult(Map<String, Object> out, String successMsg) {
        if (out != null && Boolean.TRUE.equals(out.get("ok"))) {
            return OpResult.ok(successMsg);
        }
        String err = out == null ? "平台无响应" : String.valueOf(out.get("error"));
        if (err == null || err.isBlank() || "null".equals(err)) err = "操作失败，请稍后重试";
        return OpResult.fail(err);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> out) {
        if (out == null || !Boolean.TRUE.equals(out.get("ok")) || !(out.get("data") instanceof Map<?, ?> m)) {
            return null;
        }
        return (Map<String, Object>) m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> out) {
        if (out == null || !Boolean.TRUE.equals(out.get("ok")) || !(out.get("data") instanceof List<?> l)) {
            return null;
        }
        return (List<Map<String, Object>>) l;
    }

    // ──────────── 群管动作 ────────────

    OpResult muteGroupMembers(String groupId, List<String> memberOpenids, int minutes) {
        if (memberOpenids == null || memberOpenids.isEmpty()) {
            return OpResult.fail("禁言失败：未指定目标成员");
        }
        String okMsg = minutes > 0 ? "已禁言 " + minutes + " 分钟" : "已解除禁言";
        int ok = 0;
        List<String> fails = new ArrayList<>();
        for (String mid : memberOpenids) {
            if (mid == null || mid.isBlank()) continue;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("groupOpenid", groupId);
            params.put("memberOpenid", mid);
            params.put("minutes", minutes); // 分钟，适配器内部换算成秒
            OpResult r = opResult(act(PlatformActions.GROUP_MUTE, params), okMsg);
            if (r.ok()) ok++;
            else fails.add(mid + "(" + r.message() + ")");
        }
        StringBuilder sb = new StringBuilder();
        if (ok > 0) sb.append("已禁言 ").append(ok).append(" 人").append(minutes > 0 ? " " + minutes + " 分钟" : "");
        if (!fails.isEmpty()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("失败 ").append(fails.size()).append(" 人：").append(String.join("；", fails));
        }
        return fails.isEmpty() ? OpResult.ok(sb.toString()) : OpResult.fail(sb.toString());
    }

    OpResult muteGroupMember(String groupId, String memberOpenid, int minutes) {
        return muteGroupMembers(groupId, memberOpenid == null ? List.of() : List.of(memberOpenid), minutes);
    }

    OpResult kickGroupMember(String groupId, String memberOpenid) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("memberOpenid", memberOpenid);
        return opResult(act(PlatformActions.GROUP_KICK, params), "已移出群成员");
    }

    OpResult setGroupCard(String groupId, String memberOpenid, String card) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("memberOpenid", memberOpenid);
        params.put("card", card);
        return opResult(act(PlatformActions.GROUP_SET_CARD, params), "已设置群名片");
    }

    OpResult setGroupAdmin(String groupId, String memberOpenid, boolean setAdmin) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("memberOpenid", memberOpenid);
        params.put("setAdmin", setAdmin);
        return opResult(act(PlatformActions.GROUP_SET_ADMIN, params), setAdmin ? "已设为群管理" : "已取消群管理");
    }

    OpResult approveGroupJoinRequest(String groupId, String memberOpenid, String joinRequestId,
                                     boolean approve, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("memberOpenid", memberOpenid);
        params.put("joinRequestId", joinRequestId);
        params.put("approve", approve);
        if (reason != null) params.put("reason", reason);
        return opResult(act(PlatformActions.GROUP_APPROVE, params), approve ? "已同意入群申请" : "已拒绝入群申请");
    }

    OpResult approveFriendRequest(String openid, boolean approve, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openid", openid);
        params.put("approve", approve);
        if (reason != null) params.put("reason", reason);
        return opResult(act(PlatformActions.FRIEND_APPROVE, params), approve ? "已同意好友申请" : "已拒绝好友申请");
    }

    // ──────────── 撤回 ────────────

    OpResult recallGroupMessage(String groupId, String messageId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("msgId", messageId);
        return opResult(act(PlatformActions.GROUP_RECALL, params), "已撤回群消息");
    }

    OpResult recallRecentMessages(String groupId, String memberOpenid, int count) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupId);
        params.put("memberOpenid", memberOpenid);
        params.put("count", count);
        Map<String, Object> out = act(PlatformActions.GROUP_RECALL_RECENT, params);
        if (out != null && Boolean.TRUE.equals(out.get("ok")) && out.get("data") instanceof Map<?, ?> dm) {
            Object recalled = dm.get("recalled");
            Object skipped = dm.get("skipped");
            StringBuilder sb = new StringBuilder("已撤回 ");
            sb.append(recalled == null ? count : recalled).append(" 条");
            if (skipped != null && ((Number) skipped).longValue() > 0) {
                sb.append("，跳过 ").append(skipped).append(" 条（超2分钟或平台拒绝）");
            }
            return OpResult.ok(sb.toString());
        }
        return opResult(out, "已撤回该成员最近消息");
    }

    OpResult recallPrivateMessage(String openid, String messageId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openid", openid);
        params.put("msgId", messageId);
        return opResult(act(PlatformActions.GROUP_RECALL_PRIVATE, params), "已撤回单聊消息");
    }

    // ──────────── 查询 ────────────

    GroupInfo getGroupInfo(String groupId) {
        return GroupInfo.from(data(act(PlatformActions.GROUP_INFO, Map.of("groupOpenid", groupId))));
    }

    GroupInfo getLocalGroupInfo(String groupId) {
        return GroupInfo.from(data(act(PlatformActions.GROUP_LOCAL_INFO, Map.of("groupOpenid", groupId))));
    }

    BotGroupState getBotGroupState(String groupId) {
        return BotGroupState.from(data(act(PlatformActions.GROUP_BOT_STATE, Map.of("groupOpenid", groupId))));
    }

    GroupMuteStatus getGroupMuteStatus(String groupId) {
        return GroupMuteStatus.from(data(act(PlatformActions.GROUP_MUTE_STATUS, Map.of("groupOpenid", groupId))));
    }

    JoinRequestList listGroupJoinRequests(String groupId) {
        return JoinRequestList.from(data(act(PlatformActions.GROUP_JOIN_REQUEST_LIST, Map.of("groupOpenid", groupId))));
    }

    List<GroupMember> listGroupMembers(String groupId) {
        List<Map<String, Object>> rows = list(act(PlatformActions.GROUP_MEMBER_LIST, Map.of("groupOpenid", groupId)));
        return rows == null ? List.of() : rows.stream().map(GroupMember::from).filter(Objects::nonNull).toList();
    }

    List<GroupInfo> listGroups() {
        List<Map<String, Object>> rows = list(act(PlatformActions.GROUP_LIST, Map.of()));
        return rows == null ? List.of() : rows.stream().map(GroupInfo::from).filter(Objects::nonNull).toList();
    }

    GroupBotRole getGroupBotRole(String groupId) {
        return GroupBotRole.from(data(act(PlatformActions.GROUP_BOT_ROLE, Map.of("groupOpenid", groupId))));
    }

    List<UserInfo> listUsers() {
        List<Map<String, Object>> rows = list(act(PlatformActions.USER_LIST, Map.of()));
        return rows == null ? List.of() : rows.stream().map(UserInfo::from).filter(Objects::nonNull).toList();
    }

    UserInfo getUserInfo(String openid) {
        return UserInfo.from(data(act(PlatformActions.USER_INFO, Map.of("openid", openid))));
    }
}
