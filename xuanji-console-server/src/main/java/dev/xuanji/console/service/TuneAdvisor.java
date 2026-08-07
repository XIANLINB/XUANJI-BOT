package dev.xuanji.console.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 性能模板推荐器 — 基于当前系统 CPU / 内存，估算「经济 / 性能」两套配置。
 *
 * <p>输出三类信息供运行设置页展示：
 * <ul>
 *   <li><b>params</b> — 模板对应的可调参数（调度池 / WS 连接池 / Hikari / 出站节奏）</li>
 *   <li><b>capacity</b> — 估算容量：可支持机器人上限（受内存、CPU 双约束取小）、入站/出站吞吐</li>
 *   <li><b>risks</b> — 该模板的风险提示</li>
 * </ul>
 *
 * <p>估算基准（经验值，写死注释便于后续校准）：
 * <ul>
 *   <li>每 bot 运行开销：JVM 实例 + H2 实例库（3 连接、文件句柄）+ 出站虚拟线程 ≈ 100~120 MB 内存</li>
 *   <li>入站单 bot 吞吐：pipeline 串行，经济 ~1 msg/s，性能 ~2 msg/s（受落库/插件耗时制约）</li>
 *   <li>出站单 bot：经济 pace 800ms（~1.2 msg/s），性能 pace 200ms（~5 msg/s）——防风控节奏</li>
 * </ul>
 */
@Service
public class TuneAdvisor {

    private static final long MEM_MB = 1024L * 1024L;

    private final SystemInfoService systemInfoService;

    public TuneAdvisor(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    /**
     * 推荐指定模板的完整配置与容量评估。mode: eco | perf，未知值按 eco 处理。
     *
     * <p><b>每次调用都实时采集「运行框架的这台机器」的资源</b>（{@link SystemInfoService#snapshot()}），
     * 框架打包不携带任何机器配置——谁部署、在哪台机器跑，就用那台机器的 CPU/内存估算。
     */
    public Map<String, Object> recommend(String mode) {
        Map<String, Object> sys = systemInfoService.snapshot();
        int cores = (int) sys.getOrDefault("cpuCores", 4);
        long memTotalBytes = (long) sys.getOrDefault("memTotal", 8L * 1024 * MEM_MB);
        double memGb = memTotalBytes / (double) (1024 * MEM_MB);
        boolean perf = "perf".equalsIgnoreCase(mode);

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> capacity = new LinkedHashMap<>();
        List<String> risks;
        String modeLabel;

        if (perf) {
            modeLabel = "性能";
            params.put("schedPool", Math.max(4, cores));                 // 定时任务调度池
            params.put("wsCore", Math.max(4, cores));                    // WS 连接池核心
            params.put("wsMax", Math.max(16, cores * 4));                // WS 连接池最大
            params.put("hikari", Math.min(8, Math.max(2, cores)));       // per 库连接数
            params.put("paceMs", 200L);                                  // 出站节奏（ms/条）
            params.put("note", "以当前 CPU/内存为上限拉满资源，吞吐优先");

            // 容量：性能模板可用 80% 内存、每 bot 约 100MB
            long maxBotsByMem = (long) Math.max(1, memGb * 1024 * 0.8 / 100);
            long maxBotsByCpu = Math.max(1, (long) (cores * 4L));
            long maxBots = Math.max(1, Math.min(maxBotsByMem, maxBotsByCpu));
            double inPerBot = 2.0, outPerBot = 5.0;

            capacity.put("maxBots", maxBots);
            capacity.put("maxBotsByMem", maxBotsByMem);
            capacity.put("maxBotsByCpu", maxBotsByCpu);
            capacity.put("msgInPerSec", Math.round(maxBots * inPerBot * 10) / 10.0);
            capacity.put("msgOutPerSec", Math.round(maxBots * outPerBot * 10) / 10.0);
            capacity.put("perBotMemMb", 100);
            capacity.put("summary", String.format(
                    "建议挂载 ≤ %d 个机器人；入站约 %s 条/秒、出站约 %s 条/秒（估算）",
                    maxBots, Math.round(maxBots * inPerBot), Math.round(maxBots * outPerBot)));

            risks = List.of(
                    "内存按总量 80% 占用估算：若同时跑其它大型服务，峰值并发可能内存不足（OOM）",
                    "每机器人一个 H2 实例库，挂载越多文件句柄/打开文件数越多，受系统句柄上限约束",
                    "出站节奏 200ms/条较激进，活跃度极高的群可能有触发 QQ 风控的轻微风险",
                    "Hikari 连接数放大到核数：H2 单文件锁限制下，并发写同一库仍可能 lock timeout（框架已按库隔离规避）");
        } else {
            modeLabel = "经济";
            params.put("schedPool", Math.max(2, cores / 2));             // 定时任务调度池
            params.put("wsCore", 2);                                     // WS 连接池核心
            params.put("wsMax", 8);                                      // WS 连接池最大
            params.put("hikari", 2);                                     // per 库连接数
            params.put("paceMs", 800L);                                  // 出站节奏（ms/条，防风控）
            params.put("note", "以当前系统最舒适状态配置，资源占用低、运行平稳");

            // 容量：经济模板只用 50% 内存、每 bot 约 120MB
            long maxBotsByMem = (long) Math.max(1, memGb * 1024 * 0.5 / 120);
            long maxBotsByCpu = Math.max(1, (long) (cores * 2L));
            long maxBots = Math.max(1, Math.min(maxBotsByMem, maxBotsByCpu));
            double inPerBot = 1.0, outPerBot = 1.2;

            capacity.put("maxBots", maxBots);
            capacity.put("maxBotsByMem", maxBotsByMem);
            capacity.put("maxBotsByCpu", maxBotsByCpu);
            capacity.put("msgInPerSec", Math.round(maxBots * inPerBot * 10) / 10.0);
            capacity.put("msgOutPerSec", Math.round(maxBots * outPerBot * 10) / 10.0);
            capacity.put("perBotMemMb", 120);
            capacity.put("summary", String.format(
                    "建议挂载 ≤ %d 个机器人；入站约 %s 条/秒、出站约 %s 条/秒（估算）",
                    maxBots, Math.round(maxBots * inPerBot), Math.round(maxBots * outPerBot)));

            risks = List.of(
                    "吞吐保守：单 bot 入站约 1 条/秒，群活跃时消息处理会有排队延迟",
                    "调度池/连接池收缩后，大批量机器人同时重连或定时任务并发时等待变长",
                    "出站节奏 800ms/条：高并发推送场景下发送速率约 1.2 条/秒/bot，偏慢但最稳妥");
        }

        Map<String, Object> sysSummary = new LinkedHashMap<>();
        sysSummary.put("cpuCores", cores);
        sysSummary.put("memGb", Math.round(memGb * 10) / 10.0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", perf ? "perf" : "eco");
        out.put("modeLabel", modeLabel);
        out.put("params", params);
        out.put("capacity", capacity);
        out.put("risks", risks);
        out.put("sysSummary", sysSummary);
        out.put("detectedAt", System.currentTimeMillis()); // 本机资源实时检测时间（epoch ms）
        return out;
    }
}
