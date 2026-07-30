package com.qunxing.qq_bot_xuanji.event.handler.guild;

import com.qunxing.qq_bot_xuanji.event.EventHandler;
import com.qunxing.qq_bot_xuanji.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
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
    public void handle(Long robotId, String envType, JSONObject data) {
        String eventType = data.optString("_eventType", "");

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
