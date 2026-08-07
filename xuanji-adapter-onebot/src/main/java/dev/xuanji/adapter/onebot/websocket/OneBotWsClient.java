package dev.xuanji.adapter.onebot.websocket;

import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.adapter.onebot.websocket.OneBotEventDispatcher;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

public class OneBotWsClient {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotWsClient.class);
    private final OneBotProperties props;
    private final OneBotEventDispatcher dispatcher;
    private final OneBotSessionRegistry registry;
    private final OneBotApiService api;
    private final AtomicReference<WebSocket> wsRef = new AtomicReference();
    private final AtomicReference<ClientSideSession> sessionRef = new AtomicReference();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread reconnectThread;

    public OneBotWsClient(OneBotProperties props, OneBotEventDispatcher dispatcher, OneBotSessionRegistry registry, OneBotApiService api) {
        this.props = props;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.api = api;
    }

    @EventListener(value={ContextRefreshedEvent.class})
    public void start() {
        if (!this.running.compareAndSet(false, true)) {
            return;
        }
        String url = this.props.getForward().getUrl();
        if (url == null || url.isBlank()) {
            log.warn("[OneBot-\u6b63\u5411WS] \u5df2\u542f\u7528\u4f46\u672a\u914d\u7f6e url\uff0c\u8df3\u8fc7\u8fde\u63a5");
            this.running.set(false);
            return;
        }
        this.reconnectThread = Thread.ofVirtual().name("onebot-ws-client").start(this::connectLoop);
        log.info("[OneBot-\u6b63\u5411WS] \u542f\u52a8\uff0c\u76ee\u6807: {}", (Object)url);
    }

    private void connectLoop() {
        long interval = this.props.getForward().getReconnectIntervalMs();
        while (this.running.get()) {
            try {
                this.doConnectAndWait();
            }
            catch (Exception e) {
                log.warn("[OneBot-\u6b63\u5411WS] \u8fde\u63a5\u5f02\u5e38: {}", (Object)e.getMessage());
            }
            if (!this.running.get() || interval <= 0L) break;
            try {
                Thread.sleep(interval);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            log.info("[OneBot-\u6b63\u5411WS] \u5c1d\u8bd5\u91cd\u8fde {}", (Object)this.props.getForward().getUrl());
        }
    }

    private void doConnectAndWait() throws Exception {
        String token = this.props.getForward().getAccessToken();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
        WebSocket.Builder builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10L));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        ClientListener listener = new ClientListener(this);
        WebSocket ws = builder.buildAsync(URI.create(this.props.getForward().getUrl()), listener).join();
        this.wsRef.set(ws);
        ClientSideSession session = new ClientSideSession(this, ws);
        this.sessionRef.set(session);
        this.registry.register(session);
        log.info("[OneBot-\u6b63\u5411WS] \u8fde\u63a5\u6210\u529f: {}", (Object)this.props.getForward().getUrl());
        listener.awaitClosed();
        this.registry.unregister(session);
        this.api.failAllPending("\u6b63\u5411WS\u8fde\u63a5\u5173\u95ed");
        this.sessionRef.set(null);
        this.wsRef.set(null);
    }

    @PreDestroy
    public void stop() {
        Thread t;
        if (!this.running.compareAndSet(true, false)) {
            return;
        }
        ClientSideSession s = this.sessionRef.get();
        if (s != null) {
            s.close();
        }
        if ((t = this.reconnectThread) != null) {
            t.interrupt();
        }
        log.info("[OneBot-\u6b63\u5411WS] \u5df2\u505c\u6b62");
    }

    private class ClientListener
    implements WebSocket.Listener {
        private final StringBuilder buffer;
        private final Object closedLock;
        private volatile boolean closed;
        final /* synthetic */ OneBotWsClient this$0;

        private ClientListener(OneBotWsClient oneBotWsClient) {
            OneBotWsClient oneBotWsClient2 = oneBotWsClient;
            Objects.requireNonNull(oneBotWsClient2);
            this.this$0 = oneBotWsClient2;
            this.buffer = new StringBuilder();
            this.closedLock = new Object();
            this.closed = false;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1L);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                String payload = this.buffer.toString();
                this.buffer.setLength(0);
                Thread.ofVirtual().start(() -> {
                    ClientSideSession s = this.this$0.sessionRef.get();
                    this.this$0.dispatcher.onMessage(payload, s == null ? "unknown" : s.selfId(), s);
                });
            }
            webSocket.request(1L);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[OneBot-\u6b63\u5411WS] \u5bf9\u7aef\u5173\u95ed: code={}, reason={}", (Object)statusCode, (Object)reason);
            this.markClosed();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("[OneBot-\u6b63\u5411WS] \u8fde\u63a5\u9519\u8bef: {}", (Object)error.getMessage());
            this.markClosed();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        void markClosed() {
            Object object = this.closedLock;
            synchronized (object) {
                this.closed = true;
                this.closedLock.notifyAll();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        void awaitClosed() throws InterruptedException {
            Object object = this.closedLock;
            synchronized (object) {
                while (!this.closed) {
                    this.closedLock.wait();
                }
            }
        }
    }

    private class ClientSideSession
    implements OneBotSession {
        private final WebSocket ws;
        private final Object writeLock;
        private volatile String selfId;

        ClientSideSession(OneBotWsClient oneBotWsClient, WebSocket ws) {
            Objects.requireNonNull(oneBotWsClient);
            this.writeLock = new Object();
            this.selfId = "forward";
            this.ws = ws;
        }

        @Override
        public String selfId() {
            return this.selfId;
        }

        @Override
        public String direction() {
            return "forward";
        }

        @Override
        public boolean isOpen() {
            return !this.ws.isOutputClosed();
        }

        @Override
        public boolean rebindSelfId(String realSelfId) {
            this.selfId = realSelfId;
            return true;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @Override
        public void sendText(String text) {
            Object object = this.writeLock;
            synchronized (object) {
                this.ws.sendText(text, true).join();
            }
        }

        @Override
        public void close() {
            try {
                this.ws.sendClose(1000, "shutdown");
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }
}

