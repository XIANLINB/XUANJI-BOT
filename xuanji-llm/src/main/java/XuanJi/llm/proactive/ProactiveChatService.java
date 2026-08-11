package XuanJi.llm.proactive;

import XuanJi.api.llm.ProactiveSender;
import XuanJi.llm.chat.LlmChatGuard;
import XuanJi.llm.chat.LlmQuotaService;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 主动搭话服务 —— 定时扫描冷场群，主动 @ 人问话 或发话题卡片活跃气氛。
 *
 * <p>判定（全满足才主动，防骚扰护栏）：
 * <ol>
 *   <li>配置 {@code proactiveEnabled} 开启</li>
 *   <li>当前时间在 {@code proactiveTimeStart ~ proactiveTimeEnd} 窗口内</li>
 *   <li>群距最后一条真人消息 ≥ {@code proactiveIdleMinutes} 分钟（冷场）</li>
 *   <li>今日群内有人活跃过（避免对空群/僵尸群主动）</li>
 *   <li>今日主动次数（日志表统计，重启不丢）&lt; {@code proactiveDailyLimit}</li>
 *   <li>距上次主动 ≥ {@code proactiveCooldownMinutes} 分钟</li>
 *   <li>机器人级 + 群级 token 预算充足</li>
 *   <li>存在可用发送器（{@link ProactiveSender}）</li>
 * </ol>
 *
 * <p>发送内容：随机 @ 最近活跃成员问话（文本，内嵌 {@code <@openid>}）或话题卡片（markdown），
 * 均来自内置模板库（零 token）。每次动作写 {@code xuanji_llm_proactive_log} 供审计。
 */
@Slf4j
@Component
public class ProactiveChatService {

    private final LlmConfigStore configStore;
    private final GroupActivityTracker tracker;
    private final LlmChatGuard guard;
    private final LlmQuotaService quotaService;
    private final List<ProactiveSender> senders;
    private final JdbcTemplate jdbc;

    public ProactiveChatService(LlmConfigStore configStore,
                                GroupActivityTracker tracker,
                                LlmChatGuard guard,
                                LlmQuotaService quotaService,
                                List<ProactiveSender> senders,
                                JdbcTemplate jdbc) {
        this.configStore = configStore;
        this.tracker = tracker;
        this.guard = guard;
        this.quotaService = quotaService;
        this.senders = senders;
        this.jdbc = jdbc;
    }

    /** 主动搭话扫描（P3 定时迁移：由 scheduler 系统任务 LLM_PROACTIVE 每分钟触发）。 */
    public void scan() {
        LlmConfig cfg = configStore.get();
        if (!cfg.isProactiveEnabled() || cfg.getProactiveDailyLimit() <= 0) return;
        if (!inTimeWindow(cfg)) return;
        if (senders.isEmpty()) return;
        int acted = 0;
        for (String[] group : tracker.activeGroups()) {
            String botKey = group[0];
            String groupId = group[1];
            try {
                if (canAct(cfg, botKey, groupId)) {
                    act(cfg, botKey, groupId);
                    acted++;
                }
            } catch (Exception e) {
                log.warn("[LLM] 主动搭话失败: bot={} group={} err={}", botKey, groupId, e.getMessage());
            }
        }
        if (acted > 0) log.info("[LLM] 主动搭话扫描完成，本次主动 {} 群", acted);
    }

    /** 供 API 测试：对指定群立即触发一次主动搭话（返回触发类型或 null）。 */
    public String proactiveOnce(String botKey, String groupId) {
        LlmConfig cfg = configStore.get();
        if (!cfg.isProactiveEnabled()) {
            log.warn("[LLM] 主动搭话测试：开关未开启");
            return null;
        }
        if (senders.isEmpty()) return null;
        return act(cfg, botKey, groupId);
    }

    // ════════════ 判定 ════════════

