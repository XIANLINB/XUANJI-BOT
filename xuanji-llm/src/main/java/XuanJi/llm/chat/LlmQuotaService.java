package XuanJi.llm.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群级每日限额 —— 基于 {@code xuanji_llm_group_quota} 表。
 *
 * <p>daily_limit 语义：0 = 不限制；>0 = 该群每天最多消耗的估算 token 数。
 * 判断逻辑：群今日用量（{@link LlmChatGuard#groupTodayUsed}）达到限额后，
 * 该群当天不再触发 AI（LlmChatStage 拦截）。
 */
@Slf4j
@Component
public class LlmQuotaService {

    private final JdbcTemplate jdbc;
    private final LlmChatGuard guard;

    public LlmQuotaService(JdbcTemplate jdbc, LlmChatGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    /** 群当日限额；0 = 不限。 */
    public long dailyLimit(String botKey, String groupId) {
        try {
            Long v = jdbc.query("SELECT daily_limit FROM xuanji_llm_group_quota WHERE bot_key = ? AND group_id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, botKey, groupId);
            return v != null ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 群是否仍可触发 AI（限额 > 0 且未超；限额 0 视为不限）。 */
    public boolean allowGroup(String botKey, String groupId) {
        long limit = dailyLimit(botKey, groupId);
        if (limit <= 0) return true;
        return guard.groupTodayUsed(botKey, groupId) < limit;
    }

    /** 设置群限额（limit 0 = 解除限制）。 */
    public void setLimit(String botKey, String groupId, long limit) {
        jdbc.update("""
            MERGE INTO xuanji_llm_group_quota (bot_key, group_id, daily_limit, updated_at)
            KEY (bot_key, group_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, botKey, groupId, limit);
    }

    /** 某 bot 的全部群限额（含今日用量，供前端表格展示）。 */
    public List<Map<String, Object>> listQuotas(String botKey) {
        return jdbc.query("""
            SELECT q.group_id, q.daily_limit, q.updated_at,
                   COALESCE((SELECT SUM(u.used_tokens) FROM xuanji_llm_usage u
                             WHERE u.stat_day = ? AND u.bot_key = q.bot_key
                             AND u.group_id IS NOT DISTINCT FROM q.group_id AND u.user_id = ''), 0) AS today_used
            FROM xuanji_llm_group_quota q
            WHERE q.bot_key = ?
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("groupId", rs.getString("group_id"));
                m.put("dailyLimit", rs.getLong("daily_limit"));
                m.put("todayUsed", rs.getLong("today_used"));
                m.put("updatedAt", rs.getObject("updated_at") != null ? String.valueOf(rs.getObject("updated_at")) : null);
                return m;
            }, guard.dayNumberForQuery(), botKey);
    }

    /** 近 7 天各群用量趋势（前端折线图数据源）。 */
    public List<Map<String, Object>> groupTrend(String botKey, String groupId, int days) {
        long today = guard.dayNumberForQuery();
        long start = today - (days - 1);
        return jdbc.query("""
            SELECT stat_day, used_tokens FROM xuanji_llm_usage
            WHERE bot_key = ? AND group_id IS NOT DISTINCT FROM ? AND user_id = ''
              AND stat_day >= ? AND stat_day <= ?
            ORDER BY stat_day
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("day", rs.getLong("stat_day"));
                m.put("tokens", rs.getLong("used_tokens"));
                return m;
            }, botKey, groupId, start, today);
    }

    /** 今日各 bot 用量汇总（用量页总览卡）。 */
    public List<Map<String, Object>> botTodayUsage() {
        return jdbc.query("""
            SELECT bot_key, COALESCE(SUM(used_tokens),0) AS tokens
            FROM xuanji_llm_usage WHERE stat_day = ? GROUP BY bot_key
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("botKey", rs.getString("bot_key"));
                m.put("tokens", rs.getLong("tokens"));
                return m;
            }, guard.dayNumberForQuery());
    }

    /** 某 bot 今日总用量（跨群汇总）。 */
    public long botTodayTotal(String botKey) {
        try {
            Long v = jdbc.query("SELECT COALESCE(SUM(used_tokens),0) FROM xuanji_llm_usage WHERE stat_day = ? AND bot_key = ?",
                    rs -> rs.next() ? rs.getLong(1) : 0, guard.dayNumberForQuery(), botKey);
            return v != null ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 今日产生过用量的群（用于补充展示未设限额的群）。 */
    public List<Map<String, Object>> groupsWithUsageToday(String botKey) {
        return jdbc.query("""
            SELECT group_id, COALESCE(SUM(used_tokens),0) AS tokens
            FROM xuanji_llm_usage
            WHERE stat_day = ? AND bot_key = ? AND group_id <> '' AND user_id = ''
            GROUP BY group_id
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("groupId", rs.getString("group_id"));
                m.put("tokens", rs.getLong("tokens"));
                return m;
            }, guard.dayNumberForQuery(), botKey);
    }
}
