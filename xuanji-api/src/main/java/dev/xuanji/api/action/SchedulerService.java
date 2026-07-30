package dev.xuanji.api.action;

import java.time.Duration;

/**
 * 定时任务调度 — 插件与跨语言 sidecar 共用的定时任务抽象。
 */
public interface SchedulerService {

    /** 注册定时任务，返回任务 ID 用于取消 */
    String schedule(String name, Runnable task, Duration interval);

    /** @param cron Cron 表达式 */
    String scheduleCron(String name, Runnable task, String cronExpression);

    /** 取消定时任务 */
    void cancel(String taskId);
}
