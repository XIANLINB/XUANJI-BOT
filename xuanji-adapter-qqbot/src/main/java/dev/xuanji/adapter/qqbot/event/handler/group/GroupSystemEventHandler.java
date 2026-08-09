package dev.xuanji.adapter.qqbot.event.handler.group;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.core.storage.log.MessageLogger;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
    private final XuanjiRobotProperties robotProperties;
    private final dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository;

    @Override public String getEventType() { return "GROUP_EVENT"; }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
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
                        data.toString(), dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[事件落库] 失败: {}", ex.getMessage());
            }

            switch (eventType) {
                case "GROUP_ADD_ROBOT" -> {
                    // 机器人入群 → 写入群信息表
                    upsertGroup(appId, groupOpenid, "active");
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_DEL_ROBOT" -> {
                    try {
                        qqBotRepository.markGroupRemoved(appId, groupOpenid);
                    } catch (Exception ex) {
                        log.debug("群表更新失败: {}", ex.getMessage());
                    }
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MEMBER_ADD" -> {
                    upsertMember(appId, groupOpenid, memberOpenid, data.path("username").asText(null));
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MEMBER_REMOVE" -> {
                    try {
                        qqBotRepository.markMemberRemoved(appId, groupOpenid, memberOpenid);
                    } catch (Exception ex) {
                        log.debug("成员表删除失败: {}", ex.getMessage());
                    }
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MSG_REJECT", "GROUP_MSG_RECEIVE" -> {
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_JOIN_REQUEST" -> {
                    // 用户入群申请 → 防御式解析 + 记录（自动同意/拒绝待接入 QQ API）
                    JoinRequestInfo jr = parseJoinRequest(data);
                    log.info("[群事件] 入群申请: group={}, user={}({}), source={}, 验证信息={}, 申请id={}",
                            jr.groupOpenid(), jr.username(), jr.memberOpenid(),
                            jr.applySource(), jr.verifyMessage(),jr.requestId());
                    MessageLogger.event("IN", appId, eventType,
                            jr.groupOpenid() != null ? jr.groupOpenid() : groupOpenid, data.toString());
                }
                default -> MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
            }
        } catch (Exception e) {
            log.error("[群事件] 异常: {}", e.getMessage(), e);
        }
    }

    private void upsertGroup(String appId, String groupId, String status) {
        try {
            qqBotRepository.ensureGroup(appId, groupId);
            if (status != null) qqBotRepository.upsertGroup(appId, groupId, null, null, null, null, status);
        } catch (Exception ignored) {}
    }

    private void upsertMember(String appId, String groupId, String memberId, String nickname) {
        try {
            qqBotRepository.ensureGroupMember(appId, groupId, memberId, null, nickname);
        } catch (Exception ignored) {}
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
