package XuanJi.llm.audit;

import XuanJi.api.llm.LlmMessage;
import XuanJi.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 内容审核 —— 消息过 LLM 判断是否违规，违规消息拦截不响应。
 *
 * <p>由 {@code LlmAuditStage}（order=41）在消息 Pipeline 中调用；仅在配置
 * {@code aiAudit} 开启时生效。审核结果写 {@code xuanji_llm_audit_log} 供前端查看。
 */
@Slf4j
@Service
public class LlmAuditService {

    private final LlmService llmService;
    private final JdbcTemplate jdbc;

    public LlmAuditService(LlmService llmService, JdbcTemplate jdbc) {
        this.llmService = llmService;
        this.jdbc = jdbc;
    }

    private static final String AUDIT_PROMPT = """
        你是群聊内容审核员。判断以下用户消息是否违规，违规情形包括：
        辱骂攻击、涉黄、涉政敏感、广告刷屏、人身威胁、诈骗信息。
        只输出 JSON：{"pass": true 或 false, "reason": "简短原因"}
        不要输出任何其他内容。

        消息：%s
        """;

    /** 审核消息文本。返回 true=正常放行，false=违规拦截。 */
    public boolean check(String botKey, String groupId, String userId, String text) {
        if (text == null || text.isBlank()) return true;
        try {
            String reply = llmService.chat(
                    List.of(LlmMessage.system("你是内容审核员。"),
                            LlmMessage.user(AUDIT_PROMPT.formatted(text))),
                    null);
            boolean pass = parsePass(reply);
            log.info("[AUDIT] 审核结果: pass={}, text={}", pass, shorten(text));
            logAudit(botKey, groupId, userId, text, pass ? "PASS" : "BLOCK", shorten(reply));
            return pass;
        } catch (Exception e) {
            // 审核服务异常时放行（不阻断正常聊天），记录后继续
            log.warn("[AUDIT] 审核异常放行: {}", e.getMessage());
            return true;
        }
    }

    private static boolean parsePass(String reply) {
        if (reply == null) return true;
        // 优先找 "pass": true/false
        String r = reply.toLowerCase();
        if (r.contains("\"pass\":true") || r.contains("\"pass\": true") || r.contains("pass\":true")) return true;
        if (r.contains("\"pass\":false") || r.contains("\"pass\": false") || r.contains("pass\":false")) return false;
        // 兜底：包含 false 视为违规
        return !r.contains("false");
    }

    /** 拦截记录列表（前端 AI 审核页）。 */
    public List<Map<String, Object>> logs(int limit) {
        return jdbc.query("""
            SELECT id, bot_key, group_id, user_id, text, action, reason, created_at
            FROM xuanji_llm_audit_log ORDER BY id DESC LIMIT ?
            """, (rs, i) -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("userId", rs.getString("user_id"));
                m.put("text", rs.getString("text"));
                m.put("action", rs.getString("action"));
                m.put("reason", rs.getString("reason"));
                m.put("createdAt", rs.getObject("created_at") != null ? String.valueOf(rs.getObject("created_at")) : null);
                return m;
            }, Math.min(Math.max(limit, 1), 200));
    }

    private void logAudit(String botKey, String groupId, String userId, String text, String action, String reason) {
        try {
            jdbc.update("INSERT INTO xuanji_llm_audit_log (bot_key, group_id, user_id, text, action, reason, created_at) VALUES (?,?,?,?,?,?, CURRENT_TIMESTAMP)",
                    botKey, groupId, userId, text, action, reason);
        } catch (Exception e) {
            log.warn("[AUDIT] 记录失败: {}", e.getMessage());
        }
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
