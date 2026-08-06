package dev.xuanji.adapter.qqbot.event.handler.c2c;

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
 * 单聊系统事件 — 好友添加/删除、消息通知开关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EventMapping({"FRIEND_ADD", "FRIEND_DEL", "C2C_MSG_REJECT", "C2C_MSG_RECEIVE"})
public class C2cSystemEventHandler implements EventHandler {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;
    private final dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository;

    @Override public String getEventType() { return "C2C_EVENT"; }

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
            String openid = data.path("openid").asText("");
            if (openid.isEmpty()) openid = data.path("user_openid").asText("");

            log.info("[{}][single] openid={}", eventType, openid);
            MessageLogger.event("IN", appId, eventType, data.toString());
            // 事件落库（per-bot qqbot_event，控制台事件流数据源）
            try {
                qqBotRepository.insertEvent(appId, eventType, null, openid,
                        data.toString(), dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[事件落库] 失败: {}", ex.getMessage());
            }

            switch (eventType) {
                case "FRIEND_ADD" -> {
                    try {
                        qqBotRepository.ensureUser(appId, openid, data.path("username").asText(null));
                    } catch (Exception ex) {
                        log.debug("用户表更新失败: {}", ex.getMessage());
                    }
                }
                case "FRIEND_DEL" -> {
                    try {
                        qqBotRepository.markUserRemoved(appId, openid);
                    } catch (Exception ex) {
                        log.debug("用户表更新失败: {}", ex.getMessage());
                    }
                }
                default -> {} // C2C_MSG_REJECT / C2C_MSG_RECEIVE
            }
        } catch (Exception e) {
            log.error("[C2C事件] 异常: {}", e.getMessage(), e);
        }
    }
}
