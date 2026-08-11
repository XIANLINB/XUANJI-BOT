package XuanJi.console.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行健康异常记录 — 健康页「异常历史」数据源。
 *
 * <p>落框架库 {@code xuanji_health_alarm} 表。同一 (type, 内容指纹) 在去重窗口内只记一条，
 * 避免 30s 轮询把同一个持续异常刷屏。
 */
@Slf4j
@Service
public class HealthAlarmService {

    /** 同类型同内容去重窗口（毫秒），窗口内不重复记录。 */
    private static final long DEDUP_WINDOW_MS = 5 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    /** type -> (fingerprint -> lastRecordMs) */
    private final Map<String, Map<String, Long>> lastSeen = new ConcurrentHashMap<>();

    public HealthAlarmService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录一条异常（自动 5 分钟去重）。level: INFO / WARN / ERROR。 */
    public void record(String type, String level, String message) {
        try {
            String fp = fingerprint(message);
            long now = System.currentTimeMillis();
            Map<String, Long> byType = lastSeen.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
            Long last = byType.get(fp);
            if (last != null && now - last < DEDUP_WINDOW_MS) {
                return; // 窗口内已记录过相同异常
            }
            byType.put(fp, now);
            jdbc.update("""
                INSERT INTO xuanji_health_alarm (type, level, message, create_time)
                VALUES (?, ?, ?, ?)
            """, type, level == null ? "WARN" : level, truncate(message), now / 1000L);
        } catch (Exception e) {
            log.debug("[HealthAlarm] 写入失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 最近 limit 条异常（倒序）。 */
    public List<Map<String, Object>> list(int limit) {
        try {
            return jdbc.queryForList(
                    "SELECT id, type, level, message, create_time FROM xuanji_health_alarm ORDER BY id DESC LIMIT ?",
                    Math.min(Math.max(limit, 1), 500));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String fingerprint(String s) {
        if (s == null || s.isBlank()) return "_";
        String t = s.trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 512 ? s.substring(0, 512) : s;
    }
}
