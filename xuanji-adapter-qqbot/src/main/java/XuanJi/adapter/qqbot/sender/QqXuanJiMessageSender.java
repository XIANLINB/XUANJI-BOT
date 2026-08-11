package XuanJi.adapter.qqbot.sender;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.api.action.PlatformActionHandler;
import XuanJi.api.action.PlatformActionProvider;
import XuanJi.api.action.PlatformActions;
import XuanJi.api.context.BotContext;
import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.message.XuanJiMessageElement;
import XuanJi.api.sender.XuanJiMessageSender;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * QQ 官方适配器 — 框架「动作指令」统一发送出口实现。
 *
 * <p>把核心产出的 {@link XuanJiMessage}（普通话消息链）翻译成 QQ 开放平台 API 调用：
 * <ul>
 *   <li>文本 → msg_type=0（{@code sendGroupText / sendC2cText}）；</li>
 *   <li>含 @ 的文本 → 自动升级 markdown（msg_type=2）+ {@code <qqbot-at-user id=""/>} 协议，@ 才能真正渲染；</li>
 *   <li>Markdown 元素 → msg_type=2（可附带 Keyboard）；</li>
 *   <li>图片 / 视频 / 文件元素 → 富媒体（msg_type=7，file_type 1/2/4，URL 上传转存）；</li>
 *   <li>Ark 元素 → msg_type=3 模板卡片；</li>
 *   <li>语音等字节类媒体不在链上承载，请走 LlmReplySink 专用方法。</li>
 * </ul>
 *
 * <p>{@link #reply} 依赖 {@link BotContext}（事件链内，自动带 replyToMsgId）；{@link #send}
 * 主动发送需调用方先绑定机器人上下文（如 {@link MessageSender#runWithRobotContext}），
 * 否则回退到第一个机器人。
 *
 * <p>同时实现 {@link PlatformActionProvider}：把群信息/群状态/禁言/审批/撤回注册为
 * 统一动作（{@link PlatformActions}），由 {@code PlatformActionHub} 分发，屏蔽平台差异。
 */
@Slf4j
@Component
public class QqXuanJiMessageSender implements XuanJiMessageSender, XuanJi.api.adapter.BotContextBinder,
        PlatformActionProvider {

    /** 老协议 <@openid>，自动升级为新协议 <qqbot-at-user id="openid"/> */
    private static final Pattern LEGACY_AT = Pattern.compile("<@([A-Fa-f0-9]+)>");

    private final MessageSender messageSender;
    private final XuanJi.api.action.PlatformActionHub hub;
    private final XuanJi.adapter.qqbot.storage.QqBotRepository qqBotRepository;

    public QqXuanJiMessageSender(MessageSender messageSender, XuanJi.api.action.PlatformActionHub hub,
                                 XuanJi.adapter.qqbot.storage.QqBotRepository qqBotRepository) {
        this.messageSender = messageSender;
        this.hub = hub;
        this.qqBotRepository = qqBotRepository;
    }

    /** 启动时把 QQ 支持的平台动作注册到统一分发中枢。 */
    @PostConstruct
    public void registerActions() {
        hub.register(this);
    }

    /** 在指定机器人上下文内执行任务（供 PluginServices 等无事件上下文场景绑定 bot）。 */
    @Override
    public void runWith(String botKey, Runnable task) {
        // 路由层可能传带 platform: 前缀的 botKey（如 qq:1905134745），QQ 注册按纯 AppID，需剥前缀
        messageSender.runWithRobotContext(stripPlatformPrefix(botKey), task);
    }

    private String stripPlatformPrefix(String botKey) {
        if (botKey != null && botKey.startsWith("qq:")) {
            return botKey.substring(3);
        }
        return botKey;
    }

    // ──────────────── 平台动作提供者（统一动作协议） ────────────────

    @Override
    public String platform() {
        return "qq";
    }

    /** qqbot 的 botKey 是纯 AppID（无前缀）。 */
    @Override
    public boolean matches(String botKey) {
        return botKey != null && !botKey.contains(":");
    }

    @Override
    public Map<String, PlatformActionHandler> actions() {
        Map<String, PlatformActionHandler> m = new LinkedHashMap<>();
        // 群基本信息（原始报文转 Map）
        m.put(PlatformActions.GROUP_INFO,
                p -> safeGet(() -> toMap(messageSender.getGroupInfo(str(p, "groupOpenid")))));
        // 群本地档案（查 qqbot_group 表，不调平台 API，避免限频；供插件提示等高频场景）
        m.put(PlatformActions.GROUP_LOCAL_INFO, p -> {
            java.util.Map<String, Object> g = qqBotRepository.getGroupInfo(
                    messageSender.currentRobotId(), str(p, "groupOpenid"));
            if (g == null) return Map.of("found", false);
            return Map.of("found", true,
                    "group_name", g.get("GROUP_NAME") == null ? "" : String.valueOf(g.get("GROUP_NAME")),
                    "member_count", g.get("MEMBER_COUNT") == null ? "" : String.valueOf(g.get("MEMBER_COUNT")));
        });
        // 机器人在群内的状态（原始报文转 Map）
        m.put(PlatformActions.GROUP_BOT_STATE,
                p -> safeGet(() -> toMap(messageSender.getBotGroupState(str(p, "groupOpenid")))));
        // 群成员禁言（minutes<=0 解除；memberOpenid 为被禁言成员；分钟→秒在此换算）
        m.put(PlatformActions.GROUP_MUTE, p -> {
            String group = str(p, "groupOpenid");
            String member = str(p, "memberOpenid");
            int minutes = intOf(p, "minutes");
            int seconds = minutes > 0 ? minutes * 60 : 0;
            String op = seconds > 0 ? "add" : "del";
            String expire = seconds > 0
                    ? fmtRfc3339(System.currentTimeMillis() / 1000 + seconds)
                    : "";
            ObjectNode resp = messageSender.setGroupMute(
                    messageSender.currentRobotId(), messageSender.currentEnvType(), group, member, op, expire);
            return Map.of("data", (Object) toMap(resp));
        });
        // 入群申请审批
        m.put(PlatformActions.GROUP_APPROVE, p -> {
            ObjectNode resp = messageSender.approveGroupJoinRequest(
                    str(p, "groupOpenid"), str(p, "memberOpenid"), boolOf(p, "approve"), str(p, "reason"));
            return Map.of("data", (Object) toMap(resp));
        });
        // 入群申请列表
        m.put(PlatformActions.GROUP_JOIN_REQUEST_LIST, p -> {
            Integer start = intOrNull(p, "start");
            Integer limit = intOrNull(p, "limit");
            ObjectNode resp = start != null
                    ? messageSender.listGroupJoinRequests(messageSender.currentRobotId(), messageSender.currentEnvType(),
                            str(p, "groupOpenid"), start, limit)
                    : messageSender.listGroupJoinRequests(str(p, "groupOpenid"));
            return Map.of("data", (Object) toMap(resp));
        });
        // 撤回群消息
        m.put(PlatformActions.GROUP_RECALL, p -> {
            ObjectNode resp = messageSender.retractGroupMessage(str(p, "groupOpenid"), str(p, "msgId"));
            return Map.of("data", (Object) toMap(resp));
        });
        // 撤回单聊消息
        m.put(PlatformActions.GROUP_RECALL_PRIVATE, p -> {
            ObjectNode resp = messageSender.retractC2cMessage(str(p, "openid"), str(p, "msgId"));
            Map<String, Object> d = toMap(resp);
            return d == null ? Map.of() : Map.of("data", (Object) d);
        });
        // 群禁言状态（restrict_chat_setting 原始报文转 Map）
        m.put(PlatformActions.GROUP_MUTE_STATUS, p -> {
            ObjectNode resp = messageSender.getGroupRestrictSetting(str(p, "groupOpenid"));
            Map<String, Object> d = toMap(resp);
            return d == null ? Map.of() : Map.of("data", (Object) d);
        });
        // 群成员列表（查 qqbot_group_member 表，免限频）
        m.put(PlatformActions.GROUP_MEMBER_LIST, p -> {
            java.util.List<Map<String, Object>> list = qqBotRepository.listGroupMembers(
                    messageSender.currentRobotId(), str(p, "groupOpenid"));
            return Map.of("data", (Object) (list == null ? java.util.List.of() : list));
        });
        // 机器人所在群列表（查本地库）
        m.put(PlatformActions.GROUP_LIST, p -> {
            java.util.List<Map<String, Object>> list = qqBotRepository.listGroups(messageSender.currentRobotId());
            return Map.of("data", (Object) (list == null ? java.util.List.of() : list));
        });
        // 机器人在群内角色（查 qqbot_group_robot 表）
        m.put(PlatformActions.GROUP_BOT_ROLE, p -> {
            String role = qqBotRepository.getGroupRobotRole(
                    messageSender.currentRobotId(), str(p, "groupOpenid"));
            return Map.of("data", (Object) Map.of("role", role == null ? "" : role));
        });
        // 单聊用户列表（查本地库）
        m.put(PlatformActions.USER_LIST, p -> {
            java.util.List<Map<String, Object>> list = qqBotRepository.listUsers(messageSender.currentRobotId());
            return Map.of("data", (Object) (list == null ? java.util.List.of() : list));
        });
        return m;
    }

    /** 查询失败（未配置机器人 / 网络异常等）时返回 null，不让插件命令崩溃。 */
    private Map<String, Object> safeGet(java.util.function.Supplier<Map<String, Object>> task) {
        try {
            return task.get();
        } catch (Exception e) {
            log.warn("[QQ动作] 执行失败: {}", e.getMessage());
            return null;
        }
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int intOf(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return 0;
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v).trim());
    }

    private static Integer intOrNull(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        try {
            return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean boolOf(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return false;
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    private Map<String, Object> toMap(ObjectNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(node, Map.class);
    }

    @Override
    public XuanJiSendReceipt reply(XuanJiMessage chain) {
        XuanJiEvent event = BotContext.current();
        if (event == null) {
            return XuanJiSendReceipt.fail("无事件上下文(BotContext)", 0);
        }
        XuanJiTarget target = event.group() != null
                ? new XuanJiTarget.Group(event.group().groupId())
                : (event.sender() != null && event.sender().id() != null && !event.sender().id().isBlank()
                    ? new XuanJiTarget.Private(event.sender().id()) : null);
        if (target == null) {
            return XuanJiSendReceipt.fail("无法解析发送目标", 0);
        }
        String robotId = event.bot() != null ? event.bot().selfId() : messageSender.currentRobotId();
        String envType = event.envType() != null ? event.envType() : messageSender.currentEnvType();
        return sendInternal(robotId, envType, target, chain, event.replyToMsgId());
    }

    @Override
    public XuanJiSendReceipt send(XuanJiTarget target, XuanJiMessage chain) {
        return sendInternal(messageSender.currentRobotId(), messageSender.currentEnvType(),
                target, chain, null);
    }

    // ──────────────── 翻译：XuanJiMessage → QQ 协议 ────────────────

    private XuanJiSendReceipt sendInternal(String robotId, String envType,
                                           XuanJiTarget target, XuanJiMessage chain, String replyToMsgId) {
        long t0 = System.currentTimeMillis();
        if (target == null || chain == null) {
            return XuanJiSendReceipt.fail("目标或消息为空", System.currentTimeMillis() - t0);
        }
        boolean group = target instanceof XuanJiTarget.Group;
        String id = switch (target) {
            case XuanJiTarget.Private p -> p.openid();
            case XuanJiTarget.Group g -> g.groupOpenid();
            case XuanJiTarget.Guild g -> g.channelId();
        };
        try {
            // 1) Ark 模板卡片
            XuanJiMessageElement.Ark ark = first(chain, XuanJiMessageElement.Ark.class);
            if (ark != null) {
                Object arkBody = ark.nativePayload() != null ? ark.nativePayload() : arkBody(ark.templateId());
                ObjectNode r = group
                        ? messageSender.sendGroupArk(robotId, envType, id, arkBody, replyToMsgId)
                        : messageSender.sendC2cArk(robotId, envType, id, arkBody, replyToMsgId);
                return receipt(r, t0);
            }
            // 2) Markdown（可附带键盘）
            XuanJiMessageElement.Markdown md = first(chain, XuanJiMessageElement.Markdown.class);
            if (md != null) {
                Object keyboard = null;
                XuanJiMessageElement.Keyboard kb = first(chain, XuanJiMessageElement.Keyboard.class);
                if (kb != null) keyboard = kb.nativePayload();
                Object mdBody = md.nativePayload() != null ? md.nativePayload() : markdownBody(md.content());
                ObjectNode r = group
                        ? messageSender.sendGroupMarkdown(robotId, envType, id, mdBody, keyboard, replyToMsgId)
                        : messageSender.sendC2cMarkdown(robotId, envType, id, mdBody, keyboard, replyToMsgId);
                return receipt(r, t0);
            }
            // 3) 富媒体（图片/视频/文件；语音需字节，走专用方法）
            java.util.List<XuanJiMessageElement.Media> medias = chain.medias();
            if (!medias.isEmpty()) {
                XuanJiMessageElement.Media m = medias.get(0);
                int fileType = switch (m) {
                    case XuanJiMessageElement.Image ignored -> 1;
                    case XuanJiMessageElement.Video ignored -> 2;
                    case XuanJiMessageElement.File ignored -> 4;
                    default -> -1; // Voice 等字节媒体
                };
                if (fileType > 0) {
                    ObjectNode r = group
                            ? messageSender.uploadAndSendGroupMedia(robotId, envType, id, fileType, m.rawRef(), replyToMsgId)
                            : messageSender.uploadAndSendC2cMedia(robotId, envType, id, fileType, m.rawRef(), replyToMsgId);
                    return receipt(r, t0);
                }
                return XuanJiSendReceipt.fail("语音等字节媒体请使用专用方法(replyVoice/replyImageFile)",
                        System.currentTimeMillis() - t0);
            }
            // 4) 文本（含 @ 时自动升级 markdown，@ 才能真正渲染）
            String text = chain.plainText();
            boolean hasAt = chain.has(XuanJiMessageElement.At.class) || LEGACY_AT.matcher(text).find();
            if (!hasAt) {
                if (text.isBlank()) {
                    return XuanJiSendReceipt.fail("消息链无可发送内容", System.currentTimeMillis() - t0);
                }
                ObjectNode r = group
                        ? messageSender.sendGroupText(robotId, envType, id, text, replyToMsgId)
                        : messageSender.sendC2cText(robotId, envType, id, text, replyToMsgId);
                return receipt(r, t0);
            }
            StringBuilder sb = new StringBuilder(LEGACY_AT.matcher(text).replaceAll("<qqbot-at-user id=\"$1\" />"));
            for (XuanJiMessageElement e : chain.elements()) {
                if (e instanceof XuanJiMessageElement.At at) {
                    if ("all".equals(at.userId())) {
                        sb.append("<@everyone>");
                    } else if (at.userId() != null && !at.userId().isBlank()) {
                        sb.append("<qqbot-at-user id=\"").append(at.userId()).append("\" />");
                    }
                }
            }
            ObjectNode r = group
                    ? messageSender.sendGroupMarkdown(robotId, envType, id, markdownBody(sb.toString()), null, replyToMsgId)
                    : messageSender.sendC2cMarkdown(robotId, envType, id, markdownBody(sb.toString()), null, replyToMsgId);
            return receipt(r, t0);
        } catch (Exception e) {
            log.warn("[QQ发送] 翻译/发送失败: target={} err={}", id, e.getMessage());
            return XuanJiSendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    private XuanJiSendReceipt receipt(ObjectNode resp, long t0) {
        if (resp == null) {
            return XuanJiSendReceipt.fail("发送无回执", System.currentTimeMillis() - t0);
        }
        String msgId = resp.path("id").asText(null);
        return (msgId != null && !msgId.isBlank())
                ? XuanJiSendReceipt.ok(msgId, System.currentTimeMillis() - t0)
                : XuanJiSendReceipt.ok("", System.currentTimeMillis() - t0);
    }

    /** 纯文本 markdown → QQ 协议结构（content 形式，与 QqProactiveSender 旧逻辑一致）。 */
    private static ObjectNode markdownBody(String content) {
        // 原生自定义 markdown：只带 content，不带模板字段（custom_template_id/params 会导致 QQ 11255）
        ObjectNode md = Json.obj();
        // @ 用户：老协议 <@openid>/<@!openid> → QQ markdown 合法格式 <qqbot-at-user id="openid"/>
        // （QQ markdown 里裸 <@!xxx> 会报 11255 invalid request）
        String c = content == null ? "" : content;
        c = c.replaceAll("<@!([A-Fa-f0-9]+)>", "<qqbot-at-user id=\"$1\"/>");
        c = c.replaceAll("<@([A-Fa-f0-9]+)>", "<qqbot-at-user id=\"$1\"/>");
        md.put("content", c);
        return md;
    }

    /** 仅有模板 ID 的 Ark → 最小协议结构（有 nativePayload 时调用方直接透传）。 */
    private static ObjectNode arkBody(int templateId) {
        ObjectNode ark = Json.obj();
        ark.put("template_id", templateId);
        ark.set("kv", Json.arr());
        return ark;
    }

    private static <T extends XuanJiMessageElement> T first(XuanJiMessage chain, Class<T> type) {
        for (XuanJiMessageElement e : chain.elements()) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }
        return null;
    }

    // ──────────────── 动作类（QQ 已开放的能力，override 默认 fail） ────────────────

    /** 撤回群/单聊消息（QQ 支持，runWith 上下文内解析当前机器人）。 */
    @Override
    public XuanJiSendReceipt recall(XuanJiTarget target, String msgId) {
        long t0 = System.currentTimeMillis();
        try {
            ObjectNode resp = switch (target) {
                case XuanJiTarget.Private p -> messageSender.retractC2cMessage(p.openid(), msgId);
                case XuanJiTarget.Group g -> messageSender.retractGroupMessage(g.groupOpenid(), msgId);
                case XuanJiTarget.Guild g -> null;
            };
            return resp != null ? receipt(resp, t0) : XuanJiSendReceipt.fail("目标类型不支持撤回", System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("[QQ发送] 撤回失败: {}", e.getMessage());
            return XuanJiSendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    /**
     * 设置群成员禁言（QQ 官方 restrict_chat_setting，seconds&lt;=0 解除禁言）。
     * 调用前需由调用方绑定机器人上下文（runWith），否则回退第一个机器人。
     */
    @Override
    public XuanJiSendReceipt mute(XuanJiTarget.Group group, String userId, int seconds) {
        long t0 = System.currentTimeMillis();
        try {
            String robotId = messageSender.currentRobotId();
            String envType = messageSender.currentEnvType();
            String op = seconds > 0 ? "add" : "del";
            String expire = seconds > 0
                    ? fmtRfc3339(System.currentTimeMillis() / 1000 + seconds)
                    : "";
            ObjectNode resp = messageSender.setGroupMute(
                    robotId, envType, group.groupOpenid(), userId, op, expire);
            return receipt(resp, t0);
        } catch (Exception e) {
            log.warn("[QQ发送] 禁言失败: {}", e.getMessage());
            return XuanJiSendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    /** epoch 秒 → RFC3339 带时区字符串（restrict_chat_setting 的 mute_expire_at 格式）。 */
    private static String fmtRfc3339(long epochSec) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 入群申请审批（requestId 传申请者 member_openid，target 为群）。
     * 调用前需由调用方绑定机器人上下文（runWith）。
     */
    @Override
    public XuanJiSendReceipt approve(XuanJiTarget target, String requestId, boolean accept) {
        long t0 = System.currentTimeMillis();
        if (!(target instanceof XuanJiTarget.Group g)) {
            return XuanJiSendReceipt.fail("仅支持群入群审批", System.currentTimeMillis() - t0);
        }
        try {
            ObjectNode resp = messageSender.approveGroupJoinRequest(g.groupOpenid(), requestId, accept, null);
            return receipt(resp, t0);
        } catch (Exception e) {
            log.warn("[QQ发送] 入群审批失败: {}", e.getMessage());
            return XuanJiSendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }
}
