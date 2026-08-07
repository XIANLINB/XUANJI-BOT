package dev.xuanji.scheduler.api;

import dev.xuanji.core.web.XuanjiApi;
import dev.xuanji.scheduler.core.TaskSchedulerService;
import dev.xuanji.scheduler.store.SchedulerJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 控制台 · 定时任务管理：任务 CRUD / 启停 / 手动触发 / 执行日志 / cron 预览。
 * 路由前缀 /xuanji/api/v1 由 console-server 的 XuanjiApiRoutes 统一装配。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/console/scheduler")
public class SchedulerAdminApi {

    private static final Set<String> SUPPORTED_TYPES = Set.of("BOT_PUSH", "HTTP");

    private final SchedulerJobStore store;
    private final TaskSchedulerService scheduler;

    public SchedulerAdminApi(SchedulerJobStore store, TaskSchedulerService scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> listJobs() {
        return store.listJobs();
    }

    @PostMapping("/jobs")
    public Map<String, Object> createJob(@RequestBody Map<String, Object> body) {
        Map<String, Object> err = validate(body, true);
        if (err != null) return err;
        long id = store.createJob(body);
        recomputeNextRun(id, str(body.get("cron")));
        log.info("[Scheduler] 创建任务: id={} name={} type={} cron={}", id, body.get("name"), body.get("jobType"), body.get("cron"));
        return Map.of("status", "ok", "id", id);
    }

    @PutMapping("/jobs/{id}")
    public Map<String, Object> updateJob(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (store.getJob(id) == null) return Map.of("status", "error", "msg", "任务不存在");
        Map<String, Object> err = validate(body, false);
        if (err != null) return err;
        store.updateJob(id, body);
        recomputeNextRun(id, str(body.get("cron")));
        log.info("[Scheduler] 更新任务: id={}", id);
        return Map.of("status", "ok");
    }

    @DeleteMapping("/jobs/{id}")
    public Map<String, Object> deleteJob(@PathVariable long id) {
        boolean ok = store.deleteJob(id);
        return Map.of("status", ok ? "ok" : "error", "msg", ok ? "已删除" : "任务不存在");
    }

    @PostMapping("/jobs/{id}/toggle")
    public Map<String, Object> toggleJob(@PathVariable long id, @RequestParam boolean enabled) {
        Map<String, Object> job = store.getJob(id);
        if (job == null) return Map.of("status", "error", "msg", "任务不存在");
        store.setEnabled(id, enabled);
        if (enabled) {
            recomputeNextRun(id, str(job.get("cron")));
        } else {
            store.updateNextRun(id, 0);
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/jobs/{id}/run")
    public Map<String, Object> runJob(@PathVariable long id) {
        return scheduler.forceRun(id);
    }

    @GetMapping("/jobs/{id}/logs")
    public Map<String, Object> jobLogs(@PathVariable long id,
                                       @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> rows = store.listLogs(id, limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", id);
        m.put("rows", rows);
        return m;
    }

    /** 执行分析：全局概览 + 按任务聚合（成功率/平均耗时/最近状态）。 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.execStats();
    }

    /** 执行趋势：近 days 天按日聚合（执行数/失败数/平均耗时），前端补 0 值。 */
    @GetMapping("/trend")
    public List<Map<String, Object>> trend(@RequestParam(defaultValue = "7") int days) {
        return store.execTrend(days);
    }

    /** cron 校验 + 下次执行时间预览（epoch 秒，非法返回 -1）。 */
    @GetMapping("/cron-preview")
    public Map<String, Object> cronPreview(@RequestParam String cron) {
        long next = TaskSchedulerService.previewNext(cron, System.currentTimeMillis() / 1000);
        return Map.of("valid", next >= 0, "nextRun", next);
    }

    // ═══════════════════ 内部 ═══════════════════

    private Map<String, Object> validate(Map<String, Object> body, boolean create) {
        if (str(body.get("name")).isBlank()) return Map.of("status", "error", "msg", "任务名称不能为空");
        String type = str(body.get("jobType"));
        if (!SUPPORTED_TYPES.contains(type)) {
            return Map.of("status", "error", "msg", "不支持的任务类型: " + type + "（支持 " + SUPPORTED_TYPES + "）");
        }
        String cron = str(body.get("cron"));
        if (!TaskSchedulerService.isValidCron(cron)) {
            return Map.of("status", "error", "msg", "cron 表达式非法");
        }
        if ("BOT_PUSH".equals(type)) {
            if (str(body.get("targetBot")).isBlank()) return Map.of("status", "error", "msg", "请选择机器人");
            if (str(body.get("targetType")).isBlank()) return Map.of("status", "error", "msg", "请选择目标类型（群/单聊）");
            if (str(body.get("targetId")).isBlank()) return Map.of("status", "error", "msg", "请填写目标 ID（群号/用户 openid）");
            if (str(body.get("content")).isBlank()) return Map.of("status", "error", "msg", "请填写推送内容");
        }
        if ("HTTP".equals(type)) {
            String url = str(body.get("targetId"));
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return Map.of("status", "error", "msg", "URL 必须以 http:// 或 https:// 开头");
            }
        }
        return null;
    }

    private void recomputeNextRun(long id, String cron) {
        long next = TaskSchedulerService.previewNext(cron, System.currentTimeMillis() / 1000);
        store.updateNextRun(id, Math.max(next, 0));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
