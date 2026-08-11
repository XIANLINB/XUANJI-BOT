package XuanJi.llm.scheduling;

import XuanJi.llm.memory.MemorySummaryService;
import XuanJi.scheduler.exec.JobExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统定时任务：记忆压缩（每天 03:40）。
 *
 * <p>P3 定时迁移：从 llm 模块自持 {@code @Scheduled} 改为注册为 scheduler 系统任务，
 * 可在控制台「定时任务」页统一查看 / 停用。
 */
@Slf4j
@Component
public class MemorySummaryExecutor implements JobExecutor {

    private final MemorySummaryService memorySummaryService;

    public MemorySummaryExecutor(MemorySummaryService memorySummaryService) {
        this.memorySummaryService = memorySummaryService;
    }

    @Override
    public String type() {
        return "LLM_MEMORY_SUMMARY";
    }

    @Override
    public String execute(Map<String, Object> job) {
        memorySummaryService.compressAll();
        return "记忆压缩完成";
    }
}
