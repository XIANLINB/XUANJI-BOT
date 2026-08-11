package XuanJi.starter.config;

import XuanJi.core.config.ConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;

/**
 * 定时任务调度池配置 — 读取全局配置 tune.sched_pool（性能模板写入），
 * 未配置时用 application.yml 的 spring.task.scheduling.pool.size（默认 4）。
 *
 * <p>性能模板「保存配置 → 重启框架」后，调度池线程数按所选模板生效。
 */
@Configuration(proxyBeanMethods = false)
public class SchedulerPoolConfig {

    @Bean
    public TaskScheduler taskScheduler(ObjectProvider<ConfigService> configProvider) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize(configProvider));
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }

    private int poolSize(ObjectProvider<ConfigService> provider) {
        int def = 4;
        try {
            ConfigService cs = provider.getIfAvailable();
            if (cs != null) {
                Map<String, String> g = cs.getGlobalConfig();
                String v = g.get("tune.sched_pool");
                if (v != null && !v.isBlank()) {
                    int n = Integer.parseInt(v.trim());
                    if (n > 0) return n;
                }
            }
        } catch (Exception ignored) { /* 默认 4 */ }
        return def;
    }
}
