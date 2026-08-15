package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 插件定时任务 — 标记在插件主类（继承 {@link XuanJiPluginBase} 的类）的方法上，
 * 插件启用（{@code onEnable}）时由框架自动扫描并调度，停用时取消。
 *
 * <h3>三种触发方式（互斥，优先 cron）</h3>
 * <pre>
 *   &#64;Scheduled(cron = "0 0 8 * * ?")            // 每天 08:00（Spring 6 域 cron）
 *   &#64;Scheduled(fixedRate = 60_000)              // 固定间隔 60s（上次开始算）
 *   &#64;Scheduled(fixedDelay = 60_000)             // 固定延迟 60s（上次结束算）
 *   &#64;Scheduled(cron = "...", initialDelay = 5000) // 首次延迟 5s
 * </pre>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>被标记方法<b>必须无参、返回 void</b>；否则框架跳过并告警。</li>
 *   <li>方法体内发消息时，没有「当前事件」可借 botKey，故自动绑定
 *       {@link XuanJiPlugin#defaultBot()} 指定的机器人（空则按默认平台挑一个）。</li>
 *   <li>cron 非法 / fixedRate 与 fixedDelay 同时为 0 视为未配置，跳过。</li>
 * </ul>
 *
 * <p>实现说明：框架用独立 {@code ScheduledExecutorService} 调度插件任务（与框架自身的
 * DB 任务表 {@code TaskSchedulerService} 互不影响），插件类加载器卸载时一并停掉。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scheduled {

    /** Spring 6 域 cron 表达式（"秒 分 时 日 月 周"）。非空时优先于 fixedRate/fixedDelay。 */
    String cron() default "";

    /** 固定间隔（毫秒，从任务开始计时）；<=0 忽略。 */
    long fixedRate() default 0;

    /** 固定延迟（毫秒，从任务结束计时）；<=0 忽略。 */
    long fixedDelay() default 0;

    /** 首次触发前的延迟（毫秒，默认 0 立即加入调度）。 */
    long initialDelay() default 0;
}
