package dev.xuanji.adapter.qqbot.event.handler.guild;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 系统事件处理器 — 处理频道、成员、消息审核等系统级事件
 */
@Slf4j
@Component
@EventMapping({
        "GUILD_CREATE", "GUILD_UPDATE", "GUILD_DELETE",
        "GUILD_MEMBER_ADD", "GUILD_MEMBER_UPDATE", "GUILD_MEMBER_REMOVE",
        "CHANNEL_CREATE", "CHANNEL_UPDATE", "CHANNEL_DELETE",
        "MESSAGE_AUDIT_PASS", "MESSAGE_AUDIT_REJECT",
        "MESSAGE_REACTION_ADD", "MESSAGE_REACTION_REMOVE"
})
public class GuildSystemEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "GUILD_SYSTEM_EVENT";
    }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        String eventType = data.path("_eventType").asText("");

        String eventName = switch (eventType) {
            case "GUILD_CREATE" -> "频道创建";
            case "GUILD_UPDATE" -> "频道更新";
            case "GUILD_DELETE" -> "频道删除";
            case "GUILD_MEMBER_ADD" -> "频道成员加入";
            case "GUILD_MEMBER_UPDATE" -> "频道成员更新";
            case "GUILD_MEMBER_REMOVE" -> "频道成员退出";
            case "CHANNEL_CREATE" -> "子频道创建";
            case "CHANNEL_UPDATE" -> "子频道更新";
            case "CHANNEL_DELETE" -> "子频道删除";
            case "MESSAGE_AUDIT_PASS" -> "消息审核通过";
            case "MESSAGE_AUDIT_REJECT" -> "消息审核拒绝";
            case "MESSAGE_REACTION_ADD" -> "添加表情表态";
            case "MESSAGE_REACTION_REMOVE" -> "删除表情表态";
            default -> eventType;
        };

        log.info("[{}]", eventName);
    }
}
