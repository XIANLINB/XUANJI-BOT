package dev.xuanji.adapter.qqbot.event.handler.group;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.adapter.qqbot.dto.GroupMessageEvent;
import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.adapter.qqbot.util.KeyboardBuilder;
import dev.xuanji.adapter.qqbot.util.MarkdownBuilder;
import dev.xuanji.core.command.CommandRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final XuanjiRobotProperties robotProperties;
    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;
    private final JdbcTemplate jdbc;
    private final dev.xuanji.core.storage.MessageEventRecorder eventRecorder;
    private final dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory;
    private final dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository;
    private final dev.xuanji.core.config.ConfigService configService;

    /** LLM 兜底接管判定（llm 模块实现；未引入 llm 模块时为空列表，不影响原逻辑）。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private java.util.List<dev.xuanji.api.llm.LlmAvailability> llmAvailabilityList = java.util.List.of();

    private final Timer handleTimer;
    private final Timer e2eTimer;

    public GroupMessageHandler(XuanjiRobotProperties robotProperties, MessageSender messageSender,
                               CommandRegistry commandRegistry,
                               JdbcTemplate jdbc,
                               MeterRegistry meterRegistry,
                               dev.xuanji.core.storage.MessageEventRecorder eventRecorder,
                               dev.xuanji.adapter.qqbot.bot.QqBotFactory botFactory,
                               dev.xuanji.adapter.qqbot.storage.QqBotRepository qqBotRepository,
                               dev.xuanji.core.config.ConfigService configService) {
        this.robotProperties = robotProperties;
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
        this.jdbc = jdbc;
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

            // 诊断：打印事件原始报文（含 _eventType / mentions / content 等，便于排查 @机器人 判定）
            log.info("[收到群聊消息] 原始报文: {}", data);

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

            // @机器人 诊断：原始事件类型 / mentions 数量与 is_you / isAtBot 判定结果
            log.info("[收到群聊消息] 事件类型={}, mentions={}, isAtBot={}, 原始content={}",
                    event.getEventType(),
                    event.getMentions() == null ? 0 : event.getMentions().size(),
                    event.isAtBot(),
                    event.getContent() == null ? "" : event.getContent());

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

            // 消息落库（per-bot 实例库 qqbot_message，控制台消息监控数据源）
            // 机器人自消息（author.bot=true）已在发送侧由 MessageSender 落 OUT（方向=发送），
            // 回调不再重复记 IN，避免「机器人回复在监控里显示成收到」。
            String inType = QqBotRepository.msgTypeLabel(
                    event.getMessageType(), firstAttachmentContentType(event), firstAttachmentFilename(event));
            // 纯文本但内容像 Markdown（QQ 可能以 text 段送达 markdown 文本）→ 归为 markdown
            if ("text".equals(inType)) inType = outboundType(content);
            if (!isBotAuthor) {
                try {
                    qqBotRepository.insertMessage(appId, "group", groupOpenid, memberOpenid,
                            "IN", inType, content, msgId, null, null, data.toString(),
                            dev.xuanji.core.util.TimeUtils.nowEpochSeconds());
                } catch (Exception ex) {
                    log.debug("[消息落库] 失败: {}", ex.getMessage());
                }
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

            // 记录到控制台消息流水（自消息已由发送侧记 OUT，回调不再重复记 IN）
            if (!isBotAuthor) {
                eventRecorder.record(
                        "IN", inType, event.getAuthor().getUsername(),
                        groupOpenid, content, "msgId=" + msgId);

                // 记录到日志库（用真实 appId 而非 hashCode）
                dev.xuanji.core.storage.log.MessageLogger.groupMessage("IN",
                        appId, groupOpenid, memberOpenid,
                        inType, content, data.toString());
            }

            // 事件注解指令调度（传入 appId 让框架在消息链解析时按需下载媒体）
            var xjBot = botFactory.group(groupOpenid, msgId, appId);
                CommandRegistry.setContext(botKey, groupOpenid, msgId, memberOpenid,
                        sdkEvent(event, data, appId), xjBot, "qq");
            try {
                // 权限已在 Pipeline 的 WhitelistStage(20) 统一裁决，此处不再内联检查
                // 重置「命令命中」标记：LlmChatStage 据此判断命令是否未命中（LLM 闲聊兜底）
                commandRegistry.resetCommandHitFlag();
                String cmdResult = commandRegistry.executeGroupMessage(content);
                if (cmdResult != null) {
                    // OUT 落库由 MessageSender 统一完成（方向=OUT、类型按发送方式、raw=出站载荷）
                    messageSender.sendGroupText(groupOpenid, cmdResult, msgId);
                    dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT",
                            appId, groupOpenid, memberOpenid,
                            outboundType(cmdResult), cmdResult, "");
                    return;
                }
                // 命令未命中 → @OnMessage 全量监听器（非命令场景）
                commandRegistry.dispatchOnMessage(true);
            } finally {
                CommandRegistry.clearContext();
            }

            // 未匹配 → 提示帮助（OUT 落库由 MessageSender 统一完成）
            if (event.isAtBot()) {
                // LLM 兜底接管：该群已开启 LLM 聊天时，由 LlmChatStage 异步回复，不再发生硬帮助
                if (isLlmAvailable(botEvent)) {
                    return;
                }
                String helpText = "发送\"帮助\"查看可用命令";
                messageSender.sendGroupText(groupOpenid, helpText, msgId);
                dev.xuanji.core.storage.log.MessageLogger.groupMessage("OUT", appId, groupOpenid, memberOpenid, "text", helpText, "");
            }

        } catch (Exception e) {
            log.error("[群聊消息] 解析异常: robotId={}, error={}", robotId, e.getMessage(), e);
        } finally {
            handleSample.stop(handleTimer);
            dev.xuanji.core.metrics.TraceContext.exit();
        }
    }

    /** LLM 是否会对当前事件兜底接管（任一 LlmAvailability 实现返回 true）。 */
    private boolean isLlmAvailable(dev.xuanji.api.event.BotEvent botEvent) {
        for (dev.xuanji.api.llm.LlmAvailability a : llmAvailabilityList) {
            if (a.available(botEvent)) return true;
        }
        return false;
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

    /** 取首条附件的 content_type（图片/语音/视频细分的依据）。 */
    private static String firstAttachmentContentType(GroupMessageEvent e) {
        if (e.getAttachments() == null || e.getAttachments().isEmpty()) return null;
        return e.getAttachments().get(0).getContentType();
    }

    /** 取首条附件的文件名（按扩展名兜底识别媒体类型）。 */
    private static String firstAttachmentFilename(GroupMessageEvent e) {
        if (e.getAttachments() == null || e.getAttachments().isEmpty()) return null;
        return e.getAttachments().get(0).getFilename();
    }

    /** OUT 方向消息类型：sendGroupText 走 text 传输，但指令结果常是 Markdown，
     *  按内容粗略识别，避免机器人「发出的 Markdown 显示成 text」。 */
    private static String outboundType(String content) {
        if (content == null) return "text";
        String c = content.trim();
        if (c.startsWith("#") || c.contains("**") || c.contains("```")
                || c.contains("![") || (c.contains("[") && c.contains("]("))
                || c.startsWith("> ") || c.startsWith("- ") || c.startsWith("* ")) {
            return "markdown";
        }
        return "text";
    }

    /** 构建 SDK 事件（消息链直塞：QQ 报文经 QqMessageConverter 解析后直塞 chain/hasAttachments，媒体订阅可命中）。
     *  框架层按需下载：传入 appId，URL 形态的 media 在解析时自动下载落盘（开关在 xuanji_config/bot_setting）。 */
    private static dev.xuanji.sdk.event.GroupMessageEvent sdkEvent(GroupMessageEvent raw, ObjectNode data, String appId) {
        // 直接走结构化入口：data 本就是 ObjectNode，无需 toString() 再 parse 回来（省一次 JSON 往返）
        dev.xuanji.api.message.MessageChain chain =
                dev.xuanji.adapter.qqbot.converter.QqMessageConverter.fromQqData(data, appId);
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
