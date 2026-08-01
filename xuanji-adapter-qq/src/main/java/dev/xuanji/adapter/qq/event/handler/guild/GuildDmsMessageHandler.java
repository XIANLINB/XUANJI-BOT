package dev.xuanji.adapter.qq.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
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
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        try {
            String msgId = data.path("id").asText("");
            String content = data.path("content").asText("");
            String guildId = data.path("guild_id").asText("");

            ObjectNode author = Json.getObj(data, "author");
            String senderId = author != null ? author.path("id").asText("") : "";

            log.info("[私信消息] guild={}, sender={}, content={}", guildId, senderId, content);

        } catch (Exception e) {
            log.error("[私信消息] 处理异常: error={}", e.getMessage(), e);
        }
    }
}
