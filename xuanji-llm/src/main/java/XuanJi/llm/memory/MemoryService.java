package XuanJi.llm.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长期记忆 —— 基于 {@code xuanji_llm_memory} 表，按 bot/群/用户维度独立存储。
 *
 * <p>本期实现「显式记住」：用户在对话中说"记住xxx"，框架引导 LLM 在回复末尾
 * 输出 {@code [MEMORY]key=xxx value=xxx}，本服务解析落库；后续对话前检索该
 * 维度记忆合并进 system 提示词（类似 SillyTavern 世界书的关键词注入）。
 */
@Slf4j
@Component
public class MemoryService {

    private static final Pattern MEMORY_LINE = Pattern.compile("\\[MEMORY\\]\\s*key=([^\\n]+?)\\s+value=([\\s\\S]+?)(?=\\n\\[MEMORY\\]|$)");

    private static final String TYPE_DETAIL = "DETAIL";
    private static final String TYPE_SUMMARY = "SUMMARY";
    /** 纠正记录类型：mem_value=正确说法，negative=被纠正的错误说法 */
    public static final String TYPE_CORRECTION = "CORRECTION";
    /** 摘要行的固定 key */
    public static final String SUMMARY_KEY = "__summary__";

    private final JdbcTemplate jdbc;

    public MemoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 保存详情记忆（UPSERT by bot/group/user/key）。expireSeconds<=0 表示永久。 */
    public void saveDetail(String botKey, String groupId, String userId, String key, String value, long expireSeconds) {
        save(botKey, groupId, userId, key, value, expireSeconds, TYPE_DETAIL);
    }

    /** 保存该维度的 LLM 压缩摘要（固定 key，覆盖更新）。 */
    public void saveSummary(String botKey, String groupId, String userId, String summaryText) {
        save(botKey, groupId, userId, SUMMARY_KEY, summaryText, 0, TYPE_SUMMARY);
    }

    /** 保存记忆（UPSERT by bot/group/user/key/mem_type）。expireSeconds<=0 表示永久。 */
    public void save(String botKey, String groupId, String userId, String key, String value, long expireSeconds, String memType) {
        saveWithConfidence(botKey, groupId, userId, key, value, expireSeconds, memType, 0.5, null);
    }

    /**
     * 保存纠正记录：用户明确纠正过「wrong」，正确说法是「right」。
     * <p>confidence 直接 0.9（用户明确纠正 = 高可信）；下次 prompt 以「纠正记录」形式拼入，
     * 模型提到该主题时不会再犯同一个错。
     */
    public void saveCorrection(String botKey, String groupId, String userId, String wrong, String right) {
        if (right == null || right.isBlank()) return;
        String key = wrong == null || wrong.isBlank() ? "（无主题）" : wrong.trim();
        if (key.length() > 32) key = key.substring(0, 32);
        saveWithConfidence(botKey, groupId, userId, key, right.trim(), 0, TYPE_CORRECTION, 0.9, wrong == null ? "" : wrong.trim());
        log.info("[MEMORY] 纠错已记录: bot={} group={} user={} wrong={} right={}", botKey, groupId, userId, wrong, right);
    }

    /** 完整保存入口（confidence / negative 显式）。 */
    public void saveWithConfidence(String botKey, String groupId, String userId, String key, String value,
                                   long expireSeconds, String memType, double confidence, String negative) {
        long expireAt = expireSeconds > 0 ? System.currentTimeMillis() / 1000 + expireSeconds : 0;
        String type = memType == null || memType.isBlank() ? TYPE_DETAIL : memType;
        double conf = Math.max(0.0, Math.min(1.0, confidence));
        try {
            // H2 的 MERGE KEY 对 NULL 不匹配已有行，退化为「查→插/更」两步
            Integer exists = jdbc.query("""
                SELECT 1 FROM xuanji_llm_memory
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?) AND mem_key = ?
                """, rs -> rs.next() ? 1 : null, botKey, groupId, userId, key);
            if (exists == null) {
                jdbc.update("""
                    INSERT INTO xuanji_llm_memory (bot_key, group_id, user_id, mem_key, mem_value, mem_type, updated_at, expire_at, confidence, negative)
                    VALUES (?,?,?,?,?,?, CURRENT_TIMESTAMP, ?, ?, ?)
                    """, botKey, groupId, userId, key, value, type, expireAt, conf, negative);
            } else {
                jdbc.update("""
                    UPDATE xuanji_llm_memory SET mem_value = ?, mem_type = ?, updated_at = CURRENT_TIMESTAMP, expire_at = ?, confidence = ?, negative = ?
                    WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?) AND mem_key = ?
                    """, value, type, expireAt, conf, negative, botKey, groupId, userId, key);
            }
        } catch (Exception e) {
            log.warn("[MEMORY] 保存失败: {}", e.getMessage());
        }
    }

