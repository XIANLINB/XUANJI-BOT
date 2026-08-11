package XuanJi.console.service;

import XuanJi.core.command.CommandRegistry;
import XuanJi.core.permission.PermissionService;
import XuanJi.core.pipeline.BotPipeline;
import XuanJi.core.storage.PlatformDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风控中心：命令限速 / 框架限流 / 消息去重 / 黑名单 的命中统计，
 * 以及各群风控状态（消息量 + 黑名单人数）与黑名单操作时间线。
 *
 * <p>数据来源：
 * <ul>
 *   <li>命令限速命中：{@link CommandRegistry#rateLimitHits()}（@Command rateLimit 拦截计数）</li>
 *   <li>框架级限流命中：{@link BotPipeline#getRateLimitStats()}（RateLimitStage 拦截计数）</li>
 *   <li>消息去重命中：{@link BotPipeline#getDedupStats()}（DB 命中 / 本地降级）</li>
 *   <li>黑名单：框架库 xuanji_blacklist（按群聚合）+ xuanji_blacklist_log（操作时间线）</li>
 *   <li>各群消息量：平台适配器 {@link PlatformDataProvider#groupRiskStats}</li>
 * </ul>
 */
@Slf4j
@Service
public class RiskService {

    private static final long DAY_SEC = 86400L;

    private final JdbcTemplate jdbc;
    private final ConsoleQueryService queryService;
    private final BotPipeline pipeline;
    private final CommandRegistry commandRegistry;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public RiskService(JdbcTemplate jdbc, ConsoleQueryService queryService,
                       BotPipeline pipeline, CommandRegistry commandRegistry,
                       PermissionService permissionService, AuditService auditService) {
        this.jdbc = jdbc;
        this.queryService = queryService;
        this.pipeline = pipeline;
        this.commandRegistry = commandRegistry;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    // ═══════════════════ 全局概览 ═══════════════════

    /** 全局风控命中概览：黑名单拦截 / 命令执行 / 限速 / 去重 / 黑名单总量与近期新增。 */
    public Map<String, Object> overview() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> m = new LinkedHashMap<>();

        // 黑名单拦截（WhitelistStage 计数）—— 真正"拦住谁"的第一手证据
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("blocks", num(pipeline.getWhitelistStats().get("blacklistBlocks")));
        m.put("block", block);

        // 命令执行（CommandRegistry 计数）
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("execCount", num(commandRegistry.getCommandStats().get("commandExecCount")));
        command.put("failCount", num(commandRegistry.getCommandStats().get("commandFailCount")));
        long exec = num(command.get("execCount"));
        long fail = num(command.get("failCount"));
        command.put("successRate", exec + fail == 0 ? 100.0
                : Math.round(exec * 10000.0 / (exec + fail)) / 100.0);
        m.put("command", command);

        // 限速：命令级 + 框架级
        Map<String, Object> rateLimit = new LinkedHashMap<>();
        rateLimit.put("commandHits", commandRegistry.rateLimitHits());
        rateLimit.put("stageHits", num(pipeline.getRateLimitStats().get("rateLimitHits")));
        m.put("rateLimit", rateLimit);

        // 去重：DB 跨实例命中 / 本地降级计数 / 去重表行数
        Map<String, Object> dedupStats = pipeline.getDedupStats();
        Map<String, Object> dedup = new LinkedHashMap<>();
        dedup.put("dbHits", num(dedupStats.get("dbDedupSuccess")));
        dedup.put("localHits", num(dedupStats.get("localFallbackCount")));
        dedup.put("rows", count("SELECT COUNT(*) FROM xuanji_dedup"));
        dedup.put("processed", num(dedupStats.get("processed")));
        m.put("dedup", dedup);

        // 黑名单
        Map<String, Object> blacklist = new LinkedHashMap<>();
        blacklist.put("total", count("SELECT COUNT(*) FROM xuanji_blacklist"));
        blacklist.put("globalTotal", count("SELECT COUNT(*) FROM xuanji_blacklist WHERE group_id=''"));
        blacklist.put("groupTotal", count("SELECT COUNT(*) FROM xuanji_blacklist WHERE group_id<>''"));
        blacklist.put("add24h", count("SELECT COUNT(*) FROM xuanji_blacklist_log WHERE action='ADD' AND create_time>=?",
                now - DAY_SEC));
        blacklist.put("add7d", count("SELECT COUNT(*) FROM xuanji_blacklist_log WHERE action='ADD' AND create_time>=?",
                now - 7 * DAY_SEC));
        blacklist.put("remove24h", count("SELECT COUNT(*) FROM xuanji_blacklist_log WHERE action='REMOVE' AND create_time>=?",
                now - DAY_SEC));
        m.put("blacklist", blacklist);

        // 登录审计：成功 / 失败（防爆破监控）
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("okTotal", count("SELECT COUNT(*) FROM xuanji_audit WHERE action='LOGIN_OK'"));
        login.put("failTotal", count("SELECT COUNT(*) FROM xuanji_audit WHERE action='LOGIN_FAIL'"));
        login.put("fail24h", count("SELECT COUNT(*) FROM xuanji_audit WHERE action='LOGIN_FAIL' AND create_time>=?",
                now - DAY_SEC));
        m.put("login", login);

        // 健康告警分布（xuanji_health_alarm 类型计数）
        Map<String, Object> healthAlarm = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT type, COUNT(*) AS CNT FROM xuanji_health_alarm GROUP BY type",
                    (java.sql.ResultSet rs) -> {
                        healthAlarm.put(String.valueOf(rs.getString("type")), rs.getLong("CNT"));
                    });
        } catch (Exception e) {
            log.debug("[Risk] 健康告警统计失败: {}", e.getMessage());
        }
        healthAlarm.put("total", num(healthAlarm.get("total")) == 0
                ? count("SELECT COUNT(*) FROM xuanji_health_alarm") : healthAlarm.get("total"));
        m.put("healthAlarm", healthAlarm);

        // 定时任务成功率（xuanji_scheduler_job）
        Map<String, Object> scheduler = new LinkedHashMap<>();
        try {
            var r = jdbc.queryForMap("""
                SELECT COUNT(*) AS TOTAL,
                       COALESCE(SUM(run_count),0) AS RUNS,
                       COALESCE(SUM(fail_count),0) AS FAILS
                FROM xuanji_scheduler_job
                """);
            long runs = num(r.get("RUNS")), fails = num(r.get("FAILS"));
            scheduler.put("jobTotal", num(r.get("TOTAL")));
            scheduler.put("runTotal", runs);
            scheduler.put("failTotal", fails);
            scheduler.put("successRate", runs + fails == 0 ? 100.0
                    : Math.round(runs * 10000.0 / (runs + fails)) / 100.0);
        } catch (Exception e) {
            scheduler.put("jobTotal", 0);
            scheduler.put("successRate", 100.0);
        }
        m.put("scheduler", scheduler);

        // 操作审计分布（按 action 计数，登录/改口令/机器人操作等）
        m.put("auditStats", auditService.actionStats());
        return m;
    }

    // ═══════════════════ 各群风控状态 ═══════════════════

    /**
     * 各群风控状态（跨所有 bot 聚合，近 7 天）：
     * gid/gname（缺失空串，前端兜底）/msgCnt/memberCnt/blackCnt/status。
     * status = risk（黑名单 ≥1 或消息量 ≥200）/watch（消息量 ≥50）/normal。
     */
    public List<Map<String, Object>> groups() {
        long now = System.currentTimeMillis() / 1000;
        long since = now - 7 * DAY_SEC;

        // 各 bot 平台库聚合：群消息量 + 群名 + 成员数
        Map<String, long[]> byGroup = new HashMap<>();       // gid -> [msgCnt, memberCnt]
        Map<String, String> names = new HashMap<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            try {
                for (Map<String, Object> row : p.groupRiskStats(ref.instanceId(), since, 200)) {
                    String gid = str(row.get("GID"), row.get("gid"));
                    if (gid == null || gid.isBlank()) continue;
                    long[] arr = byGroup.computeIfAbsent(gid, k -> new long[2]);
                    arr[0] += num(row.get("MSG_CNT"), row.get("msgCnt"));
                    arr[1] = Math.max(arr[1], num(row.get("MEMBER_CNT"), row.get("memberCnt")));
                    String name = str(row.get("GNAME"), row.get("gname"));
                    if (name != null && !name.isBlank()) names.putIfAbsent(gid, name);
                }
            } catch (Exception e) {
                log.debug("[Risk] 群风控聚合失败: bot={} err={}", ref.instanceId(), e.getMessage());
            }
        }

        // 框架库黑名单按群计数（group_id 空串 = 全局黑名单，不计入单群）
        Map<String, Long> blackByGroup = new HashMap<>();
        try {
            jdbc.query("SELECT group_id, COUNT(*) AS CNT FROM xuanji_blacklist WHERE group_id<>'' GROUP BY group_id",
                    (java.sql.ResultSet rs) -> {
                        String gid = rs.getString("group_id");
                        if (gid != null && !gid.isBlank()) blackByGroup.put(gid, rs.getLong("CNT"));
                    });
        } catch (Exception e) {
            log.debug("[Risk] 黑名单按群计数失败: {}", e.getMessage());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        byGroup.forEach((gid, arr) -> {
            long blackCnt = blackByGroup.getOrDefault(gid, 0L);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("gid", gid);
            r.put("gname", names.getOrDefault(gid, ""));
            r.put("msgCnt", arr[0]);
            r.put("memberCnt", arr[1]);
            r.put("blackCnt", blackCnt);
            r.put("status", blackCnt >= 1 || arr[0] >= 200 ? "risk"
                    : arr[0] >= 50 ? "watch" : "normal");
            out.add(r);
        });
        out.sort((a, b) -> Long.compare(num(b.get("msgCnt")), num(a.get("msgCnt"))));
        return out;
    }

    // ═══════════════════ 黑名单时间线 ═══════════════════

    /** 黑名单操作时间线（拉黑/解除留痕，按 bot 过滤，倒序）。 */
    public List<Map<String, Object>> blacklistTimeline(String botKey, int limit) {
        return permissionService.listBlacklistLog(botKey, limit);
    }

    // ═══════════════════ 工具 ═══════════════════

    private long count(String sql, Object... args) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long num(Object... vals) {
        for (Object v : vals) {
            if (v instanceof Number n) return n.longValue();
        }
        return 0;
    }

    private static String str(Object... vals) {
        for (Object v : vals) {
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }
}
