package XuanJi.console.controller;

import XuanJi.console.security.PinVerifier;
import XuanJi.console.security.SessionStore;
import XuanJi.console.service.AuditService;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制台登录鉴权接口（路径经 @XuanJiApi 前缀装配为 {@code /xuanji/api/v1/auth/**}）。
 *
 * <ul>
 *   <li>{@code POST /login} —— 校验 6 位访问口令，成功则下发 {@code HttpOnly} 会话 cookie</li>
 *   <li>{@code POST /logout} —— 销毁会话并清除 cookie</li>
 *   <li>{@code GET /me} —— 返回当前会话是否已登录（供前端路由守卫判断）</li>
 * </ul>
 *
 * <p>接口本身在 {@link XuanJi.console.security.AuthFilter} 白名单内，无需已登录即可访问。
 */
@XuanJiApi
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String COOKIE_NAME = "xuanji_session";

    /** 登录限速：1 分钟窗口内最多 5 次尝试；连续失败 5 次锁定 1 小时。 */
    private static final int MAX_ATTEMPTS_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L;
    private static final long LOCK_MS = 3_600_000L;

    private static final class Attempt {
        final Deque<Long> recent = new ArrayDeque<>(); // 近 1 分钟尝试时间戳
        int consecutiveFails = 0;
        long lockedUntil = 0L;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final PinVerifier pinVerifier;
    private final SessionStore sessionStore;
    private final AuditService auditService;

    public AuthController(PinVerifier pinVerifier, SessionStore sessionStore, AuditService auditService) {
        this.pinVerifier = pinVerifier;
        this.sessionStore = sessionStore;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                     HttpServletRequest request, HttpServletResponse response) {
        if (!pinVerifier.pinConfigured()) {
            return fail(response, "尚未设置访问口令，请先完成初始化引导");
        }
        String key = ip(request);
        // 限速：1 分钟最多 5 次尝试；连续错 5 次锁定 1 小时
        String limitErr = tryAcquire(key);
        if (limitErr != null) {
            auditService.record("LOGIN_FAIL", limitErr, request);
            return fail(response, limitErr);
        }
        String pin = body.get("pin");
        if (pin == null || !pinVerifier.verify(pin)) {
            recordFail(key);
            auditService.record("LOGIN_FAIL", "口令校验失败", request);
            return fail(response, "访问口令错误");
        }
        recordSuccess(key);
        String token = sessionStore.create();
        response.addHeader("Set-Cookie", buildCookie(token, sessionStore.ttlSeconds(), request.isSecure()));
        auditService.record("LOGIN_OK", "控制台登录成功", request);
        return Map.of("status", "ok", "ttlSeconds", sessionStore.ttlSeconds());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request);
        if (token != null) sessionStore.destroy(token);
        response.addHeader("Set-Cookie", buildCookie("", 0, request.isSecure()));
        auditService.record("LOGOUT", "控制台登出", request);
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return Map.of("authenticated", sessionStore.isValid(readCookie(request)));
    }

    private Map<String, Object> fail(HttpServletResponse response, String msg) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Map.of("error", msg);
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** 拼装 Set-Cookie：HttpOnly + SameSite=Strict + Path=/；HTTPS 时追加 Secure。 */
    private String buildCookie(String value, long maxAgeSeconds, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append('=').append(value).append(';');
        sb.append("Path=/;");
        sb.append("HttpOnly;");
        sb.append("SameSite=Strict;");
        if (secure) sb.append("Secure;");
        sb.append("Max-Age=").append(maxAgeSeconds);
        return sb.toString();
    }

    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    /** 尝试前检查：已锁定返回锁定提示；1 分钟窗口内超次返回限速提示；否则记录一次尝试并放行。 */
    private String tryAcquire(String key) {
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        long now = System.currentTimeMillis();
        synchronized (a) {
            if (a.lockedUntil > now) {
                return "尝试次数过多，已锁定 1 小时，请稍后再试";
            }
            while (!a.recent.isEmpty() && a.recent.peekFirst() < now - WINDOW_MS) {
                a.recent.pollFirst();
            }
            if (a.recent.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                return "尝试过于频繁，请稍后再试";
            }
            a.recent.addLast(now);
        }
        return null;
    }

    /** 记录一次失败：连续失败满 5 次触发锁定 1 小时。 */
    private void recordFail(String key) {
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (a) {
            a.consecutiveFails++;
            if (a.consecutiveFails >= MAX_ATTEMPTS_PER_MINUTE) {
                a.lockedUntil = System.currentTimeMillis() + LOCK_MS;
                a.consecutiveFails = 0;
                a.recent.clear();
            }
        }
    }

    /** 登录成功：清除该来源的失败/锁定记录。 */
    private void recordSuccess(String key) {
        attempts.remove(key);
    }
}
