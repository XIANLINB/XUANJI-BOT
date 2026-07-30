package dev.xuanji.core.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    public void handle(Long robotId, String envType, ObjectNode data) {
        log.info("[互动事件][按钮点击] data={}", data);
    }
}