    private boolean canAct(LlmConfig cfg, String botKey, String groupId) {
        if (!tracker.isIdle(botKey, groupId, cfg.getProactiveIdleMinutes() * 60L)) return false;
        if (!tracker.hasActivityToday(botKey, groupId)) return false;
        if (todayCount(botKey, groupId) >= cfg.getProactiveDailyLimit()) return false;
        if (lastActedAt(botKey, groupId) > 0
                && (System.currentTimeMillis() / 1000 - lastActedAt(botKey, groupId)) < cfg.getProactiveCooldownMinutes() * 60L) {
            return false;
        }
        if (!guard.withinTokenBudget(botKey, cfg.getDailyTokenLimit())) return false;
        if (!quotaService.allowGroup(botKey, groupId)) return false;
        return true;
    }

    private boolean inTimeWindow(LlmConfig cfg) {
        LocalTime now = LocalTime.now();
        LocalTime start = parseTime(cfg.getProactiveTimeStart(), LocalTime.of(9, 0));
        LocalTime end = parseTime(cfg.getProactiveTimeEnd(), LocalTime.of(22, 0));
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        // 跨天窗口（如 22:00-09:00）
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private static LocalTime parseTime(String s, LocalTime def) {
        try {
            if (s == null || s.isBlank()) return def;
            String[] p = s.trim().split(":");
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return def;
        }
    }

    // ════════════ 触发 ════════════

    private String act(LlmConfig cfg, String botKey, String groupId) {
        Map.Entry<String, String> target = tracker.lastActiveUser(botKey, groupId);
        boolean ask = target != null && ThreadLocalRandom.current().nextBoolean();
        String type;
        String content;
        ProactiveSender sender = senders.get(0);
        if (ask) {
            type = "ASK";
            content = ProactiveTemplates.randomAsk(target.getKey(), target.getValue());
            if (!sender.sendText(botKey, groupId, content)) return null;
        } else {
            type = "TOPIC";
            content = ProactiveTemplates.randomTopic();
            if (!sender.sendMarkdown(botKey, groupId, content)) return null;
        }
        logProactive(botKey, groupId, target != null ? target.getKey() : null, type, content);
        log.info("[LLM] 主动搭话触发: bot={} group={} type={}", botKey, groupId, type);
        return type;
    }

    // ════════════ 日志 / 计数 ════════════

    private long todayCount(String botKey, String groupId) {
        try {
            Timestamp todayStart = Timestamp.from(
                    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant());
            Long v = jdbc.query("""
                SELECT COUNT(*) FROM xuanji_llm_proactive_log
                WHERE bot_key = ? AND group_id = ? AND created_at >= ?
                """, rs -> rs.next() ? rs.getLong(1) : 0, botKey, groupId, todayStart);
            return v != null ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long lastActedAt(String botKey, String groupId) {
        try {
            Timestamp v = jdbc.query("""
                SELECT MAX(created_at) FROM xuanji_llm_proactive_log
                WHERE bot_key = ? AND group_id = ?
                """, rs -> rs.next() ? rs.getTimestamp(1) : null, botKey, groupId);
            return v != null ? v.getTime() / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void logProactive(String botKey, String groupId, String userId, String type, String content) {
        try {
            jdbc.update("""
                INSERT INTO xuanji_llm_proactive_log (bot_key, group_id, user_id, action_type, content, created_at)
                VALUES (?,?,?,?,?, CURRENT_TIMESTAMP)
                """, botKey, groupId, userId, type, content);
        } catch (Exception e) {
            log.warn("[LLM] 主动记录失败: {}", e.getMessage());
        }
    }

    /** 主动记录列表（前端用量页/审计）。 */
    public List<Map<String, Object>> logs(String botKey, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, bot_key, group_id, user_id, action_type, content, created_at
            FROM xuanji_llm_proactive_log WHERE 1=1
            """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (botKey != null && !botKey.isBlank()) {
            sql.append(" AND bot_key = ?"); args.add(botKey);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 200));
        try {
            return jdbc.query(sql.toString(), (rs, i) -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("botKey", rs.getString("bot_key"));
                m.put("groupId", rs.getString("group_id"));
                m.put("userId", rs.getString("user_id"));
                m.put("type", rs.getString("action_type"));
                m.put("content", rs.getString("content"));
                m.put("createdAt", rs.getObject("created_at") != null ? String.valueOf(rs.getObject("created_at")) : null);
                return m;
            }, args.toArray());
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
