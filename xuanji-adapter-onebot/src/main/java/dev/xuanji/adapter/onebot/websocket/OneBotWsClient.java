package dev.xuanji.adapter.onebot.websocket;

import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OneBot 正向 WebSocket 客户端 — 框架作客户端，主动连 OneBot 实现暴露的 WS 端口。
 *
 * <p>适用场景：Napcat 部署在框架可达的位置且已开放正向 WS 端口（默认 3001）；
 * 或框架跑在无法对外暴露端口的环境（容器/内网机）时的唯一选择。
 *
 * <h3>配置</h3>
 * <pre>
 * xuanji:
 *   onebot:
 *     enabled: true
 *     forward:
 *       enabled: true
 *       url: ws://127.0.0.1:3001
 *       access-token: ${ONEBOT_TOKEN:}
 * </pre>
 *
 * <p>实现用 JDK 内置 {@link java.net.http.WebSocket}（无需额外依赖），
 * 分片消息由 {@link ClientListener} 累积拼接后再交分发器。
 * 断线后按固定间隔重连，重连线程为虚拟线程。
 */
@Slf4j
public class OneBotWsClient {

    private final OneBotProperties props;
    private final OneBotEventDispatcher dispatcher;
    private final OneBotSessionRegistry registry;
    private final OneBotApiService api;

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    private final AtomicReference<ClientSideSession> sessionRef = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread reconnectThread;

    public OneBotWsClient(OneBotProperties props,
                          OneBotEventDispatcher dispatcher,
                          OneBotSessionRegistry registry,
                          OneBotApiService api) {
        this.props = props;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.api = api;
    }

    /** 上下文就绪后启动连接（不阻塞启动流程，失败进重连循环） */
    @EventListener(ContextRefreshedEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String url = props.getForward().getUrl();
        if (url == null || url.isBlank()) {
            log.warn("[OneBot-正向WS] 已启用但未配置 url，跳过连接");
            running.set(false);
            return;
        }
        reconnectThread = Thread.ofVirtual()
                .name("onebot-ws-client")
                .start(this::connectLoop);
        log.info("[OneBot-正向WS] 启动，目标: {}", url);
    }

    private void connectLoop() {
        long interval = props.getForward().getReconnectIntervalMs();
        while (running.get()) {
            try {
                doConnectAndWait();
            } catch (Exception e) {
                log.warn("[OneBot-正向WS] 连接异常: {}", e.getMessage());
            }
            if (!running.get() || interval <= 0) {
                break;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            log.info("[OneBot-正向WS] 尝试重连 {}", props.getForward().getUrl());
        }
    }

    /** 建连并阻塞到断开 */
    private void doConnectAndWait() throws Exception {
        String token = props.getForward().getAccessToken();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        WebSocket.Builder builder = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        ClientListener listener = new ClientListener();
        WebSocket ws = builder.buildAsync(URI.create(props.getForward().getUrl()), listener).join();
        wsRef.set(ws);

        ClientSideSession session = new ClientSideSession(ws);
        sessionRef.set(session);
        registry.register(session);
        log.info("[OneBot-正向WS] 连接成功: {}", props.getForward().getUrl());

        // 阻塞直到监听器标记关闭
        listener.awaitClosed();

        registry.unregister(session);
        api.failAllPending("正向WS连接关闭");
        sessionRef.set(null);
        wsRef.set(null);
    }

    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ClientSideSession s = sessionRef.get();
        if (s != null) {
            s.close();
        }
        Thread t = reconnectThread;
        if (t != null) {
            t.interrupt();
        }
        log.info("[OneBot-正向WS] 已停止");
    }

    // ==================== 监听器 ====================

    /**
     * JDK WebSocket 监听器。
     *
     * <p>注意：{@code onText} 可能收到分片（last=false），必须自行累积；
     * 每次回调后需 {@code request(1)} 才会继续投递下一条。
     */
    private class ClientListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();
        private final Object closedLock = new Object();
        private volatile boolean closed = false;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                // 事件处理放到独立虚拟线程，避免阻塞 WS 读取线程
                Thread.ofVirtual().start(() -> {
                    ClientSideSession s = sessionRef.get();
                    dispatcher.onMessage(payload, s == null ? "unknown" : s.selfId());
                });
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[OneBot-正向WS] 对端关闭: code={}, reason={}", statusCode, reason);
            markClosed();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("[OneBot-正向WS] 连接错误: {}", error.getMessage());
            markClosed();
        }

        void markClosed() {
            synchronized (closedLock) {
                closed = true;
                closedLock.notifyAll();
            }
        }

        void awaitClosed() throws InterruptedException {
            synchronized (closedLock) {
                while (!closed) {
                    closedLock.wait();
                }
            }
        }
    }

    // ==================== 客户端会话包装 ====================

    /**
     * 正向 WS 会话。
     *
     * <p>selfId 由事件报文的 self_id 决定；连接刚建立时未知，
     * 首条事件到达后 {@link OneBotEventDispatcher} 会用报文里的 self_id 兜底路由，
     * 这里的 selfId 主要用于会话注册表的 key。
     */
    private class ClientSideSession implements OneBotSession {

        private final WebSocket ws;
        private final Object writeLock = new Object();
        private volatile String selfId = "forward";

        ClientSideSession(WebSocket ws) {
            this.ws = ws;
        }

        @Override public String selfId()    { return selfId; }
        @Override public String direction() { return "forward"; }
        @Override public boolean isOpen()   { return !ws.isOutputClosed(); }

        @Override
        public void sendText(String text) {
            synchronized (writeLock) {
                ws.sendText(text, true).join();
            }
        }

        @Override
        public void close() {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                // 连接可能已断，忽略
            }
        }
    }
}
