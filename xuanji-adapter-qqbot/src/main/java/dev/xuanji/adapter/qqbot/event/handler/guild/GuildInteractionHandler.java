package dev.xuanji.adapter.qqbot.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 交互事件处理器 — 处理按钮点击等交互回调
 */
@Slf4j
@Component
@EventMapping("INTERACTION_CREATE")
public class GuildInteractionHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "INTERACTION_EVENT";
    }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        log.info("[互动事件][按钮点击] data={}", data);
    }
}