    /** 记忆被模型引用时命中计数 +1（用于可信度统计）。 */
    public void recordHit(String botKey, String groupId, String userId, String key) {
        try {
            jdbc.update("""
                UPDATE xuanji_llm_memory SET hit_count = hit_count + 1
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?) AND mem_key = ?
                """, botKey, groupId, userId, key);
        } catch (Exception e) {
            log.warn("[MEMORY] 命中计数失败: {}", e.getMessage());
        }
    }

    /** 某维度记忆列表（含过期标记）。 */
    public List<Map<String, Object>> list(String botKey, String groupId, String userId) {
        return jdbc.query("""
            SELECT id, bot_key, group_id, user_id, mem_key, mem_value, mem_type, updated_at, expire_at
            FROM xuanji_llm_memory
            WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
            ORDER BY updated_at DESC
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("userId", rs.getString("user_id"));
                m.put("key", rs.getString("mem_key"));
                m.put("value", rs.getString("mem_value"));
                m.put("type", rs.getString("mem_type"));
                m.put("updatedAt", rs.getObject("updated_at") != null ? String.valueOf(rs.getObject("updated_at")) : null);
                long exp = rs.getLong("expire_at");
                m.put("expireAt", exp);
                m.put("expired", exp > 0 && exp < System.currentTimeMillis() / 1000);
                return m;
            }, botKey, groupId, userId);
    }

    /** 删除记忆。 */
    public void delete(Long id) {
        jdbc.update("DELETE FROM xuanji_llm_memory WHERE id = ?", id);
    }

    /** 检索相关记忆并拼成注入提示词（分层：L1 摘要恒在 + L2 详情关键词命中，省 token 且不丢长期事实）。 */
    public String buildMemoryPrompt(String botKey, String groupId, String userId, String userText, int limit) {
        // 三级记忆：bot 级（bot,null,null 全场景共享）+ 当前维度（群聊: bot,group,user / 单聊: bot,null,user）
        StringBuilder sb = new StringBuilder();
        appendLevel(sb, botKey, null, null, userText, limit);          // bot 级共享
        appendLevel(sb, botKey, groupId, userId, userText, limit);     // 当前场景维度
        appendCorrections(sb, botKey, null, null);                     // bot 级纠正记录
        appendCorrections(sb, botKey, groupId, userId);                // 当前维度纠正记录
        return sb.length() == 0 ? "" : sb.toString().trim();
    }

    /** 拼一个维度的记忆摘要 + 相关详情（详情按 confidence 降序，低置信标存疑）。 */
    private void appendLevel(StringBuilder sb, String botKey, String groupId, String userId, String userText, int limit) {
        String summary = summary(botKey, groupId, userId);
        List<String> keywords = extractKeywords(userText);
        List<Map<String, Object>> details = recentRelevant(botKey, groupId, userId, keywords, limit);
        if ((summary == null || summary.isBlank()) && details.isEmpty()) return;
        sb.append("你记得关于这里的人和事：\n");
        if (summary != null && !summary.isBlank()) {
            sb.append("[长期记忆] ").append(summary).append("\n");
        }
        for (Map<String, Object> m : details) {
            String key = String.valueOf(m.get("key"));
            String value = String.valueOf(m.get("value"));
            recordHit(botKey, groupId, userId, key); // 引用即命中 +1（异步统计可信度用）
            double conf = m.get("confidence") instanceof Number n ? n.doubleValue() : 0.5;
            if (conf < 0.3) {
                sb.append("- [存疑] ").append(key).append(": ").append(value)
                  .append("（这条可能不准确，如不确定请先向用户求证）\n");
            } else {
                sb.append("- ").append(key).append(": ").append(value).append("\n");
            }
        }
        sb.append("\n");
    }

    /** 拼一个维度的纠正记录（用户明确纠正过的事实，模型不得再犯）。 */
    private void appendCorrections(StringBuilder sb, String botKey, String groupId, String userId) {
        List<Map<String, Object>> corrections;
        try {
            corrections = jdbc.query("""
                SELECT mem_key, mem_value, negative FROM xuanji_llm_memory
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
                  AND mem_type = 'CORRECTION' AND (expire_at = 0 OR expire_at > ?)
                ORDER BY updated_at DESC LIMIT 10
                """, (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("wrong", rs.getString("negative"));
                    m.put("right", rs.getString("mem_value"));
                    m.put("topic", rs.getString("mem_key"));
                    return m;
                }, botKey, groupId, userId, System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            return;
        }
        if (corrections.isEmpty()) return;
        sb.append("纠正记录（用户已纠正过的事实，务必遵守）：\n");
        for (Map<String, Object> c : corrections) {
            String wrong = String.valueOf(c.getOrDefault("wrong", ""));
            String right = String.valueOf(c.getOrDefault("right", ""));
            sb.append("- 用户已纠正过「").append(wrong).append("」，正确说法是「").append(right).append("」，不要再犯同样的错\n");
        }
        sb.append("\n");
    }

    /** 读取该维度的摘要行（无则 null）。 */
    public String summary(String botKey, String groupId, String userId) {
        try {
            return jdbc.query("""
                SELECT mem_value FROM xuanji_llm_memory
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
                  AND mem_key = ? AND mem_type = 'SUMMARY'
                """, rs -> rs.next() ? rs.getString(1) : null, botKey, groupId, userId, SUMMARY_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    /** 某维度全部有效 DETAIL 记忆（供摘要压缩），最多 max 条。 */
    public List<Map<String, Object>> listDetails(String botKey, String groupId, String userId, int max) {
        long now = System.currentTimeMillis() / 1000;
        try {
            return jdbc.query("""
                SELECT mem_key, mem_value FROM xuanji_llm_memory
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
                  AND mem_type = 'DETAIL' AND (expire_at = 0 OR expire_at > ?)
                ORDER BY updated_at DESC LIMIT ?
                """, (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", rs.getString("mem_key"));
                    m.put("value", rs.getString("mem_value"));
                    return m;
                }, botKey, groupId, userId, now, max);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 删除某维度超过 olderThanSeconds 秒的 DETAIL 记忆（摘要压缩后精简表）。 */
    public int deleteOldDetails(String botKey, String groupId, String userId, long olderThanSeconds) {
        long before = System.currentTimeMillis() / 1000 - olderThanSeconds;
        try {
            return jdbc.update("""
                DELETE FROM xuanji_llm_memory
                WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
                  AND mem_type = 'DETAIL' AND updated_at < DATEADD('SECOND', ?, TIMESTAMP '1970-01-01')
                """, botKey, groupId, userId, before);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 全部有记忆的维度组合（供摘要定时任务遍历）。 */
    public List<Map<String, String>> dimensions() {
        return jdbc.query("""
            SELECT DISTINCT bot_key, group_id, user_id FROM xuanji_llm_memory
            """, (rs, i) -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("userId", rs.getString("user_id"));
                return m;
            });
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "你", "我", "他", "她", "它", "们", "的", "了", "吗", "呢", "啊", "吧", "呀",
            "是", "在", "有", "和", "跟", "把", "被", "什么", "怎么", "为什么", "如何", "哪个",
            "帮", "请", "想", "知道", "告诉", "记住", "一下", "这个", "那个", "可以", "能",
            "机器人", "先生", "小姐", "有没有", "是不是", "请问");

    /** 提取消息关键词（中文连续串/英文 token，去停用词，限量 10）。 */
    public static List<String> extractKeywords(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher cm = Pattern.compile("[\\u4e00-\\u9fa5]{2,}").matcher(text);
        while (cm.find()) {
            String seg = cm.group();
            if (seg.length() <= 8) {
                if (!STOP_WORDS.contains(seg)) out.add(seg);
            } else {
                for (int i = 0; i + 2 <= seg.length() && out.size() < 12; i++) {
                    String g = seg.substring(i, i + 2);
                    if (!STOP_WORDS.contains(g)) out.add(g);
                }
            }
        }
        Matcher em = Pattern.compile("[a-zA-Z0-9_]{3,}").matcher(text);
        while (em.find()) out.add(em.group().toLowerCase());
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(out);
        return new ArrayList<>(uniq).stream().limit(10).toList();
    }

    /** 关键词相关记忆检索；无关键词时回退最近 N 条。按 confidence 降序（高可信优先）。 */
    private List<Map<String, Object>> recentRelevant(String botKey, String groupId, String userId,
                                                     List<String> keywords, int limit) {
        long now = System.currentTimeMillis() / 1000;
        StringBuilder sql = new StringBuilder("""
            SELECT mem_key, mem_value, confidence FROM xuanji_llm_memory
            WHERE bot_key = ? AND (group_id IS NOT DISTINCT FROM ?) AND (user_id IS NOT DISTINCT FROM ?)
              AND (expire_at = 0 OR expire_at > ?)
            """);
        List<Object> args = new ArrayList<>();
        args.add(botKey);
        args.add(groupId);
        args.add(userId);
        args.add(now);
        if (!keywords.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(mem_key LIKE ? OR mem_value LIKE ?)");
                args.add("%" + keywords.get(i) + "%");
                args.add("%" + keywords.get(i) + "%");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY confidence DESC, updated_at DESC LIMIT ?");
        args.add(limit);
        try {
            return jdbc.query(sql.toString(), (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", rs.getString("mem_key"));
                m.put("value", rs.getString("mem_value"));
                m.put("confidence", rs.getDouble("confidence"));
                return m;
            }, args.toArray());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 从 LLM 回复中解析 [MEMORY] 行并落库（返回去掉记忆行后的纯回复）。 */
    public String persistFromReply(String reply, String botKey, String groupId, String userId) {
        if (reply == null || !reply.contains("[MEMORY]")) {
            return reply;
        }
        String clean = reply.replaceAll("(?m)^\\[MEMORY\\].*$", "").replaceAll("\\n{3,}", "\n\n").trim();
        Matcher m = MEMORY_LINE.matcher(reply);
        while (m.find()) {
            String key = m.group(1).trim();
            String value = m.group(2).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                saveDetail(botKey, groupId, userId, key, value, 0);
                log.info("[MEMORY] 已记住 key={}, group={}, user={}", key, groupId, userId);
            }
        }
        return clean.isEmpty() ? "好，我记住了。" : clean;
    }

    /** 判断消息是否疑似"显式记住"请求。 */
    public static boolean isRememberRequest(String text) {
        if (text == null) return false;
        String t = text.trim();
        return t.matches("(?s).*(记住|记一下|帮我记|牢记|别忘了记).*");
    }

    /**
     * 从 LLM 回复解析 [MEMORY] 落库；若模型未输出记忆行但用户明确"记住X"，
     * 本地兜底直接从用户消息提取内容存记忆（保证"记住"必然生效）。
     */
    public String persistFromReplyAndBackfill(String reply, String botKey, String groupId, String userId, String userText) {
        boolean hadMemory = reply != null && reply.contains("[MEMORY]");
        String clean = persistFromReply(reply, botKey, groupId, userId);
        if (!hadMemory && isRememberRequest(userText)) {
            rememberFromUser(userText, botKey, groupId, userId);
        }
        return clean;
    }

    /** 本地兜底：从"记住X"的用户消息中直接提取内容存记忆（不依赖模型输出）。 */
    public void rememberFromUser(String text, String botKey, String groupId, String userId) {
        String content = extractRememberContent(text);
        if (content == null || content.isBlank()) {
            return;
        }
        String key = content.length() > 24 ? content.substring(0, 24) : content;
        saveDetail(botKey, groupId, userId, key, content, 0);
        log.info("[MEMORY] 本地兜底记住: bot={} group={} user={} key={}", botKey, groupId, userId, key);
    }

    /** 提取"记住"之后的内容。 */
    private String extractRememberContent(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("记住(?:一下|一次|一个|了)?[:：]?\\s*([\\s\\S]+)").matcher(text.trim());
        return m.find() ? m.group(1).trim() : text.trim();
    }
}
