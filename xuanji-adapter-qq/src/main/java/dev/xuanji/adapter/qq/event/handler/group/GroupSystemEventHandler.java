package dev.xuanji.adapter.qq.event.handler.group;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.core.storage.log.MessageLogger;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        "GROUP_MEMBER_ADD", "GROUP_MEMBER_REMOVE"
})
public class GroupSystemEventHandler implements EventHandler {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;

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

            switch (eventType) {
                case "GROUP_ADD_ROBOT" -> {
                    // 机器人入群 → 写入群信息表
                    upsertGroup(appId, groupOpenid, "active");
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_DEL_ROBOT" -> {
                    try {
                        // 有记录则标记软删除，无记录则插入一条已删除记录
                        jdbc.update("MERGE INTO xuanji_qqbot_group (bot_id, group_id, status, is_deleted) KEY(bot_id,group_id) VALUES (?,?,?,?)",
                                appId, groupOpenid, "removed", 1);
                    } catch (Exception ex) {
                        // 表字段可能还没建好（旧库），只记事件
                        log.debug("群表更新失败(可能是旧库): {}", ex.getMessage());
                    }
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MEMBER_ADD" -> {
                    upsertMember(appId, groupOpenid, memberOpenid);
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MEMBER_REMOVE" -> {
                    try {
                        jdbc.update("UPDATE xuanji_qqbot_group_member SET is_deleted=1 WHERE bot_id=? AND group_id=? AND member_id=?",
                                appId, groupOpenid, memberOpenid);
                    } catch (Exception ex) {
                        log.debug("成员表删除失败: {}", ex.getMessage());
                    }
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                case "GROUP_MSG_REJECT", "GROUP_MSG_RECEIVE" -> {
                    MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
                }
                default -> MessageLogger.event("IN", appId, eventType, groupOpenid, data.toString());
            }
        } catch (Exception e) {
            log.error("[群事件] 异常: {}", e.getMessage(), e);
        }
    }

    private void upsertGroup(String appId, String groupId, String status) {
        try {
            jdbc.update("MERGE INTO xuanji_qqbot_group (bot_id, group_id, status) KEY(bot_id,group_id) VALUES (?,?,?)",
                    appId, groupId, status);
        } catch (Exception ignored) {}
    }

    private void upsertMember(String appId, String groupId, String memberId) {
        try {
            jdbc.update("MERGE INTO xuanji_qqbot_group_member (bot_id, group_id, member_id, is_deleted) KEY(bot_id,group_id,member_id) VALUES (?,?,?,0)",
                    appId, groupId, memberId);
        } catch (Exception ignored) {}
    }
}
