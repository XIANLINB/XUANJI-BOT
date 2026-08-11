package XuanJi.llm.feedback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微调数据集导出（P3-G）—— 把真实对话沉淀成 OpenAI / DeepSeek 微调格式 JSONL。
 *
 * <p>数据源：{@code xuanji_llm_agent_session}（会话历史，含 user/assistant 消息）+ {@code xuanji_llm_feedback}
 * （用户 👍/👎 高置信筛选）。
 * <p>质量规则（宁缺毋滥）：
 * <ul>
 *   <li>用户 👎（score=-1）过的回复直接剔除</li>
 *   <li>含 [MEMORY]/[会话摘要] 等系统注入内容的样本剔除</li>
 *   <li>太短（<4 字）或纯标点样本剔除</li>
 * </ul>
 */
@Slf4j
@Component
public class FinetuneExportService {

    private static final int MAX_SAMPLES = 200;

    private final JdbcTemplate jdbc;
    private final XuanJi.llm.persona.PersonaService personaService;
    private final tools.jackson.databind.ObjectMapper objectMapper =
            new tools.jackson.databind.ObjectMapper();

    public FinetuneExportService(JdbcTemplate jdbc,
                                 XuanJi.llm.persona.PersonaService personaService) {
        this.jdbc = jdbc;
        this.personaService = personaService;
    }

    /**
     * 导出某机器人的微调数据集。
     *
     * @param botKey 机器人
     * @param limit  最大样本数（默认 50）
     * @return map: {samples, jsonl}
     */
    public Map<String, Object> export(String botKey, int limit) {
        int cap = Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_SAMPLES));
        // 1. 用户 👎 过的回复（剔除用）
        Set<String> disliked = new HashSet<>();
        try {
            disliked.addAll(jdbc.query("""
                SELECT reply_text FROM xuanji_llm_feedback
                WHERE bot_key = ? AND score = -1
                """, (rs, i) -> rs.getString(1), botKey));
        } catch (Exception e) {
            log.debug("[FINETUNE] 读取差评失败: {}", e.getMessage());
        }

        // 2. 读取该 bot 的会话历史
        List<Map<String, Object>> sessions;
        try {
            sessions = jdbc.query("""
                SELECT session_key, state_json FROM xuanji_llm_agent_session
                WHERE session_key LIKE ? ORDER BY updated_at DESC LIMIT 50
                """, (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", rs.getString("session_key"));
                    m.put("json", rs.getString("state_json"));
                    return m;
                }, botKey + "|%");
        } catch (Exception e) {
            sessions = List.of();
        }

        // 3. 组装 system 提示词（bot 级人格）
        String sys = personaService.buildSystemPrompt(personaService.resolve(botKey, null, null));
        if (sys.isBlank()) sys = "你是一个智能聊天助手。";

        StringBuilder jsonl = new StringBuilder();
        int samples = 0;
        for (Map<String, Object> s : sessions) {
            if (samples >= cap) break;
            String json = String.valueOf(s.get("json"));
            List<XuanJi.api.llm.LlmMessage> msgs;
            try {
                msgs = objectMapper.readValue(json,
                        new tools.jackson.core.type.TypeReference<List<XuanJi.api.llm.LlmMessage>>() {});
            } catch (Exception e) {
                continue;
            }
            // 逐条 user→assistant 配对，形成可微调样本
            for (int i = 0; i + 1 < msgs.size(); i++) {
                if (samples >= cap) break;
                XuanJi.api.llm.LlmMessage u = msgs.get(i);
                XuanJi.api.llm.LlmMessage a = msgs.get(i + 1);
                if (!"user".equals(u.role()) || !"assistant".equals(a.role())) continue;
                String userText = u.content() == null ? "" : u.content().trim();
                String reply = a.content() == null ? "" : a.content().trim();
                if (!qualify(userText, reply, disliked)) continue;
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("messages", List.of(
                        Map.of("role", "system", "content", sys),
                        Map.of("role", "user", "content", userText),
                        Map.of("role", "assistant", "content", reply)));
                try {
                    jsonl.append(objectMapper.writeValueAsString(sample)).append("\n");
                    samples++;
                } catch (Exception e) {
                    // skip
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("samples", samples);
        out.put("jsonl", jsonl.toString());
        return out;
    }

    /** 质量门槛：剔除太短 / 系统注入残留 / 用户差评过的回复。 */
    private boolean qualify(String userText, String reply, Set<String> disliked) {
        if (userText.length() < 2 || reply.length() < 4) return false;
        if (reply.contains("[MEMORY]") || reply.contains("[会话摘要]")
                || reply.contains("【") && reply.contains("】")) return false;
        if (disliked.contains(reply)) return false;
        return true;
    }
}
