package dev.xuanji.adapter.qq.registry;

import dev.xuanji.adapter.qq.config.QqPlatformConfig;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AccessToken 管理服务实现 — 内存缓存，无 Redis 依赖
 *
 * <p>从 QQ 平台 OAuth2 接口获取 Token，缓存在内存中，
 * 过期前自动刷新。适合单机部署场景，无需额外的缓存中间件。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>key = "appId:envType"（同一 AppID 在不同环境有独立的 Token）</li>
 *   <li>Token 有效期通常为 7200 秒（2 小时），提前 120 秒标记过期</li>
 *   <li>过期后下次请求自动刷新，不使用定时刷新任务</li>
 * </ul>
 *
 * <h3>线程安全性</h3>
 * <p>使用 {@link ConcurrentHashMap} 存储缓存，支持并发读写。
 * 多个线程同时发现 Token 过期时，可能会触发多次刷新，但最终缓存中存储的是最新值。
 *
 * @see AccessTokenService 接口定义
 */
@Slf4j
@Service
public class AccessTokenServiceImpl implements AccessTokenService {

    /** 老版本 QQ 平台 Token 获取接口地址 */

    /** 新版本 QQ 平台 Token 获取接口地址 */

    /** HTTP 客户端（独立实例，因为 Token 接口地址与 API 不同） */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Token 缓存
     * <p>key = "appId:envType"，value = TokenEntry（包含 token 和过期时间）
     */
    private final ConcurrentHashMap<String, TokenEntry> cache = new ConcurrentHashMap<>();

    /**
     * 获取 AccessToken（缓存优先）
     *
     * <p>先检查缓存中是否有未过期的 Token，有则直接返回；
     * 没有或已过期则调用 {@link #refreshAccessToken} 获取新 Token。
     */
    @Override
    public String getAccessToken(String appId, String appSecret, String envType, boolean isNewOpenBot) {
        String key = appId + ":" + envType;
        TokenEntry entry = cache.get(key);

        // 缓存命中且未过期，直接返回
        if (entry != null && !entry.isExpired()) {
            return entry.token;
        }

        // 缓存未命中或已过期，刷新获取新 Token
        return refreshAccessToken(appId, appSecret, envType, isNewOpenBot);
    }

    /**
     * 强制刷新 AccessToken
     *
     * <p>向 QQ 平台的 Token 接口发送 POST 请求，获取新的 AccessToken。
     * 请求体包含 appId 和 clientSecret。
     */
    @Override
    public String refreshAccessToken(String appId, String appSecret, String envType, boolean isNewOpenBot) {
        String key = appId + ":" + envType;
        try {
            // 构建请求体
            ObjectNode body = Json.obj();
            body.put("appId", appId);
            body.put("clientSecret", appSecret);

            // 根据平台版本选择 Token 接口地址
            String tokenUrl = QqPlatformConfig.getTokenUrl(isNewOpenBot);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectNode result = Json.parseObj(response.body());

            String token = result.path("access_token").asText();
            int expiresIn = result.path("expires_in").asInt();

            // 提前 120 秒过期，避免边界问题（Token 接近过期时可能已被平台废弃）
            long expireAt = System.currentTimeMillis() + (expiresIn - 120) * 1000L;
            cache.put(key, new TokenEntry(token, expireAt));

            log.debug("[AccessToken] 刷新成功: appId={}, expiresIn={}s", appId, expiresIn);
            return token;
        } catch (Exception e) {
            log.error("[AccessToken] 刷新失败: appId={}, error={}", appId, e.getMessage());
            throw new RuntimeException("获取 AccessToken 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 Token 是否有效
     */
    @Override
    public boolean isTokenValid(String appId, String envType) {
        String key = appId + ":" + envType;
        TokenEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Token 缓存条目（内部类）
     *
     * <p>封装 Token 值和过期时间，提供过期判断方法。
     */
    private static class TokenEntry {
        /** AccessToken 字符串 */
        final String token;

        /** 过期时间戳（毫秒） */
        final long expireAt;

        TokenEntry(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }

        /**
         * 判断 Token 是否已过期
         *
         * @return true=已过期，false=仍有效
         */
        boolean isExpired() {
            return System.currentTimeMillis() >= expireAt;
        }
    }
}
