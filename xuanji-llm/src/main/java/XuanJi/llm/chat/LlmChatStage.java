package XuanJi.llm.chat;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.llm.LlmReplySink;
import XuanJi.api.pipeline.PipelineStage;
import XuanJi.core.command.CommandRegistry;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM 群聊回复阶段 —— order=85（DispatchStage 之后，命令已处理完）。
 *
 * <p>触发条件（全满足才闲聊，安全优先）：
 * <ol>
 *   <li>群聊消息且非空文本</li>
 *   <li>全局 {@code enabled} 开启（AI 设置 → 聊天总开关）</li>
 *   <li>群在启用白名单（空 = 全开）</li>
 *   <li>配置要求 @ 时消息必须 @ 机器人</li>
 *   <li>冷却已过（同 bot+群 两次回复最小间隔）</li>
 *   <li>今日 token 未超限额</li>
 *   <li>命令未命中（CommandRegistry 标记）</li>
 *   <li>存在可用的回复发送器（LlmReplySink，如 qqbot 平台）</li>
 * </ol>
 *
 * <p>LLM 调用在虚拟线程池异步执行，不阻塞消息 Pipeline；
 * 回复经 {@link LlmReplySink} 发回事件群，超长自动分段（QQ 群消息长度限制）。
 */
@Slf4j
@Component
public class LlmChatStage implements PipelineStage {

    /** 异步 LLM 调用 + 发送（JDK 虚拟线程，轻量不占平台线程池）。 */
    private static final ExecutorService REPLY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** 单条 QQ 群消息安全长度上限（保守取值，超长自动分段）。 */
    private static final int MAX_SEGMENT = 1500;

    private final LlmConfigStore configStore;
    private final LlmChatGuard guard;
    private final LlmQuotaService quotaService;
    private final CommandRegistry commandRegistry;
    private final List<LlmReplySink> replySinks;
    private final XuanJi.llm.agent.AgentService agentService;
    private final XuanJi.llm.tool.ToolRegistry toolRegistry;
    private final XuanJi.llm.tool.ToolConfirmService confirmService;

    public LlmChatStage(LlmConfigStore configStore,
                        LlmChatGuard guard,
                        LlmQuotaService quotaService,
                        CommandRegistry commandRegistry,
                        List<LlmReplySink> replySinks,
                        XuanJi.llm.agent.AgentService agentService,
                        XuanJi.llm.tool.ToolRegistry toolRegistry,
                        XuanJi.llm.tool.ToolConfirmService confirmService) {
        this.configStore = configStore;
        this.guard = guard;
        this.quotaService = quotaService;
        this.commandRegistry = commandRegistry;
        this.replySinks = replySinks;
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
        this.confirmService = confirmService;
    }

    @Override public String name() { return "llm-chat"; }
    @Override public int order() { return 85; }

