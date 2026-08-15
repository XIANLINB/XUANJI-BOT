package XuanJi.core.plugin;

import XuanJi.api.annotation.Scheduled;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.adapter.BotContextBinder;
import XuanJi.api.plugin.PluginData;
import XuanJi.core.plugin.storage.PluginDataProvider;
import XuanJi.sdk.bot.Bot;
import XuanJi.sdk.bot.BotRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 插件定时任务调度器 — 独立于框架自身的 DB 任务表（{@code TaskSchedulerService}），
 * 专为插件 {@link Scheduled} 方法服务：随插件 {@code onEnable}/{@code onDisable} 自动启停。
 *
 * <h3>触发方式</h3>
 * <ul>
 *   <li>cron（Spring 6 域）：自调度，每次执行后计算下次触发延迟重新入队；</li>
 *   <li>fixedRate：从任务开始计时，固定间隔；</li>
 *   <li>fixedDelay：从任务结束计时，固定延迟。</li>
 * </ul>
 *
 * <h3>上下文</h3>
 * 任务执行时：① 绑定插件 {@link XuanJiPlugin#defaultBot()} 指定的机器人上下文
 * （通过 {@link BotContextBinder}，使主动发送解析到正确 token）；② 若方法声明
 * {@link Bot} 参数，自动注入该默认机器人的门面实例。
 */
@Slf4j(topic = "xuanji.plugin.task")
@Component
public class PluginTaskScheduler {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "xuanji-plugin-task");
                t.setDaemon(true);
                return t;
            });

    private final ObjectProvider<BotRegistry> botRegistryProvider;
    private final ObjectProvider<BotContextBinder> binderProvider;
    private final ObjectProvider<PluginDataProvider> dataProviderProvider;

    /** pluginId → 该插件所有已调度任务的句柄（停用/卸载时统一取消 + 阻止重调度）。 */
    private final Map<String, List<TaskHandle>> futures = new ConcurrentHashMap<>();

    /**
     * 单个任务的调度句柄：cron 自调度只保留「当前活跃 future」（执行完即被新 future 顶替，不累积）；
     * {@code cancelled} 标志保证 cancel 后 cron 不再重新入队，杜绝竞态泄漏。
     */
    private static final class TaskHandle {
        final AtomicReference<ScheduledFuture<?>> active = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
    }

    public PluginTaskScheduler(ObjectProvider<BotRegistry> botRegistryProvider,
                              ObjectProvider<BotContextBinder> binderProvider,
                              ObjectProvider<PluginDataProvider> dataProviderProvider) {
        this.botRegistryProvider = botRegistryProvider;
        this.binderProvider = binderProvider;
        this.dataProviderProvider = dataProviderProvider;
    }

    /**
     * 扫描插件实例上的全部 {@link Scheduled} 方法并调度。
     * 在插件 {@code onEnable} 后调用。
     *
     * @param pluginId 插件 id（与命令注入一致的权威来源，来自 wrapper.getPluginId()）
     */
    public void scheduleAll(Object pluginInstance, String pluginId) {
        if (pluginInstance == null) return;
        Class<?> cls = pluginInstance.getClass();
        XuanJiPlugin plg = cls.getAnnotation(XuanJiPlugin.class);
        String defaultBot = plg != null ? plg.defaultBot() : "";
        boolean proactive = plg != null
                && java.util.Arrays.asList(plg.permissions()).contains(XuanJiPlugin.Perm.PROACTIVE_MESSAGE);
        boolean groupAdmin = plg != null
                && java.util.Arrays.asList(plg.permissions()).contains(XuanJiPlugin.Perm.GROUP_ADMIN);

        for (Method m : cls.getDeclaredMethods()) {
            Scheduled ann = m.getAnnotation(Scheduled.class);
            if (ann == null) continue;
            if (!validate(m)) continue;
            scheduleOne(pluginId, m, pluginInstance, defaultBot, ann, proactive, groupAdmin);
        }
    }

    private void scheduleOne(String pluginId, Method m, Object instance, String defaultBot, Scheduled ann, boolean proactive, boolean groupAdmin) {
        Runnable body = () -> runTask(m, instance, defaultBot, proactive, groupAdmin, pluginId);
        List<TaskHandle> list = futures.computeIfAbsent(pluginId, k -> new CopyOnWriteArrayList<>());
        TaskHandle handle = new TaskHandle();

        if (!ann.cron().isBlank()) {
            CronExpression expr;
            try {
                expr = CronExpression.parse(ann.cron());
            } catch (Exception e) {
                log.warn("[PluginTask] 插件{} 方法{} cron 非法(忽略): {}", pluginId, m.getName(), ann.cron());
                return;
            }
            list.add(handle);
            scheduleCron(handle, expr, body);
            log.info("[PluginTask] 已注册 cron 任务: {}#{} cron={}", pluginId, m.getName(), ann.cron());
        } else if (ann.fixedRate() > 0) {
            list.add(handle);
            handle.active.set(scheduler.scheduleAtFixedRate(body, ann.initialDelay(), ann.fixedRate(), TimeUnit.MILLISECONDS));
            log.info("[PluginTask] 已注册 fixedRate 任务: {}#{} rate={}ms", pluginId, m.getName(), ann.fixedRate());
        } else if (ann.fixedDelay() > 0) {
            list.add(handle);
            handle.active.set(scheduler.scheduleWithFixedDelay(body, ann.initialDelay(), ann.fixedDelay(), TimeUnit.MILLISECONDS));
            log.info("[PluginTask] 已注册 fixedDelay 任务: {}#{} delay={}ms", pluginId, m.getName(), ann.fixedDelay());
        } else {
            log.warn("[PluginTask] 插件{} 方法{} 未配置触发方式(忽略)", pluginId, m.getName());
        }
    }

    /** cron 自调度：执行后计算下次延迟重新入队；active 只保留当前 future，不累积；cancelled 后不再重调度。 */
    private void scheduleCron(TaskHandle handle, CronExpression expr, Runnable body) {
        // 用数组持有自身引用，实现自调度（lambda 内引用自身需先完成初始化）
        Runnable[] self = new Runnable[1];
        self[0] = () -> {
            try {
                body.run();
            } catch (Throwable t) {
                log.warn("[PluginTask] cron 任务执行异常: {}", t.getMessage());
            } finally {
                if (handle.cancelled.get()) return; // 已取消，不再重新入队
                long delay = nextDelayMillis(expr);
                if (delay >= 0) {
                    ScheduledFuture<?> f = scheduler.schedule(self[0], delay, TimeUnit.MILLISECONDS);
                    handle.active.set(f);
                }
            }
        };
        long delay = nextDelayMillis(expr);
        if (delay >= 0) {
            ScheduledFuture<?> f = scheduler.schedule(self[0], delay, TimeUnit.MILLISECONDS);
            handle.active.set(f);
        }
    }

    private void runTask(Method m, Object instance, String defaultBot, boolean proactive, Boolean groupAdmin, String pluginId) {
        Runnable work = () -> {
            try {
                Object[] args = resolveArgs(m, defaultBot, proactive, groupAdmin, pluginId);
                m.invoke(instance, args);
            } catch (Throwable t) {
                log.warn("[PluginTask] 任务 {}.{} 执行失败: {}", instance.getClass().getSimpleName(), m.getName(), t.getMessage());
            }
        };
        BotContextBinder binder = binderProvider.getIfAvailable();
        if (binder != null && defaultBot != null && !defaultBot.isBlank()) {
            binder.runWith(defaultBot, work);
        } else {
            work.run();
        }
    }

    /** 注入 {@link Bot} / {@link PluginData} 参数（按 pluginId 隔离）；其它类型参数注入 null。 */
    private Object[] resolveArgs(Method m, String defaultBot, boolean proactive, boolean groupAdmin, String pluginId) {
        Class<?>[] pts = m.getParameterTypes();
        Object[] args = new Object[pts.length];
        BotRegistry reg = botRegistryProvider.getIfAvailable();
        PluginDataProvider provider = dataProviderProvider.getIfAvailable();
        for (int i = 0; i < pts.length; i++) {
            if (Bot.class.isAssignableFrom(pts[i]) && reg != null) {
                Bot b = reg.get(defaultBot);
                if (b != null) {
                    b.setProactiveAllowed(proactive);
                    b.setGroupAdminAllowed(groupAdmin);
                }
                args[i] = b;
            } else if (PluginData.class.isAssignableFrom(pts[i]) && provider != null && pluginId != null) {
                args[i] = provider.get(pluginId);
            }
        }
        return args;
    }

    /** 取消某插件的所有定时任务（停用/卸载时调用）：先置 cancelled 阻止 cron 重调度，再 cancel 活跃 future。 */
    public void cancel(String pluginId) {
        List<TaskHandle> hs = futures.remove(pluginId);
        if (hs == null) return;
        int count = 0;
        for (TaskHandle h : hs) {
            h.cancelled.set(true); // 先置位，阻止 cron finally 重新入队（杜绝竞态泄漏）
            ScheduledFuture<?> f = h.active.get();
            if (f != null) {
                try { f.cancel(true); } catch (Exception ignored) {}
            }
            h.active.set(null); // 释放引用，助 GC
            count++;
        }
        log.info("[PluginTask] 已取消插件 {} 的 {} 个定时任务", pluginId, count);
    }

    /** 校验：必须是返回 void、且仅含可注入 Bot / PluginData 参数的方法。 */
    private boolean validate(Method m) {
        if (!void.class.equals(m.getReturnType()) && !Void.class.equals(m.getReturnType())) {
            log.warn("[PluginTask] @Scheduled 方法 {} 必须返回 void，已忽略", m.getName());
            return false;
        }
        for (Class<?> pt : m.getParameterTypes()) {
            if (!Bot.class.isAssignableFrom(pt) && !PluginData.class.isAssignableFrom(pt)) {
                log.warn("[PluginTask] @Scheduled 方法 {} 仅支持 Bot/PluginData 参数(已忽略): {}", m.getName(), pt.getSimpleName());
                return false;
            }
        }
        return true;
    }

    private static long nextDelayMillis(CronExpression expr) {
        ZonedDateTime next = expr.next(ZonedDateTime.now());
        if (next == null) return -1;
        return Math.max(0, Duration.between(ZonedDateTime.now(), next).toMillis());
    }
}
