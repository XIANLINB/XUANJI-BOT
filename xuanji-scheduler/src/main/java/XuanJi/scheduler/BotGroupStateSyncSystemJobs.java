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
 * 机器人群内状态同步系统任务注册 —— 每 1 分钟错峰刷新 bot_state 到 qqbot_group_robot。
 */
@Slf4j
@Component
public class BotGroupStateSyncSystemJobs {

    private final SchedulerJobStore jobStore;
    private final JdbcTemplate jdbc;

    public BotGroupStateSyncSystemJobs(SchedulerJobStore jobStore, JdbcTemplate jdbc) {
        this.jobStore = jobStore;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensure() {
        ensureJob("BOT_GROUP_STATE_SYNC", "机器人群内状态同步（每 1 分钟错峰）",
                "0 * * * * ?", "系统：定时错峰刷新机器人在群内状态（30QPM 预算内）");
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
