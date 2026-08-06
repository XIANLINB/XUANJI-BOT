package dev.xuanji.adapter.qqbot.event.handler.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.adapter.qqbot.dto.GroupMessageEvent;
import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.adapter.qqbot.util.KeyboardBuilder;
import dev.xuanji.adapter.qqbot.util.MarkdownBuilder;
import dev.xuanji.core.command.CommandRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final dev.xuanji.core.storage.log.MessageLogService logSvc;
    private final dev.xuanji.core.storage.MessageEventRecorder eventRecorder;
    private final dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory;
    private final dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository;
    private final dev.xuanji.core.config.ConfigService configService;

    private final Timer handleTimer;
    private final Timer e2eTimer;

    public GroupMessageHandler(XuanjiRobotProperties robotProperties, MessageSender messageSender,
                               CommandRegistry commandRegistry,
                               JdbcTemplate jdbc, dev.xuanji.core.storage.log.MessageLogService logSvc,
                               MeterRegistry meterRegistry,
                               dev.xuanji.core.storage.MessageEventRecorder eventRecorder,
                               dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory,
                               dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository,
                               dev.xuanji.core.config.ConfigService configService) {
        this.robotProperties = robotProperties;
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
        this.jdbc = jdbc;
        this.logSvc = logSvc;
        this.eventRecorder = eventRecorder;
        this.botFactory = botFactory;
        this.qqBotRepository = qqBotRepository;
        this.handleTimer = Timer.builder("xuanji.message.handle")
                .description("插件指令处理耗时").register(meterRegistry);
        this.e2eTimer = Timer.builder("xuanji.message.e2e")
                .description("消息端到端耗时").register(meterRegistry);
        this.configService = configService;
    }

    @Override
    public String getEventType() {
        return "GROUP_MESSAGE_EVENT";
    }

    @Override
    public void handle(dev.xuanji.api.event.BotEvent botEvent) {
        ObjectNode data = (ObjectNode) botEvent.platformData();
        String robotId = botEvent.bot() != null ? botEvent.bot().selfId() : "";
        String envType = botEvent.envType() != null ? botEvent.envType() : "PRODUCTION";
        dev.xuanji.core.metrics.TraceContext.enter(data.has("_eventId") ? data.get("_eventId").asText() : "", String.valueOf(robotId));
        Timer.Sample handleSample = Timer.start();
        try {
            GroupMessageEvent event = objectMapper.readValue(data.toString(), GroupMessageEvent.class);

            // 机器人自消息：author.bot=true（其他机器人/本机器人发的）。总是落库记录，处理与否按三级配置
            boolean isBotAuthor = event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot());

            String content = event.getPlainTextContent().trim();
            String msgId = event.getId();
            String groupOpenid = event.getGroupOpenid();

            // 提取 member_openid（QQ 平台不用 QQ 号，每 bot 独立）
            String memberOpenid = "";
            if (event.getAuthor() != null) {
                memberOpenid = event.getAuthor().getMemberOpenid();
                if (memberOpenid == null) memberOpenid = event.getAuthor().getId();
            }

            log.info("[收到群聊消息][群{}] sender={}, memberOpenId={}, content={}",
                    groupOpenid, event.getAuthor().getUsername(), memberOpenid, content);

            // 解析 botKey / appId
            String botKey = robotProperties.findBotKeyByRobotId(robotId);
            if (botKey == null) botKey = "bot1";
            String appId = robotProperties.getRobots() != null && robotProperties.getRobots().get(botKey) != null
                    ? robotProperties.getRobots().get(botKey).getAppId() : String.valueOf(robotId);

            // 自动同步：群 + 成员记录（per-bot 实例库；机器人自消息不同步成员）
            if (!isBotAuthor) {
                String senderName = event.getAuthor() != null ? event.getAuthor().getUsername() : null;
                autoSyncGroup(appId, groupOpenid, memberOpenid, role(event), senderName);
            }

            // 消息落库（per-bot 实例库 qqbot_message，控制台消息监控数据源；机器人自消息也记录）
            try {
                qqBotRepository.insertMessage(appId, "group", groupOpenid, memberOpenid,
                        "IN", "text", content, msgId, null, null, data.toString(),
                        dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[消息落库] 失败: {}", ex.getMessage());
            }

            // 机器人自消息：三级配置判定「忽略其他机器人消息」→ 只记录不做任何处理
            if (isBotAuthor) {
                boolean ignore = configService.isIgnoreBotMessages(botKey, groupOpenid);
                if (ignore) {
                    log.info("[群聊消息] 忽略其他机器人消息（只记录不处理）: sender={}, group={}", memberOpenid, groupOpenid);
                    // 事件落库（记录）
                    try {
                        qqBotRepository.insertEvent(appId, "GROUP_MESSAGE_CREATE", groupOpenid, memberOpenid,
                                data.toString(), dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
                    } catch (Exception ex) {
                        log.debug("[事件落库] 失败: {}", ex.getMessage());
                    }
                    return;
                }
                // 不忽略：继续按正常消息处理（多机器人互聊场景）
            }
            // 事件落库（per-bot qqbot_event，控制台事件流数据源）
            try {
                qqBotRepository.insertEvent(appId, "GROUP_MESSAGE_CREATE", groupOpenid, memberOpenid,
                        data.toString(), dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[事件落库] 失败: {}", ex.getMessage());
            }

            // 记录到控制台消息流水
            eventRecorder.record(
                    "IN", "text", event.getAuthor().getUsername(),
                    groupOpenid, content, "msgId=" + msgId);

            // 记录到日志库（用真实 appId 而非 hashCode）
            dev.xuanji.core.storage.log.MessageLogger.groupMessage("IN",
                    appId, groupOpenid, memberOpenid,
                    "text", content, data.toString());

            // 事件注解指令调度
            var xjBot = botFactory.group(groupOpenid, msgId, appId);
                CommandRegistry.setContext(botKey, groupOpenid, msgId, memberOpenid,
                        sdkEvent(event, data), xjBot, "qq");
            try {
                // 权限已在 Pipeline 的 WhitelistStage(20) 统一裁决，此处不再内联检查
                String cmdResult = commandRegistry.executeGroupMessage(content);
                if (cmdResult != null) {
                    messageSender.sendGroupText(groupOpenid, cmdResult, msgId);
                    dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT",
                            appId, groupOpenid, memberOpenid,
                            "text", cmdResult, "");
                    // OUT 落库（per-bot qqbot_message，消息监控「发送」方向）
                    try {
                        qqBotRepository.insertMessage(appId, "group", groupOpenid, memberOpenid,
                                "OUT", "text", cmdResult, null, null, null, null,
                                dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
                    } catch (Exception ex) {
                        log.debug("[OUT落库] 失败: {}", ex.getMessage());
                    }
                    return;
                }
            } finally {
                CommandRegistry.clearContext();
            }

            // 未匹配 → 提示帮助
            if (event.isAtBot()) {
                String helpText = "发送\"帮助\"查看可用命令";
                messageSender.sendGroupText(groupOpenid, helpText, msgId);
                dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT", appId, groupOpenid, memberOpenid, "text", helpText, "");
                try {
                    qqBotRepository.insertMessage(appId, "group", groupOpenid, memberOpenid,
                            "OUT", "text", helpText, null, null, null, null,
                            dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
                } catch (Exception ex) {
                    log.debug("[OUT落库] 失败: {}", ex.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[群聊消息] 解析异常: robotId={}, error={}", robotId, e.getMessage(), e);
        } finally {
            handleSample.stop(handleTimer);
            dev.xuanji.core.metrics.TraceContext.exit();
        }
    }

    private void autoSyncGroup(String appId, String groupId, String memberId, String role, String nickname) {
        try {
            qqBotRepository.ensureGroup(appId, groupId);
            qqBotRepository.ensureGroupMember(appId, groupId, memberId,
                    role != null ? role : "member", nickname);
        } catch (Exception e) {
            log.debug("[群同步] 失败: {}", e.getMessage());
        }
    }

    private static String role(GroupMessageEvent e) {
        try {
            return e.getAuthor() != null ? e.getAuthor().getMemberRole() : null;
        } catch (Exception ex) { return null; }
    }

    /** 构建 SDK 事件（消息链直塞：QQ 报文经 QqMessageConverter 解析后直塞 chain/hasAttachments，媒体订阅可命中）。 */
    private static dev.xuanji.sdk.event.GroupMessageEvent sdkEvent(GroupMessageEvent raw, ObjectNode data) {
        dev.xuanji.api.message.MessageChain chain =
                dev.xuanji.adapter.qqbot.converter.QqMessageConverter.fromQqPayload(data.toString());
        return new dev.xuanji.sdk.event.GroupMessageEvent.Builder()
                .messageId(raw.getId())
                .content(raw.getContent())
                .plainText(raw.getPlainTextContent())
                .messageType(raw.getMessageType() != null ? raw.getMessageType() : 0)
                .groupId(raw.getGroupOpenid())
                .senderId(raw.getSenderId())
                .senderName(raw.getAuthor() != null ? raw.getAuthor().getUsername() : "?")
                .senderRole(raw.getAuthor() != null ? raw.getAuthor().getMemberRole() : null)
                .atBot(raw.isAtBot())
                .chain(chain)
                .hasAttachments(chain.hasMedia())
                .platform("qq")
                .build();
    }
}
