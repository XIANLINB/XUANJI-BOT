package dev.xuanji.console.service;

import dev.xuanji.console.service.ConsoleQueryService.BotRef;
import dev.xuanji.core.metric.QpsMeter;
import dev.xuanji.core.sender.BotPushSender;
import dev.xuanji.core.storage.ConnectionStatusProvider;
import dev.xuanji.core.storage.HealthMetricProvider;
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
 * 预警中心：周期性检查框架运行指标（QPS / 消息量 / 事件数 / 出站失败率 / CPU / 内存 /
 * 磁盘 / JVM / 连接状态 / 黑名单 / 慢阶段 / 去重命中 / 插件超时），命中后调用单聊接口
 * 给该 bot 配置的预警用户发送通知，并写入告警记录。
 *
 * <p>每个指标（规则）可独立开关 + 设置阈值，配置存 {@code xuanji_alert_config.rules}
 * （JSON），默认全开启、使用内置默认阈值。同 bot 同指标 30 分钟内只告警一次（防刷屏）。
 */
@Slf4j
@Service
public class AlertService {

    private static final long CHECK_WINDOW_SEC = 60L;

    private final JdbcTemplate jdbc;
    private final SystemInfoService systemInfoService;
    private final ConsoleQueryService queryService;
    private final List<BotPushSender> senders;
    private final List<ConnectionStatusProvider> connProviders;
    private final List<HealthMetricProvider> healthProviders;
    private final dev.xuanji.core.pipeline.BotPipeline botPipeline;
    private final dev.xuanji.core.command.CommandRegistry commandRegistry;
    private final dev.xuanji.core.config.ConfigService configService;
    private final dev.xuanji.core.plugin.XuanjiPluginManager pluginManager;

    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBlacklistCount = new ConcurrentHashMap<>();
    // 累计计数器（slow-stage / dedup-hit / rate-limit / ws-reconnect / job-fail）上次检查值，用于算「近 1 分钟增量」，
    // 避免用累计值 > 阈值导致一旦累计超过就永远触发
    private final Map<String, Long> lastCounters = new ConcurrentHashMap<>();

    // ═══════════════════ 全局可配置项（xuanji_config 键） ═══════════════════
    /** 检查间隔（毫秒），默认 60 秒。配置键 alert.check_interval_ms。 */
    public static final String CFG_CHECK_INTERVAL = "alert.check_interval_ms";
    /** 同 bot 同指标冷却时间（分钟），默认 30 分钟。配置键 alert.cooldown_minutes。 */
    public static final String CFG_COOLDOWN_MINUTES = "alert.cooldown_minutes";

    // ═══════════════════ 规则定义（key / 名称 / 单位 / 默认阈值 / 默认开启 / 级别） ═══════════════════
    public record RuleDef(String key, String name, String unit, double defThreshold, boolean defEnabled, String level) {}

    public static final List<RuleDef> RULE_DEFS = List.of(
            new RuleDef("qps", "QPS 突增", "条/秒", 20, true, "framework"),
            new RuleDef("msg-surge", "消息量突增", "倍", 3, true, "framework"),
            new RuleDef("event-surge", "事件数突增", "倍", 3, true, "framework"),
            new RuleDef("outbound-fail", "出站失败率", "%", 30, true, "framework"),
            new RuleDef("cpu", "CPU 使用率", "%", 85, true, "framework"),
            new RuleDef("mem", "内存使用率", "%", 90, true, "framework"),
            new RuleDef("disk", "磁盘使用率", "%", 90, true, "framework"),
            new RuleDef("jvm", "JVM 堆使用率", "%", 85, true, "framework"),
            new RuleDef("conn-down", "连接异常", "—", 0, true, "bot"),
            new RuleDef("blacklist", "黑名单激增", "条/分钟", 10, true, "bot"),
            new RuleDef("slow-stage", "Pipeline 慢阶段", "次/分钟", 1, true, "framework"),
            new RuleDef("dedup-hit", "去重命中率高", "次/分钟", 30, true, "framework"),
            new RuleDef("rate-limit", "命令限流命中", "次/分钟", 30, true, "framework"),
            new RuleDef("ws-reconnect", "WS 重连次数", "次/分钟", 3, true, "bot"),
            new RuleDef("ws-heartbeat", "WS 心跳超时", "次", 2, true, "bot"),
            new RuleDef("qqapi-circuit", "QQ API 熔断", "—", 0, true, "framework"),
            new RuleDef("pool-queue", "线程池排队积压", "条", 100, true, "framework"),
            new RuleDef("plugin-error", "插件加载错误", "个", 1, true, "framework"),
            new RuleDef("job-fail", "定时任务失败", "次/分钟", 3, true, "framework")
    );

