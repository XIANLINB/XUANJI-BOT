package dev.xuanji.adapter.onebot.websocket;

import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

/**
 * OneBot 反向 WebSocket 服务端 — 框架作服务端，等 OneBot 实现主动连过来。
 *
 * <p>这是 Shiro 连 Napcat 的主推方式，也是本框架的推荐方式：
 * 框架只需暴露一个端点，Napcat 侧配置 {@code ws-reverse.universal} 指向它即可，
 * 框架无需知道 Napcat 部署在哪、也不需要 Napcat 端口对框架可达。
 *
 * <h3>Napcat 侧配置示例</h3>
 * <pre>
 * ws-reverse:
 *   enable: true
 *   universal: ws://127.0.0.1:8080/onebot/ws
 *   token: 与 xuanji.onebot.reverse.access-token 一致
 * </pre>
 *
 * <h3>握手鉴权</h3>
 * OneBot v11 规定反向 WS 客户端携带 {@code Authorization: Bearer <token>}
 * 与 {@code X-Self-ID: <QQ号>} 请求头；部分实现改用 {@code ?access_token=} 查询参数，
 * 两种都接受。
 */
@Slf4j
@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "xuanji.onebot", name = "enabled", havingValue = "true")
public class OneBotWsServer implements WebSocketConfigurer {

    private final OneBotProperties props;
    private final OneBotEventDispatcher dispatcher;
    private final OneBotSessionRegistry registry;
    private final OneBotApiService api;

    public OneBotWsServer(OneBotProperties props,
                          OneBotEventDispatcher dispatcher,
                          OneBotSessionRegistry registry,
                          OneBotApiService api) {
        this.props = props;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.api = api;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry wsRegistry) {
        if (!props.getReverse().isEnabled()) {
            log.info("[OneBot-反向WS] 未启用（xuanji.onebot.reverse.enabled=false）");
            return;
        }
        String path = props.getReverse().getPath();
        wsRegistry.addHandler(handler(), path)
                .addInterceptors(new AuthHandshakeInterceptor(props))
                .setAllowedOriginPatterns("*");
        log.info("[OneBot-反向WS] 端点已开放: {}（Napcat 的 ws-reverse.universal 请指向此地址）", path);
        if (props.getReverse().getAccessToken() == null || props.getReverse().getAccessToken().isBlank()) {
            log.warn("[OneBot-反向WS] 未配置 access-token，任何人都能连接此端点，仅建议本机回环环境使用");
        }
    }

    private WebSocketHandler handler() {
        return new TextWebSocketHandler() {

            @Override
            public void afterConnectionEstablished(WebSocketSession wsSession) {
                String selfId = String.valueOf(
                        wsSession.getAttributes().getOrDefault(ATTR_SELF_ID, "unknown"));
                OneBotSession session = new ServerSideSession(wsSession, selfId);
                registry.register(session);
                wsSession.getAttributes().put(ATTR_SESSION, session);
                log.info("[OneBot-反向WS] 连接建立: selfId={}, remote={}", selfId, wsSession.getRemoteAddress());
            }

            @Override
            protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
                String selfId = String.valueOf(
                        wsSession.getAttributes().getOrDefault(ATTR_SELF_ID, "unknown"));
                dispatcher.onMessage(message.getPayload(), selfId);
            }

            @Override
            public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
                log.warn("[OneBot-反向WS] 传输异常: {}", exception.getMessage());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
                Object s = wsSession.getAttributes().get(ATTR_SESSION);
                if (s instanceof OneBotSession obs) {
                    registry.unregister(obs);
                }
                api.failAllPending("反向WS连接关闭 " + status);
                log.info("[OneBot-反向WS] 连接关闭: status={}", status);
            }
        };
    }

    static final String ATTR_SELF_ID = "onebot.selfId";
    static final String ATTR_SESSION = "onebot.session";

    // ==================== 握手鉴权 ====================

    /**
     * 校验 access_token 并提取 X-Self-ID。
     *
     * <p>令牌来源按优先级：{@code Authorization: Bearer xxx} → {@code Authorization: xxx}
     * → 查询参数 {@code ?access_token=xxx}。
     */
    static class AuthHandshakeInterceptor
            implements org.springframework.web.socket.server.HandshakeInterceptor {

        private final OneBotProperties props;

        AuthHandshakeInterceptor(OneBotProperties props) {
            this.props = props;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String expected = props.getReverse().getAccessToken();

            if (expected != null && !expected.isBlank()) {
                String actual = extractToken(request);
                if (!expected.equals(actual)) {
                    log.warn("[OneBot-反向WS] 鉴权失败，拒绝连接: remote={}", request.getRemoteAddress());
                    response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return false;
                }
            }

            String selfId = request.getHeaders().getFirst("X-Self-ID");
            if (selfId == null || selfId.isBlank()) {
                // 少数实现不发 X-Self-ID，事件报文里的 self_id 会兜底
                selfId = "unknown";
                log.debug("[OneBot-反向WS] 握手无 X-Self-ID，将由事件报文的 self_id 兜底");
            }
            attributes.put(ATTR_SELF_ID, selfId);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 无需后置处理
        }

        private static String extractToken(ServerHttpRequest request) {
            String auth = request.getHeaders().getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                return auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : auth.trim();
            }
            if (request instanceof ServletServerHttpRequest ssr) {
                String q = ssr.getServletRequest().getParameter("access_token");
                if (q != null && !q.isBlank()) {
                    return q.trim();
                }
            }
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String kv : query.split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0 && "access_token".equals(kv.substring(0, eq))) {
                        return kv.substring(eq + 1).trim();
                    }
                }
            }
            return null;
        }
    }

    // ==================== 服务端会话包装 ====================

    /**
     * 反向 WS 会话。
     *
     * <p>Spring 的 {@link WebSocketSession} 不允许并发写，因此 sendText 加锁串行化，
     * 避免多插件并行回复时报文交错。
     */
    static class ServerSideSession implements OneBotSession {

        private final WebSocketSession ws;
        private final String selfId;
        private final Object writeLock = new Object();

        ServerSideSession(WebSocketSession ws, String selfId) {
            this.ws = ws;
            this.selfId = selfId;
        }

        @Override public String selfId()    { return selfId; }
        @Override public String direction() { return "reverse"; }
        @Override public boolean isOpen()   { return ws.isOpen(); }

        @Override
        public void sendText(String text) {
            synchronized (writeLock) {
                try {
                    ws.sendMessage(new TextMessage(text));
                } catch (IOException e) {
                    throw new IllegalStateException("反向WS发送失败: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void close() {
            try {
                ws.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // 已关闭或半死连接，忽略
            }
        }
    }
}
