package XuanJi.llm.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 聊天守卫 —— 冷却 + 每日 token 限额的成本护栏。
 *
 * <p>冷却为进程内状态（同一 bot+群 两次回复最小间隔）；每日 token 限额持久化到
 * {@code xuanji_llm_usage}（按自然日 + bot 累计，重启不丢）。token 量以回复文本
 * 字符数估算（中文约 1 字 ≈ 1 token，英文略高），MVP 够用，精确统计后续接 usage 字段。
 */
@Slf4j
@Component
public class LlmChatGuard {

    private final JdbcTemplate jdbc;

    /** bot_key:group_id → 上次回复 epoch 秒 */
    private final ConcurrentHashMap<String, Long> lastReplyAt = new ConcurrentHashMap<>();
    /** 估算 token 与字符数比例（1 token ≈ 2 字符，中文场景偏保守） */
    private static final int CHARS_PER_TOKEN = 2;

    public LlmChatGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ──────────── 冷却 ────────────

    /** 冷却是否已过（只读，不消耗状态）。 */
    public boolean isCooledDown(String botKey, String groupId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        Long last = lastReplyAt.get(key(botKey, groupId));
        if (last == null) return false;
        return (System.currentTimeMillis() / 1000) - last < cooldownSeconds;
    }

    /** 记录一次回复时间（触发冷却）。 */
    public void markReplied(String botKey, String groupId) {
        lastReplyAt.put(key(botKey, groupId), System.currentTimeMillis() / 1000);
    }

    private static String key(String botKey, String groupId) {
        return botKey + ":" + groupId;
    }

    // ──────────── 每日 token 限额 ────────────

    /** 今日已用 token 数（含内存增量与库内持久值，bot 级汇总）。 */
    public long todayUsed(String botKey) {
        return dbUsed(botKey, null) + memoryTodayDelta(botKey).get();
    }

    /** 某群今日已用 token 数（群维度）。 */
    public long groupTodayUsed(String botKey, String groupId) {
        return dbUsed(botKey, groupId);
    }

    /** 是否仍在使用额度内（bot 级）。 */
    public boolean withinTokenBudget(String botKey, long dailyLimit) {
        return dailyLimit <= 0 || todayUsed(botKey) < dailyLimit;
    }

    /** 记录本次估算 token 用量（回复文本字符估算兜底，用于未知 usage 场景）。 */
    public void recordTokens(String botKey, String groupId, String replyText) {
        long est = Math.max(1, replyText.length() / CHARS_PER_TOKEN);
        record(botKey, groupId, est, 0, 0);
    }

    /** 记录本次真实 token 用量（来自 LLM 响应 usage）。未知（全 0）时跳过，避免污染统计。 */
    public void recordTokens(String botKey, String groupId, long promptTokens, long completionTokens) {
        if (promptTokens <= 0 && completionTokens <= 0) return;
        record(botKey, groupId, promptTokens + completionTokens, promptTokens, completionTokens);
    }

    private void record(String botKey, String groupId, long total, long prompt, long completion) {
        long day = dayNumber();
        try {
            // H2 的 MERGE...VALUES 不支持引用已有列做累加，退化为「查→插/更」两步
            Integer exists = jdbc.query(
                    "SELECT 1 FROM xuanji_llm_usage WHERE stat_day = ? AND bot_key = ? AND group_id IS NOT DISTINCT FROM ? AND user_id = ''",
                    rs -> rs.next() ? 1 : null, day, botKey, groupId);
            if (exists == null) {
                jdbc.update("""
                    INSERT INTO xuanji_llm_usage (stat_day, bot_key, group_id, user_id, used_tokens, prompt_tokens, completion_tokens)
                    VALUES (?, ?, ?, '', ?, ?, ?)
                    """, day, botKey, groupId, total, prompt, completion);
            } else {
                jdbc.update("""
                    UPDATE xuanji_llm_usage SET used_tokens = used_tokens + ?,
                           prompt_tokens = prompt_tokens + ?, completion_tokens = completion_tokens + ?
                    WHERE stat_day = ? AND bot_key = ? AND group_id IS NOT DISTINCT FROM ? AND user_id = ''
                    """, total, prompt, completion, day, botKey, groupId);
            }
        } catch (Exception e) {
            log.warn("[LLM] 用量记录失败: {}", e.getMessage());
        }
        memoryTodayDelta(botKey).addAndGet(total);
    }

    private AtomicLong memoryTodayDelta(String botKey) {
        return dailyDeltas.computeIfAbsent(key(String.valueOf(dayNumber()), botKey), k -> new AtomicLong());
    }

    private final ConcurrentHashMap<String, AtomicLong> dailyDeltas = new ConcurrentHashMap<>();

    /** 查询某维度今日用量（groupId 为 null 时聚合全部群）。 */
    private long dbUsed(String botKey, String groupId) {
        try {
            Long v;
            if (groupId == null) {
                v = jdbc.query("SELECT COALESCE(SUM(used_tokens),0) FROM xuanji_llm_usage WHERE stat_day = ? AND bot_key = ?",
                        rs -> rs.next() ? rs.getLong(1) : 0, dayNumber(), botKey);
            } else {
                v = jdbc.query("""
                    SELECT COALESCE(SUM(used_tokens),0) FROM xuanji_llm_usage
                    WHERE stat_day = ? AND bot_key = ? AND group_id IS NOT DISTINCT FROM ? AND user_id = ''
                    """, rs -> rs.next() ? rs.getLong(1) : 0, dayNumber(), botKey, groupId);
            }
            return v != null ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long dayNumber() {
        return LocalDate.now().atStartOfDay(ZoneOffset.UTC).toEpochSecond() / 86400;
    }

    /** 供外部（如 LlmQuotaService）按同口径计算自然日序号。 */
    public long dayNumberForQuery() {
        return dayNumber();
    }
}