    public AlertService(JdbcTemplate jdbc, SystemInfoService systemInfoService,
                        ConsoleQueryService queryService, List<BotPushSender> senders,
                        List<ConnectionStatusProvider> connProviders,
                        List<HealthMetricProvider> healthProviders,
                        dev.xuanji.core.pipeline.BotPipeline botPipeline,
                        dev.xuanji.core.command.CommandRegistry commandRegistry,
                        dev.xuanji.core.config.ConfigService configService,
                        dev.xuanji.core.plugin.XuanjiPluginManager pluginManager) {
        this.jdbc = jdbc;
        this.systemInfoService = systemInfoService;
        this.queryService = queryService;
        this.senders = senders;
        this.connProviders = connProviders;
        this.healthProviders = healthProviders;
        this.botPipeline = botPipeline;
        this.commandRegistry = commandRegistry;
        this.configService = configService;
        this.pluginManager = pluginManager;
    }

    // ═══════════════════ 配置 ═══════════════════

    public List<Map<String, Object>> listConfigs() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM xuanji_alert_config");
        // 合并未配置的 bot（默认关闭 + 规则默认全开）
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) byKey.put(String.valueOf(r.get("BOT_KEY")), camel(r));
        for (BotRef ref : queryService.botRefs()) {
            byKey.computeIfAbsent(ref.instanceId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("botKey", k);
                m.put("enabled", false);
                m.put("alertUserId", "");
                m.put("rules", "{}");
                return m;
            });
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : byKey.values()) {
            // 补充机器人名称（xuanji_bot_setting EAV）
            String bk = String.valueOf(m.get("botKey"));
            for (BotRef ref : queryService.botRefs()) {
                if (ref.instanceId().equals(bk)) {
                    m.put("botName", ref.botName() == null || ref.botName().isBlank() ? bk : ref.botName());
                    break;
                }
            }
            m.put("rules", parseRules(String.valueOf(m.getOrDefault("rules", ""))));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> saveConfig(String botKey, boolean enabled, String alertUserId,
                                          Map<String, Object> rules) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM xuanji_alert_config WHERE bot_key=?", botKey);
        long now = System.currentTimeMillis() / 1000;
        String rulesJson = rules == null ? "" : toJson(rules);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO xuanji_alert_config (bot_key, enabled, alert_user_id, rules, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                    botKey, enabled, alertUserId == null ? "" : alertUserId, rulesJson, now, now);
        } else {
            jdbc.update("UPDATE xuanji_alert_config SET enabled=?, alert_user_id=?, rules=?, updated_at=? WHERE bot_key=?",
                    enabled, alertUserId == null ? "" : alertUserId, rulesJson, now, botKey);
        }
        log.info("[Alert] 预警配置已保存: bot={} enabled={} user={} rules={}", botKey, enabled, alertUserId, rulesJson);
        return Map.of("status", "ok");
    }

    // ═══════════════════ 规则解析 ═══════════════════

    /** 解析 rules JSON → {key: {enabled, threshold}}；缺失项用默认值补全。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseRules(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = new LinkedHashMap<>();
        if (json != null && !json.isBlank()) {
            try {
                raw = new tools.jackson.databind.ObjectMapper().readValue(json, Map.class);
            } catch (Exception e) {
                raw = new LinkedHashMap<>();
            }
        }
        for (RuleDef def : RULE_DEFS) {
            Object r = raw.get(def.key());
            if (r instanceof Map<?, ?> rm) {
                boolean enabled = rm.get("enabled") instanceof Boolean b ? b : def.defEnabled();
                double threshold = rm.get("threshold") instanceof Number n ? n.doubleValue() : def.defThreshold();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("enabled", enabled);
                item.put("threshold", threshold);
                result.put(def.key(), item);
            } else {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("enabled", def.defEnabled());
                item.put("threshold", def.defThreshold());
                result.put(def.key(), item);
            }
        }
        return result;
    }

    /** 某规则是否启用（默认开）。 */
    private boolean ruleEnabled(Map<String, Object> rules, String key) {
        Object r = rules.get(key);
        if (r instanceof Map<?, ?> rm && rm.get("enabled") instanceof Boolean b) return b;
        return true;
    }

    /** 某规则阈值（默认值兜底）。 */
    private double ruleThreshold(Map<String, Object> rules, String key, double def) {
        Object r = rules.get(key);
        if (r instanceof Map<?, ?> rm && rm.get("threshold") instanceof Number n) return n.doubleValue();
        return def;
    }

    private static double defThreshold(String key) {
        for (RuleDef d : RULE_DEFS) {
            if (d.key().equals(key)) return d.defThreshold();
        }
        return 0;
    }

    private static String toJson(Object o) {
        try {
            return new tools.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ═══════════════════ 检查循环 ═══════════════════

    /** 检查间隔（毫秒）：读 xuanji_config alert.check_interval_ms，默认 60s；钳制 10s~10min。 */
    public long checkIntervalMs() {
        return cfgLong(CFG_CHECK_INTERVAL, 60_000L, 10_000L, 600_000L);
    }

    /** 同指标冷却时间（毫秒）：读 xuanji_config alert.cooldown_minutes，默认 30 分钟；钳制 1~1440 分钟。 */
    public long cooldownMs() {
        long min = cfgLong(CFG_COOLDOWN_MINUTES, 30L, 1L, 1440L);
        return min * 60_000L;
    }

    private long cfgLong(String key, long def, long min, long max) {
        try {
            String v = configService.getGlobalConfig().get(key);
            if (v != null && !v.isBlank()) {
                long n = Long.parseLong(v.trim());
                if (n >= min && n <= max) return n;
            }
        } catch (Exception ignored) { }
        return def;
    }

    /** 上次实际执行检查的时间（毫秒），配合 tick 实现动态检查间隔（配置改动无需重启）。 */
    private final java.util.concurrent.atomic.AtomicLong lastCheckRun = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 检查循环：固定 10 秒 tick，内部按配置的检查间隔决定是否真正执行。
     * 用 tick + 内部间隔判断而非 @Scheduled 动态 SpEL，避免 bean 创建期循环依赖，
     * 且改配置（alert.check_interval_ms）后无需重启立即生效。
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 30_000)
    public void check() {
        long interval = checkIntervalMs();
        long now = System.currentTimeMillis();
        long last = lastCheckRun.get();
        if (last != 0 && now - last < interval) return; // 未到配置的检查间隔
        if (!lastCheckRun.compareAndSet(last, now)) return; // 并发兜底
        try {
            List<Map<String, Object>> configs = jdbc.queryForList(
                    "SELECT * FROM xuanji_alert_config WHERE enabled=TRUE AND alert_user_id<>''");
            if (configs.isEmpty()) return;
            for (Map<String, Object> cfg : configs) {
                String botKey = String.valueOf(cfg.get("BOT_KEY"));
                String alertUserId = String.valueOf(cfg.get("ALERT_USER_ID"));
                Map<String, Object> rules = parseRules(String.valueOf(cfg.getOrDefault("RULES", "")));
                try {
                    checkBot(botKey, alertUserId, rules);
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

    private void checkBot(String botKey, String alertUserId, Map<String, Object> rules) {
        List<String> alerts = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - CHECK_WINDOW_SEC;

        // 1. QPS 突增
        if (ruleEnabled(rules, "qps")) {
            double t = ruleThreshold(rules, "qps", defThreshold("qps"));
            double qps = QpsMeter.avg(60);
            if (qps > t) {
                alerts.add(String.format("QPS 突增：近 60 秒平均 %.1f 条/秒（阈值 %.0f）", qps, t));
            }
        }

        // 2/3. 消息量 / 事件数 时段突增（当前 5 分钟 vs 之前 60 分钟均值 ×N）
        // 判定：5 分钟量 > max(绝对下限, 1小时均值/12 × 倍数)；且必须有历史基线（避免启动初期误报）
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
        if (ruleEnabled(rules, "msg-surge")) {
            double mul = ruleThreshold(rules, "msg-surge", defThreshold("msg-surge"));
            double base = (msgH / 12.0) * mul;
            if (msg5 > 30 && msgH > 10 && msg5 > base) {
                alerts.add(String.format("消息量突增：近 5 分钟 %d 条，超过近 1 小时均值×%.0f 基线 %.0f 条", msg5, mul, base));
            }
        }
        if (ruleEnabled(rules, "event-surge")) {
            double mul = ruleThreshold(rules, "event-surge", defThreshold("event-surge"));
            double base = (evtH / 12.0) * mul;
            if (evt5 > 20 && evtH > 10 && evt5 > base) {
                alerts.add(String.format("事件数突增：近 5 分钟 %d 个事件，超过近 1 小时均值×%.0f 基线 %.0f 个", evt5, mul, base));
            }
        }

        // 4. 出站失败率（定时任务近 50 条执行日志 FAIL 占比）
        if (ruleEnabled(rules, "outbound-fail")) {
            double t = ruleThreshold(rules, "outbound-fail", defThreshold("outbound-fail"));
            double failRate = outboundFailRate();
            if (failRate > t) {
                alerts.add(String.format("出站失败率上升：定时任务近 50 次执行失败率 %.0f%%（阈值 %.0f%%）", failRate, t));
            }
        }

        // 5/6/7/8. 机器资源超限（CPU / 内存 / 磁盘 / JVM 各自独立规则）
        Map<String, Object> sys = systemInfoService.snapshot();
        double cpu = num(sys.get("cpuLoad"));
        double mem = num(sys.get("memRatio"));
        double disk = num(sys.get("diskRatio"));
        double jvm = num(sys.get("jvmRatio"));
        if (ruleEnabled(rules, "cpu") && cpu > ruleThreshold(rules, "cpu", defThreshold("cpu"))) {
            alerts.add(String.format("CPU 使用率超限：%.1f%%（阈值 %.0f%%）", cpu, ruleThreshold(rules, "cpu", defThreshold("cpu"))));
        }
        if (ruleEnabled(rules, "mem") && mem > ruleThreshold(rules, "mem", defThreshold("mem"))) {
            alerts.add(String.format("内存使用率超限：%.1f%%（阈值 %.0f%%）", mem, ruleThreshold(rules, "mem", defThreshold("mem"))));
        }
        if (ruleEnabled(rules, "disk") && disk > ruleThreshold(rules, "disk", defThreshold("disk"))) {
            alerts.add(String.format("磁盘使用率超限：%.1f%%（阈值 %.0f%%）", disk, ruleThreshold(rules, "disk", defThreshold("disk"))));
        }
        if (ruleEnabled(rules, "jvm") && jvm > ruleThreshold(rules, "jvm", defThreshold("jvm"))) {
            alerts.add(String.format("JVM 堆使用率超限：%.1f%%（阈值 %.0f%%）", jvm, ruleThreshold(rules, "jvm", defThreshold("jvm"))));
        }

        // 9. 连接异常（修复：sessions 字段是 robotId 而非 botKey）
        if (ruleEnabled(rules, "conn-down")) {
            String connState = connectionState(botKey);
            if (connState != null && !connState.isEmpty()) {
                alerts.add("连接异常：" + connState);
            }
        }

        // 10. 黑名单激增（两次检查间新增条数）
        if (ruleEnabled(rules, "blacklist")) {
            double t = ruleThreshold(rules, "blacklist", defThreshold("blacklist"));
            Long last = lastBlacklistCount.get(botKey);
            long nowCnt = blacklistCount(botKey);
            if (last != null && nowCnt - last > t) {
                alerts.add(String.format("黑名单激增：近 1 分钟新增 %d 条（阈值 %.0f）", nowCnt - last, t));
            }
            lastBlacklistCount.put(botKey, nowCnt);
        }

        // 11. Pipeline 慢阶段（累计计数 → 近 1 分钟增量）
        if (ruleEnabled(rules, "slow-stage")) {
            double t = ruleThreshold(rules, "slow-stage", defThreshold("slow-stage"));
            try {
                Map<String, Object> slow = botPipeline.getSlowStageCounts();
                long total = 0;
                for (Object v : slow.values()) {
                    if (v instanceof Number n) total += n.longValue();
                }
                long delta = deltaCounter("slow-stage:" + botKey, total);
                if (delta > t) {
                    alerts.add(String.format("Pipeline 慢阶段：近 1 分钟超 100ms 阶段新增 %d 次（阈值 %.0f）", delta, t));
                }
            } catch (Exception ignored) { }
        }

        // 12. 去重命中率高（累计命中 → 近 1 分钟增量）
        if (ruleEnabled(rules, "dedup-hit")) {
            double t = ruleThreshold(rules, "dedup-hit", defThreshold("dedup-hit"));
            try {
                Map<String, Object> dedup = dedupStats();
                long hits = dedup.get("dbDedupSuccess") instanceof Number n ? n.longValue() : 0;
                long local = dedup.get("localFallbackCount") instanceof Number n2 ? n2.longValue() : 0;
                long dup = hits + local;
                long delta = deltaCounter("dedup-hit:" + botKey, dup);
                if (delta > t) {
                    alerts.add(String.format("去重命中率高：近 1 分钟重复事件新增 %d 次（阈值 %.0f）", delta, t));
                }
            } catch (Exception ignored) { }
        }

        // 13. 命令限流命中（累计命中 → 近 1 分钟增量）
        if (ruleEnabled(rules, "rate-limit")) {
            double t = ruleThreshold(rules, "rate-limit", defThreshold("rate-limit"));
            try {
                Map<String, Object> plugin = pluginStats();
                long hits = plugin.get("rateLimitHits") instanceof Number n ? n.longValue() : 0;
                long delta = deltaCounter("rate-limit:" + botKey, hits);
                if (delta > t) {
                    alerts.add(String.format("命令限流命中：近 1 分钟被限流 %d 次（阈值 %.0f）", delta, t));
                }
            } catch (Exception ignored) { }
        }

        // 14. WS 重连次数（累计 → 近 1 分钟增量，Bot 级：只统计该 bot 的会话）
        if (ruleEnabled(rules, "ws-reconnect")) {
            double t = ruleThreshold(rules, "ws-reconnect", defThreshold("ws-reconnect"));
            try {
                long reconnects = 0;
                for (ConnectionStatusProvider p : connProviders) {
                    Object sessions = p.connections().get("sessions");
                    if (!(sessions instanceof List<?> list)) continue;
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> m)) continue;
                        Object rid = m.get("robotId");
                        if (rid == null) rid = m.get("key");
                        if (rid == null || !String.valueOf(rid).equals(botKey)) continue;
                        reconnects = m.get("totalReconnects") instanceof Number n ? n.longValue() : 0;
                        break;
                    }
                }
                long delta = deltaCounter("ws-reconnect:" + botKey, reconnects);
                if (delta > t) {
                    alerts.add(String.format("WS 重连频繁：近 1 分钟重连 %d 次（阈值 %.0f）", delta, t));
                }
            } catch (Exception ignored) { }
        }

        // 15. WS 心跳超时（Bot 级：lastHeartbeatAck 距今超过 90 秒视为心跳丢失）
        if (ruleEnabled(rules, "ws-heartbeat")) {
            double t = ruleThreshold(rules, "ws-heartbeat", defThreshold("ws-heartbeat"));
            try {
                long nowMs = System.currentTimeMillis();
                long stale = 0;
                for (ConnectionStatusProvider p : connProviders) {
                    Object sessions = p.connections().get("sessions");
                    if (!(sessions instanceof List<?> list)) continue;
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> m)) continue;
                        Object rid = m.get("robotId");
                        if (rid == null) rid = m.get("key");
                        if (rid == null || !String.valueOf(rid).equals(botKey)) continue;
                        // getAllStatus 未暴露 lastHeartbeatAck，这里用 state 是否为重连态兜底
                        String st = String.valueOf(m.get("state"));
                        if ("RECONNECTING".equalsIgnoreCase(st) || "CONNECTING".equalsIgnoreCase(st)) stale++;
                        break;
                    }
                }
                if (stale > 0 && stale >= t) {
                    alerts.add(String.format("WS 心跳异常：%d 个会话处于重连/连接中（阈值 %.0f）", stale, t));
                }
            } catch (Exception ignored) { }
        }

        // 16. QQ API 熔断（框架级快照：任一平台熔断打开）
        if (ruleEnabled(rules, "qqapi-circuit")) {
            try {
                for (HealthMetricProvider p : healthProviders) {
                    Object cb = p.healthMetrics().get("circuitBreaker");
                    if (!(cb instanceof Map<?, ?> cbm)) continue;
                    boolean open = cbm.get("open") instanceof Boolean b ? b : false;
                    if (open) {
                        alerts.add("QQ API 熔断打开：连续失败触发熔断，接口请求将快速失败");
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }

        // 17. 线程池排队积压（框架级快照：任一池 queueSize 超阈值）
        if (ruleEnabled(rules, "pool-queue")) {
            double t = ruleThreshold(rules, "pool-queue", defThreshold("pool-queue"));
            try {
                java.util.List<Map<String, Object>> pools = dev.xuanji.core.concurrent.ThreadPoolRegistry.snapshot();
                for (Map<String, Object> pool : pools) {
                    long q = pool.get("queueSize") instanceof Number n ? n.longValue() : 0;
                    String name = String.valueOf(pool.getOrDefault("name", "?"));
                    if (q > t) {
                        alerts.add(String.format("线程池排队积压：%s 排队 %d 条（阈值 %.0f）", name, q, t));
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }

        // 18. 插件加载错误（框架级快照：任一插件 state=ERROR）
        if (ruleEnabled(rules, "plugin-error")) {
            double t = ruleThreshold(rules, "plugin-error", defThreshold("plugin-error"));
            try {
                var plugins = pluginManager.listPlugins();
                long errCnt = plugins.stream().filter(p -> "ERROR".equalsIgnoreCase(p.state())).count();
                if (errCnt >= t) {
                    var errNames = plugins.stream().filter(p -> "ERROR".equalsIgnoreCase(p.state()))
                            .map(dev.xuanji.core.plugin.XuanjiPluginManager.PluginInfo::id).toList();
                    alerts.add(String.format("插件加载错误：%d 个插件处于 ERROR 状态（%s）", errCnt, String.join(", ", errNames)));
                }
            } catch (Exception ignored) { }
        }

        // 19. 定时任务失败（累计 fail_count → 近 1 分钟增量；表可能不存在则跳过）
        if (ruleEnabled(rules, "job-fail")) {
            double t = ruleThreshold(rules, "job-fail", defThreshold("job-fail"));
            try {
                Long fails = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(fail_count),0) FROM xuanji_scheduler_job", Long.class);
                long failTotal = fails == null ? 0 : fails;
                long delta = deltaCounter("job-fail:" + botKey, failTotal);
                if (delta > t) {
                    alerts.add(String.format("定时任务失败：近 1 分钟失败 %d 次（阈值 %.0f）", delta, t));
                }
            } catch (Exception ignored) { /* scheduler 表未初始化时跳过 */ }
        }

        for (String alert : alerts) {
            fire(botKey, ruleOf(alert), alert, alertUserId);
        }
    }

    /** 累计计数器增量：返回 当前值 - 上次记录值，并更新记录。首次调用返回 0。 */
    private long deltaCounter(String key, long current) {
        Long last = lastCounters.get(key);
        lastCounters.put(key, current);
        if (last == null) return 0;
        long delta = current - last;
        return delta > 0 ? delta : 0;
    }

    private void fire(String botKey, String rule, String message, String alertUserId) {
        String key = botKey + ":" + rule;
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(key);
        long cooldown = cooldownMs();
        if (last != null && now - last < cooldown) return; // 冷却窗口内不重复通知
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

    /** 去重统计（复用 BotPipeline）。 */
    private Map<String, Object> dedupStats() {
        try {
            return botPipeline.getDedupStats();
        } catch (Exception ignored) { }
        return Map.of();
    }

    /** 插件统计（复用 CommandRegistry）。 */
    private Map<String, Object> pluginStats() {
        try {
            return commandRegistry.getStats();
        } catch (Exception ignored) { }
        return Map.of();
    }

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

    /**
     * 查某 bot 连接状态；正常返回 null，异常返回描述。
     *
     * <p><b>修复历史误报</b>：QqBotWsManager.getAllStatus() 返回的 session 字段是
     * {@code robotId} / {@code key}，不是 botKey/bot_key——旧实现匹配不上导致
     * 「未找到连接会话」误报。现按 robotId（或 key）匹配；若该 bot 在 sessions 中
     * 不存在则视为无连接（不告警，避免启动初期/未启用 ws 时误报）。
     */
    private String connectionState(String botKey) {
        for (ConnectionStatusProvider p : connProviders) {
            try {
                Object sessions = p.connections().get("sessions");
                if (!(sessions instanceof List<?> list)) continue;
                if (list.isEmpty()) continue; // 无会话不误报
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    // 兼容三种字段名：robotId（ws manager）/ key（ws manager）/ botKey/bot_key
                    Object rid = m.get("robotId");
                    if (rid == null) rid = m.get("key");
                    if (rid == null) rid = m.get("botKey");
                    if (rid == null) rid = m.get("bot_key");
                    if (rid == null || !String.valueOf(rid).equals(botKey)) continue;
                    String st = String.valueOf(m.get("state"));
                    if ("READY".equalsIgnoreCase(st) || "CONNECTED".equalsIgnoreCase(st)) return null;
                    return "WS 状态=" + st;
                }
                // 遍历完未匹配到该 bot → 无该 bot 会话，不告警
                return null;
            } catch (Exception e) {
                log.debug("[Alert] 连接状态查询失败: {}", e.getMessage());
            }
        }
        return null;
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
        if (message.contains("CPU")) return "cpu";
        if (message.contains("内存")) return "mem";
        if (message.contains("磁盘")) return "disk";
        if (message.contains("JVM")) return "jvm";
        if (message.contains("连接")) return "conn-down";
        if (message.contains("黑名单")) return "blacklist";
        if (message.contains("慢阶段")) return "slow-stage";
        if (message.contains("去重")) return "dedup-hit";
        if (message.contains("限流")) return "rate-limit";
        if (message.contains("WS 重连")) return "ws-reconnect";
        if (message.contains("心跳")) return "ws-heartbeat";
        if (message.contains("API 熔断")) return "qqapi-circuit";
        if (message.contains("线程池")) return "pool-queue";
        if (message.contains("插件")) return "plugin-error";
        if (message.contains("定时任务")) return "job-fail";
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
