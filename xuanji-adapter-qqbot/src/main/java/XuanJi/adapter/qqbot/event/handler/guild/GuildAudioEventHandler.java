package XuanJi.adapter.qqbot.event.handler.guild;

import XuanJi.core.event.EventHandler;
import XuanJi.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import XuanJi.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 音频事件处理器
 */
@Slf4j
@Component
@EventMapping({
        "AUDIO_START", "AUDIO_FINISH", "AUDIO_ON_MIC", "AUDIO_OFF_MIC",
        "AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER", "AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT"
})
public class GuildAudioEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "AUDIO_EVENT";
    }

    @Override
    public void handle(XuanJi.api.event.XuanJiEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        String eventType = data.path("_eventType").asText("");

        String eventName = switch (eventType) {
            case "AUDIO_START" -> "音频开始播放";
            case "AUDIO_FINISH" -> "音频播放结束";
            case "AUDIO_ON_MIC" -> "上麦";
            case "AUDIO_OFF_MIC" -> "下麦";
            case "AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER" -> "成员进入音视频";
            case "AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT" -> "成员离开音视频";
            default -> eventType;
        };

        log.info("[音频{}]", eventName);
    }
}
