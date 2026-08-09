package dev.xuanji.adapter.qqbot.websocket;

import dev.xuanji.adapter.qqbot.config.QqPlatformConfig;

import dev.xuanji.adapter.qqbot.registry.AccessTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * QQ Bot WebSocket 网关地址获取服务
 *
 * <p>职责：调用 QQ 开放平台的 {@code /gateway} 接口，获取 WebSocket 连接所需的网关地址（wss URL）。
 * 是 WebSocket 连接流程的第一步——先获取网关地址，再通过 {@link QqBotWsClient} 建立连接。
 *
 * <p>工作流程：
 * <ol>
 *   <li>根据环境类型（SANDBOX / PRODUCTION）确定 API 基地址</li>
 *   <li>通过 {@link AccessTokenService} 获取有效的 AccessToken</li>
 *   <li>向 {@code /gateway} 发送 GET 请求，携带 Authorization 头</li>
 *   <li>解析响应 JSON，提取 {@code url} 字段返回</li>
 * </ol>
 *
 * <p>环境区分：
 * <ul>
 *   <li>SANDBOX — 使用沙箱地址 {@code https://sandbox.api.sgroup.qq.com}</li>
 *   <li>PRODUCTION — 使用配置文件中的正式地址（默认 {@code https://api.sgroup.qq.com}）</li>
 * </ul>
 *
 * <p>线程安全性：本类无状态，所有字段均为 final 或配置值，可在多线程环境下安全调用。
 *
 * @see QqBotWsClient  使用网关地址建立 WebSocket 连接
 * @see QqBotWsManager 调用本服务获取网关地址后启动客户端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    /** AccessToken 管理服务，用于获取 QQ 平台的访问令牌 */
    private final AccessTokenService accessTokenService;

    /**
     * 公共 HTTP 客户端（由 HttpClientConfig 统一管理，连接池复用）
     * 避免每个服务各自创建 HttpClient 实例导致连接池浪费
     */
    private final HttpClient httpClient;

    /** 老版本 QQ 开放平台 API 基地址（正式环境） */

    /** 老版本 QQ 开放平台 API 基地址（沙箱环境） */

    /** 新版本 QQ 开放平台 API 基地址（统一地址，不区分沙箱/正式） */

    /**
     * 获取 WebSocket 网关地址
     *
     * <p>向 QQ 开放平台的 {@code /gateway} 接口发送请求，获取用于建立 WebSocket 连接的网关 URL。
     *
     * <p>平台地址说明：
     * <ul>
     *   <li>老版本正式环境: {@code https://api.sgroup.qq.com}</li>
     *   <li>老版本沙箱环境: {@code https://sandbox.api.sgroup.qq.com}</li>
     *   <li>新版本统一地址: {@code https://api.bot.qq.com}</li>
     * </ul>
     *
     * @param appId        机器人的 AppID，在 QQ 开放平台注册时获得
     * @param appSecret    机器人的 AppSecret（原始明文值，非加密存储），用于获取 AccessToken
     * @param envType      环境类型，取值 "SANDBOX"（沙箱）或 "PRODUCTION"（正式环境）
     * @param isNewOpenBot 是否使用新开放平台（true=新平台，false=老平台）
     * @return 网关地址，如 {@code wss://api.sgroup.qq.com/websocket}
     * @throws RuntimeException 获取网关地址失败时抛出（网络异常、HTTP 非 200 响应等）
     */
    public String getGateway(String appId, String appSecret, String envType, boolean isNewOpenBot) {
        // 根据平台版本和环境类型选择 API 基地址
        String apiBase = QqPlatformConfig.getApiBaseUrl(isNewOpenBot, envType);

        // 先获取 AccessToken（缓存优先，过期自动刷新）
        String accessToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);
        String authHeader = "QQBot " + accessToken;

        try {
            // 构建 GET /gateway 请求，携带 Authorization 头
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/gateway"))
                    .header("Authorization", authHeader)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("获取网关地址失败: appId={}, status={}, body={}", appId, response.statusCode(), response.body());
                throw new RuntimeException("获取网关地址失败，HTTP " + response.statusCode());
            }

            // 从响应 JSON 中提取网关 URL
            ObjectNode json = Json.parseObj(response.body());
            String url = json.path("url").asText();
            log.info("获取网关地址成功: appId={}, env={}, url={}", appId, envType, url);
            return url;

        } catch (Exception e) {
            log.error("获取网关地址异常: appId={}, error={}", appId, e.getMessage(), e);
            throw new RuntimeException("获取网关地址失败: " + e.getMessage(), e);
        }
    }
}
