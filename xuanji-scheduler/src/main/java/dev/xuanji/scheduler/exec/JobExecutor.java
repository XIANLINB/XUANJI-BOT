package dev.xuanji.scheduler.exec;

import java.util.Map;

/**
 * 定时任务执行器 SPI：按 jobType 分发（BOT_PUSH / HTTP / SYSTEM / PLUGIN）。
 * 返回执行结果字符串（写入日志 result），异常由调度器记录 fail。
 */
public interface JobExecutor {

    /** 任务类型标识。 */
    String type();

    /**
     * 执行一次任务。
     *
     * @param job 任务行（小写驼峰 key）
     * @return 执行结果描述（成功时）
     * @throws Exception 执行失败时抛出，由调度器记 fail + 日志
     */
    String execute(Map<String, Object> job) throws Exception;
}
