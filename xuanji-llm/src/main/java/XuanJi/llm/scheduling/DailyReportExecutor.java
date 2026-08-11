package XuanJi.llm.scheduling;

import XuanJi.llm.summary.DailySummaryService;
import XuanJi.scheduler.exec.JobExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统定时任务：AI 日报扫描（每分钟）。
 *
 * <p>日报的「到点发送」由 {@link DailySummaryService#scan} 按 {@code xuanji_llm_summary_config}
 * 动态扫描（每个群可配独立时间），本执行器每分钟触发一次扫描。
 * P3 定时迁移：从 llm 模块自持 {@code @Scheduled} 改为注册为 scheduler 系统任务，
 * 可在控制台「定时任务」页统一查看 / 停用。
 */
@Slf4j
@Component
public class DailyReportExecutor implements JobExecutor {

    private final DailySummaryService dailySummaryService;

    public DailyReportExecutor(DailySummaryService dailySummaryService) {
        this.dailySummaryService = dailySummaryService;
    }

    @Override
    public String type() {
        return "LLM_DAILY_REPORT";
    }

    @Override
    public String execute(Map<String, Object> job) {
        dailySummaryService.scan();
        return "AI 日报扫描完成";
    }
}
