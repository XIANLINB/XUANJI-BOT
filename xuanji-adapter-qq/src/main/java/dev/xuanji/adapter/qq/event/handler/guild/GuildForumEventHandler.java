package dev.xuanji.adapter.qq.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 论坛事件处理器
 */
@Slf4j
@Component
@EventMapping({
        "FORUM_THREAD_CREATE", "FORUM_THREAD_UPDATE", "FORUM_THREAD_DELETE",
        "FORUM_POST_CREATE", "FORUM_POST_DELETE",
        "FORUM_REPLY_CREATE", "FORUM_REPLY_DELETE",
        "FORUM_PUBLISH_AUDIT_RESULT"
})
public class GuildForumEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "FORUM_EVENT";
    }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        String eventType = data.path("_eventType").asText("");

        String eventName = switch (eventType) {
            case "FORUM_THREAD_CREATE" -> "主题创建";
            case "FORUM_THREAD_UPDATE" -> "主题更新";
            case "FORUM_THREAD_DELETE" -> "主题删除";
            case "FORUM_POST_CREATE" -> "帖子创建";
            case "FORUM_POST_DELETE" -> "帖子删除";
            case "FORUM_REPLY_CREATE" -> "评论创建";
            case "FORUM_REPLY_DELETE" -> "评论删除";
            case "FORUM_PUBLISH_AUDIT_RESULT" -> "发表审核结果";
            default -> eventType;
        };

        log.info("[论坛{}]", eventName);
    }
}
