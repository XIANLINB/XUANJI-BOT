package dev.xuanji.adapter.qqbot.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
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
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        String eventType = data.path("_eventType").asText("");

        String eventName = switch (eventType) {
            case "MESSAGE_DELETE" -> "频道消息撤回";
            case "PUBLIC_MESSAGE_DELETE" -> "频道公开消息删除";
            case "DIRECT_MESSAGE_DELETE" -> "私信消息撤回";
            default -> eventType;
        };

        log.info("[消息删除] {}", eventName);
    }
}
