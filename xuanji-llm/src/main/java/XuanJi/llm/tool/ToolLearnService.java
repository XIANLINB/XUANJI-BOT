package XuanJi.llm.tool;

import XuanJi.api.llm.LlmToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用经验库（P1）—— 让 AI 用同一个工具越用越准。
 *
 * <p>数据采集：{@link ToolRegistry#execute} 每次调用记录 {tool, args, ok, error}。
 * 经验沉淀：同一工具同类错误累计 ≥3 次 → 用 LLM 生成一条 {@code fix_hint}（正确参数格式示例）。
 * 注入：Agent 组装工具定义时，把该工具 top-3 历史错误 + fix_hint 拼进 description，
 * 模型下次调用就知道正确姿势（尤其 cron 翻译、模板 data 结构）。
 */
@Slf4j
@Component
public class ToolLearnService {

    private static final int FIX_HINT_THRESHOLD = 3;   // 同类错误累计 ≥3 次触发生成 fix_hint

    private final JdbcTemplate jdbc;
    private final XuanJi.llm.LlmService llmService;

    public ToolLearnService(JdbcTemplate jdbc, XuanJi.llm.LlmService llmService) {
        this.jdbc = jdbc;
        this.llmService = llmService;
    }

    /**
     * 记录一次工具调用（成功/失败）。
     *
     * @param tool     工具名
     * @param argsJson 模型传入的参数 JSON（失败时用于分析）
     * @param ok       是否成功
     * @param error    失败时的错误信息（成功为 null）
     */
    public void record(String tool, String argsJson, boolean ok, String error) {
        try {
            if (ok) {
                // 成功调用只累计命中（供后续统计成功率）
                jdbc.update("""
                    UPDATE xuanji_llm_tool_learn SET hit_count = hit_count + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE tool = ? AND error = ''
                    """, tool);
                return;
            }
            String err = error == null ? "" : error.trim();
            if (err.length() > 500) err = err.substring(0, 500);
            String args = argsJson == null ? "" : argsJson;
            if (args.length() > 800) args = args.substring(0, 800);
            // 找是否已有同类错误记录（同工具 + 同错误信息前缀）
            String key = err.length() > 60 ? err.substring(0, 60) : err;
            Integer exists = jdbc.query("""
                SELECT 1 FROM xuanji_llm_tool_learn WHERE tool = ? AND error = ?
                """, rs -> rs.next() ? 1 : null, tool, err);
            if (exists == null) {
                jdbc.update("""
                    INSERT INTO xuanji_llm_tool_learn (tool, args, error, hit_count, created_at, updated_at)
                    VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, tool, args, err);
            } else {
                jdbc.update("""
                    UPDATE xuanji_llm_tool_learn SET hit_count = hit_count + 1, args = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE tool = ? AND error = ?
                    """, args, tool, err);
            }
            // 同类错误达到阈值且无 fix_hint → 生成
            maybeGenerateFixHint(tool, err);
        } catch (Exception e) {
            log.warn("[TOOL-LEARN] 记录失败: {}", e.getMessage());
        }
    }

    /** 同类错误达到阈值且无 fix_hint 时，用 LLM 生成修复建议。 */
    private void maybeGenerateFixHint(String tool, String error) {
        try {
            Integer cnt = jdbc.query("""
                SELECT hit_count FROM xuanji_llm_tool_learn WHERE tool = ? AND error = ?
                """, rs -> rs.next() ? rs.getInt(1) : 0, tool, error);
            if (cnt == null || cnt < FIX_HINT_THRESHOLD) return;
            String fix = jdbc.query("""
                SELECT fix_hint FROM xuanji_llm_tool_learn WHERE tool = ? AND error = ?
                """, rs -> rs.next() ? rs.getString(1) : null, tool, error);
            if (fix != null && !fix.isBlank()) return;
            // 用 LLM 分析错误 → 生成一句话修复建议（失败不阻塞）
            String hint = askFixHint(tool, error);
            if (hint != null && !hint.isBlank()) {
                if (hint.length() > 300) hint = hint.substring(0, 300);
                jdbc.update("""
                    UPDATE xuanji_llm_tool_learn SET fix_hint = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE tool = ? AND error = ?
                    """, hint, tool, error);
                log.info("[TOOL-LEARN] 已生成 fix_hint: tool={} err={}", tool, error);
            }
        } catch (Exception e) {
            log.warn("[TOOL-LEARN] fix_hint 生成失败: {}", e.getMessage());
        }
    }

    private String askFixHint(String tool, String error) {
        try {
            String prompt = "工具「" + tool + "」调用失败，错误信息：\n" + error
                    + "\n\n请用一句话给出修复建议（正确的调用方式/参数格式，中文，50 字以内）：";
            String reply = llmService.chat(List.of(XuanJi.api.llm.LlmMessage.user(prompt)), null);
            return reply == null ? null : reply.trim();
        } catch (Exception e) {
            log.warn("[TOOL-LEARN] LLM 生成 fix_hint 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 增强工具定义：把该工具 top-3 历史错误 + fix_hint 拼进 description。
     * 返回新列表（不改原对象）。
     */
    public List<LlmToolDefinition> enhance(List<LlmToolDefinition> defs) {
        if (defs == null || defs.isEmpty()) return defs;
        List<LlmToolDefinition> out = new ArrayList<>();
        for (LlmToolDefinition d : defs) {
            out.add(enhanceOne(d));
        }
        return out;
    }

    private LlmToolDefinition enhanceOne(LlmToolDefinition d) {
        try {
            List<Map<String, Object>> lessons = jdbc.query("""
                SELECT error, fix_hint FROM xuanji_llm_tool_learn
                WHERE tool = ? AND error <> '' ORDER BY hit_count DESC, updated_at DESC LIMIT 3
                """, (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("error", rs.getString("error"));
                    m.put("fix_hint", rs.getString("fix_hint"));
                    return m;
                }, d.name());
            if (lessons.isEmpty()) return d;
            StringBuilder extra = new StringBuilder("\n\n【使用提示·历史错误】");
            for (Map<String, Object> l : lessons) {
                String err = String.valueOf(l.get("error"));
                String hint = l.get("fix_hint") == null ? "" : String.valueOf(l.get("fix_hint"));
                if (err.length() > 80) err = err.substring(0, 80);
                extra.append("\n- 曾出错: ").append(err);
                if (!hint.isBlank()) extra.append(" → 修复: ").append(hint);
            }
            return new LlmToolDefinition(d.name(), d.description() + extra, d.descriptionZh(),
                    d.parameters(), d.confirm(), d.source());
        } catch (Exception e) {
            return d;
        }
    }
}
