package XuanJi.llm.agent;

import XuanJi.api.action.ConversationSession;
import XuanJi.api.llm.LlmMessage;
import XuanJi.api.llm.LlmToolDefinition;
import XuanJi.core.command.CommandRegistry;
import XuanJi.core.command.ConversationSessionManager;
import XuanJi.llm.LlmService;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.persona.PersonaService;
import XuanJi.llm.profile.UserProfileService;
import XuanJi.llm.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 服务 —— 任务化多轮对话：跨消息保持会话历史（ConversationSession），
 * 每次用户消息续接上下文，内部由 {@link LlmService#chatWithTools} 驱动工具循环
 * （模型自主规划 → 调工具 → 回填 → 直到给出答案）。
 *
 * <p>闲聊自然触发：LlmChatStage 在处理群聊消息时调用 {@link #runTurn}。
 * 会话维度 bot+群+用户，TTL 过期自动 end（默认 5 分钟，连续追问期间持续刷新）。
 */
@Slf4j
@Service
public class AgentService {

    private static final String FLOW = "agent";
    /** 历史消息保留上限（超出的最老消息裁剪，控制上下文长度） */
    private static final int MAX_HISTORY = 24;

    /** P0-B 事实核查指令：涉及具体事实先检索，查不到明说"不确定"，不编造。 */
    private static final String FACT_CHECK_GUIDELINE = """
        【事实核查】回答涉及具体事实（时间/人名/数字/群成员/机器人配置/之前说过的话/约定）时：
        - 先调用 memory_search / knowledge_search 检索确认，不要凭记忆编造
        - 检索不到对应事实时，明确说「这个我不确定」，不要虚构细节
        - 用户刚纠正过的事实（见上方「纠正记录」）务必遵守，不得再犯""";

    private final LlmService llmService;
    private final ConversationSessionManager sessionManager;
    private final PersonaService personaService;
    private final UserProfileService profileService;
    private final MemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final XuanJi.llm.tool.ToolLearnService toolLearnService;
    private final LlmConfigStore configStore;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final tools.jackson.databind.ObjectMapper objectMapper =
            new tools.jackson.databind.ObjectMapper();

    public AgentService(LlmService llmService,
                        ConversationSessionManager sessionManager,
                        PersonaService personaService,
                        UserProfileService profileService,
                        MemoryService memoryService,
                        ToolRegistry toolRegistry,
                        XuanJi.llm.tool.ToolLearnService toolLearnService,
                        LlmConfigStore configStore,
                        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.llmService = llmService;
        this.sessionManager = sessionManager;
        this.personaService = personaService;
        this.profileService = profileService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.toolLearnService = toolLearnService;
        this.configStore = configStore;
        this.jdbc = jdbc;
    }

    /**
     * 处理一轮用户消息（Agent 模式），返回 AI 回复。
     *
     * @param executor 工具执行回调（调用方注入：含 confirm 确认逻辑 + MCP 桥接）
     */
    public String runTurn(String botKey, String groupId, String userId, String userText,
                          LlmService.ToolExecutor executor) {
        // ConversationSessionManager 依赖 CommandRegistry 上下文取会话键
        CommandRegistry.setContext(botKey, groupId, "", userId, null, null, "qqbot");
        try {
            LlmConfig cfg = configStore.get();
            List<LlmMessage> history = loadHistory(botKey, groupId, userId);

            // 组装：system(人格) + 画像 + 记忆 + 历史 + 本轮用户消息
            List<LlmMessage> messages = new ArrayList<>();
            String sys = personaService.buildSystemPrompt(personaService.resolve(botKey, groupId, userId));
            if (!sys.isEmpty()) messages.add(LlmMessage.system(sys));
            String drift = driftStrengthening(botKey);
            if (!drift.isEmpty()) messages.add(LlmMessage.system(drift));
            if (cfg.isProfileEnabled() && userId != null) {
                String profilePrompt = profileService.buildProfilePrompt(botKey, groupId, userId);
                if (!profilePrompt.isEmpty()) messages.add(LlmMessage.system(profilePrompt));
            }
            if (MemoryService.isRememberRequest(userText)) {
                messages.add(LlmMessage.system(
                        "如果用户要求记住某事，请在回复末尾输出一行 [MEMORY]key=关键名 value=要记住的内容；没有要记住的则不输出该行。"));
            } else {
                String memPrompt = memoryService.buildMemoryPrompt(botKey, groupId, userId, userText, 20);
                if (!memPrompt.isEmpty()) messages.add(LlmMessage.system(memPrompt));
            }
            // P0-B 事实核查指令：涉及具体事实先检索，查不到明说"不确定"，不编造
            messages.add(LlmMessage.system(FACT_CHECK_GUIDELINE));
            messages.addAll(history);
            messages.add(LlmMessage.user(userText));

            // 工具（含 MCP 桥接注册的动态工具）+ 经验库增强（历史错误 + fix_hint 拼进描述）
            List<LlmToolDefinition> toolDefs = toolLearnService.enhance(toolRegistry.definitions());
            if (!cfg.isIntentRouting()) {
                toolDefs = toolDefs.stream().filter(d -> !"run_command".equals(d.name())).toList();
            }

            String reply = llmService.chatWithTools(messages, toolDefs, executor);

            // 解析 [MEMORY] 行并持久化；模型未输出记忆行但用户"记住X"时本地兜底，返回纯回复
            reply = memoryService.persistFromReplyAndBackfill(reply == null ? "" : reply,
                    botKey, groupId, userId, userText);

            // P0-C 纠错闭环：用户纠正上一轮回复 → 存反事实记忆（confidence 0.9），下次不再犯
            try {
                Correction c = detectCorrection(userText);
                if (c != null && c.right() != null) {
                    String wrong = lastReplyOf(history);
                    memoryService.saveCorrection(botKey, groupId, userId,
                            wrong.isEmpty() ? c.topic() : wrong, c.right());
                }
            } catch (Exception e) {
                log.debug("[AGENT] 纠错检测异常: {}", e.getMessage());
            }

            // P2-E 人设自评（仅 roleplay 开启 + 配置了锚点）：回复后轻量校验是否偏离人设
            try {
                XuanJi.llm.persona.LlmPersona persona = personaService.resolve(botKey, groupId, userId);
                if (persona.isRoleplayMode()
                        && persona.getAnchors() != null && !persona.getAnchors().isBlank()) {
                    selfCheckPersona(botKey, groupId, userId, persona, reply);
                }
            } catch (Exception e) {
                log.debug("[AGENT] 人设自评异常: {}", e.getMessage());
            }

            // 更新会话历史（排除 system；本轮 user+assistant 追加）
            List<LlmMessage> newHistory = new ArrayList<>(history);
            newHistory.add(LlmMessage.user(userText));
            newHistory.add(LlmMessage.assistant(reply));
            newHistory = compressIfNeeded(newHistory);
            saveHistory(botKey, groupId, userId, newHistory);
            return reply;
        } finally {
            CommandRegistry.clearContext();
        }
    }

    /** 取走本线程最近一次 Agent 对话的真实 token 用量（{prompt, completion}），供用量统计精确记录。 */
    public long[] lastUsage() {
        return llmService.takeLastUsage();
    }

    /**
     * 历史超阈值时压缩：把最老的若干条交给 LLM 生成「会话摘要」，以一条摘要消息替换，
     * 既控制上下文长度，又保留更早的关键信息（记忆更长）。
     */
    private List<LlmMessage> compressIfNeeded(List<LlmMessage> history) {
        if (history.size() <= MAX_HISTORY) {
            return history;
        }
        int drop = history.size() - MAX_HISTORY + 2; // 压缩最老部分，留 2 条余量给摘要
        drop = Math.max(1, Math.min(drop, history.size() - 1));
        List<LlmMessage> head = new ArrayList<>(history.subList(0, drop));
        List<LlmMessage> tail = new ArrayList<>(history.subList(drop, history.size()));
        String summary = summarize(head);
        if (summary != null && !summary.isBlank()) {
            tail.add(0, LlmMessage.system("[会话摘要] " + summary));
            log.info("[AGENT] 会话压缩: {} 条 → 摘要", head.size());
        } else {
            // 摘要失败：退化为直接裁掉最老的 2 条
            while (tail.size() > MAX_HISTORY) {
                tail.remove(0);
            }
        }
        return tail;
    }

    /** 用 LLM 把早期对话压缩为一段摘要（失败返回 null，不阻塞主流程）。 */
    private String summarize(List<LlmMessage> head) {
        try {
            StringBuilder sb = new StringBuilder(
                    "请把以下对话压缩成一段简洁的中文会话摘要（保留：关键事实、约定、用户个人信息与喜好、进行中的任务；省略寒暄和无关细节。100 字以内）：\n\n");
            for (LlmMessage m : head) {
                sb.append(m.role()).append(": ").append(m.content()).append("\n");
            }
            String reply = llmService.chat(List.of(LlmMessage.user(sb.toString())), null);
            return reply == null || reply.isBlank() ? null : reply.trim();
        } catch (Exception e) {
            log.warn("[AGENT] 会话摘要失败: {}", e.getMessage());
            return null;
        }
    }

    /** 会话键：botKey|scope|userId|flow（与 ConversationSessionManager.key 同口径）。 */
    private String sessionKey(String botKey, String groupId, String userId) {
        String scope = (groupId != null && !groupId.isBlank()) ? groupId : userId;
        return (botKey == null ? "" : botKey) + "|" + (scope == null ? "" : scope)
                + "|" + (userId == null ? "" : userId) + "|" + FLOW;
    }

    /**
     * 加载会话历史：内存优先；内存空（如框架重启）从 DB 恢复，恢复后回填内存。
     */
    @SuppressWarnings("unchecked")
    private List<LlmMessage> loadHistory(String botKey, String groupId, String userId) {
        List<LlmMessage> history = sessionManager.get(FLOW, List.class);
        if (history != null) {
            return history;
        }
        try {
            String json = jdbc.query("""
                SELECT state_json FROM xuanji_llm_agent_session WHERE session_key = ?
                """, rs -> rs.next() ? rs.getString("state_json") : null,
                    sessionKey(botKey, groupId, userId));
            if (json != null && !json.isBlank()) {
                List<LlmMessage> restored = objectMapper.readValue(json,
                        new tools.jackson.core.type.TypeReference<List<LlmMessage>>() {});
                if (restored != null && !restored.isEmpty()) {
                    sessionManager.update(FLOW, restored);
                    log.info("[AGENT] 会话已从 DB 恢复: bot={}, group={}, msgs={}", botKey, groupId, restored.size());
                    return restored;
                }
            }
        } catch (Exception e) {
            log.warn("[AGENT] 会话恢复失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /** 保存会话历史：内存 + DB 双层持久化（重启不丢）。 */
    private void saveHistory(String botKey, String groupId, String userId, List<LlmMessage> history) {
        sessionManager.update(FLOW, history);
        try {
            String json = objectMapper.writeValueAsString(history);
            jdbc.update("""
                MERGE INTO xuanji_llm_agent_session (session_key, flow, state_json, updated_at)
                KEY (session_key) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, sessionKey(botKey, groupId, userId), FLOW, json);
        } catch (Exception e) {
            log.warn("[AGENT] 会话持久化失败: {}", e.getMessage());
        }
    }

    /** 会话剩余有效秒数（供前端/日志展示）。 */
    public long ttlSeconds(String botKey, String groupId, String userId) {
        CommandRegistry.setContext(botKey, groupId, "", userId, null, null, "qqbot");
        try {
            return sessionManager.ttlSeconds(FLOW);
        } finally {
            CommandRegistry.clearContext();
        }
    }

    // ──────────── P0-C 纠错检测 ────────────

    /** 纠正检测结果：topic=被纠正的主题，right=用户给出的正确说法。 */
    public record Correction(String topic, String right) {}

    /** 纠正意图正则：匹配"不对/错了/不是X是Y/我说的是Y/记住Y"等句式。 */
    private static final java.util.regex.Pattern CORRECTION_INTENT = java.util.regex.Pattern.compile(
            "(?s)(不是|不对|错了|说错了|记错了|我说的是|我讲的是|应该是|其实是|正确的是|记住|以后记住|别(再说|再讲|提)|不要再)");

    /** 提取纠正内容（用户给出的正确说法）；无法提取返回 null。 */
    private static final java.util.regex.Pattern CORRECTION_RIGHT = java.util.regex.Pattern.compile(
            "(?s)(?:不是|不对|错了|说错了|记错了|我说的是|我讲的是|应该是|其实是|正确的是|记住|以后记住)[：:]?\\s*([^，。！？\\n]{2,48})");

    /**
     * 检测用户消息是否为对上一轮机器人回复的纠正。
     * <p>保守策略：先命中纠正意图词，再尝试提取"正确说法"；两者都满足才算纠正。
     * 无法提取正确说法时返回 {@code topic=null} 的条目（调用方据 reply 兜底 wrong）。
     */
    static Correction detectCorrection(String userText) {
        if (userText == null) return null;
        String t = userText.trim();
        if (t.isEmpty() || t.length() > 80) return null; // 超长消息大概率是闲聊/问句，不判纠正
        if (!CORRECTION_INTENT.matcher(t).find()) return null;
        java.util.regex.Matcher m = CORRECTION_RIGHT.matcher(t);
        String right = m.find() ? m.group(1).trim() : null;
        // 提取主题：取消息里第一个 2~10 字实词串
        String topic = extractTopic(t);
        return new Correction(topic, right);
    }

    private static String extractTopic(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,10}").matcher(text);
        java.util.List<String> segs = new ArrayList<>();
        while (m.find() && segs.size() < 3) segs.add(m.group());
        if (segs.isEmpty()) return text.length() > 16 ? text.substring(0, 16) : text;
        // 跳过纯纠正词片段，取有信息量的片段
        for (String s : segs) {
            if (!s.contains("不是") && !s.contains("不对") && !s.contains("错了")
                    && !s.contains("记住") && !s.contains("我说") && !s.contains("以后")) {
                return s;
            }
        }
        return segs.get(segs.size() - 1);
    }

    /** 取历史里最近一条 assistant 回复（被纠正的错误说法），无则空串。 */
    private static String lastReplyOf(List<LlmMessage> history) {
        if (history == null) return "";
        for (int i = history.size() - 1; i >= 0; i--) {
            LlmMessage m = history.get(i);
            if (m.role().equals("assistant") && m.content() != null && !m.content().isBlank()) {
                String c = m.content();
                return c.length() > 60 ? c.substring(0, 60) : c;
            }
        }
        return "";
    }

    // ──────────── P2-E 人设自评 ────────────

    /** 自评阈值：当天偏离超过该数 → 下一轮注入强化提示。 */
    private static final int DRIFT_THRESHOLD = 5;

    /** 轻量自评：用同一模型判断回复是否符合角色锚点；fail 记入偏离日志。 */
    private void selfCheckPersona(String botKey, String groupId, String userId,
                                  XuanJi.llm.persona.LlmPersona persona, String reply) {
        if (reply == null || reply.isBlank()) return;
        String anchors = persona.getAnchors().trim();
        String sample = reply.length() > 300 ? reply.substring(0, 300) : reply;
        String prompt = "你是角色人设质量评审。角色锚点：\n" + anchors
                + "\n\nAI 的回复：\n" + sample
                + "\n\n判断该回复是否符合角色锚点（语气/立场/禁忌）。只输出一行：pass 或 fail：原因（20字内）";
        String verdict;
        try {
            verdict = llmService.chat(List.of(XuanJi.api.llm.LlmMessage.user(prompt)), null);
        } catch (Exception e) {
            return;
        }
        if (verdict == null || verdict.isBlank()) return;
        if (verdict.trim().toLowerCase().startsWith("fail")) {
            String reason = verdict.replaceFirst("(?i)^fail\\s*[:：]?", "").trim();
            if (reason.length() > 200) reason = reason.substring(0, 200);
            try {
                jdbc.update("""
                    INSERT INTO xuanji_llm_persona_drift (bot_key, group_id, user_id, persona_scope, reply, reason, created_at)
                    VALUES (?,?,?,?,?,?, CURRENT_TIMESTAMP)
                    """, botKey, groupId, userId, persona.getScope(), sample, reason);
            } catch (Exception e) {
                log.debug("[AGENT] 偏离日志写入失败: {}", e.getMessage());
            }
            log.info("[AGENT] 人设偏离: scope={} reason={}", persona.getScope(), reason);
        }
    }

    /** 高频偏离强化：当天偏离超过阈值返回一段强化提示（否则空串）。 */
    String driftStrengthening(String botKey) {
        try {
            Integer cnt = jdbc.query("""
                SELECT COUNT(*) FROM xuanji_llm_persona_drift
                WHERE bot_key = ? AND created_at >= DATEADD('DAY', -1, CURRENT_TIMESTAMP)
                """, rs -> rs.next() ? rs.getInt(1) : 0, botKey);
            if (cnt != null && cnt >= DRIFT_THRESHOLD) {
                return "【人设保持】你近期多次偏离人设设定，请严格遵守上方「人设锚点」，语气、立场、禁忌都不得违背。";
            }
        } catch (Exception e) {
            log.debug("[AGENT] 偏离统计失败: {}", e.getMessage());
        }
        return "";
    }
}
