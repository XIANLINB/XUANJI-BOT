package XuanJi.llm.kb;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.tool.LlmToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库（RAG MVP 版）—— 上传文档文本 → 分段入库 → 关键词检索。
 *
 * <p>提供 {@code knowledge_search} 工具注册进 ToolRegistry：群聊/Agent 中用户提问时，
 * 模型自动调用本工具检索知识库段落，再结合上下文作答（文档问答）。
 *
 * <p>MVP 用关键词检索（零成本零依赖），后续可升级向量 Embedding（KbService.search
 * 为唯一检索入口，替换内部实现即可）。
 */
@Slf4j
@Service
public class KbService {

    /** 单段最大字符数（超长硬切）。 */
    private static final int MAX_CHUNK = 500;

    private final JdbcTemplate jdbc;

    public KbService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ════════════ 文档管理 ════════════

    /** 上传文本并分段入库。 */
    public long addText(String botKey, String name, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        List<String> chunks = split(content);
        jdbc.update("INSERT INTO xuanji_llm_kb_doc (bot_key, name, char_count, chunk_count, created_at) VALUES (?,?,?,?, CURRENT_TIMESTAMP)",
                botKey, name, content.length(), chunks.size());
        Long docId = jdbc.queryForObject("SELECT MAX(id) FROM xuanji_llm_kb_doc", Long.class);
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("INSERT INTO xuanji_llm_kb_chunk (doc_id, seq, content) VALUES (?,?,?)",
                    docId, i, chunks.get(i));
        }
        log.info("[KB] 文档已入库: id={}, name={}, 段数={}", docId, name, chunks.size());
        return docId == null ? 0 : docId;
    }

    /** 上传文件并分段入库（支持 txt/md/csv/json/log 等 UTF-8 文本；pdf 暂需转文本）。 */
    public long addFile(String botKey, String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        String name = filename == null || filename.isBlank() ? "文档" : filename;
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc")) {
            throw new IllegalArgumentException("暂不支持 " + name.substring(name.lastIndexOf('.'))
                    + " 直接上传，请转成 txt/md 后粘贴内容或上传文本文件");
        }
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        return addText(botKey, name, text);
    }

    /** 文档列表。 */
    public List<Map<String, Object>> list(String botKey) {
        return jdbc.query("""
            SELECT id, bot_key, name, char_count, chunk_count, created_at
            FROM xuanji_llm_kb_doc WHERE bot_key = ? ORDER BY id DESC
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("botKey", rs.getString("bot_key"));
                m.put("name", rs.getString("name"));
                m.put("charCount", rs.getInt("char_count"));
                m.put("chunkCount", rs.getInt("chunk_count"));
                m.put("createdAt", rs.getObject("created_at") != null ? String.valueOf(rs.getObject("created_at")) : null);
                return m;
            }, botKey);
    }

    /** 删除文档（含分段）。 */
    public void delete(long docId) {
        jdbc.update("DELETE FROM xuanji_llm_kb_chunk WHERE doc_id = ?", docId);
        jdbc.update("DELETE FROM xuanji_llm_kb_doc WHERE id = ?", docId);
    }

    // ════════════ 检索 ════════════

    /** 关键词检索知识库，返回命中的段落文本（带来源文档名）。 */
    public String search(String botKey, String query, int topN) {
        List<String> keywords = MemoryService.extractKeywords(query);
        if (keywords.isEmpty()) return "（知识库无相关内容）";
        StringBuilder sql = new StringBuilder("""
            SELECT c.content, d.name FROM xuanji_llm_kb_chunk c
            JOIN xuanji_llm_kb_doc d ON c.doc_id = d.id
            WHERE d.bot_key = ? AND (
            """);
        List<Object> args = new ArrayList<>();
        args.add(botKey);
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("c.content LIKE ?");
            args.add("%" + keywords.get(i) + "%");
        }
        sql.append(") LIMIT ?");
        args.add(Math.min(Math.max(topN, 1), 8));
        try {
            List<Map<String, Object>> rows = jdbc.query(sql.toString(), (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("content", rs.getString("content"));
                m.put("doc", rs.getString("name"));
                return m;
            }, args.toArray());
            if (rows.isEmpty()) return "（知识库无相关内容）";
            StringBuilder sb = new StringBuilder("【知识库检索结果】\n");
            for (Map<String, Object> r : rows) {
                sb.append("[来源:").append(r.get("doc")).append("] ")
                        .append(String.valueOf(r.get("content")).trim()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "（知识库检索失败: " + e.getMessage() + "）";
        }
    }

    // ════════════ LLM 工具 ════════════

    @LlmTool(name = "knowledge_search",
            descriptionZh = "知识库文档检索问答",
            description = "检索知识库：当用户询问框架/项目的知识、配置方法、功能用法时调用，返回相关文档段落。用户说「查一下」「知识库里有什么」「怎么配置X」时使用",
            confirm = false)
    public String knowledgeSearch(LlmToolContext ctx,
                                  @LlmToolParam(name = "query", value = "要检索的问题或关键词，如 '多机器人配置'") String query) {
        return search(ctx != null ? ctx.botKey() : "", query, 5);
    }

    // ════════════ 分段 ════════════

    /** 文本分段：优先按空行切，段超长硬切。 */
    static List<String> split(String content) {
        List<String> out = new ArrayList<>();
        String[] paras = content.replace("\r\n", "\n").split("\n\\s*\n");
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (cur.length() + t.length() + 1 > MAX_CHUNK) {
                if (cur.length() > 0) out.add(cur.toString().trim());
                cur.setLength(0);
            }
            if (t.length() > MAX_CHUNK) {
                // 超长段硬切
                if (cur.length() > 0) out.add(cur.toString().trim());
                cur.setLength(0);
                for (int i = 0; i < t.length(); i += MAX_CHUNK) {
                    out.add(t.substring(i, Math.min(i + MAX_CHUNK, t.length())));
                }
                continue;
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(t);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }
}