    @Override
    public Result handle(XuanJiEvent event, PipelineStage.PipelineChain chain) {
        if (event.message() == null) {
            return Result.CONTINUE;
        }
        boolean isGroup = event.isGroupEvent();
        String groupId = isGroup ? event.group().groupId() : null;
        String botKey = event.bot() != null ? event.bot().selfId() : "";
        // 机器人自消息（author.bot=true，其他机器人/本机器人发的）：不触发 AI，
        // 防止多机器人互相对话死循环（平台 handler 已按配置忽略处理，此处兜底 AI 不再接话）
        if (event.platformData() != null
                && event.platformData().path("author").path("bot").asBoolean(false)) {
            log.info("[LLM] 跳过AI: 机器人消息不触发AI bot={}", botKey);
            return Result.CONTINUE;
        }
        // 命令已命中（命令 handler 已执行并可能回复）→ 不闲聊
        if (commandRegistry.isCommandHitInCurrentEvent()) {
            log.info("[LLM] 跳过AI: 命令已命中 bot={}", botKey);
            return Result.CONTINUE;
        }
        LlmConfig cfg = configStore.get();
        String userId = event.sender() != null ? event.sender().id() : null;

        // ═══════════ 场景判断：群聊 / 单聊(C2C) ═══════════
        final String dimKey;   // 冷却/用量维度（群=groupId，单聊=userId）
        if (isGroup) {
            if (!cfg.isEnabled()) {
                log.info("[LLM] 跳过群聊AI: 聊天总开关未开启 group={}", groupId);
                return Result.CONTINUE;
            }
            if (!LlmAvailabilityImpl.groupAllowed(cfg, groupId)) {
                log.info("[LLM] 跳过群聊AI: 群不在白名单 group={}", groupId);
                return Result.CONTINUE;
            }
            boolean atBot = LlmMentionUtil.isAtBot(event);
            if (cfg.isMentionRequired() && !atBot) {
                log.info("[LLM] 跳过群聊AI: 未@机器人(mentionRequired=true) group={} atBot={}", groupId, atBot);
                return Result.CONTINUE;
            }
            if (!guard.withinTokenBudget(botKey, cfg.getDailyTokenLimit())) {
                log.info("[LLM] 跳过群聊AI: 今日token超限(bot级) group={}", groupId);
                return Result.CONTINUE;
            }
            if (!quotaService.allowGroup(botKey, groupId)) {
                log.info("[LLM] 跳过群聊AI: 群token限额已用尽 group={}", groupId);
                return Result.CONTINUE;
            }
            dimKey = groupId;
        } else {
            // C2C 单聊
            if (userId == null || userId.isBlank()) {
                log.info("[LLM] 跳过单聊AI: 无发送者 user 信息");
                return Result.CONTINUE;
            }
            if (!cfg.isC2cEnabled()) {
                log.info("[LLM] 跳过单聊AI: 单聊总开关未开启 user={}", userId);
                return Result.CONTINUE;
            }
            if (!LlmAvailabilityImpl.c2cAllowed(cfg, userId)) {
                log.info("[LLM] 跳过单聊AI: 用户不在白名单 user={}", userId);
                return Result.CONTINUE;
            }
            if (!guard.withinTokenBudget(botKey, cfg.getC2cDailyTokenLimit())) {
                log.info("[LLM] 跳过单聊AI: 今日token超限 user={}", userId);
                return Result.CONTINUE;
            }
            dimKey = userId;
        }
        if (replySinks.isEmpty()) {
            log.info("[LLM] 跳过AI: 无回复发送器(LlmReplySink) bot={}", botKey);
            return Result.CONTINUE;
        }

        String text = event.message().plainText().trim();
        // 图片注入：消息带图片时把引用追加进上下文，模型才知道要调 image_understand 工具
        List<String> imageRefs = event.message().medias().stream()
                .filter(m -> m.mediaType() == XuanJi.api.annotation.MediaType.IMAGE)
                .map(XuanJi.api.message.XuanJiMessageElement.Media::rawRef)
                .toList();
        StringBuilder mediaSb = new StringBuilder(text);
        for (String ref : imageRefs) {
            mediaSb.append("\n\n[用户发来一张图片，引用: ").append(ref)
                   .append("。如果你需要查看/描述这张图，请调用 image_understand 工具并传入该引用地址。]");
        }
        final String textWithMedia = mediaSb.toString().trim();
        // 纯图/纯媒体消息（无文本）也应触发 AI：图片引用已拼入 textWithMedia
        if (text.isEmpty() && imageRefs.isEmpty()) {
            log.info("[LLM] 跳过AI: 消息文本为空且无媒体 bot={}", botKey);
            return Result.CONTINUE;
        }

        // 冷却占用（原子）：在真正决定回复的线程内一次性「检查+标记」，杜绝冷却窗口内并发消息
        // 重复触发 LLM（原「Pipeline 线程检查冷却 → REPLY 线程 markReplied」存在竞态）。
        int cooldown = isGroup ? cfg.getCooldownSeconds() : cfg.getC2cCooldownSeconds();
        if (!guard.tryGrantReply(botKey, dimKey, cooldown)) {
            log.info("[LLM] 跳过AI: 冷却中 bot={}, dim={}", botKey, dimKey);
            return Result.CONTINUE;
        }

        // 工具确认回复优先：用户回复确认词且有待确认工具 → 直接执行并回复（不调 LLM）
        if (userId != null && XuanJi.llm.tool.ToolConfirmService.isConfirmText(text)
                && confirmService.hasPending(botKey, dimKey, userId)) {
            log.info("[LLM] 用户确认执行工具: bot={}, dim={}, pending={}",
                    botKey, dimKey, confirmService.describe(botKey, dimKey, userId));
            REPLY_EXECUTOR.execute(() -> {
                try {
                    String result = confirmService.tryConfirm(botKey, dimKey, userId);
                    if (result != null) {
                        guard.recordTokens(botKey, dimKey, result);
                        for (LlmReplySink sink : replySinks) {
                            for (String seg : splitText(result)) {
                                sink.reply(event, seg);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[LLM] 确认工具执行失败: {}", e.getMessage());
                }
            });
            return Result.CONTINUE;
        }

        // 异步调 LLM 并发送（保存 event 快照，跨线程安全：XuanJiEvent 为不可变 record）
        log.info("[FLOW] 🤖 触发AI回复 {} bot={}, dim={}, text={}",
                isGroup ? "群聊" : "单聊", botKey, dimKey, text);
        REPLY_EXECUTOR.execute(() -> {
            try {
                XuanJi.llm.tool.LlmToolContext ctx =
                        new XuanJi.llm.tool.LlmToolContext(botKey, groupId, userId, textWithMedia, event);
                // Agent 模式：组装人格/画像/记忆 + 会话历史 + 工具循环（含 MCP 桥接）
                String reply = agentService.runTurn(botKey, groupId, userId, textWithMedia, (toolName, argsJson) -> {
                    // 工具确认策略：全局关闭「需确认」→ 危险工具也直接执行（自由模式）
                    if (toolRegistry.requiresConfirm(toolName) && configStore.get().isToolConfirmRequired()) {
                        return confirmService.request(botKey, groupId, userId, toolName, argsJson, ctx);
                    }
                    return toolRegistry.execute(toolName, argsJson, ctx);
                });
                if (reply == null || reply.isBlank()) {
                    log.debug("[LLM] 回复为空，跳过: bot={}", botKey);
                    return;
                }
                // 用量统计：优先真实 usage（LLM 响应 usage.prompt/completion），未知则回退字符估算
                long[] u = agentService.lastUsage();
                if (u != null && (u[0] > 0 || u[1] > 0)) {
                    guard.recordTokens(botKey, dimKey, u[0], u[1]);
                } else {
                    guard.recordTokens(botKey, dimKey, reply);
                }
                for (LlmReplySink sink : replySinks) {
                    for (String seg : splitText(reply)) {
                        sink.reply(event, seg);
                    }
                }
            } catch (Exception e) {
                log.warn("[LLM] 回复失败: bot={}, err={}", botKey, e.getMessage());
            }
        });
        return Result.CONTINUE;
    }

    /** 超长回复分段：优先在换行处切，其次按上限硬切。 */
    static List<String> splitText(String text) {
        if (text.length() <= MAX_SEGMENT) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_SEGMENT, text.length());
            int cut = end;
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start + MAX_SEGMENT / 2) cut = nl + 1;
            }
            String seg = text.substring(start, cut).trim();
            if (!seg.isEmpty()) parts.add(seg);
            start = cut;
        }
        return parts;
    }
}
