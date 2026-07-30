package com.qunxing.qq_bot_xuanji.event.handler.guild;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 频道消息事件处理器
 *
 * <p>处理用户在 QQ 频道的子频道中发送给机器人的消息：
 * <ul>
 *   <li>AT_MESSAGE_CREATE — 公域机器人：用户 @机器人 时触发</li>
 *   <li>MESSAGE_CREATE — 私域机器人：所有消息都会触发</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({"AT_MESSAGE_CREATE", "MESSAGE_CREATE"})
public class GuildMessageEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "GUILD_MESSAGE_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, JSONObject data) {
        try {
            String eventType = data.optString("_eventType", "");
            String msgId = data.optString("id", "");
            String content = data.optString("content", "");
            String channelId = data.optString("channel_id", "");
            String guildId = data.optString("guild_id", "");

            JSONObject author = data.optJSONObject("author");
            String senderId = author != null ? author.optString("id", "") : "";

            log.info("[频道消息] type={}, guild={}, channel={}, sender={}, content={}",
                    eventType, guildId, channelId, senderId, content);

        } catch (Exception e) {
            log.error("[频道消息] 处理异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }
}
