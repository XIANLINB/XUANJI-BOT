package XuanJi.adapter.qqbot.event.handler.group;

import XuanJi.adapter.qqbot.service.GroupProfileSync;
import XuanJi.adapter.qqbot.service.GroupRobotStateSyncService;
import XuanJi.api.json.Json;
import XuanJi.core.event.EventHandler;
import XuanJi.core.event.EventMapping;
import XuanJi.core.config.XuanJiRobotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 群聊系统事件处理器 — 机器人入群/退群、成员进退群、消息通知开关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EventMapping({
        "GROUP_ADD_ROBOT", "GROUP_DEL_ROBOT",
        "GROUP_MSG_REJECT", "GROUP_MSG_RECEIVE",
        "GROUP_MEMBER_ADD", "GROUP_MEMBER_REMOVE",
        "GROUP_JOIN_REQUEST"
})
public class GroupSystemEventHandler implements EventHandler {

    private final JdbcTemplate jdbc;
    private final XuanJiRobotProperties robotProperties;
    private final XuanJi.adapter.qqbot.storage.QqBotRepository qqBotRepository;
    private final GroupProfileSync groupProfileSync;
    private final GroupRobotStateSyncService groupRobotStateSync;
    private final XuanJi.core.command.CommandRegistry commandRegistry;

    @Override public String getEventType() { return "GROUP_EVENT"; }

