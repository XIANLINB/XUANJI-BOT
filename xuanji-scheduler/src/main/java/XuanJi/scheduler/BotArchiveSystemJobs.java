package XuanJi.scheduler;

import XuanJi.scheduler.store.SchedulerJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 框架系统定时任务初始化 —— 注册与平台无关的框架维护任务（scheduler 统一管理）。
 *
 * <p>启动完成后幂等注册（按 job_type 判存在）：
 * <ul>
 *   <li>{@code BOT_ARCHIVE_CLEANUP}：清理过期机器人归档（TTL 30 天），每天 04:30</li>
 * </ul>
 */
@Slf4j
@Component
public class BotArchiveSystemJobs {

    private final SchedulerJobStore jobStore;
    private final JdbcTemplate jdbc;

    public BotArchiveSystemJobs(SchedulerJobStore jobStore, JdbcTemplate jdbc) {
        this.jobStore = jobStore;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensure() {
        ensureJob("BOT_ARCHIVE_CLEANUP", "归档清理（每天 04:30）", "0 30 4 * * *", "系统：删除过期机器人归档");
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
            job.put("remark", remark);
            jobStore.createJob(job);
            log.info("[BOT-SYSJOB] 已注册系统定时任务: {} ({})", type, cron);
        } catch (Exception e) {
            log.warn("[BOT-SYSJOB] 注册系统任务失败 {}: {}", type, e.getMessage());
        }
    }
}
