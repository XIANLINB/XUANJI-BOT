package XuanJi.adapter.qqbot.websocket;

import XuanJi.adapter.qqbot.config.QqPlatformConfig;
import XuanJi.adapter.qqbot.registry.AccessTokenService;
import XuanJi.core.concurrent.ThreadPoolRegistry;
import XuanJi.core.pipeline.BotPipeline;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * QQ XuanJiBot WebSocket 连接管理器
 *
 * <p>管理所有机器人的 WebSocket 连接生命周期，是 WebSocket 模块的核心协调者。
 * 负责机器人注册、连接启动/停止、健康监控和状态查询。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>连接管理</b> — 维护 {@code robotId:envType} 到 {@link QqBotWsClient} 的映射</li>
 *   <li><b>线程池共享</b> — 创建并管理心跳调度池和连接池，所有客户端共用，避免资源浪费</li>
 *   <li><b>健康监控</b> — 定期检查所有连接状态，自动恢复断开的连接</li>
 *   <li><b>状态查询</b> — 提供全局指标和单个机器人状态的查询接口</li>
 * </ul>
 *
 * <h3>线程池配置</h3>
 * <ul>
 *   <li>心跳调度池 — 核心线程数 = max(2, CPU 核心数)，守护线程</li>
 *   <li>连接池 — 核心 4 线程，最大 16 线程，队列容量 200，CallerRunsPolicy 拒绝策略</li>
 * </ul>
 *
 * <h3>健康检查</h3>
 * <p>每 30 秒执行一次（初始延迟 15 秒），检查所有客户端的连接状态。
 * 对于已断开但仍标记为运行中的客户端，自动尝试恢复连接。
 *
 * <p>线程安全性：使用 {@link ConcurrentHashMap} 存储客户端和配置，支持并发访问。
 *
 * @see QqBotWsClient  单个机器人的 WebSocket 客户端
 * @see GatewayService  获取 WebSocket 网关地址
 * @see EventSink 处理接收到的事件
 */
@Slf4j
@Component
public class QqBotWsManager {

    /** 事件流水线，注入到每个新建的客户端中 */
    private final BotPipeline botPipeline;

    /** 网关地址服务，用于获取 WebSocket 连接地址 */
    private final GatewayService gatewayService;

    /** AccessToken 管理服务，注入到每个新建的客户端中 */
    private final AccessTokenService accessTokenService;

    /** API 服务（注入到客户端，启动后调 /users/@me 同步机器人信息） */
    private final XuanJi.adapter.qqbot.api.QqApiService qqApiService;

    /** 平台库 Repository（注入到客户端，upsertBotInfo 写入 qqbot_botinfo） */
    private final XuanJi.adapter.qqbot.storage.QqBotRepository qqBotRepository;
    /** 全局配置（tune.* 模板参数读取：ws_core/ws_max/heartbeat_pool，未配置用代码默认值）。 */
    private final XuanJi.core.config.ConfigService configService;

    /** 读全局配置整数（key 不存在/非法时用默认值）。 */
    private int cfgInt(String key, int def) {
        try {
            Map<String, String> g = configService != null ? configService.getGlobalConfig() : Map.of();
            String v = g.get(key);
            if (v != null && !v.isBlank()) {
                int n = Integer.parseInt(v.trim());
                if (n > 0) return n;
            }
        } catch (Exception ignored) { /* 解析失败用默认值 */ }
        return def;
    }

    /**
     * 默认的事件订阅意图位掩码
     *
     * <p>当注册机器人时未指定 intents 时使用此默认值。
     * 默认值 104857600 = GROUP_AT_MESSAGE | C2C_MESSAGE | DIRECT_MESSAGE | INTERACTION | GUILDS | GUILD_MEMBERS
     */

    /**
     * 是否使用新开放平台（全局配置）
     *
     * <p>true = 使用新开放平台（api.bot.qq.com），false = 使用老开放平台（api.sgroup.qq.com）
     * 此配置影响所有机器人的 API 地址和 Token 获取地址
     */
    private volatile boolean isNewOpenBot = false;

    /**
     * 活跃的 WebSocket 客户端映射表
     * key = "robotId:envType"，value = 对应的客户端实例
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final Map<String, QqBotWsClient> clients = new ConcurrentHashMap<>();

    /**
     * 机器人凭证注册表
     * key = "robotId:envType"，value = 机器人配置（AppID、AppSecret 等）
     * 用于在启动客户端时查找凭证
     */
    private final Map<String, RobotConfig> robotConfigs = new ConcurrentHashMap<>();

