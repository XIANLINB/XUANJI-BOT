package dev.xuanji.adapter.onebot.websocket;

import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.adapter.onebot.websocket.OneBotEventDispatcher;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix="xuanji.onebot", name={"enabled"}, havingValue="true")
public class OneBotWsServer
implements WebSocketConfigurer {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotWsServer.class);
    private final OneBotProperties props;
    private final OneBotEventDispatcher dispatcher;
    private final OneBotSessionRegistry registry;
    private final OneBotApiService api;
    static final String ATTR_SELF_ID = "onebot.selfId";
    static final String ATTR_SESSION = "onebot.session";

    public OneBotWsServer(OneBotProperties props, OneBotEventDispatcher dispatcher, OneBotSessionRegistry registry, OneBotApiService api) {
        this.props = props;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.api = api;
    }

    public void registerWebSocketHandlers(WebSocketHandlerRegistry wsRegistry) {
        if (!this.props.getReverse().isEnabled()) {
            log.info("[OneBot-\u53cd\u5411WS] \u672a\u542f\u7528\uff08xuanji.onebot.reverse.enabled=false\uff09");
            return;
        }
        String path = this.props.getReverse().getPath();
        wsRegistry.addHandler(this.handler(), new String[]{path}).addInterceptors(new HandshakeInterceptor[]{new AuthHandshakeInterceptor(this.props)}).setAllowedOriginPatterns(new String[]{"*"});
        log.info("[OneBot-\u53cd\u5411WS] \u7aef\u70b9\u5df2\u5f00\u653e: {}\uff08Napcat \u7684 ws-reverse.universal \u8bf7\u6307\u5411\u6b64\u5730\u5740\uff09", (Object)path);
        if (this.props.getReverse().getAccessToken() == null || this.props.getReverse().getAccessToken().isBlank()) {
            log.warn("[OneBot-\u53cd\u5411WS] \u672a\u914d\u7f6e access-token\uff0c\u4efb\u4f55\u4eba\u90fd\u80fd\u8fde\u63a5\u6b64\u7aef\u70b9\uff0c\u4ec5\u5efa\u8bae\u672c\u673a\u56de\u73af\u73af\u5883\u4f7f\u7528");
        }
    }

    private WebSocketHandler handler() {
        return new TextWebSocketHandler() {
            public void afterConnectionEstablished(WebSocketSession wsSession) {
                String selfId = String.valueOf(wsSession.getAttributes().getOrDefault(OneBotWsServer.ATTR_SELF_ID, "unknown"));
                ServerSideSession session = new ServerSideSession(wsSession, selfId);
                OneBotWsServer.this.registry.register(session);
                wsSession.getAttributes().put(OneBotWsServer.ATTR_SESSION, session);
                log.info("[OneBot-反向WS] 连接建立: selfId={}, remote={}", selfId, wsSession.getRemoteAddress());
            }

            protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
                OneBotSession session = wsSession.getAttributes().get(OneBotWsServer.ATTR_SESSION) instanceof OneBotSession obs
                        ? obs : null;
                String fallbackSelfId = session != null ? session.selfId()
                        : String.valueOf(wsSession.getAttributes().getOrDefault(OneBotWsServer.ATTR_SELF_ID, "unknown"));
                OneBotWsServer.this.dispatcher.onMessage(message.getPayload(), fallbackSelfId, session);
            }

            public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
                log.warn("[OneBot-反向WS] 传输异常: {}", exception.getMessage());
            }

            public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
                Object s = wsSession.getAttributes().get(OneBotWsServer.ATTR_SESSION);
                if (s instanceof OneBotSession obs) {
                    OneBotWsServer.this.registry.unregister(obs);
                }
                OneBotWsServer.this.api.failAllPending("反向WS连接关闭 " + status);
                log.info("[OneBot-反向WS] 连接关闭: status={}", status);
            }
        };
    }

    static class AuthHandshakeInterceptor
    implements HandshakeInterceptor {
        private final OneBotProperties props;

        AuthHandshakeInterceptor(OneBotProperties props) {
            this.props = props;
        }

        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String actual;
            String expected = this.props.getReverse().getAccessToken();
            if (expected != null && !expected.isBlank() && !expected.equals(actual = AuthHandshakeInterceptor.extractToken(request))) {
                log.warn("[OneBot-\u53cd\u5411WS] \u9274\u6743\u5931\u8d25\uff0c\u62d2\u7edd\u8fde\u63a5: remote={}", (Object)request.getRemoteAddress());
                response.setStatusCode((HttpStatusCode)HttpStatus.UNAUTHORIZED);
                return false;
            }
            String selfId = request.getHeaders().getFirst("X-Self-ID");
            if (selfId == null || selfId.isBlank()) {
                selfId = "unknown";
                log.debug("[OneBot-\u53cd\u5411WS] \u63e1\u624b\u65e0 X-Self-ID\uff0c\u5c06\u7531\u4e8b\u4ef6\u62a5\u6587\u7684 self_id \u56de\u7ed1");
            }
            attributes.put(OneBotWsServer.ATTR_SELF_ID, selfId);
            return true;
        }

        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        }

        private static String extractToken(ServerHttpRequest request) {
            ServletServerHttpRequest ssr;
            String q;
            String auth = request.getHeaders().getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                return auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : auth.trim();
            }
            if (request instanceof ServletServerHttpRequest && (q = (ssr = (ServletServerHttpRequest)request).getServletRequest().getParameter("access_token")) != null && !q.isBlank()) {
                return q.trim();
            }
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String kv : query.split("&")) {
                    int eq = kv.indexOf(61);
                    if (eq <= 0 || !"access_token".equals(kv.substring(0, eq))) continue;
                    return kv.substring(eq + 1).trim();
                }
            }
            return null;
        }
    }

    static class ServerSideSession
    implements OneBotSession {
        private final WebSocketSession ws;
        private volatile String selfId;
        private final Object writeLock = new Object();

        ServerSideSession(WebSocketSession ws, String selfId) {
            this.ws = ws;
            this.selfId = selfId;
        }

        @Override
        public String selfId() {
            return this.selfId;
        }

        @Override
        public String direction() {
            return "reverse";
        }

        @Override
        public boolean isOpen() {
            return this.ws.isOpen();
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
                try {
                    this.ws.sendMessage((WebSocketMessage)new TextMessage((CharSequence)text));
                }
                catch (IOException e) {
                    throw new IllegalStateException("\u53cd\u5411WS\u53d1\u9001\u5931\u8d25: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void close() {
            try {
                this.ws.close(CloseStatus.NORMAL);
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
    }
}

