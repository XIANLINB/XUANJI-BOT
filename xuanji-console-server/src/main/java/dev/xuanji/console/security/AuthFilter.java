package dev.xuanji.console.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 控制台 API 鉴权网关 —— 拦截 {@code /xuanji/api/v1/**}，要求携带有效会话 cookie。
 *
 * <h3>白名单（不鉴权即可访问）</h3>
 * <ul>
 *   <li>{@code /xuanji/api/v1/setup/**} —— 首次安装向导（设 PIN / 录机器人 / 完成），安装前必须开放</li>
 *   <li>{@code /xuanji/api/v1/auth/**} —— 登录 / 登出 / 自身状态，登录接口本身不能要求已登录</li>
 *   <li>{@code /xuanji/api/v1/console/health} —— 健康检查</li>
 * </ul>
 *
 * <p>非白名单且未携带有效 {@code xuanji_session} cookie 的请求一律返回 401。
 * 静态资源（{@code /xuanji/console/**}）与 actuator 不在 API 前缀下，本过滤器直接放行。
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "xuanji_session";
    private static final String API_PREFIX = "/xuanji/api/v1";

    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            API_PREFIX + "/setup",
            API_PREFIX + "/auth",
            API_PREFIX + "/console/health"
    );

    private final SessionStore sessionStore;

    public AuthFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(API_PREFIX) || isWhitelisted(uri) || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = readCookie(request);
        if (token != null && sessionStore.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"未登录或会话已过期\"}");
    }

    private boolean isWhitelisted(String uri) {
        for (String prefix : WHITELIST_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
