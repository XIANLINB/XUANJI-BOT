package dev.xuanji.adapter.qq.websocket;

import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.EventSink;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import dev.xuanji.api.json.Json;

import dev.xuanji.adapter.qq.registry.AccessTokenService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单个机器人的 QQ Bot WebSocket 客户端
 *
 * <p>负责与 QQ 开放平台 WebSocket 网关建立长连接，完成握手、鉴权、心跳维持和事件接收。
 * 每个机器人实例拥有一个独立的客户端，由 {@link QqBotWsManager} 统一管理。
 *
 * <h3>生命周期</h3>
 * <p>connect -> Hello(op=10) -> Identify(op=2) -> Ready(DISPATCH) -> 心跳循环 + 事件接收
 * <ol>
 *   <li><b>connect</b> — 通过 Java HttpClient 建立 WebSocket 连接</li>
 *   <li><b>Hello</b> — 收到网关返回的心跳间隔(heartbeat_interval)</li>
 *   <li><b>Identify</b> — 发送鉴权信息（token + intents + shard）</li>
 *   <li><b>Ready</b> — 鉴权成功，获取 session_id，进入正常工作状态</li>
 *   <li><b>心跳</b> — 按 interval 周期发送心跳包(op=1)，超时未收到 ACK 则重连</li>
 *   <li><b>事件接收</b> — 收到 op=0 的 Dispatch 事件，分发给 {@link EventSink}</li>
 * </ol>
 *
 * <h3>重连机制</h3>
 * <p>断线后使用指数退避策略重连（1s, 2s, 4s, ..., 最大 60s）。
 * 重连失败时会重新获取网关地址，因为网关地址可能已变更。
 *
 * <h3>多机器人优化</h3>
 * <ul>
 *   <li>使用外部传入的共享线程池（心跳调度池 + 连接池），不再每个客户端创建独立线程池</li>
 *   <li>共享 HttpClient（静态单例 {@link #WS_HTTP_CLIENT}），复用 TCP 连接</li>
 *   <li>支持运行时通过 {@link #stop()} 停止和 {@link #start()} 重启</li>
 * </ul>
 *
 * <h3>线程安全性</h3>
 * <p>所有可变状态字段使用 {@code volatile} 或原子类型保证可见性。
 * 状态切换通过 {@link AtomicBoolean#compareAndSet} 保证原子性。
 *
 * @see QqBotWsManager 管理多个客户端实例的生命周期
 * @see GatewayService  提供 WebSocket 网关地址
 * @see EventSink 处理从 WebSocket 接收到的事件
 */
@Slf4j
public class QqBotWsClient {

    // ==================== 配置字段（构造时注入，不可变） ====================

    /** 机器人 ID（数据库主键），用于标识和日志记录 */
    private final Long robotId;

    /** 环境类型："SANDBOX" 或 "PRODUCTION"，决定连接的网关环境 */
    private final String envType;

    /** 机器人 AppID，用于获取 AccessToken 和鉴权 */
    private final String appId;

    /** 机器人 AppSecret（明文），用于获取 AccessToken */
    private final String appSecret;

    /** WebSocket 网关地址（如 wss://api.sgroup.qq.com/websocket），运行时可更新 */
    private volatile String gatewayUrl;

    /** 事件分发器，将接收到的事件路由到对应的 Handler */
    private final EventSink eventDispatcher;

    /** 网关服务，用于重连时获取新的网关地址 */
    private final GatewayService gatewayService;

    /** AccessToken 管理服务，用于鉴权时获取有效的 Token */
    private final AccessTokenService accessTokenService;

    /**
     * 事件订阅意图（intents），位掩码表示
     * 例如：104857600 = (1 << 25) | (1 << 30) 表示订阅单聊消息和频道 @消息
     * 具体值通过 xuanji-robots.yml 配置
     */
    private final int intents;

    /** 是否使用新开放平台（true=新平台 api.bot.qq.com，false=老平台 api.sgroup.qq.com） */
    private final boolean isNewOpenBot;

    // ==================== 运行时状态（volatile 保证多线程可见性） ====================

    /** 当前 WebSocket 连接实例，null 表示未连接 */
    private volatile WebSocket webSocket;

    /** 心跳定时任务的 Future，用于取消心跳 */
    private volatile ScheduledFuture<?> heartbeatFuture;

    /** 上次收到心跳 ACK 的时间戳（毫秒），用于超时检测 */
    private volatile long lastHeartbeatAck = 0;

    /** QQ 平台分配的会话 ID，用于 Resume 恢复连接 */
    private volatile String sessionId;

    /** 最后收到的事件序列号（s 字段），用于 Resume 恢复连接 */
    private volatile int lastSeq = -1;

    /** 客户端是否处于运行状态（CAS 操作保证线程安全） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 重连延迟秒数（指数退避：1, 2, 4, 8, ..., 最大 60） */
    private final AtomicInteger reconnectDelay = new AtomicInteger(1);

    /** 随机数生成器，用于心跳间隔添加抖动（避免多客户端同时发送心跳） */
    private final Random random = new Random();

    // ==================== 共享线程池（由 Manager 注入，所有机器人共用） ====================

    /** 共享的心跳调度线程池，用于定时发送心跳包和超时检测 */
    private final ScheduledExecutorService scheduler;

    /** 共享的连接线程池，用于异步执行 WebSocket 连接和重连 */
    private final ExecutorService connectExecutor;

    /** 共享的 HttpClient（静态单例），用于建立 WebSocket 连接，所有客户端复用 */
    private static final HttpClient WS_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * WebSocket 连接状态枚举
     * <ul>
     *   <li>DISCONNECTED — 未连接（初始状态或连接断开后）</li>
     *   <li>CONNECTING — 正在建立 WebSocket 连接</li>
     *   <li>IDENTIFYING — 已连接，正在发送鉴权（Identify）</li>
     *   <li>CONNECTED — 已就绪，正常接收事件和发送心跳</li>
     *   <li>RECONNECTING — 连接断开，正在等待重连</li>
     * </ul>
     */
    public enum WsState {
        DISCONNECTED, CONNECTING, IDENTIFYING, CONNECTED, RECONNECTING
    }

    /** 当前连接状态 */
    private volatile WsState state = WsState.DISCONNECTED;

    /** 连接成功的时间戳（毫秒），用于计算在线时长 */
    private volatile long connectedAt;

    /** 最近一次错误信息，用于状态查询接口展示 */
    private volatile String lastError;

    /** 累计重连次数，用于监控和诊断 */
    private volatile long totalReconnects = 0;

    /** 累计处理事件数，用于监控和诊断 */
    private volatile long totalEvents = 0;

    /**
     * 构造函数
     *
     * @param robotId          机器人 ID
     * @param envType          环境类型（SANDBOX / PRODUCTION）
     * @param appId            机器人 AppID
     * @param appSecret        机器人 AppSecret（明文）
     * @param gatewayUrl       WebSocket 网关地址
     * @param eventDispatcher  事件分发器
     * @param gatewayService   网关地址服务（用于重连时获取新地址）
     * @param accessTokenService AccessToken 服务
     * @param intents          事件订阅意图位掩码
     * @param isNewOpenBot     是否使用新开放平台
     * @param scheduler        共享心跳调度线程池
     * @param connectExecutor  共享连接线程池
     */
    public QqBotWsClient(Long robotId, String envType, String appId, String appSecret,
                          String gatewayUrl, EventSink eventDispatcher,
                          GatewayService gatewayService, AccessTokenService accessTokenService,
                          int intents, boolean isNewOpenBot,
                          ScheduledExecutorService scheduler,
                          ExecutorService connectExecutor) {
        this.robotId = robotId;
        this.envType = envType;
        this.appId = appId;
        this.appSecret = appSecret;
        this.gatewayUrl = gatewayUrl;
        this.eventDispatcher = eventDispatcher;
        this.gatewayService = gatewayService;
        this.accessTokenService = accessTokenService;
        this.intents = intents;
        this.isNewOpenBot = isNewOpenBot;
        this.scheduler = scheduler;
        this.connectExecutor = connectExecutor;
    }

    /**
     * 启动 WebSocket 连接
     *
     * <p>使用 CAS 操作确保同一客户端不会重复启动。
     * 连接过程在共享连接线程池中异步执行，不阻塞调用线程。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[BotWS] 已在运行中, robotId={}", robotId);
            return;
        }
        log.info("[BotWS] 启动连接, robotId={}, env={}, gateway={}", robotId, envType, gatewayUrl);
        connectExecutor.submit(this::doConnect);
    }

    /**
     * 停止 WebSocket 连接
     *
     * <p>使用 CAS 操作确保不会重复停止。
     * 停止后会取消心跳定时任务、关闭 WebSocket 连接，并将状态置为 DISCONNECTED。
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("[BotWS] 停止连接, robotId={}", robotId);
        cancelHeartbeat();
        closeWebSocket();
        state = WsState.DISCONNECTED;
    }

    // ==================== 状态查询方法 ====================

    /** @return 客户端是否处于运行状态 */
    public boolean isRunning() { return running.get(); }

    /** @return 当前 WebSocket 连接状态 */
    public WsState getState() { return state; }

    /** @return 连接成功的时间戳（毫秒），未连接时为 0 */
    public long getConnectedAt() { return connectedAt; }

    /** @return 最近一次错误信息，无错误时为 null */
    public String getLastError() { return lastError; }

    /** @return 机器人 ID */
    public Long getRobotId() { return robotId; }

    /** @return 环境类型（SANDBOX / PRODUCTION） */
    public String getEnvType() { return envType; }

    /** @return 累计重连次数 */
    public long getTotalReconnects() { return totalReconnects; }

    /** @return 累计处理事件数 */
    public long getTotalEvents() { return totalEvents; }

    /** @return 上次收到心跳 ACK 的时间戳（毫秒） */
    public long getLastHeartbeatAck() { return lastHeartbeatAck; }

    /** @return QQ 平台分配的会话 ID（Ready 后才有值） */
    public String getSessionId() { return sessionId; }

    /** @return 最后收到的事件序列号 */
    public int getLastSeq() { return lastSeq; }

    // ===== 连接逻辑 =====

    /**
     * 执行 WebSocket 连接
     *
     * <p>通过 Java HttpClient 异步建立 WebSocket 连接。
     * 连接成功后，WsListener.onOpen 会被回调，随后进入 Hello 握手流程。
     * 连接失败时记录错误并触发重连。
     */
    private void doConnect() {
        try {
            state = WsState.CONNECTING;
            closeWebSocket(); // 先关闭旧连接

            // 异步建立 WebSocket 连接，超时 10 秒
            CompletableFuture<WebSocket> future = WS_HTTP_CLIENT.newWebSocketBuilder()
                    .buildAsync(URI.create(gatewayUrl), new WsListener());

            // 等待连接建立（onOpen 回调会在 future 完成前触发）
            future.get(10, TimeUnit.SECONDS);
            log.info("[BotWS] WebSocket 连接完成, robotId={}", robotId);

        } catch (Exception e) {
            log.error("[BotWS] 连接失败, robotId={}, error={}", robotId, e.getMessage());
            lastError = e.getMessage();
            state = WsState.DISCONNECTED;
            scheduleReconnect(); // 连接失败，触发重连
        }
    }

    /**
     * 发送 Identify 鉴权消息（op=2）
     *
     * <p>向网关发送鉴权信息，包含 AccessToken、intents（事件订阅掩码）和 shard（分片信息）。
     * 鉴权成功后网关会推送 READY 事件。
     */
    private void sendIdentify() {
        state = WsState.IDENTIFYING;
        String accessToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);
        log.info("[BotWS] 获取AccessToken: robotId={}, env={}, tokenLen={}",
                robotId, envType, accessToken != null ? accessToken.length() : 0);

        // 构建 Identify 消息：op=2 表示鉴权
        ObjectNode identify = Json.obj();
        identify.put("op", 2);
        ObjectNode d = Json.obj();
        d.put("token", "QQBot " + accessToken);  // 鉴权令牌格式：QQBot {ACCESS_TOKEN}
        d.put("intents", intents);                // 事件订阅意图位掩码
        d.put("shard", Json.arr().add(0).add(1));  // 分片：[当前分片, 总分片数]
        d.put("properties", Json.obj());    // 连接属性（可选）
        identify.put("d", d);

        String identifyJson = identify.toString();
        log.info("[BotWS] Identify 消息: {}", identifyJson);
        send(identifyJson);
        log.info("[BotWS] Identify 已发送, robotId={}, intents={}", robotId, intents);
    }

    /**
     * 发送 Resume 恢复消息（op=6）
     *
     * <p>当连接意外断开后，如果 session_id 和 seq 有效，可以尝试 Resume 恢复会话，
     * 而不是重新 Identify。Resume 可以避免丢失断线期间的事件。
     */
    private void sendResume() {
        String accessToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);
        ObjectNode resume = Json.obj();
        resume.put("op", 6);
        ObjectNode d = Json.obj();
        d.put("token", "QQBot " + accessToken);
        d.put("session_id", sessionId);  // 之前的会话 ID
        d.put("seq", lastSeq);           // 最后的事件序列号
        resume.put("d", d);
        send(resume.toString());
        log.info("[BotWS] Resume 已发送, robotId={}, seq={}", robotId, lastSeq);
    }

    // ===== 心跳 =====

    /**
     * 启动心跳定时任务
     *
     * <p>按照网关指定的心跳间隔周期性发送心跳包（op=1）。
     * 首次心跳添加随机抖动（0~10% 的 interval），避免多个客户端同时发送心跳。
     * 同时检测心跳 ACK 超时：如果超过 2 个心跳周期未收到 ACK，判定连接已断开并触发重连。
     *
     * @param intervalMs 网关指定的心跳间隔（毫秒），来自 Hello 消息的 heartbeat_interval 字段
     */
    private void startHeartbeat(int intervalMs) {
        cancelHeartbeat(); // 取消已有心跳任务

        // 添加 0~10% 的随机抖动，避免所有客户端同时发送心跳
        int jitter = (int) (intervalMs * 0.1 * random.nextDouble());
        long initialDelay = intervalMs + jitter;

        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                // 心跳 ACK 超时检测：如果上次 ACK 距今超过 2 个心跳周期，判定连接异常
                if (lastHeartbeatAck > 0) {
                    long elapsed = System.currentTimeMillis() - lastHeartbeatAck;
                    if (elapsed > intervalMs * 2) {
                        log.warn("[BotWS] 心跳ACK超时({}ms)，触发重连, robotId={}", elapsed, robotId);
                        cancelHeartbeat();
                        state = WsState.DISCONNECTED;
                        closeWebSocket();
                        if (running.get()) scheduleReconnect();
                        return;
                    }
                }

                // 发送心跳包：op=1, d=null
                ObjectNode hb = Json.obj();
                hb.put("op", 1);
                hb.put("d", NullNode.instance);
                send(hb.toString());
                log.debug("[BotWS] 心跳已发送, robotId={}", robotId);
            } catch (Exception e) {
                log.warn("[BotWS] 心跳发送失败, robotId={}, error={}", robotId, e.getMessage());
            }
        }, initialDelay, initialDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消心跳定时任务
     * 取消后不再发送心跳包，也不会触发超时检测
     */
    private void cancelHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    // ===== 消息处理 =====

    /**
     * 处理从 WebSocket 收到的消息
     *
     * <p>根据 OpCode（op 字段）路由到不同的处理逻辑：
     * <ul>
     *   <li>op=0  — Dispatch 事件分发（正常业务事件）</li>
     *   <li>op=1  — 心跳请求（服务端要求客户端立即发送心跳）</li>
     *   <li>op=7  — 重连指令（服务端要求客户端重新连接）</li>
     *   <li>op=9  — Invalid Session（鉴权失败，需停止连接）</li>
     *   <li>op=10 — Hello（握手，包含心跳间隔）</li>
     *   <li>op=11 — Heartbeat ACK（心跳确认）</li>
     * </ul>
     *
     * @param text 原始 JSON 消息文本
     */
    private void handleMessage(String text) {
        WsPayload payload;
        try {
            payload = WsPayload.parse(text);
        } catch (Exception e) {
            log.warn("[BotWS] 消息解析失败, robotId={}, error={}", robotId, e.getMessage());
            return;
        }

        switch (payload.getOp()) {
            case 0 -> handleDispatch(payload);
            case 1 -> handleHeartbeatRequest();
            case 7 -> handleReconnect();
            case 9 -> handleInvalidSession(payload);
            case 10 -> handleHello(payload);
            case 11 -> {
                // 心跳 ACK：记录收到时间，用于超时检测
                lastHeartbeatAck = System.currentTimeMillis();
                log.debug("[BotWS] HeartbeatACK, robotId={}", robotId);
            }
            default -> log.debug("[BotWS] 未知 op={}, robotId={}", payload.getOp(), robotId);
        }
    }

    /**
     * 处理 Hello 消息（op=10）
     *
     * <p>Hello 是网关返回的第一条消息，包含心跳间隔（heartbeat_interval）。
     * 收到 Hello 后启动心跳定时任务，然后发送 Identify 鉴权消息。
     *
     * @param payload Hello 消息，d 字段包含 heartbeat_interval
     */
    private void handleHello(WsPayload payload) {
        int heartbeatInterval = payload.getD().path("heartbeat_interval").asInt(41250);
        log.info("[BotWS] Hello 收到, heartbeat_interval={}, robotId={}", heartbeatInterval, robotId);
        startHeartbeat(heartbeatInterval);
        sendIdentify();
    }

    /**
     * 处理 Dispatch 事件（op=0）
     *
     * <p>Dispatch 是正常的业务事件推送。如果是 READY 事件则记录会话信息并标记为已连接；
     * 否则将事件交给 {@link EventSink} 分发到对应的 Handler。
     *
     * @param payload Dispatch 消息，t 字段为事件类型，d 字段为事件数据
     */
    private void handleDispatch(WsPayload payload) {
        String eventType = payload.getT();
        ObjectNode data = payload.getD();
        Integer seq = payload.getS();

        // 更新序列号（用于 Resume）
        if (seq != null) lastSeq = seq;

        // READY 事件：鉴权成功，记录会话信息
        if ("READY".equals(eventType)) {
            state = WsState.CONNECTED;
            connectedAt = System.currentTimeMillis();
            lastHeartbeatAck = System.currentTimeMillis();
            reconnectDelay.set(1); // 重置重连延迟
            sessionId = data.path("session_id").asText();
            log.info("[BotWS] Ready! sessionId={}, robotId={}", sessionId, robotId);
            return;
        }

        if (data == null || eventType == null) return;

        // 记录收到的事件
        log.info("[BotWS] 收到事件: type={}, robotId={}, seq={}", eventType, robotId, seq);

        // 提取事件 ID 并分发给 EventSink
        String eventId = data.path("id").asText("");
        totalEvents++;

        // P3: 注入元数据到原始 ObjectNode（供 DispatchStage 桥接旧分发器）
        data.put("_eventType", eventType);
        data.put("_robotId", robotId);
        data.put("_envType", envType);
        if (eventId != null && !eventId.isEmpty()) {
            data.put("_eventId", eventId);
        }

        // P2: 转换为统一 BotEvent 并记录（验证抽象可用）
        try {
            Bot bot = new Bot("qq:" + appId, "qq", appId, dev.xuanji.api.adapter.Bot.Status.ONLINE, java.util.Set.of());
            dev.xuanji.api.event.BotEvent be = dev.xuanji.adapter.qq.converter.QqEventConverter.convert(bot, eventType, data, eventId);
            log.info("[BotEvent] 已转换: type={}, user={}, group={}, text={}",
                    be.type().fullName(), be.sender().nickname(),
                    be.group() != null ? be.group().groupId() : "私聊",
                    be.message() != null ? be.message().plainText() : "");

            // P3: 通过 BotPipeline 分发（替代旧 EventDispatcher）
            dev.xuanji.core.pipeline.BotPipeline.get().proceed(be);
        } catch (Exception e) {
            log.error("[BotEvent] 流水线处理异常: {}", e.getMessage(), e);
            // 回退到旧分发器
            eventDispatcher.dispatch(eventType, robotId, envType, data, eventId);
        }
    }

    /**
     * 处理心跳请求（op=1）
     * 服务端要求客户端立即发送一次心跳
     */
    private void handleHeartbeatRequest() {
        ObjectNode hb = Json.obj();
        hb.put("op", 1);
        hb.put("d", NullNode.instance);
        send(hb.toString());
    }

    /**
     * 处理重连指令（op=7）
     * 服务端要求客户端断开并重新连接
     */
    private void handleReconnect() {
        log.warn("[BotWS] 收到 Reconnect 指令, robotId={}", robotId);
        state = WsState.RECONNECTING;
        cancelHeartbeat();
        doConnect();
    }

    /**
     * 处理 Invalid Session（op=9）
     * 鉴权失败，可能是 Token 无效或过期，停止连接不再自动重连
     */
    private void handleInvalidSession(WsPayload payload) {
        log.error("[BotWS] Invalid Session (鉴权失败)，robotId={}, d={}", robotId, payload.getD());
        lastError = "鉴权失败: Invalid Session";
        state = WsState.DISCONNECTED;
        cancelHeartbeat();
        closeWebSocket();
        running.set(false); // 停止运行，不再自动重连
    }

    // ===== 断线重连 =====

    /**
     * 调度重连
     *
     * <p>使用指数退避策略：延迟 = min(当前延迟 * 2, 60 秒)。
     * 重连失败时会尝试获取新的网关地址（因为网关地址可能已变更）。
     */
    private void scheduleReconnect() {
        if (!running.get()) return;

        int delay = reconnectDelay.get();
        log.info("[BotWS] 将在 {}s 后重连, robotId={}", delay, robotId);
        state = WsState.RECONNECTING;
        totalReconnects++;

        connectExecutor.submit(() -> {
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (running.get()) {
                try {
                    doConnect();
                } catch (Exception e) {
                    log.error("[BotWS] 重连失败, robotId={}, error={}", robotId, e.getMessage());
                    lastError = e.getMessage();
                    // 重连失败，尝试获取新的网关地址（网关地址可能已变更）
                    try {
                        String newGateway = gatewayService.getGateway(appId, appSecret, envType, isNewOpenBot);
                        this.gatewayUrl = newGateway;
                        log.info("[BotWS] 获取新网关地址: robotId={}, url={}", robotId, newGateway);
                    } catch (Exception gwError) {
                        log.warn("[BotWS] 获取新网关也失败, robotId={}, error={}", robotId, gwError.getMessage());
                    }
                    // 指数退避：延迟翻倍，最大 60 秒
                    reconnectDelay.set(Math.min(delay * 2, 60));
                    scheduleReconnect();
                }
            }
        });
    }

    // ===== WebSocket 发送 =====

    /**
     * 发送文本消息到 WebSocket
     *
     * <p>如果 WebSocket 未连接则忽略（不抛异常），避免在断线期间的定时任务导致错误扩散。
     *
     * @param text 要发送的 JSON 文本
     */
    private void send(String text) {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            log.debug("[BotWS] WebSocket 未连接, robotId={}", robotId);
            return;
        }
        try {
            ws.sendText(text, true); // true 表示完整消息（非分片）
        } catch (Exception e) {
            log.error("[BotWS] 发送失败, robotId={}, error={}", robotId, e.getMessage());
        }
    }

    /**
     * 关闭 WebSocket 连接
     * 发送正常关闭帧（code=1000），然后清除引用
     */
    private void closeWebSocket() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown");
            } catch (Exception ignored) {} // 关闭时的异常可忽略
            this.webSocket = null;
        }
    }

    // ===== WebSocket Listener =====

    /**
     * WebSocket 事件监听器（内部类）
     *
     * <p>处理 WebSocket 的生命周期事件：onOpen、onText、onClose、onError。
     * 使用 StringBuilder 缓冲分片消息，只在 last=true 时处理完整消息。
     */
    private class WsListener implements WebSocket.Listener {

        /** 消息缓冲区，用于拼接分片消息 */
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            // 保存 WebSocket 引用（必须在 onOpen 中设置，否则 sendIdentify 时可能为 null）
            QqBotWsClient.this.webSocket = webSocket;
            log.info("[BotWS] onOpen, robotId={}", robotId);
            webSocket.request(1); // 请求接收下一条消息
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                // 消息接收完毕，处理完整消息
                String text = buffer.toString();
                buffer.setLength(0); // 清空缓冲区
                log.debug("[BotWS] 收到帧, robotId={}, len={}", robotId, text.length());
                try {
                    handleMessage(text);
                } catch (Exception e) {
                    log.error("[BotWS] 处理消息异常, robotId={}, error={}", robotId, e.getMessage(), e);
                }
            }
            webSocket.request(1); // 请求接收下一条消息
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("[BotWS] onClose, robotId={}, statusCode={}, reason={}", robotId, statusCode, reason);
            // 忽略旧连接的关闭事件（避免重连时旧连接的回调干扰）
            if (webSocket != QqBotWsClient.this.webSocket) {
                log.debug("[BotWS] 忽略旧连接的 onClose, robotId={}", robotId);
                return null;
            }
            cancelHeartbeat();
            state = WsState.DISCONNECTED;
            if (running.get()) {
                scheduleReconnect(); // 仍在运行中，触发重连
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("[BotWS] onError, robotId={}, error={}", robotId, error.getMessage());
            // 忽略旧连接的错误事件
            if (webSocket != QqBotWsClient.this.webSocket) {
                log.debug("[BotWS] 忽略旧连接的 onError, robotId={}", robotId);
                return;
            }
            lastError = error.getMessage();
            cancelHeartbeat();
            state = WsState.DISCONNECTED;
            if (running.get()) {
                scheduleReconnect(); // 仍在运行中，触发重连
            }
        }
    }
}
