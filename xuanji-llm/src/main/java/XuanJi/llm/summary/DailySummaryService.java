package XuanJi.llm.summary;

import XuanJi.api.llm.LlmMessage;
import XuanJi.api.llm.ProactiveSender;
import XuanJi.llm.LlmService;
import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.proactive.GroupActivityTracker;
import XuanJi.llm.profile.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 日报 —— 每日定时为启用群生成"今日群聊总结"并发到群里。
 *
 * <p>数据源：群今日消息数（GroupActivityTracker）+ 活跃用户画像（UserProfileService）
 * + 记忆摘要（MemoryService），拼给 LLM 生成 100-200 字总结。
 * 配置存 {@code xuanji_llm_summary_config}，生成记录存 {@code xuanji_llm_summary_log}。
 */
@Slf4j
@Service
public class DailySummaryService {

    private final JdbcTemplate jdbc;
    private final LlmService llmService;
    private final GroupActivityTracker tracker;
    private final UserProfileService profileService;
    private final MemoryService memoryService;
    private final List<ProactiveSender> senders;
    private final XuanJi.llm.render.HtmlRenderService renderService;
    private final XuanJi.llm.render.ReportDataCollector reportCollector;

    public DailySummaryService(JdbcTemplate jdbc,
                               LlmService llmService,
                               GroupActivityTracker tracker,
                               UserProfileService profileService,
                               MemoryService memoryService,
                               List<ProactiveSender> senders,
                               XuanJi.llm.render.HtmlRenderService renderService,
                               XuanJi.llm.render.ReportDataCollector reportCollector) {
        this.jdbc = jdbc;
        this.llmService = llmService;
        this.tracker = tracker;
        this.profileService = profileService;
        this.memoryService = memoryService;
        this.senders = senders;
        this.renderService = renderService;
        this.reportCollector = reportCollector;
    }

    // ════════════ 配置 ════════════

