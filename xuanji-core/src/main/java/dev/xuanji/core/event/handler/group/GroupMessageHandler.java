package dev.xuanji.core.event.handler.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.api.dto.GroupMessageEvent;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import dev.xuanji.core.command.CommandRegistry;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 群聊消息事件处理器
 *
 * <h3>测试命令（@机器人后发送）</h3>
 * <ul>
 *   <li>文本 — 文本消息</li>
 *   <li>markdown — Markdown 消息</li>
 *   <li>按钮 — Markdown + 键盘按钮</li>
 *   <li>ark23 — Ark 链接+文本列表模板</li>
 *   <li>ark24 — Ark 文本+缩略图模板</li>
 *   <li>ark37 — Ark 大图模板</li>
 *   <li>图片 — 图片消息</li>
 *   <li>语音 — 语音消息</li>
 *   <li>视频 — 视频消息</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE"})
public class GroupMessageHandler implements EventHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final XuanjiRobotProperties robotProperties;
    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;

    public GroupMessageHandler(XuanjiRobotProperties robotProperties, MessageSender messageSender,
                                CommandRegistry commandRegistry) {
        this.robotProperties = robotProperties;
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public String getEventType() {
        return "GROUP_MESSAGE_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, ObjectNode data) {
        try {
            GroupMessageEvent event = objectMapper.readValue(data.toString(), GroupMessageEvent.class);

            // 检查是否为机器人发送的消息
            if (event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot())) {
                if (robotProperties.isIgnoreBotMessages()) {
                    return;
                }
            }

            String content = event.getPlainTextContent().trim();
            String msgId = event.getId();
            String groupOpenid = event.getGroupOpenid();

            log.info("[收到群聊消息][群{}] sender={}, content={}",
                    groupOpenid, event.getAuthor().getUsername(), content);

            // 提取 member_openid（QQ 平台不用 QQ 号，每 bot 独立）
            String memberOpenid = "";
            if (event.getAuthor() != null) {
                memberOpenid = event.getAuthor().getMemberOpenid();
                if (memberOpenid == null) memberOpenid = event.getAuthor().getId();
            }

            log.info("[收到群聊消息][群{}] sender={}, member={}, content={}",
                    groupOpenid, event.getAuthor().getUsername(), memberOpenid, content);

            // @Command 指令匹配（包含全部 9 条测试命令）
            CommandRegistry.setCurrentUser(memberOpenid);
            try {
                String cmdResult = commandRegistry.execute(content);
                if (cmdResult != null) {
                    messageSender.sendGroupText(groupOpenid, cmdResult, msgId);
                    return;
                }
            } finally {
                CommandRegistry.clearCurrentUser();
            }

            // 未匹配 → 提示帮助
            if (event.isAtBot()) {
                messageSender.sendGroupText(groupOpenid, "发送\"帮助\"查看可用命令", msgId);
            }

        } catch (Exception e) {
            log.error("[群聊消息] 解析异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }
}
