package XuanJi.llm.profile;

import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmMessage;
import XuanJi.llm.LlmService;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户画像 —— 全量消息模式下，对一个群成员"日积月累地认识"。
 *
 * <p>两条链路：
 * <ol>
 *   <li><b>本地统计（零 token，实时）</b>：消息数、最近发言缓冲、平均句长、活跃时间。
 *       每次 onMessage 更新内存，周期落库。</li>
 *   <li><b>LLM 画像提炼（少量 token，异步）</b>：距上次提炼 ≥ profileExtractHours 小时，
 *       或新消息 ≥ profileExtractMsgThreshold 条，把「近期发言 + 旧画像」喂 LLM 做增量合并，
 *       产出 {@code profile_summary}（他是谁）+ {@code speech_style}（说话风格）。</li>
 * </ol>
 *
 * <p>对话前 {@link #buildProfilePrompt} 将画像拼成一行注入 system，
 * 让 AI "像认识朋友一样"知道当前对话者是谁、什么风格。
 */
@Slf4j
@Component
public class UserProfileService {

    /** 每用户保留的近期发言缓冲条数（供 LLM 提炼） */
    private static final int BUFFER_SIZE = 50;
    /** 提炼时喂给 LLM 的发言条数 */
    private static final int EXTRACT_MSG_LIMIT = 30;
    /** 内存统计每 N 条落库一次（降写频） */
    private static final int FLUSH_EVERY = 20;

    private final JdbcTemplate jdbc;
    private final LlmService llmService;
    private final LlmConfigStore configStore;

    /** 异步画像提炼（虚拟线程，不阻塞消息 Pipeline） */
    private static final ExecutorService EXTRACT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** 维度 key：botKey:groupId:userId */
    private final ConcurrentHashMap<String, Buffer> buffers = new ConcurrentHashMap<>();

    public UserProfileService(JdbcTemplate jdbc, LlmService llmService, LlmConfigStore configStore) {
        this.jdbc = jdbc;
        this.llmService = llmService;
        this.configStore = configStore;
    }

    // ════════════ 消息入站（全量消息统计） ════════════

    /** 群消息入站：更新该用户统计；满足条件时异步触发 LLM 画像提炼。 */
    public void onGroupMessage(String botKey, String groupId, String userId, String nickname, String role, String text) {
        if (botKey == null || groupId == null || userId == null) return;
        if (text == null || text.isBlank()) return;
        LlmConfig cfg = configStore.get();
        if (!cfg.isProfileEnabled()) return;

        Buffer b = buffers.computeIfAbsent(key(botKey, groupId, userId), k -> new Buffer());
        synchronized (b) {
            b.msgCount++;
            b.lastSeen = System.currentTimeMillis() / 1000;
            b.sumLen += text.length();
            b.count++;
            b.nickname = nickname == null || nickname.isBlank() ? b.nickname : nickname;
            b.role = role == null || role.isBlank() ? b.role : role;
            b.recent.addLast(text);
            while (b.recent.size() > BUFFER_SIZE) b.recent.removeFirst();
            if (b.msgCount % FLUSH_EVERY == 0) {
                flushToDb(b, botKey, groupId, userId);
            }
            // 提炼触发：新消息达到阈值 或 距上次提炼满间隔且确有新消息
            long now = System.currentTimeMillis() / 1000;
            long gap = cfg.getProfileExtractHours() * 3600L;
            boolean timeUp = gap > 0 && (now - b.lastExtractAt) >= gap && (b.msgCount - b.msgAtLastExtract) >= 10;
            boolean countUp = cfg.getProfileExtractMsgThreshold() > 0
                    && (b.msgCount - b.msgAtLastExtract) >= cfg.getProfileExtractMsgThreshold();
            if (timeUp || countUp) {
                b.lastExtractAt = now;
                b.msgAtLastExtract = b.msgCount;
                List<String> snapshot = new ArrayList<>(b.recent);
                EXTRACT_EXECUTOR.execute(() -> extract(botKey, groupId, userId, snapshot));
            }
        }
    }

    // ════════════ LLM 画像提炼 ════════════

    private void extract(String botKey, String groupId, String userId, List<String> recent) {
        LlmConfig cfg = configStore.get();
        if (!cfg.isProfileEnabled() || cfg.getApiKey() == null || cfg.getApiKey().isBlank()) return;
        String old = profileSummary(botKey, groupId, userId);
        StringBuilder msgs = new StringBuilder();
        int n = Math.min(recent.size(), EXTRACT_MSG_LIMIT);
        for (int i = recent.size() - n; i < recent.size(); i++) {
            msgs.append("- ").append(recent.get(i)).append("\n");
        }
        String prompt = """
            你是用户画像分析师。根据该用户在群里的近期发言，更新对他的人物认知。
            输出固定两段（直接给内容，不要额外解释）：
            [SUMMARY] 对这个人的认知总结：身份/性格/与你(机器人)的关系/他关心的话题，150字内
            [STYLE] 他的说话风格：语气、口癖、句长、表情/标点习惯，100字内
            若新发言与旧画像冲突，以新发言为准。

            旧画像：%s

            近期发言（最近%d条）：
            %s
            """.formatted(old == null ? "(首次建立)" : old, n, msgs);
        try {
            String reply = llmService.chat(
                    List.of(LlmMessage.system("你是用户画像分析师。"), LlmMessage.user(prompt)),
                    LlmChatOptions.defaults());
            String summary = parseSection(reply, "SUMMARY");
            String style = parseSection(reply, "STYLE");
            if (summary == null && style == null) return;
            jdbc.update("""
                UPDATE xuanji_llm_user_profile
                SET profile_summary = ?, speech_style = ?, extract_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE bot_key = ? AND group_id = ? AND user_id = ?
                """, summary, style, System.currentTimeMillis() / 1000, botKey, groupId, userId);
            log.info("[PROFILE] 画像提炼完成 bot={} group={} user={}", botKey, groupId, userId);
        } catch (Exception e) {
            log.warn("[PROFILE] 画像提炼失败: {}", e.getMessage());
        }
    }

    private static String parseSection(String reply, String tag) {
        if (reply == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?s)\\[" + tag + "\\]\\s*(.*?)(?=\\n\\[|$)").matcher(reply);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    // ════════════ 落库 / 查询 ════════════

    private void flushToDb(Buffer b, String botKey, String groupId, String userId) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_llm_user_profile (bot_key, group_id, user_id, nickname, member_role, msg_count, last_seen, updated_at)
                KEY (bot_key, group_id, user_id)
                VALUES (?,?,?,?,?,?,?, CURRENT_TIMESTAMP)
                """, botKey, groupId, userId, b.nickname, b.role, b.msgCount, b.lastSeen);
        } catch (Exception e) {
            log.warn("[PROFILE] 落库失败: {}", e.getMessage());
        }
    }

    /** 某用户画像摘要（无则 null）。 */
    public String profileSummary(String botKey, String groupId, String userId) {
        try {
            return jdbc.query("""
                SELECT profile_summary FROM xuanji_llm_user_profile
                WHERE bot_key = ? AND group_id = ? AND user_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, botKey, groupId, userId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 画像列表（前端"用户认知"页数据源）。 */
    public List<Map<String, Object>> list(String botKey, String groupId) {
        StringBuilder sql = new StringBuilder("""
            SELECT bot_key, group_id, user_id, nickname, member_role,
                   profile_summary, speech_style, msg_count, last_seen, updated_at
            FROM xuanji_llm_user_profile WHERE 1=1
            """);
        List<Object> args = new ArrayList<>();
        if (botKey != null && !botKey.isBlank()) {
            sql.append(" AND bot_key = ?"); args.add(botKey);
        }
        if (groupId != null && !groupId.isBlank()) {
            sql.append(" AND group_id = ?"); args.add(groupId);
        }
        sql.append(" ORDER BY msg_count DESC LIMIT 200");
        try {
            return jdbc.query(sql.toString(), (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("userId", rs.getString("user_id"));
                m.put("nickname", rs.getString("nickname"));
                m.put("role", rs.getString("member_role"));
                m.put("summary", rs.getString("profile_summary"));
                m.put("style", rs.getString("speech_style"));
                m.put("msgCount", rs.getLong("msg_count"));
                m.put("lastSeen", rs.getObject("last_seen") != null ? String.valueOf(rs.getObject("last_seen")) : null);
                return m;
            }, args.toArray());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 删除用户画像。 */
    public void delete(String botKey, String groupId, String userId) {
        jdbc.update("DELETE FROM xuanji_llm_user_profile WHERE bot_key = ? AND group_id = ? AND user_id = ?",
                botKey, groupId, userId);
        buffers.remove(key(botKey, groupId, userId));
    }

    // ════════════ 对话注入 ════════════

    /** 拼一行"当前对话者"注入 system，让 AI 认识当前发消息的人。 */
    public String buildProfilePrompt(String botKey, String groupId, String userId) {
        try {
            return jdbc.query("""
                SELECT nickname, member_role, profile_summary, speech_style, preference_summary
                FROM xuanji_llm_user_profile
                WHERE bot_key = ? AND group_id = ? AND user_id = ?
                """, rs -> {
                    if (!rs.next()) return "";
                    StringBuilder sb = new StringBuilder("当前对话者：");
                    String nick = rs.getString("nickname");
                    String role = rs.getString("member_role");
                    String summary = rs.getString("profile_summary");
                    String style = rs.getString("speech_style");
                    String pref = rs.getString("preference_summary");
                    sb.append(nick != null && !nick.isBlank() ? nick : "群成员");
                    if (role != null && !role.isBlank()) {
                        sb.append("（").append(role).append("）");
                    }
                    if (summary != null && !summary.isBlank()) {
                        sb.append("｜关于他：").append(summary);
                    }
                    if (style != null && !style.isBlank()) {
                        sb.append("｜他的说话风格：").append(style);
                    }
                    // P2-F 用户偏好摘要（👍/👎 反馈蒸馏）：AI 按用户喜好调整回复风格
                    if (pref != null && !pref.isBlank()) {
                        sb.append("｜他的回复偏好：").append(pref);
                    }
                    return sb.toString();
                }, botKey, groupId, userId);
        } catch (Exception e) {
            return "";
        }
    }

    private static String key(String botKey, String groupId, String userId) {
        return botKey + ":" + groupId + ":" + userId;
    }

    /** 单用户内存缓冲（线程内同步访问）。 */
    private static class Buffer {
        long msgCount;
        long lastSeen;
        long lastExtractAt;
        long msgAtLastExtract;
        long sumLen;
        long count;
        String nickname;
        String role;
        final Deque<String> recent = new ArrayDeque<>();

        long avgLen() {
            return count == 0 ? 0 : sumLen / count;
        }
    }
}
