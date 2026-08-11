package XuanJi.llm.feedback;

import XuanJi.api.llm.LlmMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户反馈闭环（P2-F）—— 👍/👎 采集 → 偏好蒸馏 → 注入 system prompt。
 *
 * <p>采集：控制台「AI 对话」页每条 bot 回复带 👍/👎 按钮；群聊后续可扩展「点赞」消息识别。
 * 蒸馏：同一用户反馈累计 ≥ {@link #DISTILL_THRESHOLD} 条 → 用 LLM 提炼一段「用户偏好摘要」
 * （如"喜欢简短幽默，讨厌 emoji"），存入 {@code xuanji_llm_user_profile.preference_summary}，
 * 对话前并入画像 prompt，让 AI 按用户喜好说话。
 */
@Slf4j
@Component
public class FeedbackService {

    /** 反馈累计达到该数才蒸馏偏好摘要 */
    private static final int DISTILL_THRESHOLD = 20;
    /** 蒸馏最小间隔（小时），避免高频重复调用 LLM */
    private static final long DISTILL_MIN_INTERVAL_HOURS = 6;

    private final JdbcTemplate jdbc;
    private final XuanJi.llm.LlmService llmService;

    /** 异步偏好蒸馏（虚拟线程，不阻塞对话主流程） */
    private static final ExecutorService DISTILL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public FeedbackService(JdbcTemplate jdbc, XuanJi.llm.LlmService llmService) {
        this.jdbc = jdbc;
        this.llmService = llmService;
    }

    /**
     * 记录一条用户反馈。
     *
     * @param botKey    机器人
     * @param groupId   群（控制台对话可为 null/空）
     * @param userId    用户（控制台对话可为 null/空）
     * @param replyText 被评价的 AI 回复文本
     * @param score     1=👍 / -1=👎
     */
    public void record(String botKey, String groupId, String userId, String replyText, int score) {
        if (replyText == null || replyText.isBlank()) return;
        try {
            String text = replyText.length() > 500 ? replyText.substring(0, 500) : replyText;
            String hash = Integer.toHexString(text.hashCode());
            jdbc.update("""
                INSERT INTO xuanji_llm_feedback (bot_key, group_id, user_id, reply_hash, reply_text, score, created_at)
                VALUES (?,?,?,?,?,?, CURRENT_TIMESTAMP)
                """, botKey, groupId, userId, hash, text, score);
            log.info("[FEEDBACK] 收到反馈: bot={} user={} score={} len={}", botKey, userId, score, text.length());
            maybeDistill(botKey, groupId, userId);
        } catch (Exception e) {
            log.warn("[FEEDBACK] 记录失败: {}", e.getMessage());
        }
    }

    /** 反馈攒够阈值且距上次蒸馏超时 → 异步蒸馏偏好摘要。 */
    private void maybeDistill(String botKey, String groupId, String userId) {
        try {
            Integer cnt = jdbc.query("""
                SELECT COUNT(*) FROM xuanji_llm_feedback
                WHERE user_id = ? AND bot_key = ? AND score <> 0
                """, rs -> rs.next() ? rs.getInt(1) : 0, userId, botKey);
            if (cnt == null || cnt < DISTILL_THRESHOLD) return;
            // 距上次蒸馏超时才重新提炼（读 preference_summary 的 extract 时间）
            Long last = jdbc.query("""
                SELECT extract_at FROM xuanji_llm_user_profile
                WHERE bot_key = ? AND group_id = ? AND user_id = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, botKey, groupId == null ? "" : groupId, userId);
            if (last != null && last > 0
                    && (System.currentTimeMillis() / 1000 - last) < DISTILL_MIN_INTERVAL_HOURS * 3600) {
                return;
            }
            DISTILL_EXECUTOR.execute(() -> distill(botKey, groupId, userId));
        } catch (Exception e) {
            log.debug("[FEEDBACK] 蒸馏检查失败: {}", e.getMessage());
        }
    }

    /** 提炼偏好摘要：读取该用户全部反馈 → LLM 总结 → 存 preference_summary。 */
    private void distill(String botKey, String groupId, String userId) {
        try {
            List<Map<String, Object>> rows = jdbc.query("""
                SELECT reply_text, score FROM xuanji_llm_feedback
                WHERE user_id = ? AND bot_key = ? AND score <> 0
                ORDER BY created_at DESC LIMIT 50
                """, (rs, i) -> Map.of(
                        "text", String.valueOf(rs.getString("reply_text")),
                        "score", rs.getInt("score")), userId, botKey);
            if (rows.size() < DISTILL_THRESHOLD) return;
            StringBuilder sb = new StringBuilder("根据用户反馈提炼「回复偏好」（用户点👍的回复风格保留，点👎的避免）：\n");
            for (Map<String, Object> r : rows) {
                sb.append(r.get("score").equals(1) ? "[喜欢] " : "[不喜欢] ")
                  .append(r.get("text")).append("\n");
            }
            sb.append("\n输出一段 80 字以内的中文用户偏好摘要（如：喜欢简短幽默的回复，讨厌 emoji 和长篇大论）：");
            String summary = llmService.chat(List.of(LlmMessage.user(sb.toString())), null);
            if (summary == null || summary.isBlank()) return;
            if (summary.length() > 300) summary = summary.substring(0, 300);
            String gid = groupId == null ? "" : groupId;
            jdbc.update("""
                MERGE INTO xuanji_llm_user_profile (bot_key, group_id, user_id, preference_summary, extract_at, updated_at)
                KEY (bot_key, group_id, user_id) VALUES (?,?,?,?,?, CURRENT_TIMESTAMP)
                """, botKey, gid, userId, summary.trim(), System.currentTimeMillis() / 1000);
            log.info("[FEEDBACK] 偏好摘要已蒸馏: bot={} user={} → {}", botKey, userId, summary);
        } catch (Exception e) {
            log.warn("[FEEDBACK] 偏好蒸馏失败: {}", e.getMessage());
        }
    }

    /** 读取用户偏好摘要（供 buildProfilePrompt 并入）。 */
    public String preferenceSummary(String botKey, String groupId, String userId) {
        try {
            return jdbc.query("""
                SELECT preference_summary FROM xuanji_llm_user_profile
                WHERE bot_key = ? AND group_id = ? AND user_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null,
                    botKey, groupId == null ? "" : groupId, userId);
        } catch (Exception e) {
            return null;
        }
    }
}
