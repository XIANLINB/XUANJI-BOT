package dev.xuanji.console.controller;

import dev.xuanji.console.security.PinVerifier;
import dev.xuanji.console.security.SessionStore;
import dev.xuanji.console.service.AuditService;
import dev.xuanji.core.web.XuanjiApi;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 控制台登录鉴权接口（路径经 @XuanjiApi 前缀装配为 {@code /xuanji/api/v1/auth/**}）。
 *
 * <ul>
 *   <li>{@code POST /login} —— 校验 6 位访问口令，成功则下发 {@code HttpOnly} 会话 cookie</li>
 *   <li>{@code POST /logout} —— 销毁会话并清除 cookie</li>
 *   <li>{@code GET /me} —— 返回当前会话是否已登录（供前端路由守卫判断）</li>
 * </ul>
 *
 * <p>接口本身在 {@link dev.xuanji.console.security.AuthFilter} 白名单内，无需已登录即可访问。
 */
@XuanjiApi
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String COOKIE_NAME = "xuanji_session";

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
        String pin = body.get("pin");
        if (pin == null || !pinVerifier.verify(pin)) {
            auditService.record("LOGIN_FAIL", "口令校验失败", ip(request));
            return fail(response, "访问口令错误");
        }
        String token = sessionStore.create();
        response.addHeader("Set-Cookie", buildCookie(token, sessionStore.ttlSeconds(), request.isSecure()));
        auditService.record("LOGIN_OK", "控制台登录成功", ip(request));
        return Map.of("status", "ok", "ttlSeconds", sessionStore.ttlSeconds());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request);
        if (token != null) sessionStore.destroy(token);
        response.addHeader("Set-Cookie", buildCookie("", 0, request.isSecure()));
        auditService.record("LOGOUT", "控制台登出", ip(request));
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
}
