package XuanJi.llm.scheduling;

import XuanJi.scheduler.store.SchedulerJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 系统定时任务初始化 —— 把 llm 模块原本自持的 {@code @Scheduled} 迁为 scheduler 统一管理的
 * 系统任务（控制台「定时任务」页可查看 / 停用）。
 *
 * <p>在应用启动完成后幂等注册（按 job_type 判存在）：
 * <ul>
 *   <li>{@code LLM_MEMORY_SUMMARY}：记忆压缩，每天 03:40</li>
 *   <li>{@code LLM_PROACTIVE}：主动搭话扫描，每分钟</li>
 *   <li>{@code LLM_DAILY_REPORT}：AI 日报扫描，每分钟（到点发送按 summary_config 动态判断）</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmSystemJobs {

    private final SchedulerJobStore jobStore;
    private final JdbcTemplate jdbc;

    public LlmSystemJobs(SchedulerJobStore jobStore, JdbcTemplate jdbc) {
        this.jobStore = jobStore;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensure() {
        ensureJob("LLM_MEMORY_SUMMARY", "记忆压缩（每天 03:40）", "0 40 3 * * *", "系统：LLM 记忆摘要压缩");
        ensureJob("LLM_PROACTIVE", "主动搭话扫描（每分钟）", "* * * * *", "系统：活跃群主动搭话扫描");
        ensureJob("LLM_DAILY_REPORT", "AI 日报扫描（每分钟）", "* * * * *", "系统：AI 日报定点发送");
    }

    private void ensureJob(String type, String name, String cron, String remark) {
        try {
            Integer exists = jdbc.query("""
                SELECT 1 FROM xuanji_scheduler_job WHERE job_type = ? AND deleted = FALSE
                """, rs -> rs.next() ? 1 : null, type);
            if (exists != null) {
                return;
            }
            Map<String, Object> job = new HashMap<>();
            job.put("name", name);
            job.put("jobType", type);
            job.put("cron", cron);
            job.put("enabled", true);
            job.put("content", remark);
            jobStore.createJob(job);
            log.info("[LLM-SYSJOB] 已注册系统定时任务: {} ({})", type, cron);
        } catch (Exception e) {
            log.warn("[LLM-SYSJOB] 注册失败: {} err={}", type, e.getMessage());
        }
    }
}
