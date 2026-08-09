package dev.xuanji.console.service;

import dev.xuanji.core.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 性能模板推荐器 — 基于当前系统 CPU / 内存 / 磁盘 估算三档配置（经济 / 运动 / 性能）。
 *
 * <h3>三档设计目标</h3>
 * <ul>
 *   <li><b>经济</b>（eco）— 单台小机器（≤ 4 核 / 4 GB），稳定跑 1~2 个机器人；节奏宽松，资源占用低</li>
 *   <li><b>运动</b>（sport）— 单台中机器（4~8 核 / 8 GB），稳定跑 5~10 个机器人；节奏平衡，并发可控</li>
 *   <li><b>性能</b>（perf）— 单台大机器（8+ 核 / 16 GB+），稳定跑 20+ 个机器人；节奏紧凑，最大并发</li>
 * </ul>
 *
 * <h3>参数映射</h3>
 * 写入全局配置表（{@code xuanji_config}）的键：
 * <ul>
 *   <li>{@code sched_pool} → Spring @Scheduled 池大小（需重启）</li>
 *   <li>{@code ws_core / ws_max} → QqBotWsManager 连接池（需重启）</li>
 *   <li>{@code heartbeat_pool} → QqBotWsManager 心跳池（需重启）</li>
 *   <li>{@code hikari_main_max / hikari_log_max / hikari_instance_max} → HikariCP（需重启）</li>
 *   <li>{@code outbound.pace_ms} → 出站节奏（<b>运行时立即生效</b>）</li>
 *   <li>{@code out_threads_per_bot} → 出站池每 bot 线程数（BotOutboundExecutor 懒建，<b>下次触发时生效</b>）</li>
 *   <li>{@code bot_concurrency} → BotPipeline 每 bot 并发事件数（<b>下次触发时生效</b>）</li>
 *   <li>{@code tune.active_mode} → 当前模板标识</li>
 * </ul>
 *
 * <h3>估算公式</h3>
 * 单 bot 内存：JVM 实例 + H2 实例库（3 连接 + 文件句柄）+ 出站虚拟线程 ≈ 100~150 MB
 * 入站单 bot 吞吐：pipeline 串行经济 ~1 msg/s，运动 ~2 msg/s，性能 ~3 msg/s
 * 出站单 bot：经济 1.2 条/秒，运动 2.5 条/秒，性能 5 条/秒（受 pace 限制）
 */
@Slf4j
@Service
public class TuneAdvisor {

    private static final long MEM_MB = 1024L * 1024L;
    private static final int DEFAULT_CORES = 4;
    private static final int DEFAULT_MEM_GB = 8;

    private final SystemInfoService systemInfoService;
    private final ConfigService configService;

    public TuneAdvisor(SystemInfoService systemInfoService, ConfigService configService) {
        this.systemInfoService = systemInfoService;
        this.configService = configService;
    }

