package dev.xuanji.core.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
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
    public void handle(Long robotId, String envType, ObjectNode data) {
        try {
            String eventType = data.path("_eventType").asText("");
            String msgId = data.path("id").asText("");
            String content = data.path("content").asText("");
            String channelId = data.path("channel_id").asText("");
            String guildId = data.path("guild_id").asText("");

            ObjectNode author = Json.getObj(data, "author");
            String senderId = author != null ? author.path("id").asText("") : "";

            log.info("[频道消息] type={}, guild={}, channel={}, sender={}, content={}",
                    eventType, guildId, channelId, senderId, content);

        } catch (Exception e) {
            log.error("[频道消息] 处理异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }
}
