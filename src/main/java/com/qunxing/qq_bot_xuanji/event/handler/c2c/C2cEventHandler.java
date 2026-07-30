package com.qunxing.qq_bot_xuanji.event.handler.c2c;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 单聊事件处理器
 *
 * <p>处理与单聊相关的非消息事件：
 * <ul>
 *   <li>FRIEND_ADD — 用户添加机器人为好友</li>
 *   <li>FRIEND_DEL — 用户删除机器人好友</li>
 *   <li>C2C_MSG_REJECT — 用户关闭主动消息推送权限</li>
 *   <li>C2C_MSG_RECEIVE — 用户开启主动消息推送权限</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({"FRIEND_ADD", "FRIEND_DEL", "C2C_MSG_REJECT", "C2C_MSG_RECEIVE"})
public class C2cEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "C2C_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, JSONObject data) {
        try {
            String eventType = data.optString("_eventType", "");
            String openid = data.optString("openid", "");

            String eventName = switch (eventType) {
                case "FRIEND_ADD" -> "添加好友";
                case "FRIEND_DEL" -> "删除好友";
                case "C2C_MSG_REJECT" -> "关闭消息推送";
                case "C2C_MSG_RECEIVE" -> "开启消息推送";
                default -> eventType;
            };

            log.info("[{}][用户{}]", eventName, openid);

        } catch (Exception e) {
            log.error("[单聊事件] 处理异常: error={}", e.getMessage(), e);
        }
    }
}
