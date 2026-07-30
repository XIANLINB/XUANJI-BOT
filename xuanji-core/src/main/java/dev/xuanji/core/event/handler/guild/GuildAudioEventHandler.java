package dev.xuanji.core.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
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
    public void handle(Long robotId, String envType, ObjectNode data) {
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
