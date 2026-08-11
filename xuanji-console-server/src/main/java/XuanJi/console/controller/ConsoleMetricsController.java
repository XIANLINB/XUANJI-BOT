package XuanJi.console.controller;

import XuanJi.console.service.ConsoleQueryService;
import XuanJi.console.service.SystemInfoService;
import XuanJi.console.service.TuneAdvisor;
import XuanJi.core.concurrent.ThreadPoolRegistry;
import XuanJi.core.metric.QpsMeter;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@XuanJiApi
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
                "avg60", QpsMeter.avg(60),
                "outCurrent", QpsMeter.currentOut(),
                "outPeak60", QpsMeter.peakOut(60),
                "outAvg60", QpsMeter.avgOut(60),
                "inTotal60", QpsMeter.total(60),
                "outTotal60", QpsMeter.totalOut(60)));

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

    /** QPS 逐秒曲线（旧→新），默认最近 60 秒。含入站 + 出站双通道。 */
    @GetMapping("/metrics/qps")
    public Map<String, Object> qps(@RequestParam(defaultValue = "60") int seconds) {
        int n = Math.min(Math.max(seconds, 1), 60);
        long[] vals = QpsMeter.snapshot(n);
        long[] outVals = QpsMeter.snapshotOut(n);
        long nowSec = System.currentTimeMillis() / 1000L;
        List<String> labels = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            labels.add(HHMSS.format(Instant.ofEpochSecond(nowSec - i)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("values", vals);
        m.put("outValues", outVals);
        m.put("current", QpsMeter.current());
        m.put("peak60", QpsMeter.peak(60));
        m.put("avg60", QpsMeter.avg(60));
        m.put("outCurrent", QpsMeter.currentOut());
        m.put("outPeak60", QpsMeter.peakOut(60));
        m.put("outAvg60", QpsMeter.avgOut(60));
        return m;
    }

    /** 性能模板推荐：mode = eco | perf。 */
    @GetMapping("/tune/recommend")
    public Map<String, Object> recommend(@RequestParam(defaultValue = "eco") String mode) {
        return tuneAdvisor.recommend(mode);
    }

    /** 应用指定模板：把模板参数写入全局配置（出站节奏立即生效，线程池参数需重启）。 */
    @PostMapping("/tune/apply")
    public Map<String, Object> applyTune(@RequestBody(required = false) Map<String, Object> body) {
        String mode = body == null ? "eco" : String.valueOf(body.getOrDefault("mode", "eco"));
        return tuneAdvisor.apply(mode);
    }

    /** 一键恢复默认：删除所有 tune.* 配置 + 还原 outbound.pace_ms 为 0。
     *  二次确认（请求体 confirm="RESET"）防误触。线程池参数需重启框架后才回到代码硬编码默认值。 */
    @PostMapping("/tune/reset")
    public Map<String, Object> resetTune(@RequestBody(required = false) Map<String, Object> body) {
        String confirm = body == null ? "" : String.valueOf(body.getOrDefault("confirm", ""));
        if (!"RESET".equals(confirm)) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("status", "error");
            err.put("msg", "二次确认失败：请求体 {\"confirm\":\"RESET\"} 必须精确输入 RESET");
            return err;
        }
        return tuneAdvisor.reset();
    }

    /** 当前使用中的模板（从未应用则 mode=none）。 */
    @GetMapping("/tune/current")
    public Map<String, Object> currentTune() {
        return tuneAdvisor.current();
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

        // ── v1.3.1 入群申请审批能力（2026-08-12）──
        versions.add(versionEntry("v1.3.1", "2026-08-12", "正式版",
                List.of(
                        "入群申请审批：join_request_id 修正为必传（修复平台 11004 无效或已过期的审批令牌）",
                        "审批接口对齐官方：请求体 op=approve/decline、路径 /approval_join_request/ 修正",
                        "入群申请列表接口修正：path=/join_request_list、分页参数 cursor，verify_info 双数据源解析（列表拉取 admin_review_qa / 事件推送 verify_message）",
                        "插件审批命令组：#查看入群申请列表 / #同意 / #拒绝 / #全部同意 / #全部拒绝",
                        "修复去重误判：入群申请等系统事件改用 join_request_id 作事件 ID，不再退化成内容键误丢",
                        "框架只下发完整字段（getJoinRequestInfo），审批判定逻辑完全由插件实现",
                        "插件能力类型化：PluginServices 全部方法返回类型化对象（JoinRequest/JoinRequestList/GroupInfo/BotGroupState/GroupMember/UserInfo/GroupBotRole/OpResult），告别裸 JSON",
                        "错误信息精确定位：平台 500/504 错误透传平台 message+错误码+排查建议（如禁言 10013），撤回/审批缺参前置校验返回具体原因",
                        "开发指南页面改四 Tab：开发指南 / 事件（事件类型+对象字段）/ 动作（参考 Shiro 风格，方法+参数+返回值）/ 注解（全注解+字段+示例）")));

        // ── v1.3.0 插件群管能力（2026-08-12）──
        versions.add(versionEntry("v1.3.0", "2026-08-12", "正式版",
                List.of(
                        "管理操作日志：qqbot_op_log 表 + dispatch 统一埋点 + 前端操作日志页（筛选/搜索/失败留痕）",
                        "PluginServices 命令方法统一返回 OpResult（成功提示/失败原因），插件可面向用户展示",
                        "禁言/撤回群聊方法名加 Group 前缀（muteGroupMember / recallGroupMessage），批量禁言 muteGroupMembers",
                        "框架负责事件解析过滤：getMentionedUsers 返回已过滤可操作目标（排除机器人/自己），@Arg 原生参数解析",
                        "撤回最近 N 条消息：框架查库 + 2 分钟窗口 + 逐条执行，默认条数下沉框架",
                        "系统事件页新增群名称列，CSV 导出同步")));

        // ── v1.2.0 QQ 接口限频治理 + 缺失接口（2026-08-12）──
        versions.add(versionEntry("v1.2.0", "2026-08-12", "正式版",
                List.of(
                        "QQ 接口限频层 QqApiRateLimit：按 appId+接口类别分桶（QPS 令牌桶 / QPM 分钟窗口），429 自动降档兜底",
                        "分享链接生成（POST /v2/generate_url_link，50 QPS）",
                        "互动事件响应（PUT /interactions/{id}，50 QPS）+ WS 入站 INTERACTION 自动回应",
                        "入群自动审批策略 6 接口（60 QPM）：列表/创建/修改/删除/执行/白名单",
                        "QQ clientSecret 加密落库（CredentialCipher AES-256-GCM，enc: 前缀零停机迁移）")));

        // ── v1.1.0 稳定性加固（2026-08-12）──
        versions.add(versionEntry("v1.1.0", "2026-08-12", "正式版",
                List.of(
                        "QQ API 熔断器真正生效：连续失败熔断 + 半开探针，冷却期快速失败",
                        "Pipeline 单条消息整体超时（默认 60s 可配置），慢 Stage 不再挂死线程",
                        "LLM 主对话瞬态错误重试（429/5xx/超时，指数退避 2 次）",
                        "出站执行器有界队列 + 调用线程同步执行兜底（防 OOM）",
                        "WS 优雅关闭（awaitTermination）+ 重连竞态守卫；Stage 异常不中断整链；泄漏 Map 惰性清理",
                        "前端 SSE 断线状态徽标 + 轮询失败提示；插件卸载即清副本残留")));

        // ── v1.0.0 正式版（2026-08-09）──
        versions.add(versionEntry("v1.0.0", "2026-08-09", "正式版",
                List.of(
                        "多平台多机器人：QQ 官方 XuanJiBot（WebSocket/Webhook）+ OneBot（Napcat 兼容），一个框架同时管理 N 个机器人",
                        "AI 能力引擎（LLM）：对话 / 人格角色扮演 / 长期记忆 / 用户画像 / 主动搭话",
                        "智能工具：@LlmTool 函数调用 / 意图路由（人话→命令）/ 自然语言建定时",
                        "Agent 自主会话 + MCP 服务桥接（外部工具无缝接入）",
                        "知识库 RAG / AI 审核 / AI 日报 / 图片理解 / 文生图 / 语音合成（TTS）/ 图文卡片渲染",
                        "多供应商多模型管理：一页配置 DeepSeek/智谱/小米/Fish 等，按能力（对话/视觉/语音/图像）选模型，支持多 Key 轮询",
                        "可视化控制台：仪表盘 / 消息监控 / 群聊·单聊管理 / 插件市场 / 定时任务 / 供应商管理 / 全套 AI 能力页",
                        "插件体系：@Command 语法糖 / PluginStorage 持久化 / 热加载 / 运行时扫描卸载",
                        "运行健康 + 安全中心 + 备份恢复：系统资源 / QPS / 线程池 / 审计日志 / 在线导出恢复")));

        // ── v0.2.0 开发版（2026-08-07）──
        versions.add(versionEntry("v0.2.0", "2026-08-07", "开发版",
                List.of(
                        "框架骨架：多模块 Maven + JDK25 虚拟线程 + 事件分发管线",
                        "模块基座：xuanji-api（SPI）/ core（内核）/ adapter（平台）/ console（控制台）/ starter（启动器）",
                        "控制台基座：登录鉴权（PIN + 会话 Cookie）/ 侧边栏导航 / 亮暗主题",
                        "数据库体系：框架库 / 日志库 / 每机器人实例库 三级 H2 存储",
                        "QQ 官方适配器：群聊·单聊消息 / 富媒体收发 / 系统事件（加群退群加好友）",
                        "OneBot 反向 WS 适配器（Napcat 兼容）",
                        "插件体系：@Command / PluginStorage / PluginConfig 动态面板 / 运行时扫描卸载",
                        "备份恢复 + 安全中心：业务库/日志库三选导出、恢复前自动快照、PIN 修改与审计留痕")));

        // ── v0.1.0 开发版（2026-07-30）──
        versions.add(versionEntry("v0.1.0", "2026-07-30", "开发版",
                List.of(
                        "项目初始化：Git 仓库 + 多模块 Maven 工程搭建（api / core / adapter / starter / sdk）",
                        "框架初始架构：事件模型与统一事件分发管线设计",
                        "QQ 官方适配器雏形：XuanJiBot 接入配置 / WebSocket 连接 / 群消息收发打底",
                        "控制台最早形态：登录引导 / 基础页面框架")));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current", "v1.3.1");
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
