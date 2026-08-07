package dev.xuanji.scheduler.core;

import dev.xuanji.scheduler.exec.JobExecutor;
import dev.xuanji.scheduler.store.SchedulerJobStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调度核心：Spring CronExpression 解析 + 30s tick 扫描到期任务 + 虚拟线程执行池。
 *
 * <ul>
 *   <li>tick 线程每 30s 扫一次 enabled 且 next_run 到期的任务（独立 daemon 线程，不占 Spring 调度池）</li>
 *   <li>执行用虚拟线程池（JDK 21+），同任务防重入（running 集合）</li>
 *   <li>每次执行后：写执行日志 + 计数 + 重算 next_run（cron 下一次触发）</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskSchedulerService implements DisposableBean {

    private final SchedulerJobStore store;
    private final Map<String, JobExecutor> executors;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService ticker;
    private ExecutorService workers;

    public TaskSchedulerService(SchedulerJobStore store, List<JobExecutor> executorList) {
        this.store = store;
        this.executors = new HashMap<>();
        for (JobExecutor ex : executorList) {
            executors.put(ex.type(), ex);
        }
    }

    @PostConstruct
    public void start() {
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "xuanji-sched-tick");
            t.setDaemon(true);
            return t;
        });
        workers = Executors.newVirtualThreadPerTaskExecutor();
        ticker.scheduleWithFixedDelay(this::tick, 10, 30, TimeUnit.SECONDS);
        log.info("[Scheduler] 定时任务调度器启动: tick=30s executors={}", executors.keySet());
    }

    @PreDestroy
    @Override
    public void destroy() {
        if (ticker != null) ticker.shutdownNow();
        if (workers != null) workers.shutdownNow();
        log.info("[Scheduler] 定时任务调度器已关闭");
    }

    /** tick：扫描到期任务并提交执行。 */
    void tick() {
        List<Map<String, Object>> due;
        try {
            due = store.listDueJobs(System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            log.warn("[Scheduler] tick 扫描失败: {}", e.getMessage());
            return;
        }
        for (Map<String, Object> job : due) {
            long id = num(job.get("id"));
            if (!running.add(id)) continue; // 防重入：同任务同一 tick 只跑一次
            workers.submit(() -> {
                try {
                    executeJob(job);
                } finally {
                    running.remove(id);
                }
            });
        }
    }

    /** 手动触发（不影响原 cron 调度表，仅执行一次 + 记日志计数）。 */
    public Map<String, Object> forceRun(long id) {
        Map<String, Object> job = store.getJob(id);
        if (job == null) return Map.of("status", "error", "msg", "任务不存在");
        if (!running.add(id)) return Map.of("status", "error", "msg", "任务正在执行中");
        workers.submit(() -> {
            try {
                executeJob(job);
            } finally {
                running.remove(id);
            }
        });
        return Map.of("status", "ok", "msg", "已触发执行");
    }

    /** 执行一次任务：跑逻辑 + 记日志 + 计数 + 重算 next_run。 */
    void executeJob(Map<String, Object> job) {
        long id = num(job.get("id"));
        String name = str(job.get("name"));
        String type = str(job.get("jobType"));
        long t0 = System.currentTimeMillis();
        String status = "SUCCESS";
        String result = "";
        String error = "";
        try {
            JobExecutor ex = executors.get(type);
            if (ex == null) {
                status = "SKIP";
                result = "未注册执行器: " + type;
            } else {
                result = ex.execute(job);
            }
            store.touchRun(id, true);
        } catch (Exception e) {
            status = "FAIL";
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            store.touchRun(id, false);
            log.warn("[Scheduler] 任务执行失败: id={} name={} error={}", id, name, error);
        } finally {
            store.addLog(id, name, t0, System.currentTimeMillis(), status, result, error);
            long next = previewNext(str(job.get("cron")), System.currentTimeMillis() / 1000);
            store.updateNextRun(id, next);
        }
    }

    // ═══════════════════ cron 工具 ═══════════════════

    /**
     * 归一化 cron：Spring CronExpression 要求 6 位（秒 分 时 日 月 周），
     * 兼容用户常用的 5 位写法（自动补秒字段 0，如 {@code 0 8 * * *} → {@code 0 0 8 * * *}）。
     */
    static String normalizeCron(String cron) {
        if (cron == null) return "";
        String c = cron.trim();
        String[] parts = c.split("\\s+");
        return parts.length == 5 ? "0 " + c : c;
    }

    /** 计算 cron 下一次触发时间（epoch 秒）；非法 cron 返回 -1。 */
    public static long previewNext(String cron, long fromEpochSeconds) {
        try {
            CronExpression expr = CronExpression.parse(normalizeCron(cron));
            LocalDateTime base = Instant.ofEpochSecond(Math.max(fromEpochSeconds, 1))
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime next = expr.next(base);
            return next == null ? -1 : next.atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 校验 cron 是否合法。 */
    public static boolean isValidCron(String cron) {
        return cron != null && previewNext(cron, System.currentTimeMillis() / 1000) >= 0;
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