    /**
     * 推荐指定模板的完整配置与容量评估。mode: eco | sport | perf，未知值按 eco 处理。
     */
    public Map<String, Object> recommend(String mode) {
        Map<String, Object> sys = systemInfoService.snapshot();
        int cores = (int) sys.getOrDefault("cpuCores", DEFAULT_CORES);
        long memTotalBytes = (long) sys.getOrDefault("memTotal", DEFAULT_MEM_GB * 1024L * MEM_MB);
        double memGb = memTotalBytes / (double) (1024 * MEM_MB);

        Map<String, Object> params;
        Map<String, Object> capacity;
        List<String> risks;
        String modeLabel;
        String recommendedBotCount;
        String note;

        if ("sport".equalsIgnoreCase(mode)) {
            modeLabel = "运动";
            recommendedBotCount = "5-10";
            note = "中等规模部署；并发度与节奏平衡；适合单机主用或小型集群";
            params = buildSportParams(cores);
            capacity = buildCapacity(memGb, cores, 140, 2.0, 2.5, "sport");
            risks = List.of(
                    "中等机器（4~8 核 / 8 GB）若同时跑其他服务，CPU 可能偶发抢占导致单事件延迟波动",
                    "每机器人独立 H2 文件，10 bot 即对应 10 个 db 文件（每个 ≈ 2~10 MB）+ 30 个文件句柄，文件系统 ulimit 需要调整（默认 1024 通常足够）",
                    "出站节奏 400ms/条意味着单 bot 出站 ≈ 2.5 条/秒，群活跃时仍有排队可能",
                    "HikariCP 每库 3 连接是 H2 单文件锁安全上限；并发写入若出现 lock timeout 优先考虑加索引");
        } else if ("perf".equalsIgnoreCase(mode)) {
            modeLabel = "性能";
            recommendedBotCount = "20+";
            note = "高吞吐部署；最大并发；适合高活跃度群组 / 多机器人生产环境";
            params = buildPerfParams(cores);
            capacity = buildCapacity(memGb, cores, 100, 3.0, 5.0, "perf");
            risks = List.of(
                    "内存按机器总量的 80% 占用估算；若同机跑其他服务可能 OOM（建议 JVM -Xmx ≤ 机器 ×80%）",
                    "20+ bot 各自一份 H2 文件（每个 ≈ 2~10 MB），文件句柄消耗显著；Linux 需 ulimit -n ≥ 8192",
                    "出站节奏 200ms/条较激进；QQ 风控阈值附近，活跃群可能被限流",
                    "HikariCP 每库 4 连接：H2 单文件锁下并发写同一库仍可能 lock timeout；BotPipeline 并发池加大可缓解",
                    "Spring @Scheduled 池扩到 cores：单实例 Spring 任务并发上限提高，但同进程其它 @Scheduled 任务会一起被调度",
                    "网络带宽：性能模板假设上行 ≥ 5 MB/s（QQ 平台回调密集）；若机房间链路质量差需上调 pacing");
        } else {
            modeLabel = "经济";
            recommendedBotCount = "1-2";
            note = "小规模部署；低资源占用；适合测试 / 玩机 / 单机器人生产";
            params = buildEcoParams(cores);
            capacity = buildCapacity(memGb, cores, 200, 1.0, 1.2, "eco");
            risks = List.of(
                    "单 bot 入站约 1 条/秒；高活跃群（如 100 人活跃群）消息处理会有明显排队延迟",
                    "调度池/连接池收缩后：大批量 bot 同时重连或定时任务并发时等待变长（实测约 50~200ms）",
                    "出站节奏 800ms/条：推送场景下发送速率约 1.2 条/秒/bot；偏慢但最稳妥",
                    "HikariCP 每库 2 连接：写入高峰（消息风暴）可能出现 lock timeout，建议监控慢阶段");
        }

        Map<String, Object> sysSummary = new LinkedHashMap<>();
        sysSummary.put("cpuCores", cores);
        sysSummary.put("memGb", Math.round(memGb * 10) / 10.0);
        sysSummary.put("cpuModel", sys.getOrDefault("cpuModel", "未知"));
        sysSummary.put("osName", sys.getOrDefault("osName", ""));
        sysSummary.put("javaVersion", sys.getOrDefault("javaVersion", ""));

        // 延迟分析（按阶段）
        List<Map<String, Object>> latencyBreakdown = buildLatencyBreakdown(mode);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode.equalsIgnoreCase("sport") ? "sport" : mode.equalsIgnoreCase("perf") ? "perf" : "eco");
        out.put("modeLabel", modeLabel);
        out.put("recommendedBotCount", recommendedBotCount);
        out.put("note", note);
        out.put("params", params);
        out.put("capacity", capacity);
        out.put("risks", risks);
        out.put("latencyBreakdown", latencyBreakdown);
        out.put("sysSummary", sysSummary);
        out.put("detectedAt", System.currentTimeMillis());
        return out;
    }

    // ─────────────── 三档参数构建 ───────────────

    private Map<String, Object> buildEcoParams(int cores) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("schedPool", 2);                                       // Spring @Scheduled 池
        p.put("wsCore", 2);                                          // WS 连接池核心
        p.put("wsMax", 6);                                           // WS 连接池最大
        p.put("heartbeatPool", Math.max(2, cores / 2));              // WS 心跳池
        p.put("hikariMainMax", 2);                                   // 主库 HikariCP
        p.put("hikariLogMax", 2);                                    // 日志库 HikariCP
        p.put("hikariInstanceMax", 2);                               // 每 bot 实例库 HikariCP
        p.put("outPaceMs", 800L);                                    // 出站节奏 ms/条
        p.put("outThreadsPerBot", 1);                                // 出站池每 bot 线程数
        p.put("botConcurrency", 2);                                  // BotPipeline 每 bot 并发事件数
        return p;
    }

    private Map<String, Object> buildSportParams(int cores) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("schedPool", 4);
        p.put("wsCore", 4);
        p.put("wsMax", Math.max(12, cores * 2));
        p.put("heartbeatPool", Math.max(2, cores));
        p.put("hikariMainMax", 4);
        p.put("hikariLogMax", 3);
        p.put("hikariInstanceMax", 3);
        p.put("outPaceMs", 400L);
        p.put("outThreadsPerBot", 2);
        p.put("botConcurrency", 4);
        return p;
    }

    private Map<String, Object> buildPerfParams(int cores) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("schedPool", Math.max(cores, 8));
        p.put("wsCore", cores);
        p.put("wsMax", Math.max(16, cores * 4));
        p.put("heartbeatPool", Math.max(2, cores));
        p.put("hikariMainMax", 6);
        p.put("hikariLogMax", 4);
        p.put("hikariInstanceMax", 4);
        p.put("outPaceMs", 200L);
        p.put("outThreadsPerBot", 3);
        p.put("botConcurrency", 8);
        return p;
    }

    private Map<String, Object> buildCapacity(double memGb, int cores, int memPerBotMb,
                                                double inPerBot, double outPerBot, String mode) {
        Map<String, Object> cap = new LinkedHashMap<>();
        // 内存约束：按 80%（性能）/ 60%（运动）/ 50%（经济）占用
        double memRatio = "perf".equals(mode) ? 0.8 : "sport".equals(mode) ? 0.6 : 0.5;
        long maxBotsByMem = (long) Math.max(1, memGb * 1024 * memRatio / memPerBotMb);
        // CPU 约束：按 cores * 因子
        double cpuFactor = "perf".equals(mode) ? 4 : "sport".equals(mode) ? 2 : 1.5;
        long maxBotsByCpu = Math.max(1, (long) (cores * cpuFactor));
        long maxBots = Math.max(1, Math.min(maxBotsByMem, maxBotsByCpu));

        cap.put("memPerBotMb", memPerBotMb);
        cap.put("maxBots", maxBots);
        cap.put("maxBotsByMem", maxBotsByMem);
        cap.put("maxBotsByCpu", maxBotsByCpu);
        cap.put("msgInPerSec", Math.round(maxBots * inPerBot * 10) / 10.0);
        cap.put("msgOutPerSec", Math.round(maxBots * outPerBot * 10) / 10.0);
        cap.put("summary", String.format(
                "推荐挂载 ≤ %d 个机器人；入站约 %s 条/秒、出站约 %s 条/秒（估算，基于本机 %d 核/%.1f GB）",
                maxBots, Math.round(maxBots * inPerBot), Math.round(maxBots * outPerBot), cores, memGb));
        return cap;
    }

    /**
     * 延迟分析：按事件处理链路分阶段估算（无插件场景）。
     * 路径：WS/Webhook 接收 → 鉴权/去重/限流 → 解析 → 调度 handler → DB 写 → 出站调用（异步）。
     */
    private List<Map<String, Object>> buildLatencyBreakdown(String mode) {
        // 基准倍数（基于模板）
        double mult = "perf".equalsIgnoreCase(mode) ? 0.6 : "sport".equalsIgnoreCase(mode) ? 1.0 : 1.5;

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(stage("WS 接收", "QQ 平台推送 → 解析 OpCode → 事件对象", "ms", 5 * mult, 30 * mult));
        stages.add(stage("黑名单/权限", "L4 黑名单一票否决 + L0 主人放行（DB 读）", "ms", 2 * mult, 15 * mult));
        stages.add(stage("去重 (DB)", "xuanji_dedup 主键冲突检测", "ms", 3 * mult, 20 * mult));
        stages.add(stage("限流", "框架级 RateLimitStage 每用户冷却", "ms", 1 * mult, 5 * mult));
        stages.add(stage("Plugin 调度", "CommandRegistry 匹配 handler + invoke", "ms", 10 * mult, 100 * mult));
        stages.add(stage("DB 写（消息/事件入库）", "HikariCP 获取连接 → INSERT → 释放", "ms", 8 * mult, 50 * mult));
        stages.add(stage("出站调用（异步）", "BotOutboundExecutor.submit + pace 节流", "ms", 1 * mult, 5 * mult));
        // 汇总
        double normal = 30 * mult, worst = 225 * mult;
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("name", "合计（不含插件耗时）");
        total.put("desc", "正常 / 最坏情况估算");
        total.put("unit", "ms");
        total.put("normal", Math.round(normal * 10) / 10.0);
        total.put("worst", Math.round(worst * 10) / 10.0);
        total.put("isTotal", true);
        if (worst > 3000) total.put("warning", "⚠️ 最坏情况可能超过 3s");
        stages.add(total);
        return stages;
    }

    private Map<String, Object> stage(String name, String desc, String unit, double normal, double worst) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("desc", desc);
        m.put("unit", unit);
        m.put("normal", Math.round(normal * 10) / 10.0);
        m.put("worst", Math.round(worst * 10) / 10.0);
        return m;
    }

    // ─────────────── apply / current ───────────────

    /**
     * 应用指定模板：把三档参数全部写入全局配置。
     *
     * <p>每个参数的生效时机不同：
     * <ul>
     *   <li><b>运行时立即生效</b>：outbound.pace_ms（BotOutboundExecutor 动态读）</li>
     *   <li><b>下次触发时生效</b>：out_threads_per_bot（出站池懒建）、bot_concurrency（BotPipeline 懒建）</li>
     *   <li><b>需重启框架</b>：sched_pool / ws_core / ws_max / heartbeat_pool / hikari_main_max / hikari_log_max / hikari_instance_max</li>
     * </ul>
     *
     * <p>apply() 不做实际重启 —— 用户点击保存配置后，根据 UI 提示选择是否重启。
     */
    public Map<String, Object> apply(String mode) {
        Map<String, Object> rec = recommend(mode);
        String m = rec.get("mode").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) rec.get("params");

        long now = System.currentTimeMillis();
        configService.setGlobal("tune.active_mode", m);
        configService.setGlobal("tune.applied_at", String.valueOf(now));

        // 写入所有参数
        configService.setGlobal("tune.sched_pool", String.valueOf(params.get("schedPool")));
        configService.setGlobal("tune.ws_core", String.valueOf(params.get("wsCore")));
        configService.setGlobal("tune.ws_max", String.valueOf(params.get("wsMax")));
        configService.setGlobal("tune.heartbeat_pool", String.valueOf(params.get("heartbeatPool")));
        configService.setGlobal("tune.hikari_main_max", String.valueOf(params.get("hikariMainMax")));
        configService.setGlobal("tune.hikari_log_max", String.valueOf(params.get("hikariLogMax")));
        configService.setGlobal("tune.hikari_instance_max", String.valueOf(params.get("hikariInstanceMax")));
        configService.setGlobal("tune.out_threads_per_bot", String.valueOf(params.get("outThreadsPerBot")));
        configService.setGlobal("tune.bot_concurrency", String.valueOf(params.get("botConcurrency")));
        configService.setGlobal("tune.pace_ms", String.valueOf(params.get("outPaceMs")));
        // 运行时立即生效：出站节奏
        configService.setGlobal("outbound.pace_ms", String.valueOf(params.get("outPaceMs")));

        Map<String, Object> out = new LinkedHashMap<>(rec);
        out.put("appliedAt", now);
        // 标注每个参数的生效时机（前端展示用）
        List<Map<String, Object>> appliedKeys = new ArrayList<>();
        appliedKeys.add(effect("sched_pool", params.get("schedPool"), "重启"));
        appliedKeys.add(effect("ws_core", params.get("wsCore"), "重启"));
        appliedKeys.add(effect("ws_max", params.get("wsMax"), "重启"));
        appliedKeys.add(effect("heartbeat_pool", params.get("heartbeatPool"), "重启"));
        appliedKeys.add(effect("hikari_main_max", params.get("hikariMainMax"), "重启"));
        appliedKeys.add(effect("hikari_log_max", params.get("hikariLogMax"), "重启"));
        appliedKeys.add(effect("hikari_instance_max", params.get("hikariInstanceMax"), "重启"));
        appliedKeys.add(effect("out_threads_per_bot", params.get("outThreadsPerBot"), "下次触发"));
        appliedKeys.add(effect("bot_concurrency", params.get("botConcurrency"), "下次触发"));
        appliedKeys.add(effect("outbound.pace_ms", params.get("outPaceMs"), "立即生效"));
        out.put("appliedKeys", appliedKeys);
        out.put("needsRestart", true);
        return out;
    }

    private Map<String, Object> effect(String key, Object value, String timing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("value", value);
        m.put("timing", timing);
        return m;
    }

    /** 当前应用中的模板状态：从未应用则 mode=none。 */
    public Map<String, Object> current() {
        Map<String, String> g = configService.getGlobalConfig();
        String mode = g.getOrDefault("tune.active_mode", "");
        Map<String, Object> out = new LinkedHashMap<>();
        if (mode.isBlank() || !(mode.equals("eco") || mode.equals("sport") || mode.equals("perf"))) {
            out.put("mode", "none");
            out.put("modeLabel", "未应用模板");
            out.put("appliedAt", 0L);
        } else {
            Map<String, Object> rec = recommend(mode);
            out.put("mode", mode);
            out.put("modeLabel", rec.get("modeLabel"));
            out.put("appliedAt", Long.parseLong(g.getOrDefault("tune.applied_at", "0")));
            out.put("params", rec.get("params"));
            out.put("sysSummary", rec.get("sysSummary"));
            // 实际生效的出站节奏（可能被用户手动改过）
            out.put("paceMsNow", g.getOrDefault("outbound.pace_ms", ""));
        }
        return out;
    }

    /** 一键恢复默认配置：删除所有 tune.* 配置 + 还原 outbound.pace_ms 为 0（默认不节流）。
     *  重启框架后线程池参数也会回到代码硬编码默认值。 */
    public Map<String, Object> reset() {
        List<String> resetKeys = List.of(
                "tune.active_mode", "tune.applied_at", "tune.sched_pool", "tune.ws_core", "tune.ws_max",
                "tune.heartbeat_pool", "tune.hikari_main_max", "tune.hikari_log_max",
                "tune.hikari_instance_max", "tune.out_threads_per_bot", "tune.bot_concurrency",
                "tune.pace_ms", "outbound.pace_ms"
        );
        int removed = 0;
        for (String k : resetKeys) {
            try {
                configService.deleteKey("global", null, null, k);
                removed++;
            } catch (Exception e) {
                log.warn("[Tune] 重置键失败: {}: {}", k, e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("resetKeys", resetKeys);
        out.put("resetCount", removed);
        out.put("note", "线程池参数（schedPool/WS/Hikari）需重启框架后才会从代码硬编码默认值生效；出站节奏已立即还原为 0（不节流）");
        return out;
    }
}