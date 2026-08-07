package dev.xuanji.console.controller;

import dev.xuanji.console.service.ConsoleQueryService;
import dev.xuanji.console.service.SystemInfoService;
import dev.xuanji.console.service.TuneAdvisor;
import dev.xuanji.core.concurrent.ThreadPoolRegistry;
import dev.xuanji.core.metric.QpsMeter;
import dev.xuanji.core.storage.PlatformDataProvider;
import dev.xuanji.core.web.XuanjiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 运行监控指标：系统资源 / QPS / 线程池 / 框架统计 / 性能模板推荐。
 *
 * <p>全部真实数据：系统资源来自 {@link SystemInfoService}（JDK 内置 API），
 * QPS 来自 {@link QpsMeter}（入站事件在 BotPipeline 统一计数），
 * 线程池来自 {@link ThreadPoolRegistry}（各组件创建池时注册）。
 */
@XuanjiApi
@RestController
@RequestMapping("/console")
public class ConsoleMetricsController {

    private static final DateTimeFormatter HHMSS =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SystemInfoService systemInfoService;
    private final TuneAdvisor tuneAdvisor;
    private final ConsoleQueryService queryService;

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    public ConsoleMetricsController(SystemInfoService systemInfoService,
                                    TuneAdvisor tuneAdvisor,
                                    ConsoleQueryService queryService) {
        this.systemInfoService = systemInfoService;
        this.tuneAdvisor = tuneAdvisor;
        this.queryService = queryService;
    }

