package XuanJi.llm.plugin;

import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import XuanJi.api.adapter.BotContextBinder;
import XuanJi.api.context.BotContext;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmMessage;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.sender.XuanJiMessageSender;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;
import XuanJi.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件能力门面实现 — 把框架服务（LLM / 统一发送出口 / 统一动作协议）暴露给插件。
 *
 * <p>插件命令方法声明 {@link PluginServices} 参数即由 CommandRegistry 自动注入本实现。
 * 群管/查询类动作统一走 {@link PlatformActionHub}（动作名协议，屏蔽 qqbot/onebot 平台差异）；
 * 主动发送走 {@link XuanJiMessageSender}，调用前通过 {@link BotContextBinder} 绑定机器人上下文
 * （botKey 为空串时直接执行，由发送出口回退到第一个机器人）。
 */
@Slf4j
@Component
public class PluginServicesImpl implements PluginServices {

    private final LlmService llmService;
    private final ObjectProvider<XuanJiMessageSender> senderProvider;
    private final ObjectProvider<BotContextBinder> binderProvider;
    private final PlatformActionHub actionHub;

    public PluginServicesImpl(LlmService llmService,
                              ObjectProvider<XuanJiMessageSender> senderProvider,
                              ObjectProvider<BotContextBinder> binderProvider,
                              PlatformActionHub actionHub) {
        this.llmService = llmService;
        this.senderProvider = senderProvider;
        this.binderProvider = binderProvider;
        this.actionHub = actionHub;
    }

    // ──────────── LLM ────────────

    @Override
    public String chat(String user) {
        return chat(null, user);
    }

