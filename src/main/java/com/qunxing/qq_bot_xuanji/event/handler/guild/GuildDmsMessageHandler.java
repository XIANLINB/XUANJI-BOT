package com.qunxing.qq_bot_xuanji.event.handler.guild;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 频道私信消息事件处理器
 */
@Slf4j
@Component
@EventMapping("DIRECT_MESSAGE_CREATE")
public class GuildDmsMessageHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "DMS_MESSAGE_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, JSONObject data) {
        try {
            String msgId = data.optString("id", "");
            String content = data.optString("content", "");
            String guildId = data.optString("guild_id", "");

            JSONObject author = data.optJSONObject("author");
            String senderId = author != null ? author.optString("id", "") : "";

            log.info("[私信消息] guild={}, sender={}, content={}", guildId, senderId, content);

        } catch (Exception e) {
            log.error("[私信消息] 处理异常: error={}", e.getMessage(), e);
        }
    }
}
