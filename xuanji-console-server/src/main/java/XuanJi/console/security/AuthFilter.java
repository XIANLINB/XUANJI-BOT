package XuanJi.console.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
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
            API_PREFIX + "/auth",
            API_PREFIX + "/console/health",
            // 内置演示 MCP server（JSON-RPC，仅暴露无副作用演示工具，供 McpClient 连接验收）
            API_PREFIX + "/console/llm/mcp-demo"
    );

    private final SessionStore sessionStore;
    private final JdbcTemplate jdbc;

    public AuthFilter(SessionStore sessionStore, JdbcTemplate jdbc) {
        this.sessionStore = sessionStore;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(API_PREFIX) || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        // setup 前缀：
        //   /setup/status 是「是否已安装」的只读查询，前端路由守卫依赖它判断安装状态，必须始终可访问；
        //   其余写操作（pin/verify/bot/complete）仅在未安装时免鉴权，安装完成后动态回收（要求登录）。
        if (uri.startsWith(API_PREFIX + "/setup")) {
            if (uri.startsWith(API_PREFIX + "/setup/status") || !isSetupCompleted()) {
                chain.doFilter(request, response);
                return;
            }
            // 已安装且非 status：落入下方正常鉴权
        } else if (isWhitelisted(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String token = readCookie(request);
        if (token != null && sessionStore.isValid(token)) {
            // 记录操作人标识（控制台为 PIN 单口令，无账号体系 → 用来源 IP 区分操作者），
            // 供平台动作审计（qqbot_op_log 的 operator_*）在 console 来源时读取。
            request.setAttribute("xuanji.console.operator", clientIp(request));
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"未登录或会话已过期\"}");
    }

    /** 客户端 IP（优先取 X-Forwarded-For 首个，兜底 remoteAddr）。 */
    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String first = fwd.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        String addr = request.getRemoteAddr();
        return addr == null ? "" : addr;
    }

    private boolean isWhitelisted(String uri) {
        for (String prefix : WHITELIST_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }

    /** 是否已完成安装：查 xuanji_setup.completed；表不存在/查询失败视为未安装（放行安装向导）。 */
    private volatile Boolean setupCompleted;

    private boolean isSetupCompleted() {
        Boolean cached = setupCompleted;
        if (cached != null && cached) return true; // 已安装 → 永久 true，不再查库
        try {
            Boolean done = jdbc.queryForObject("SELECT completed FROM xuanji_setup WHERE id=1", Boolean.class);
            boolean completed = done != null && done;
            if (completed) setupCompleted = true;
            return completed;
        } catch (Exception e) {
            return false;
        }
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
