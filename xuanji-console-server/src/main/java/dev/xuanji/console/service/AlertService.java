package dev.xuanji.console.service;

import dev.xuanji.console.service.ConsoleQueryService.BotRef;
import dev.xuanji.core.metric.QpsMeter;
import dev.xuanji.core.sender.BotPushSender;
import dev.xuanji.core.storage.ConnectionStatusProvider;
import dev.xuanji.core.storage.PlatformDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预警中心：周期性检查框架运行指标（QPS 突增 / 消息量时段突增 / 事件突增 /
 * 出站失败率 / 连接异常 / 机器资源超限 / 黑名单激增），命中后调用单聊接口
 * 给该 bot 配置的预警用户发送通知，并写入告警记录。
 *
 * <p>同 bot 同指标 30 分钟内只告警一次（防刷屏）；阈值采用默认值 + 全局配置可覆盖
 * （xuanji_config 键 {@code alert.threshold.*}）。
 */
@Slf4j
@Service
public class AlertService {

    private static final long ALERT_COOLDOWN_MS = 30 * 60 * 1000L;
    private static final long CHECK_WINDOW_SEC = 60L;

    private final JdbcTemplate jdbc;
    private final SystemInfoService systemInfoService;
    private final ConsoleQueryService queryService;
    private final List<BotPushSender> senders;
    private final List<ConnectionStatusProvider> connProviders;

    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBlacklistCount = new ConcurrentHashMap<>();

    public AlertService(JdbcTemplate jdbc, SystemInfoService systemInfoService,
                        ConsoleQueryService queryService, List<BotPushSender> senders,
                        List<ConnectionStatusProvider> connProviders) {
        this.jdbc = jdbc;
        this.systemInfoService = systemInfoService;
        this.queryService = queryService;
        this.senders = senders;
        this.connProviders = connProviders;
    }

    // ═══════════════════ 配置 ═══════════════════

