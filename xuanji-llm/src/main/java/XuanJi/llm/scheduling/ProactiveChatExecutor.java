package XuanJi.llm.scheduling;

import XuanJi.llm.proactive.ProactiveChatService;
import XuanJi.scheduler.exec.JobExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统定时任务：主动搭话扫描（每分钟）。
 *
 * <p>P3 定时迁移：从 llm 模块自持 {@code @Scheduled} 改为注册为 scheduler 系统任务，
 * 可在控制台「定时任务」页统一查看 / 停用。
 */
@Slf4j
@Component
public class ProactiveChatExecutor implements JobExecutor {

    private final ProactiveChatService proactiveChatService;

    public ProactiveChatExecutor(ProactiveChatService proactiveChatService) {
        this.proactiveChatService = proactiveChatService;
    }

    @Override
    public String type() {
        return "LLM_PROACTIVE";
    }

    @Override
    public String execute(Map<String, Object> job) {
        proactiveChatService.scan();
        return "主动搭话扫描完成";
    }
}
