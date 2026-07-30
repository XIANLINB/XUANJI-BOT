package com.qunxing.qq_bot_xuanji.event.handler.guild;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
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
    public void handle(Long robotId, String envType, JSONObject data) {
        log.info("[互动事件][按钮点击] data={}", data);
    }
}