    public List<Map<String, Object>> listConfigs() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM xuanji_alert_config");
        // 合并未配置的 bot（默认关闭）
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) byKey.put(String.valueOf(r.get("BOT_KEY")), camel(r));
        for (BotRef ref : queryService.botRefs()) {
            byKey.computeIfAbsent(ref.instanceId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("botKey", k);
                m.put("enabled", false);
                m.put("alertUserId", "");
                return m;
            });
        }
        return new ArrayList<>(byKey.values());
    }

    public Map<String, Object> saveConfig(String botKey, boolean enabled, String alertUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM xuanji_alert_config WHERE bot_key=?", botKey);
        long now = System.currentTimeMillis() / 1000;
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO xuanji_alert_config (bot_key, enabled, alert_user_id, created_at, updated_at) VALUES (?,?,?,?,?)",
                    botKey, enabled, alertUserId == null ? "" : alertUserId, now, now);
        } else {
            jdbc.update("UPDATE xuanji_alert_config SET enabled=?, alert_user_id=?, updated_at=? WHERE bot_key=?",
                    enabled, alertUserId == null ? "" : alertUserId, now, botKey);
        }
        log.info("[Alert] 预警配置已保存: bot={} enabled={} user={}", botKey, enabled, alertUserId);
        return Map.of("status", "ok");
    }

    // ═══════════════════ 检查循环 ═══════════════════

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void check() {
        try {
            List<Map<String, Object>> configs = jdbc.queryForList(
                    "SELECT * FROM xuanji_alert_config WHERE enabled=TRUE AND alert_user_id<>''");
            if (configs.isEmpty()) return;
            for (Map<String, Object> cfg : configs) {
                String botKey = String.valueOf(cfg.get("BOT_KEY"));
                String alertUserId = String.valueOf(cfg.get("ALERT_USER_ID"));
                try {
                    checkBot(botKey, alertUserId);
                } catch (Exception e) {
                    log.warn("[Alert] bot={} 检查异常: {}", botKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Alert] 检查循环异常: {}", e.getMessage());
        }
    }

    /** 手动触发一次全部检查（前端「立即检查」）。 */
    public Map<String, Object> checkNow() {
        check();
        return Map.of("status", "ok", "msg", "检查完成（仅对已启用并配置预警用户的 bot）");
    }

    private void checkBot(String botKey, String alertUserId) {
        List<String> alerts = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - CHECK_WINDOW_SEC;

        // 1. QPS 突增
        double qps = QpsMeter.avg(60);
        if (qps > threshold("qps", 20)) {
            alerts.add(String.format("QPS 突增：近 60 秒平均 %.1f 条/秒（阈值 %s）", qps, threshold("qps", 20)));
        }

        // 2/3. 消息量 / 事件数 时段突增（当前 5 分钟 vs 之前 60 分钟均值 ×3）
        long fiveMinAgo = now - 300;
        long hourAgo = now - 3600;
        long msg5 = 0, msgH = 0, evt5 = 0, evtH = 0;
        for (BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            msg5 += p.countMessagesSince(ref.instanceId(), "group", fiveMinAgo)
                    + p.countMessagesSince(ref.instanceId(), "c2c", fiveMinAgo);
            msgH += p.countMessagesSince(ref.instanceId(), "group", hourAgo)
                    + p.countMessagesSince(ref.instanceId(), "c2c", hourAgo);
            evt5 += p.countAllEventsSince(ref.instanceId(), fiveMinAgo);
            evtH += p.countAllEventsSince(ref.instanceId(), hourAgo);
        }
        if (msg5 > Math.max(30, (msgH / 12) * 3)) {
            alerts.add(String.format("消息量突增：近 5 分钟 %d 条，为近 1 小时均值 %d 条的 %d 倍", msg5, msgH / 12, msg5 * 12 / Math.max(1, msgH)));
        }
        if (evt5 > Math.max(20, (evtH / 12) * 3)) {
            alerts.add(String.format("事件数突增：近 5 分钟 %d 个事件，为近 1 小时均值 %d 条的 %d 倍", evt5, evtH / 12, evt5 * 12 / Math.max(1, evtH)));
        }

        // 4. 出站失败率（定时任务近 50 条执行日志 FAIL 占比）
        double failRate = outboundFailRate();
        if (failRate > threshold("outboundFail", 30)) {
            alerts.add(String.format("出站失败率上升：定时任务近 50 次执行失败率 %.0f%%（阈值 %s%%）", failRate, threshold("outboundFail", 30)));
        }

        // 5. 机器资源超限
        Map<String, Object> sys = systemInfoService.snapshot();
        double cpu = num(sys.get("cpuLoad"));
        double mem = num(sys.get("memRatio"));
        double disk = num(sys.get("diskRatio"));
        List<String> res = new ArrayList<>();
        if (cpu > threshold("cpu", 85)) res.add("CPU " + cpu + "%");
        if (mem > threshold("mem", 90)) res.add("内存 " + mem + "%");
        if (disk > threshold("disk", 90)) res.add("磁盘 " + disk + "%");
        if (!res.isEmpty()) alerts.add("机器资源超限：" + String.join("、", res));

        // 6. 连接异常
        String connState = connectionState(botKey);
        if (connState != null && !connState.isEmpty()) {
            alerts.add("连接异常：" + connState);
        }

        // 7. 黑名单激增（两次检查间新增条数）
        Long last = lastBlacklistCount.get(botKey);
        long nowCnt = blacklistCount(botKey);
        if (last != null && nowCnt - last > threshold("blacklist", 10)) {
            alerts.add(String.format("黑名单激增：近 1 分钟新增 %d 条（阈值 %s）", nowCnt - last, threshold("blacklist", 10)));
        }
        lastBlacklistCount.put(botKey, nowCnt);

        for (String alert : alerts) {
            fire(botKey, ruleOf(alert), alert, alertUserId);
        }
    }

    private void fire(String botKey, String rule, String message, String alertUserId) {
        String key = botKey + ":" + rule;
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(key);
        if (last != null && now - last < ALERT_COOLDOWN_MS) return; // 30 分钟去重
        lastAlert.put(key, now);

        jdbc.update("INSERT INTO xuanji_alert_record (bot_key, rule, message, create_time) VALUES (?,?,?,?)",
                botKey, rule, message, now / 1000);

        for (BotPushSender s : senders) {
            try {
                var receipt = s.push(botKey, "C2C", alertUserId,
                        "[璇玑预警] " + message + "（如需调整阈值请到控制台·预警中心）");
                log.info("[Alert] 已推送预警: bot={} rule={} user={} success={}", botKey, rule, alertUserId, receipt.success());
                return;
            } catch (Exception e) {
                log.warn("[Alert] 推送失败: bot={} error={}", botKey, e.getMessage());
            }
        }
        log.warn("[Alert] 无可用推送通道，预警仅入库: bot={} rule={}", botKey, rule);
    }

    // ═══════════════════ 指标采集 ═══════════════════

    private double outboundFailRate() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT status FROM (SELECT status FROM xuanji_scheduler_job_log ORDER BY id DESC LIMIT 50) t");
            if (rows.isEmpty()) return 0;
            long fail = rows.stream().filter(r -> "FAIL".equals(String.valueOf(r.get("STATUS")))).count();
            return fail * 100.0 / rows.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private long blacklistCount(String botKey) {
        try {
            Long v = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM xuanji_blacklist WHERE bot_key=?", Long.class, botKey);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 查某 bot 连接状态；正常返回 null，异常返回描述。 */
    private String connectionState(String botKey) {
        for (ConnectionStatusProvider p : connProviders) {
            try {
                Object sessions = p.connections().get("sessions");
                if (!(sessions instanceof List<?> list)) continue;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    Object key = m.get("botKey");
                    if (key == null) key = m.get("bot_key");
                    if (key == null || !String.valueOf(key).equals(botKey)) continue;
                    String st = String.valueOf(m.get("state"));
                    if ("READY".equalsIgnoreCase(st) || "CONNECTED".equalsIgnoreCase(st)) return null;
                    return "WS 状态=" + st;
                }
                return "未找到连接会话";
            } catch (Exception e) {
                log.debug("[Alert] 连接状态查询失败: {}", e.getMessage());
            }
        }
        return null;
    }

    private double threshold(String key, double def) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT value FROM xuanji_config WHERE k=?", "alert.threshold." + key);
            if (!rows.isEmpty()) {
                return Double.parseDouble(String.valueOf(rows.get(0).get("VALUE")));
            }
        } catch (Exception ignored) { }
        return def;
    }

    // ═══════════════════ 告警记录 ═══════════════════

    public List<Map<String, Object>> records(int limit) {
        return camelList(jdbc.queryForList(
                "SELECT * FROM xuanji_alert_record ORDER BY id DESC LIMIT ?",
                Math.min(Math.max(limit, 1), 200)));
    }

    private static String ruleOf(String message) {
        if (message.contains("QPS")) return "qps";
        if (message.contains("消息量")) return "msg-surge";
        if (message.contains("事件数")) return "event-surge";
        if (message.contains("失败率")) return "outbound-fail";
        if (message.contains("资源")) return "resource";
        if (message.contains("连接")) return "conn-down";
        if (message.contains("黑名单")) return "blacklist";
        return "unknown";
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            m.put(camelKey(e.getKey()), e.getValue());
        }
        return m;
    }

    private static List<Map<String, Object>> camelList(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(camel(r));
        return out;
    }

    private static String camelKey(String key) {
        String lower = key.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : lower.toCharArray()) {
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