    /** 运行监控总览：系统资源 + QPS + 线程池 + 框架统计。 */
    @GetMapping("/metrics/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("system", systemInfoService.snapshot());
        m.put("qps", Map.of(
                "current", QpsMeter.current(),
                "peak60", QpsMeter.peak(60),
                "avg60", QpsMeter.avg(60)));

        List<Map<String, Object>> pools = new ArrayList<>(ThreadPoolRegistry.snapshot());
        Map<String, Object> tomcat = tomcatPool();
        if (tomcat != null) pools.add(tomcat);
        m.put("pools", pools);

        Map<String, Object> fw = new LinkedHashMap<>();
        fw.put("bots", queryService.botRefs().size());
        long groups = 0, friends = 0, msgs = 0, evts = 0;
        for (ConsoleQueryService.BotRef b : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(b.platform());
            if (p == null) continue;
            groups += p.countGroups(b.instanceId());
            friends += p.countFriends(b.instanceId());
            msgs += p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_GROUP, 0L)
                    + p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_C2C, 0L);
            evts += p.countAllEvents(b.instanceId());
        }
        fw.put("groups", groups);
        fw.put("friends", friends);
        fw.put("messages", msgs);
        fw.put("events", evts);
        m.put("framework", fw);
        return m;
    }

    /** QPS 逐秒曲线（旧→新），默认最近 60 秒。 */
    @GetMapping("/metrics/qps")
    public Map<String, Object> qps(@RequestParam(defaultValue = "60") int seconds) {
        int n = Math.min(Math.max(seconds, 1), 60);
        long[] vals = QpsMeter.snapshot(n);
        long nowSec = System.currentTimeMillis() / 1000L;
        List<String> labels = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            labels.add(HHMSS.format(Instant.ofEpochSecond(nowSec - i)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("values", vals);
        m.put("current", QpsMeter.current());
        m.put("peak60", QpsMeter.peak(60));
        m.put("avg60", QpsMeter.avg(60));
        return m;
    }

    /** 性能模板推荐：mode = eco | perf。 */
    @GetMapping("/tune/recommend")
    public Map<String, Object> recommend(@RequestParam(defaultValue = "eco") String mode) {
        return tuneAdvisor.recommend(mode);
    }

    /**
     * Tomcat HTTP 线程池快照（反射获取，避免 console-server 编译依赖 tomcat-embed）。
     * 链路：ApplicationContext.getWebServer() → TomcatWebServer.getTomcat() → getConnector()
     * → getProtocolHandler() → getExecutor()（Tomcat 线程池或标准线程池）。
     * 非 Tomcat 容器或获取失败返回 null，监控自动跳过。
     */
    private Map<String, Object> tomcatPool() {
        try {
            if (applicationContext == null) return null;
            Method gws = findMethod(applicationContext.getClass(), "getWebServer");
            if (gws == null) return null;
            Object ws = gws.invoke(applicationContext);
            if (ws == null) return null;
            Method gt = findMethod(ws.getClass(), "getTomcat");
            if (gt == null) return null;
            Object tomcat = gt.invoke(ws);
            Method gc = findMethod(tomcat.getClass(), "getConnector");
            if (gc == null) return null;
            Object connector = gc.invoke(tomcat);
            Method gph = findMethod(connector.getClass(), "getProtocolHandler");
            if (gph == null) return null;
            Object ph = gph.invoke(connector);
            Method ge = findMethod(ph.getClass(), "getExecutor");
            if (ge == null) return null;
            Object executor = ge.invoke(ph);
            if (executor == null) return null;

            int core = (int) call(executor, "getCorePoolSize", 0);
            int max = (int) callEither(executor, "getMaxThreads", "getMaximumPoolSize", 0);
            int active = (int) callEither(executor, "getActiveCount", "getBusyThreads", 0);
            int poolSize = (int) callEither(executor, "getCurrentThreadCount", "getPoolSize", 0);
            int queueSize = (int) call(executor, "getQueueSize", 0);
            long completed = (long) call(executor, "getCompletedTaskCount", 0L);
            return Map.of(
                    "name", "Tomcat HTTP线程池",
                    "type", "ThreadPoolExecutor",
                    "core", core,
                    "max", max,
                    "active", active,
                    "poolSize", poolSize,
                    "queueSize", queueSize,
                    "completed", completed,
                    "note", "HTTP 请求处理（Boot 4 默认虚拟线程）");
        } catch (Exception e) {
            return null;
        }
    }

    /** 在类继承链中查找方法（含私有）。 */
    private static Method findMethod(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Object call(Object target, String name, Object def) {
        try {
            Method m = findMethod(target.getClass(), name);
            return m != null ? m.invoke(target) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static Object callEither(Object target, String n1, String n2, Object def) {
        Object v = call(target, n1, null);
        if (v != null) return v;
        return call(target, n2, def);
    }

    // ═══════════════════ 框架版本日志（时间线） ═════════════════

    /** 框架版本日志：按版本倒序返回，仪表盘「框架版本日志」时间线展示。 */
    @GetMapping("/version-log")
    public Map<String, Object> versionLog() {
        List<Map<String, Object>> versions = new ArrayList<>();

        versions.add(versionEntry("v1.0.0", "2026-08-07", "正式版",
                List.of(
                        "QQ 官方适配器：群聊/单聊消息、富媒体收发、系统事件（加群/退群/加好友）",
                        "OneBot 反向 WS 适配器（Napcat 兼容）",
                        "插件体系：@Command 语法糖、PluginStorage 持久化、PluginConfig 动态配置面板、运行时扫描/卸载",
                        "控制台：仪表盘（消息趋势 7/15/30 天）、消息监控、群聊/单聊管理、插件市场",
                        "运行健康：系统资源 / QPS 实时曲线 / 线程池明细 / 经济·性能两套模板推荐",
                        "安全中心：PIN 修改、审计日志（登录/SQL/备份/卸载留痕）",
                        "备份恢复：业务库/日志库/全部三选，在线导出 zip + 恢复前自动快照")));

        versions.add(versionEntry("v0.9.0", "2026-08-06", "开发里程碑",
                List.of(
                        "框架骨架：多模块 Maven + JDK25 虚拟线程 + 事件分发管线",
                        "控制台基座：登录鉴权（PIN+会话 Cookie）、侧边栏导航、亮/暗主题",
                        "数据库体系：框架库/日志库/每机器人实例库三级 H2 存储")));

        versions.add(versionEntry("Next", "", "规划中",
                List.of(
                        "定时任务中心（独立 scheduler 模块）",
                        "数据统计：消息热力图 / 活跃群与用户 TOP",
                        "对话调试台：模拟消息跑 pipeline",
                        "插件在线市场")));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current", "v1.0.0");
        m.put("versions", versions);
        return m;
    }

    private static Map<String, Object> versionEntry(String version, String date, String tag, List<String> items) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("version", version);
        e.put("date", date);
        e.put("tag", tag);
        e.put("items", items);
        return e;
    }
}