    /** 共享的心跳调度线程池（守护线程，应用退出时自动关闭） */
    private ScheduledExecutorService heartbeatScheduler;

    /** 共享的连接线程池（守护线程，用于异步执行 WebSocket 连接和重连） */
    private ExecutorService connectExecutor;

    /**
     * 构造函数，注入必要的服务
     *
     * @param botPipeline        事件流水线（Whitelist/RateLimit/权限等阶段在此生效）
     * @param gatewayService     网关地址服务
     * @param accessTokenService AccessToken 管理服务
     */
    public QqBotWsManager(BotPipeline botPipeline,
                           GatewayService gatewayService,
                           AccessTokenService accessTokenService,
                           XuanJi.adapter.qqbot.api.QqApiService qqApiService,
                           XuanJi.adapter.qqbot.storage.QqBotRepository qqBotRepository,
                           XuanJi.core.config.ConfigService configService) {
        this.botPipeline = botPipeline;
        this.gatewayService = gatewayService;
        this.accessTokenService = accessTokenService;
        this.qqApiService = qqApiService;
        this.qqBotRepository = qqBotRepository;
        this.configService = configService;
    }

    /**
     * 初始化线程池
     *
     * <p>Spring 容器启动后自动调用，创建共享的心跳调度池和连接池。
     * 所有线程设为守护线程，不阻止 JVM 退出。
     */
    @PostConstruct
    public void init() {
        // 心跳调度池：核心线程数 = 读 tune.heartbeat_pool，未配置默认 max(2, CPU 核心数)
        int heartbeatThreads = cfgInt("tune.heartbeat_pool", Math.max(2, Runtime.getRuntime().availableProcessors()));
        heartbeatScheduler = Executors.newScheduledThreadPool(heartbeatThreads, r -> {
            Thread t = new Thread(r, "ws-heartbeat-shared");
            t.setDaemon(true);
            return t;
        });

        // 连接池：核心/最大读 tune.ws_core / tune.ws_max，未配置默认 4/16；队列 200，满时由调用线程执行
        int wsCore = cfgInt("tune.ws_core", 4);
        int wsMax = cfgInt("tune.ws_max", 16);
        connectExecutor = new ThreadPoolExecutor(
                wsCore, wsMax, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "ws-connect-shared");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行，避免丢弃任务
        );

        log.info("[BotWS Manager] 初始化完成：心跳池={}, 连接池={}/{}", heartbeatThreads, wsCore, wsMax);

