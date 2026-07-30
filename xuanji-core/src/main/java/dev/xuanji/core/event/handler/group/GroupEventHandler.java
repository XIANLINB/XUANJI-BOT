package dev.xuanji.core.event.handler.group;

import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 群聊事件处理器
 *
 * <p>处理与群聊相关的系统事件：
 * <ul>
 *   <li>GROUP_ADD_ROBOT — 机器人被添加到群聊</li>
 *   <li>GROUP_DEL_ROBOT — 机器人被移出群聊</li>
 *   <li>GROUP_MSG_REJECT — 群管理员关闭机器人消息通知</li>
 *   <li>GROUP_MSG_RECEIVE — 群管理员开启机器人消息通知</li>
 *   <li>GROUP_MEMBER_ADD — 群成员加入</li>
 *   <li>GROUP_MEMBER_REMOVE — 群成员退出</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({
        "GROUP_ADD_ROBOT", "GROUP_DEL_ROBOT",
        "GROUP_MSG_REJECT", "GROUP_MSG_RECEIVE",
        "GROUP_MEMBER_ADD", "GROUP_MEMBER_REMOVE"
})
public class GroupEventHandler implements EventHandler {

    @Override
    public String getEventType() {
        return "GROUP_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, ObjectNode data) {
        try {
            String eventType = data.path("_eventType").asText("");
            String groupOpenid = data.path("group_openid").asText("");
            String opMemberOpenid = data.path("op_member_openid").asText("");
            String memberOpenid = data.path("member_openid").asText("");

            // 根据事件类型显示友好的名称
            String eventName = switch (eventType) {
                case "GROUP_ADD_ROBOT" -> "机器人入群";
                case "GROUP_DEL_ROBOT" -> "机器人退群";
                case "GROUP_MSG_REJECT" -> "群通知关闭";
                case "GROUP_MSG_RECEIVE" -> "群通知开启";
                case "GROUP_MEMBER_ADD" -> "成员入群";
                case "GROUP_MEMBER_REMOVE" -> "成员退群";
                default -> eventType;
            };

            log.info("[{}][群{}] opMember={}, member={}",
                    eventName, groupOpenid, opMemberOpenid, memberOpenid);

        } catch (Exception e) {
            log.error("[群事件] 处理异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }
}