    public List<Map<String, Object>> configs(String botKey) {
        String sql = botKey == null || botKey.isBlank()
                ? "SELECT bot_key, group_id, enabled, run_hour, run_minute, image_mode FROM xuanji_llm_summary_config ORDER BY group_id"
                : "SELECT bot_key, group_id, enabled, run_hour, run_minute, image_mode FROM xuanji_llm_summary_config WHERE bot_key = ? ORDER BY group_id";
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("botKey", rs.getString("bot_key"));
            m.put("groupId", rs.getString("group_id"));
            m.put("enabled", rs.getBoolean("enabled"));
            m.put("runHour", rs.getInt("run_hour"));
            m.put("runMinute", rs.getInt("run_minute"));
            m.put("imageMode", rs.getObject("image_mode") != null && rs.getInt("image_mode") == 1);
            return m;
        }, botKey == null || botKey.isBlank() ? new Object[0] : new Object[]{botKey});
    }

    public void setConfig(String botKey, String groupId, boolean enabled, int hour, int minute, boolean imageMode) {
        jdbc.update("""
            MERGE INTO xuanji_llm_summary_config (bot_key, group_id, enabled, run_hour, run_minute, image_mode)
            KEY (bot_key, group_id) VALUES (?,?,?,?,?,?)
            """, botKey, groupId, enabled, hour, minute, imageMode ? 1 : 0);
    }

    /** 删除某群的日报配置。 */
    public void deleteConfig(String botKey, String groupId) {
        jdbc.update("DELETE FROM xuanji_llm_summary_config WHERE bot_key = ? AND group_id = ?",
                botKey, groupId);
        log.info("[SUMMARY] 删除日报配置: bot={}, group={}", botKey, groupId);
    }

    public List<Map<String, Object>> history(int limit) {
        return jdbc.query("""
            SELECT id, bot_key, group_id, content, created_at FROM xuanji_llm_summary_log
            ORDER BY id DESC LIMIT ?
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("content", rs.getString("content"));
                m.put("createdAt", rs.getObject("created_at") != null ? String.valueOf(rs.getObject("created_at")) : null);
                return m;
            }, Math.min(Math.max(limit, 1), 100));
    }

    // ════════════ 生成 ════════════

    /** 为某群生成今日总结（不发送）。 */
    public String generate(String botKey, String groupId) {
        long msgCount = tracker.todayMsgCount(botKey, groupId);
        List<Map<String, Object>> profiles = profileService.list(botKey, groupId).stream().limit(5).toList();
        String memSummary = memoryService.summary(botKey, groupId, null);

        StringBuilder ctx = new StringBuilder();
        ctx.append("今日群消息数：").append(msgCount).append("\n");
        if (!profiles.isEmpty()) {
            ctx.append("活跃用户画像：\n");
            for (Map<String, Object> p : profiles) {
                ctx.append("- ").append(p.get("nickname")).append("（消息").append(p.get("msgCount")).append("条）")
                        .append(p.get("summary") != null ? " " + p.get("summary") : "").append("\n");
            }
        }
        if (memSummary != null && !memSummary.isBlank()) {
            ctx.append("近期记忆摘要：").append(memSummary).append("\n");
        }

        String prompt = """
            你是群聊日报编辑。根据以下群聊数据，生成一份简洁温暖的「今日群聊总结」，100-200字：
            概括今天群里聊了什么氛围如何、活跃成员、值得记住的事。语气自然，不要条条列点，像人写的随笔。

            群聊数据：
            %s
            """.formatted(ctx);
        try {
            String reply = llmService.chat(List.of(
                    LlmMessage.system("你是群聊日报编辑，负责生成今日总结。"),
                    LlmMessage.user(prompt)), null);
            if (reply != null && !reply.isBlank()) {
                jdbc.update("INSERT INTO xuanji_llm_summary_log (bot_key, group_id, content, created_at) VALUES (?,?,?, CURRENT_TIMESTAMP)",
                        botKey, groupId, reply);
            }
            return reply;
        } catch (Exception e) {
            log.warn("[SUMMARY] 日报生成失败: {}", e.getMessage());
            return "日报生成失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 生成并发送到群。imageMode=true 时走图文卡片渲染。 */
    public boolean generateAndSend(String botKey, String groupId, boolean imageMode) {
        String text = generate(botKey, groupId);
        if (text == null || text.isBlank() || text.startsWith("日报生成失败")) return false;
        if (imageMode) {
            try {
                byte[] png = renderService.render("daily-report",
                        reportCollector.collectDailyReport(botKey, groupId, null, text));
                for (ProactiveSender s : senders) {
                    if (s.sendImageBytes(botKey, groupId, png)) {
                        log.info("[SUMMARY] 图文日报已发送: bot={} group={} {}B", botKey, groupId, png.length);
                        return true;
                    }
                }
                log.warn("[SUMMARY] 图文日报发送失败（所有 sender）: bot={} group={}", botKey, groupId);
                return false;
            } catch (Exception e) {
                log.warn("[SUMMARY] 图文日报渲染/发送失败，降级文本: {}", e.getMessage());
                // 渲染失败降级文本版
            }
        }
        for (ProactiveSender s : senders) {
            if (s.sendText(botKey, groupId, text)) {
                log.info("[SUMMARY] 日报已发送: bot={} group={}", botKey, groupId);
                return true;
            }
        }
        return false;
    }

    // ════════════ 定时扫描 ════════════

    /** AI 日报扫描（P3 定时迁移：由 scheduler 系统任务 LLM_DAILY_REPORT 每分钟触发，按 summary_config 动态判断到点）。 */
    public void scan() {
        try {
            LocalTime now = LocalTime.now();
            for (Map<String, Object> cfg : configs("")) {
                if (!Boolean.TRUE.equals(cfg.get("enabled"))) continue;
                if (now.getHour() == ((Number) cfg.get("runHour")).intValue()
                        && now.getMinute() == ((Number) cfg.get("runMinute")).intValue()) {
                    String botKey = String.valueOf(cfg.get("botKey"));
                    String groupId = String.valueOf(cfg.get("groupId"));
                    boolean imageMode = Boolean.TRUE.equals(cfg.get("imageMode"));
                    try {
                        generateAndSend(botKey, groupId, imageMode);
                    } catch (Exception e) {
                        log.warn("[SUMMARY] 定时日报失败: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SUMMARY] 扫描异常: {}", e.getMessage());
        }
    }
}