        // 注册到监控：连接/重连线程池 + 心跳调度池实时状态
        ThreadPoolRegistry.register("QQ-WS连接池(共享)", () -> {
            ThreadPoolExecutor e = (ThreadPoolExecutor) connectExecutor;
            return new ThreadPoolRegistry.PoolInfo(
                    "QQ-WS连接池(共享)", "ThreadPoolExecutor",
                    e.getCorePoolSize(), e.getMaximumPoolSize(),
                    e.getActiveCount(), e.getPoolSize(), e.getQueue().size(),
                    e.getCompletedTaskCount(), "WS 连接/重连任务，队列满 CallerRuns");
        });
        ThreadPoolRegistry.register("QQ-WS心跳池(共享)", () -> {
            ScheduledThreadPoolExecutor e = (ScheduledThreadPoolExecutor) heartbeatScheduler;
            return new ThreadPoolRegistry.PoolInfo(
                    "QQ-WS心跳池(共享)", "ScheduledThreadPool",
                    e.getCorePoolSize(), e.getCorePoolSize(),
                    e.getActiveCount(), e.getPoolSize(), e.getQueue().size(),
                    e.getCompletedTaskCount(), "心跳/鉴权定时任务，核心=max(2,CPU核数)");
        });
    }

    /**
     * 销毁时关闭所有连接和线程池
     *
     * <p>Spring 容器关闭前自动调用，优雅地停止所有 WebSocket 连接。
     */
    @PreDestroy
    public void destroy() {
        log.info("[BotWS Manager] 关闭所有连接...");
        clients.values().forEach(QqBotWsClient::stop);
        clients.clear();
        if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        if (connectExecutor != null) connectExecutor.shutdownNow();
    }

    /**
     * 设置是否使用新开放平台（全局配置）
     *
     * <p>此配置影响所有机器人的 API 地址和 Token 获取地址。
     * 需要在注册机器人之前调用。
     *
     * @param isNewOpenBot true=新平台 api.bot.qq.com，false=老平台 api.sgroup.qq.com
     */
    public void setNewOpenBot(boolean isNewOpenBot) {
        this.isNewOpenBot = isNewOpenBot;
        log.debug("[BotWS Manager] 设置开放平台版本: isNewOpenBot={}", isNewOpenBot);
    }

    /**
     * 注册机器人配置
     *
     * <p>将机器人的凭证信息存储到内存注册表中，供后续启动连接时使用。
     *
     * @param robotId   机器人 ID
     * @param envType   环境类型（SANDBOX / PRODUCTION）
     * @param appId     机器人 AppID
     * @param appSecret 机器人 AppSecret（明文）
     * @param intents   事件订阅意图位掩码，0 表示使用默认值
     */
    public void registerRobot(String robotId, String envType, String appId, String appSecret, int intents) {
        String key = robotId + ":" + envType;
        int finalIntents = intents > 0 ? intents : QqPlatformConfig.DEFAULT_INTENTS;
        robotConfigs.put(key, new RobotConfig(robotId, envType, appId, appSecret, finalIntents));
        log.info("[BotWS Manager] 注册机器人: robotId={}, env={}, intents={}", robotId, envType, finalIntents);
    }

    /**
     * 启动指定机器人的 WebSocket 连接
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @throws RuntimeException 如果机器人未注册
     */
    public void start(String robotId, String envType) {
        String key = robotId + ":" + envType;
        RobotConfig config = robotConfigs.get(key);
        if (config == null) {
            throw new RuntimeException("机器人未注册: " + key);
        }
        startClient(config);
    }

    /**
     * 启动所有已注册的机器人
     *
     * <p>遍历所有已注册的机器人配置，逐个启动连接。
     * 单个启动失败不影响其他机器人，会记录失败计数。
     */
    public void startAll() {
        log.info("[BotWS Manager] 启动所有已注册的机器人 ({} 个)...", robotConfigs.size());
        int started = 0;
        int failed = 0;
        for (RobotConfig config : robotConfigs.values()) {
            try {
                startClient(config);
                started++;
            } catch (Exception e) {
                failed++;
                log.error("[BotWS Manager] 启动失败: robotId={}, error={}", config.robotId, e.getMessage());
            }
        }
        log.info("[BotWS Manager] 启动完成: 成功={}, 失败={}", started, failed);
    }

    /**
     * 停止指定机器人的所有 WebSocket 连接
     *
     * <p>移除以该 robotId 开头的所有客户端（可能包含多个环境类型）。
     *
     * @param robotId 机器人 ID
     */
    public void stop(String robotId) {
        clients.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(robotId + ":")) {
                entry.getValue().stop();
                log.info("[BotWS Manager] 已停止: key={}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 重启指定机器人的 WebSocket 连接
     *
     * <p>先停止再启动，用于配置变更或连接异常时的手动恢复。
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     */
    public void restart(String robotId, String envType) {
        stop(robotId);
        start(robotId, envType);
    }

    // ===== 健康监控 =====

    /**
     * 定时健康检查（每 30 秒执行，初始延迟 15 秒）
     *
     * <p>检查所有客户端的连接状态，统计健康/重连中/已断开的数量。
     * 对于已断开但仍标记为运行中的客户端，自动尝试恢复连接。
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void healthCheck() {
        if (clients.isEmpty()) return;

        int healthy = 0, reconnecting = 0, disconnected = 0;
        for (Map.Entry<String, QqBotWsClient> entry : clients.entrySet()) {
            QqBotWsClient client = entry.getValue();
            switch (client.getState()) {
                case CONNECTED -> healthy++;
                case RECONNECTING, CONNECTING, IDENTIFYING -> reconnecting++;
                case DISCONNECTED -> {
                    disconnected++;
                    // 客户端标记为运行中但已断开，尝试自动恢复
                    if (client.isRunning()) {
                        log.warn("[BotWS Manager] 自动恢复断开连接: {}", entry.getKey());
                        try { client.start(); } catch (Exception e) {
                            log.error("[BotWS Manager] 恢复失败: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        // 有异常状态时才输出日志，避免正常时刷屏
        if (disconnected > 0 || reconnecting > 0) {
            log.info("[BotWS Manager] 健康检查: 健康={}, 重连中={}, 已断开={}", healthy, reconnecting, disconnected);
        }
    }

    // ===== 状态查询 =====

    /**
     * 获取指定机器人的 WebSocket 连接状态
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @return 连接状态枚举，未找到返回 DISCONNECTED
     */
    public QqBotWsClient.WsState getState(String robotId, String envType) {
        QqBotWsClient client = clients.get(robotId + ":" + envType);
        return client != null ? client.getState() : QqBotWsClient.WsState.DISCONNECTED;
    }

    /**
     * 检查指定机器人是否已连接
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @return true=已连接且正在运行
     */
    public boolean isConnected(String robotId, String envType) {
        QqBotWsClient client = clients.get(robotId + ":" + envType);
        return client != null && client.isRunning() && client.getState() == QqBotWsClient.WsState.CONNECTED;
    }

    /**
     * 获取全局 WebSocket 指标
     *
     * <p>统计所有客户端的连接状态分布和累计数据，用于监控面板展示。
     *
     * @return 包含 totalClients、connected、reconnecting、disconnected、totalEvents、totalReconnects 的 Map
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        int connected = 0, reconnecting = 0, disconnected = 0;
        long totalEvents = 0, totalReconnects = 0;

        for (QqBotWsClient client : clients.values()) {
            switch (client.getState()) {
                case CONNECTED -> connected++;
                case RECONNECTING, CONNECTING, IDENTIFYING -> reconnecting++;
                case DISCONNECTED -> disconnected++;
            }
            totalEvents += client.getTotalEvents();
            totalReconnects += client.getTotalReconnects();
        }

        metrics.put("totalClients", clients.size());
        metrics.put("connected", connected);
        metrics.put("reconnecting", reconnecting);
        metrics.put("disconnected", disconnected);
        metrics.put("totalEvents", totalEvents);
        metrics.put("totalReconnects", totalReconnects);
        return metrics;
    }

    /**
     * 获取指定机器人的详细状态
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @return 包含 running、state、totalEvents、totalReconnects 的 Map
     */
    public Map<String, Object> getStatus(String robotId, String envType) {
        QqBotWsClient client = clients.get(robotId + ":" + envType);
        Map<String, Object> status = new LinkedHashMap<>();
        if (client != null) {
            status.put("running", client.isRunning());
            status.put("state", client.getState().name());
            status.put("totalEvents", client.getTotalEvents());
            status.put("totalReconnects", client.getTotalReconnects());
        } else {
            status.put("running", false);
            status.put("state", "DISCONNECTED");
        }
        return status;
    }

    /**
     * 获取所有机器人的状态列表
     *
     * @return 每个元素包含 key、robotId、envType、state、running、totalEvents、totalReconnects
     */
    public List<Map<String, Object>> getAllStatus() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, QqBotWsClient> entry : clients.entrySet()) {
            QqBotWsClient client = entry.getValue();
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("key", entry.getKey());
            status.put("robotId", client.getRobotId());
            status.put("envType", client.getEnvType());
            status.put("state", client.getState().name());
            status.put("running", client.isRunning());
            status.put("totalEvents", client.getTotalEvents());
            status.put("totalReconnects", client.getTotalReconnects());
            list.add(status);
        }
        return list;
    }

    // ===== 内部方法 =====

    /**
     * 启动单个机器人的 WebSocket 客户端
     *
     * <p>流程：停止旧客户端 -> 获取网关地址 -> 创建新客户端 -> 启动连接
     *
     * @param config 机器人配置
     */
    private void startClient(RobotConfig config) {
        String key = config.robotId + ":" + config.envType;

        // 如果已有旧客户端，先停止
        QqBotWsClient old = clients.get(key);
        if (old != null) old.stop();

        // 获取 WebSocket 网关地址
        String gatewayUrl = gatewayService.getGateway(config.appId, config.appSecret, config.envType, isNewOpenBot);

        // 创建新客户端，注入共享线程池
        QqBotWsClient client = new QqBotWsClient(
                config.robotId, config.envType,
                config.appId, config.appSecret,
                gatewayUrl, botPipeline, gatewayService, accessTokenService,
                qqApiService, qqBotRepository,
                config.intents, isNewOpenBot,
                heartbeatScheduler,
                connectExecutor);

        clients.put(key, client);
        client.start();
        log.info("[BotWS Manager] 启动连接: robotId={}, env={}, 总连接数={}",
                config.robotId, config.envType, clients.size());
    }

    /**
     * 机器人配置记录（内存中存储）
     *
     * @param robotId   机器人 ID
     * @param envType   环境类型
     * @param appId     AppID
     * @param appSecret AppSecret（明文）
     */
    public record RobotConfig(String robotId, String envType, String appId, String appSecret, int intents) {}
}
