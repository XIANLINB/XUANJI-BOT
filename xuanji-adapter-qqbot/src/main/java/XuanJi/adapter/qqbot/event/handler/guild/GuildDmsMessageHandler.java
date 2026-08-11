package XuanJi.adapter.qqbot.event.handler.guild;

import XuanJi.core.event.EventHandler;
import XuanJi.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import XuanJi.api.json.Json;
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
    public void handle(XuanJi.api.event.XuanJiEvent botEvent) {
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
