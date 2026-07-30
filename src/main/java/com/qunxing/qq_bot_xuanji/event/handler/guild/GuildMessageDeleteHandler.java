package com.qunxing.qq_bot_xuanji.event.handler.guild;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 消息删除事件处理器
 */
@Slf4j
@Component
@EventMapping({"MESSAGE_DELETE", "PUBLIC_MESSAGE_DELETE", "DIRECT_MESSAGE_DELETE"})
public class GuildMessageDeleteHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "MESSAGE_DELETE_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, JSONObject data) {
        String eventType = data.optString("_eventType", "");

        String eventName = switch (eventType) {
            case "MESSAGE_DELETE" -> "频道消息撤回";
            case "PUBLIC_MESSAGE_DELETE" -> "频道公开消息删除";
            case "DIRECT_MESSAGE_DELETE" -> "私信消息撤回";
            default -> eventType;
        };

        log.info("[消息删除] {}", eventName);
    }
}