    @Override
    public String chat(String system, String user) {
        List<LlmMessage> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(LlmMessage.system(system));
        }
        messages.add(LlmMessage.user(user == null ? "" : user));
        return llmService.chat(messages, LlmChatOptions.defaults());
    }

    // ──────────── 主动发送 ────────────

    @Override
    public XuanJiSendReceipt sendToGroup(String botKey, String groupOpenid, XuanJiMessage chain) {
        XuanJiSendReceipt[] box = new XuanJiSendReceipt[1];
        runWith(botKey, () -> box[0] = sender().send(new XuanJiTarget.Group(groupOpenid), chain));
        return box[0] != null ? box[0] : XuanJiSendReceipt.fail("发送无回执", 0);
    }

    @Override
    public XuanJiSendReceipt sendToPrivate(String botKey, String openid, XuanJiMessage chain) {
        XuanJiSendReceipt[] box = new XuanJiSendReceipt[1];
        runWith(botKey, () -> box[0] = sender().send(new XuanJiTarget.Private(openid), chain));
        return box[0] != null ? box[0] : XuanJiSendReceipt.fail("发送无回执", 0);
    }

    // ──────────── 群管（统一动作协议） ────────────

    @Override
    public OpResult approveGroupJoin(String botKey, String groupOpenid, String memberOpenid,
                                     boolean approve, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("memberOpenid", memberOpenid);
        params.put("approve", approve);
        if (reason != null) params.put("reason", reason);
        return opResult(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_APPROVE, params),
                approve ? "已同意入群申请" : "已拒绝入群申请");
    }

    @Override
    public OpResult muteGroupMember(String botKey, String groupOpenid, String memberOpenid, int minutes) {
        return muteGroupMembers(botKey, groupOpenid, memberOpenid == null ? List.of() : List.of(memberOpenid), minutes);
    }

    @Override
    public OpResult muteGroupMembers(String botKey, String groupOpenid, List<String> memberOpenids, int minutes) {
        if (memberOpenids == null || memberOpenids.isEmpty()) {
            return OpResult.fail("禁言失败：未指定目标成员");
        }
        String successMsg = minutes > 0 ? "已禁言 " + minutes + " 分钟" : "已解除禁言";
        int ok = 0;
        List<String> fails = new ArrayList<>();
        for (String mid : memberOpenids) {
            if (mid == null || mid.isBlank()) continue;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("groupOpenid", groupOpenid);
            params.put("memberOpenid", mid);
            params.put("minutes", minutes); // 分钟，适配器内部换算成秒
            OpResult r = opResult(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_MUTE, params), successMsg);
            if (r.ok()) {
                ok++;
            } else {
                fails.add(mid + "(" + r.message() + ")");
            }
        }
        StringBuilder sb = new StringBuilder();
        if (ok > 0) {
            sb.append("已禁言 ").append(ok).append(" 人").append(minutes > 0 ? " " + minutes + " 分钟" : "");
        }
        if (!fails.isEmpty()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("失败 ").append(fails.size()).append(" 人：").append(String.join("；", fails));
        }
        return fails.isEmpty() ? OpResult.ok(sb.toString()) : OpResult.fail(sb.toString());
    }

    @Override
    public OpResult recallGroupMessage(String botKey, String groupOpenid, String msgId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("msgId", msgId);
        return opResult(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_RECALL, params),
                "已撤回群消息");
    }

    @Override
    public OpResult recallRecentMessages(String botKey, String groupOpenid, String memberOpenid) {
        return recallRecentMessages(botKey, groupOpenid, memberOpenid, 1);
    }

    @Override
    public OpResult recallRecentMessages(String botKey, String groupOpenid, String memberOpenid, int count) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("memberOpenid", memberOpenid);
        params.put("count", count);
        Map<String, Object> out = actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_RECALL_RECENT, params);
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

    @Override
    public OpResult recallPrivateMessage(String botKey, String openid, String msgId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openid", openid);
        params.put("msgId", msgId);
        return opResult(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_RECALL_PRIVATE, params),
                "已撤回单聊消息");
    }

    // ──────────── 平台信息查询（统一动作协议） ────────────

    @Override
    public Map<String, Object> getGroupInfo(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_INFO,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public Map<String, Object> getLocalGroupInfo(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_LOCAL_INFO,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public OpResult autoApproveGroupJoin(String botKey, String groupOpenid, String memberOpenid, String expectedAnswer) {
        if (groupOpenid == null || groupOpenid.isBlank() || memberOpenid == null || memberOpenid.isBlank()) {
            return OpResult.fail("自动审批被拒：缺少群或成员参数");
        }
        // 1. 拉入群申请列表
        Map<String, Object> listResp = listGroupJoinRequests(botKey, groupOpenid);
        if (listResp == null) {
            return OpResult.fail("自动审批失败：入群申请列表为空（机器人非群管理或接口失败）");
        }
        // 2. 找该成员的最新申请（listResp 结构：{data:{list:[...]}}，见 TestPlugin 实测日志）
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = listResp.get("data") instanceof Map<?, ?> dm
                ? (Map<String, Object>) dm : listResp;
        Object listObj = dataMap.get("list");
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return OpResult.fail("自动审批失败：未找到任何入群申请");
        }
        Map<?, ?> targetReq = null;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && memberOpenid.equals(String.valueOf(m.get("member_openid")))) {
                targetReq = m;
                break;
            }
        }
        if (targetReq == null) {
            return OpResult.fail("自动审批失败：未找到该成员的入群申请（member=" + memberOpenid + "）");
        }
        // 3. 解析验证信息（框架已注入 _verify_parsed）
        Object vpObj = targetReq.get("_verify_parsed");
        boolean qaMode = false;
        String answer = null;
        if (vpObj instanceof Map<?, ?> vp) {
            qaMode = Boolean.TRUE.equals(vp.get("qaMode"));
            answer = vp.get("answer") == null ? null : String.valueOf(vp.get("answer"));
        }
        String reqId = targetReq.get("join_request_id") == null ? "" : String.valueOf(targetReq.get("join_request_id"));
        // 4. 判定
        boolean pass;
        String reason = null;
        if (!qaMode) {
            pass = true; // 未设置入群问题 → 自动通过
        } else {
            String exp = expectedAnswer == null ? "" : expectedAnswer.trim().toLowerCase();
            if (exp.isEmpty()) {
                return OpResult.fail("自动审批拒绝：设置了入群问题但未配置正确答案，无法审核（请传入 expectedAnswer）");
            }
            pass = answer != null && answer.trim().toLowerCase().equals(exp);
            if (!pass) {
                reason = "入群问题答案不正确";
            }
        }
        // 5. 执行审批
        OpResult r = approveGroupJoin(botKey, groupOpenid, memberOpenid, pass, reason);
        if (!r.ok()) return r;
        return OpResult.ok(pass
                ? (qaMode ? "已自动通过：入群问题答案正确" : "已自动通过：无需验证问题")
                : "已自动拒绝：入群问题答案不正确（reqId=" + reqId + "）");
    }

    @Override
    public Map<String, Object> listGroupJoinRequests(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_JOIN_REQUEST_LIST,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public Map<String, Object> getBotGroupState(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_BOT_STATE,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public Map<String, Object> getGroupMuteStatus(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_MUTE_STATUS,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public List<Map<String, Object>> listGroupMembers(String botKey, String groupOpenid) {
        return actionList(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_MEMBER_LIST,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public List<Map<String, Object>> listGroups(String botKey) {
        return actionList(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_LIST, Map.of()));
    }

    @Override
    public Map<String, Object> getGroupBotRole(String botKey, String groupOpenid) {
        return actionData(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_BOT_ROLE,
                Map.of("groupOpenid", groupOpenid)));
    }

    @Override
    public List<Map<String, Object>> listUsers(String botKey) {
        return actionList(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.USER_LIST, Map.of()));
    }

    // ──────────── 工具 ────────────

    /**
     * 解析实际生效的 botKey：调用方传入空时，回退到当前事件所属机器人（含 {@code platform:} 前缀），
     * 让 {@link PlatformActionHub} 能精确路由到对应平台 provider，避免误路由到"第一个注册平台"。
     */
    private String effectiveBotKey(String botKey) {
        if (botKey != null && !botKey.isBlank()) {
            return botKey;
        }
        try {
            XuanJi.api.event.XuanJiEvent ev = BotContext.current();
            if (ev != null && ev.bot() != null && ev.bot().id() != null && !ev.bot().id().isBlank()) {
                return ev.bot().id();
            }
        } catch (Exception ignored) { }
        return botKey == null ? "" : botKey;
    }

    /** 群管命令结果：成功返回携带成功提示的 OpResult；失败透传框架/适配器提供的错误原因。 */
    private OpResult opResult(Map<String, Object> out, String successMsg) {
        if (out != null && Boolean.TRUE.equals(out.get("ok"))) {
            return OpResult.ok(successMsg);
        }
        String err = out == null ? "平台无响应" : String.valueOf(out.get("error"));
        if (err == null || err.isBlank() || "null".equals(err)) err = "操作失败，请稍后重试";
        return OpResult.fail(err);
    }

    /** 动作结果 data；失败或平台不支持返回 null。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> actionData(Map<String, Object> out) {
        if (out == null || !Boolean.TRUE.equals(out.get("ok")) || !(out.get("data") instanceof Map<?, ?> m)) {
            return null;
        }
        return (Map<String, Object>) m;
    }

    /** 动作结果 data（列表型）；失败或平台不支持返回 null。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> actionList(Map<String, Object> out) {
        if (out == null || !Boolean.TRUE.equals(out.get("ok")) || !(out.get("data") instanceof List<?> l)) {
            return null;
        }
        return (List<Map<String, Object>>) l;
    }

    private XuanJiMessageSender sender() {
        XuanJiMessageSender s = senderProvider.getIfAvailable();
        if (s == null) {
            throw new IllegalStateException("无可用发送出口（未接入任何平台适配器）");
        }
        return s;
    }

    /** botKey 为空时直接执行（发送出口回退第一个机器人）；非空时绑定指定机器人上下文。 */
    private void runWith(String botKey, Runnable task) {
        if (botKey == null || botKey.isBlank()) {
            task.run();
            return;
        }
        BotContextBinder binder = binderProvider.getIfAvailable();
        if (binder != null) {
            binder.runWith(botKey, task);
        } else {
            task.run();
        }
    }
}