    @Override
    public void handle(XuanJi.api.event.XuanJiEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        try {
            String eventType = data.path("_eventType").asText("");
            String botKey = robotProperties.findBotKeyByRobotId(robotId);
            if (botKey == null) botKey = "bot1";
            String appId = robotProperties.getRobots() != null && robotProperties.getRobots().get(botKey) != null
                    ? robotProperties.getRobots().get(botKey).getAppId() : String.valueOf(robotId);
            String groupOpenid = data.path("group_openid").asText("");
            String opMemberOpenid = data.path("op_member_openid").asText("");
            String memberOpenid = data.path("member_openid").asText("");

            log.info("[{}][群{}] opMember={}, member={}", eventType, groupOpenid, opMemberOpenid, memberOpenid);

            // 事件落库（per-bot qqbot_event，控制台事件流数据源）
            try {
                qqBotRepository.insertEvent(appId, eventType, groupOpenid, memberOpenid,
                        data.toString(), XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[事件落库] 失败: {}", ex.getMessage());
            }

            // 所有群事件统一分发给插件（@GroupEvent 监听：入群/退群提示、入群审批、机器人进群/退群等）
            dispatchPluginEvent(data, eventType, robotId);

            switch (eventType) {
                case "GROUP_ADD_ROBOT" -> {
                    // 机器人入群 → 写入群信息表（带加入时间）+ 拉取群信息同步档案 + 立即同步机器人群内状态
                    long joinTime = data.path("timestamp").asLong(0);
                    if (joinTime <= 0) joinTime = System.currentTimeMillis() / 1000;
                    upsertGroup(appId, groupOpenid, "active", joinTime);
                    groupProfileSync.syncGroupInfo(appId, groupOpenid);
                    // 先落一行空状态（保证进入定时错峰队列），再立即拉一次 bot_state 补角色
                    qqBotRepository.upsertGroupRobotState(appId, groupOpenid, null, null, null, null, joinTime);
                    groupRobotStateSync.sync(appId, groupOpenid);
                }
                case "GROUP_DEL_ROBOT" -> {
                    try {
                        qqBotRepository.markGroupRemoved(appId, groupOpenid);
                    } catch (Exception ex) {
                        log.debug("群表更新失败: {}", ex.getMessage());
                    }
                }
                case "GROUP_MEMBER_ADD" -> {
                    // 新成员入群 → 建成员档案（重复入群不新增，但成员数仍 +1）
                    try {
                        upsertMember(appId, groupOpenid, memberOpenid, data.path("username").asText(null));
                    } catch (Exception ignored) { }
                    // 收到事件即 +1（不依赖是否新增）：插件随后读 member_count 已是 +1 后的值
                    qqBotRepository.adjustGroupMemberCount(appId, groupOpenid, 1);
                }
                case "GROUP_MEMBER_REMOVE" -> {
                    // 成员退群 → 软删成员档案 + 群成员数 -1（收到事件即 -1，与 ADD 对称）
                    try {
                        qqBotRepository.markMemberRemoved(appId, groupOpenid, memberOpenid);
                    } catch (Exception ex) {
                        log.debug("成员表删除失败: {}", ex.getMessage());
                    }
                    qqBotRepository.adjustGroupMemberCount(appId, groupOpenid, -1);
                }
                case "GROUP_MSG_REJECT", "GROUP_MSG_RECEIVE" -> {
                }
                case "GROUP_JOIN_REQUEST" -> {
                    // 用户入群申请 → 防御式解析 + 记录（审批逻辑由插件/群管能力处理）
                    JoinRequestInfo jr = parseJoinRequest(data);
                    log.info("[群事件] 入群申请: group={}, user={}({}), source={}, 验证信息={}, 申请id={}",
                            jr.groupOpenid(), jr.username(), jr.memberOpenid(),
                            jr.applySource(), jr.verifyMessage(), jr.requestId());
                }
            }
        } catch (Exception e) {
            log.error("[群事件] 异常: {}", e.getMessage(), e);
        }
    }

    private void upsertGroup(String appId, String groupId, String status, Long joinTime) {
        try {
            qqBotRepository.ensureGroup(appId, groupId);
            if (status != null) qqBotRepository.upsertGroup(appId, groupId, null, null, null, joinTime, status,
                    null, null, null);
        } catch (Exception ignored) {}
    }

    /** 建成员档案；返回是否新增（true 才给群成员数 +1）。 */
    private boolean upsertMember(String appId, String groupId, String memberId, String nickname) {
        try {
            return qqBotRepository.ensureGroupMember(appId, groupId, memberId, null, nickname);
        } catch (Exception ignored) { return false; }
    }

    /** 把群系统事件构造为 SDK 事件并分发到插件（@GroupEvent 监听，如入群/退群提示插件）。 */
    private void dispatchPluginEvent(ObjectNode data, String eventType, String robotId) {
        try {
            String groupId = data.path("group_openid").asText("");
            String memberId = data.path("member_openid").asText("");
            if (groupId.isBlank() || memberId.isBlank()) return;
            String botId = robotProperties.getRobots() != null && robotProperties.getRobots().get(
                    robotProperties.findBotKeyByRobotId(robotId)) != null
                    ? robotProperties.getRobots().get(robotProperties.findBotKeyByRobotId(robotId)).getAppId() : robotId;
            XuanJi.sdk.event.GroupMessageEvent.Builder b = new XuanJi.sdk.event.GroupMessageEvent.Builder()
                    .groupId(groupId)
                    .senderId(memberId)
                    .senderName(data.path("username").asText(""))
                    .senderRole(data.path("member_role").asText(""))
                    .platform("qq")
                    .eventType(eventType)
                    .botId(botId);
            // 入群申请：把完整字段（原始 verify_info + 解析后的 verifyParsed）下发给插件，由插件自行审批
            if ("GROUP_JOIN_REQUEST".equals(eventType)) {
                b.joinRequestInfo(buildJoinRequestInfo(data));
            }
            commandRegistry.dispatchGroupEvent(b.build());
        } catch (Exception ex) {
            log.debug("[群事件] 插件分发失败 {}: {}", eventType, ex.getMessage());
        }
    }

    /**
     * 构造入群申请完整信息（框架只负责解析下发，审批判定由插件完成）：
     * {@code memberOpenid / username / applyAt / applySource / joinRequestId /
     * verifyInfo(原始 Map) / verifyParsed(解析后: method/verifyMessage/question/answer/qaMode)}。
     */
    private Map<String, Object> buildJoinRequestInfo(ObjectNode data) {
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("memberOpenid", data.path("member_openid").asText(""));
        info.put("username", data.path("username").asText(""));
        info.put("applyAt", data.path("apply_at").asText(""));
        info.put("applySource", data.path("apply_source").asText(""));
        info.put("joinRequestId", data.path("join_request_id").asText(""));
        if (data.path("verify_info").isObject()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vi = Json.mapper().convertValue(data.path("verify_info"), Map.class);
            info.put("verifyInfo", vi);
            info.put("verifyParsed", XuanJi.adapter.qqbot.sender.QqXuanJiMessageSender.parseVerifyInfo(vi));
        }
        return info;
    }

    /** 入群申请解析结果（字段全 nullable，防御式多候选键取值）。 */
    public record JoinRequestInfo(String groupOpenid, String requestId, String memberOpenid,
                                  String username, String applySource, String applyAt,
                                  String verifyMethod, String verifyMessage) {}

    /**
     * 解析入群申请报文（GROUP_ADD_REQUEST）。
     *
     * <p>防御式兼容官方未固化结构：多候选字段名兜底、缺失即 null、data 为 null 不崩。
     */
    public static JoinRequestInfo parseJoinRequest(ObjectNode d) {
        if (d == null) return new JoinRequestInfo(null, null, null, null, null, null, null, null);
        String group = first(d, "group_openid", "group_id", "groupOpenid");
        String reqId = first(d, "join_request_id", "request_id", "requestId", "seq");
        String member = first(d, "member_openid", "user_openid", "memberOpenid");
        String username = first(d, "username", "member_name", "nickname");
        String source = first(d, "apply_source", "applySource", "source");
        String applyAt = first(d, "apply_at", "applyAt", "timestamp", "create_time");
        ObjectNode verify = d.has("verify_info") && d.get("verify_info").isObject()
                ? (ObjectNode) d.get("verify_info") : null;
        String method = verify == null ? null : first(verify, "method", "verify_method");
        String msg = verify == null ? null : first(verify, "verify_message", "message", "verifyMessage");
        return new JoinRequestInfo(group, reqId, member, username, source, applyAt, method, msg);
    }

    private static String first(ObjectNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull() && !node.get(k).asText().isEmpty()) {
                return node.get(k).asText();
            }
        }
        return null;
    }
}
