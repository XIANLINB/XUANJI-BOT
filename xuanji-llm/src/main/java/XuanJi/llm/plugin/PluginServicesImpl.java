package XuanJi.llm.plugin;

import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import XuanJi.api.adapter.BotContextBinder;
import XuanJi.api.context.BotContext;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmMessage;
import XuanJi.api.message.XuanJiMessage;
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
    public boolean approveGroupJoin(String botKey, String groupOpenid, String memberOpenid,
                                    boolean approve, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("memberOpenid", memberOpenid);
        params.put("approve", approve);
        if (reason != null) params.put("reason", reason);
        return isOk(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_APPROVE, params));
    }

    @Override
    public boolean muteMember(String botKey, String groupOpenid, String memberOpenid, int seconds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("memberOpenid", memberOpenid);
        params.put("seconds", seconds);
        return isOk(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_MUTE, params));
    }

    @Override
    public boolean recallMessage(String botKey, String groupOpenid, String msgId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupOpenid", groupOpenid);
        params.put("msgId", msgId);
        return isOk(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_RECALL, params));
    }

    @Override
    public boolean recallPrivateMessage(String botKey, String openid, String msgId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openid", openid);
        params.put("msgId", msgId);
        return isOk(actionHub.dispatch(effectiveBotKey(botKey), PlatformActions.GROUP_RECALL_PRIVATE, params));
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

    private boolean isOk(Map<String, Object> out) {
        return out != null && Boolean.TRUE.equals(out.get("ok"));
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
