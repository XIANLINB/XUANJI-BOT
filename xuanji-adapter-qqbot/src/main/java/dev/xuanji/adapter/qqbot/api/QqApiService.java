package dev.xuanji.adapter.qqbot.api;

import dev.xuanji.adapter.qqbot.config.QqPlatformConfig;

import dev.xuanji.adapter.qqbot.registry.AccessTokenService;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;

import dev.xuanji.api.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QQ 开放平台 OpenAPI 底层调用服务
 *
 * <p>所有 QQ 平台 API 调用的统一入口，封装了鉴权、重试、限流、错误解析等通用逻辑。
 * 上层业务代码（如事件 Handler）通过本服务调用 QQ 平台接口，无需关心底层细节。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>鉴权</b> — 自动注入 AccessToken（Authorization: QQBot {token}），
 *       Token 由 {@link AccessTokenService} 管理，缓存和刷新对调用方透明</li>
 *   <li><b>重试</b> — 401 响应时自动刷新 Token 并重试一次（不递归，避免死循环）</li>
 *   <li><b>限流</b> — 429 响应时抛出 {@link BusinessException}，由上层决定处理策略</li>
 *   <li><b>错误解析</b> — 使用 {@link dev.xuanji.adapter.qqbot.enums.QqApiErrorCode}
 *       解析平台错误码，提供结构化的错误信息和排查建议</li>
 *   <li><b>链路追踪</b> — 记录响应头中的 X-Tps-trace-ID，便于问题排查</li>
 * </ul>
 *
 * <h3>支持的 HTTP 方法</h3>
 * <ul>
 *   <li>GET — 查询类接口（如获取用户信息、频道列表）</li>
 *   <li>POST — 发送消息、上传文件</li>
 *   <li>PUT — 互动事件响应、身份组成员操作</li>
 *   <li>PATCH — 修改频道/身份组、禁言</li>
 *   <li>DELETE — 撤回消息、删除子频道/身份组</li>
 * </ul>
 *
 * <h3>调用方式</h3>
 * <p>提供两套重载方法：
 * <ul>
 *   <li><b>通过 robotId 调用</b>（推荐）— 自动从 {@link RobotRegistry} 查找凭证，
 *       如 {@link #post(Long, String, String, ObjectNode)}</li>
 *   <li><b>通过凭证调用</b> — 手动传入 appId 和 appSecret，
 *       如 {@link #post(String, String, String, String, ObjectNode)}</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 发送群聊消息（通过 robotId）
 * ObjectNode body = Json.obj();
 * body.put("content", "Hello");
 * body.put("msg_type", 0);
 * ObjectNode resp = qqApiService.post(robotId, "SANDBOX",
 *     "/v2/groups/{group_openid}/messages", body);
 *
 * // 查询频道信息（通过凭证）
 * ObjectNode info = qqApiService.get(appId, appSecret, "PRODUCTION",
 *     "/v2/guilds/{guild_id}");
 * }</pre>
 *
 * @see AccessTokenService  AccessToken 生命周期管理
 * @see RobotRegistry       机器人凭证查找
 * @see dev.xuanji.adapter.qqbot.enums.QqApiErrorCode QQ 平台错误码枚举
 */
@Slf4j
@Service
public class QqApiService {

    /** AccessToken 管理服务，负责 Token 的获取、缓存和刷新 */
    private final AccessTokenService accessTokenService;

    /** 机器人注册表，用于通过 robotId 查找 AppID 和 AppSecret */
    private final RobotRegistry robotRegistry;

    /** 公共 HTTP 客户端（由 HttpClientConfig 统一管理，连接池复用） */
    private final HttpClient httpClient;

    /**
     * 是否使用新开放平台（全局配置）
     * <p>true = 使用新开放平台（api.bot.qq.com），false = 使用老开放平台（api.sgroup.qq.com）
     */
    private volatile boolean isNewOpenBot = false;

    /**
     * 构造函数，注入依赖
     *
     * @param accessTokenService AccessToken 管理服务
     * @param robotRegistry      机器人注册表
     * @param httpClient         公共 HTTP 客户端
     */
    public QqApiService(AccessTokenService accessTokenService, RobotRegistry robotRegistry, HttpClient httpClient) {
        this.accessTokenService = accessTokenService;
        this.robotRegistry = robotRegistry;
        this.httpClient = httpClient;
    }

    /**
     * 设置是否使用新开放平台（全局配置）
     *
     * @param isNewOpenBot true=新平台 api.bot.qq.com，false=老平台 api.sgroup.qq.com
     */
    public void setNewOpenBot(boolean isNewOpenBot) {
        this.isNewOpenBot = isNewOpenBot;
    }

    // ===== 简易熔断（供控制台运行健康页展示） =====

    private static final int CIRCUIT_THRESHOLD = 5;
    private static final long CIRCUIT_COOLDOWN_MS = 30_000;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;

    /** 熔断快照（控制台 /console/health 的 platforms.qqbot 键）。 */
    public Map<String, Object> getCircuitBreakerSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", circuitOpen);
        m.put("consecutiveFailures", consecutiveFailures.get());
        m.put("openedAt", circuitOpenedAt);
        return m;
    }

    private void onApiSuccess() {
        consecutiveFailures.set(0);
        if (circuitOpen) circuitOpen = false;
    }

    private void onApiFailure() {
        if (consecutiveFailures.incrementAndGet() >= CIRCUIT_THRESHOLD) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
        }
    }

    // ===== 通过 robotId 调用的便捷方法（自动查找凭证） =====

    /**
     * POST 请求（通过 robotId）
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型（SANDBOX / PRODUCTION）
     * @param path    API 路径（如 /v2/groups/{group_openid}/messages）
     * @param body    请求体 JSON
     * @return 响应 JSON
     */
    public ObjectNode post(String robotId, String envType, String path, ObjectNode body) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        return sendRequest("POST", creds[0], creds[1], envType, path, body, isNewOpenBot);
    }

    /**
     * POST 请求（通过 robotId，自定义超时）
     *
     * <p>用于媒体上传等需要较长超时的请求。
     *
     * @param robotId      机器人 ID
     * @param envType      环境类型
     * @param path         API 路径
     * @param body         请求体 JSON
     * @param timeoutSeconds 超时时间（秒）
     * @return 响应 JSON
     */
    public ObjectNode postWithTimeout(String robotId, String envType, String path, ObjectNode body, int timeoutSeconds) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        return sendRequestWithTimeout("POST", creds[0], creds[1], envType, path, body, isNewOpenBot, timeoutSeconds);
    }

    /**
     * POST multipart 上传（富媒体 /files 接口，本地文件流方式）。
     *
     * <p>QQ 富媒体上传支持两种方式：url（JSON 字段）与 multipart 文件流。
     * 本地文件转 base64 后走此通道（form 字段：file_type / srv_send_msg / file 文件流）。
     * 响应处理与 sendRequest 对齐（2xx 成功；其余抛 {@link BusinessException}，含平台错误码）。
     *
     * @param robotId  机器人 ID
     * @param envType  环境类型
     * @param path     API 路径（如 /v2/groups/{openid}/files）
     * @param fileType 文件类型（1 图片 / 2 视频 / 3 语音）
     * @param fileBytes 文件内容
     * @param filename 文件名（用于 multipart filename 字段）
     * @param timeoutSeconds 超时（秒）
     * @return 响应 JSON（含 file_info）
     */
    public ObjectNode postMultipart(String robotId, String envType, String path,
                                    int fileType, byte[] fileBytes, String filename, int timeoutSeconds) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        String accessToken = accessTokenService.getAccessToken(creds[0], creds[1], envType, isNewOpenBot);
        String apiBase = QqPlatformConfig.getApiBaseUrl(isNewOpenBot, envType);
        String boundary = "XuanjiBoundary" + System.nanoTime();

        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        try {
            writeFormField(body, boundary, "file_type", String.valueOf(fileType));
            writeFormField(body, boundary, "srv_send_msg", "0");
            body.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new BusinessException(500, "构造上传请求失败: " + e.getMessage());
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .header("Authorization", "QQBot " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));

        try {
            HttpResponse<String> response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            int statusCode = response.statusCode();
            log.debug("QQ API multipart 响应: status={} path={}", statusCode, path);
            if (statusCode == 200 || statusCode == 201 || statusCode == 202) {
                onApiSuccess();
                if (responseBody == null || responseBody.isEmpty()) return Json.obj();
                return Json.parseObj(responseBody);
            }
            String traceId = response.headers().firstValue("X-Tps-trace-ID").orElse("N/A");
            String errBody = responseBody == null ? "" : responseBody;
            throw new BusinessException(statusCode,
                    "QQ富媒体上传失败(" + statusCode + ") traceId=" + traceId + " " + truncate(errBody, 200));
        } catch (java.io.IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "QQ富媒体上传异常: " + e.getMessage());
        }
    }

    private static void writeFormField(java.io.ByteArrayOutputStream out, String boundary,
                                       String name, String value) throws java.io.IOException {
        out.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * PUT 请求（通过 robotId）
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @param path    API 路径
     * @param body    请求体 JSON
     * @return 响应 JSON
     */
    public ObjectNode put(String robotId, String envType, String path, ObjectNode body) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        // 使用全局配置的 isNewOpenBot
        return sendRequest("PUT", creds[0], creds[1], envType, path, body, isNewOpenBot);
    }

    /**
     * GET 请求（通过 robotId）
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @param path    API 路径
     * @return 响应 JSON
     */
    public ObjectNode get(String robotId, String envType, String path) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        // 使用全局配置的 isNewOpenBot
        return sendRequest("GET", creds[0], creds[1], envType, path, null, isNewOpenBot);
    }

    /** 拉取当前机器人信息（GET /users/@me）—— 含 union_openid / share_url / welcome_msg 等。 */
    public ObjectNode getMe(String robotId, String envType) {
        return get(robotId, envType, "/users/@me");
    }

    /**
     * DELETE 请求（通过 robotId）
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @param path    API 路径
     * @return 响应 JSON
     */
    public ObjectNode delete(String robotId, String envType, String path) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        // 使用全局配置的 isNewOpenBot
        return sendRequest("DELETE", creds[0], creds[1], envType, path, null, isNewOpenBot);
    }

    /**
     * PATCH 请求（通过 robotId）
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型（SANDBOX / PRODUCTION）
     * @param path    API 路径（如 /channels/{channel_id}）
     * @param body    请求体 JSON
     * @return 响应 JSON
     */
    public ObjectNode patch(String robotId, String envType, String path, ObjectNode body) {
        String[] creds = getCredentialOrThrow(robotId, envType);
        return sendRequest("PATCH", creds[0], creds[1], envType, path, body, isNewOpenBot);
    }

    /**
     * 获取机器人凭证
     *
     * <p>从 {@link RobotRegistry} 查找机器人配置，提取 AppID 和 AppSecret。
     * 注意：appSecretEncrypted 字段在当前框架模式下存储的是明文密钥。
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型
     * @return String[]{appId, appSecret}
     * @throws RuntimeException 机器人不存在时抛出
     */
    private String[] getCredentialOrThrow(String robotId, String envType) {
        var robot = robotRegistry.getRobot(robotId);
        if (robot == null) throw new RuntimeException("机器人不存在: " + robotId);
        return new String[]{robot.getAppId(), robot.getAppSecretEncrypted()};
    }

    /** 老版本 QQ 开放平台 API 基地址（正式环境） */

    /** 老版本 QQ 开放平台 API 基地址（沙箱环境） */

    /** 新版本 QQ 开放平台 API 基地址（统一地址） */

    /**
     * POST 请求（通过凭证）
     *
     * @param appId     机器人 AppID
     * @param appSecret 机器人 AppSecret 明文
     * @param envType   环境类型（SANDBOX / PRODUCTION）
     * @param path      API 路径（如 /v2/users/{openid}/messages）
     * @param body      请求体 JSON（可为 null）
     * @return 响应 JSON
     */
    public ObjectNode post(String appId, String appSecret, String envType,
                           String path, ObjectNode body) {
        return sendRequest("POST", appId, appSecret, envType, path, body, false);
    }

    /**
     * PUT 请求（通过凭证）— 用于互动事件响应等
     */
    public ObjectNode put(String appId, String appSecret, String envType,
                          String path, ObjectNode body) {
        return sendRequest("PUT", appId, appSecret, envType, path, body, false);
    }

    /**
     * PATCH 请求（通过凭证）— 用于修改频道/身份组/禁言等
     */
    public ObjectNode patch(String appId, String appSecret, String envType,
                            String path, ObjectNode body) {
        return sendRequest("PATCH", appId, appSecret, envType, path, body, false);
    }

    /**
     * DELETE 请求（通过凭证）— 用于撤回消息等
     *
     * <p>注意：发送超出 2 分钟的消息不可撤回。
     * 子频道/私信撤回支持 hidetip 参数控制是否隐藏提示小灰条。
     */
    public ObjectNode delete(String appId, String appSecret, String envType,
                             String path) {
        return sendRequest("DELETE", appId, appSecret, envType, path, null, false);
    }

    /**
     * GET 请求（通过凭证）
     */
    public ObjectNode get(String appId, String appSecret, String envType,
                          String path) {
        return sendRequest("GET", appId, appSecret, envType, path, null, false);
    }

    /**
     * 统一请求发送方法
     *
     * <p>所有 API 调用的底层实现，处理鉴权、请求构建、响应解析和错误处理。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从缓存获取 AccessToken（自动管理生命周期 7200 秒）</li>
     *   <li>构建 HTTP 请求，注入 Authorization 头: "QQBot {ACCESS_TOKEN}"</li>
     *   <li>发送请求</li>
     *   <li>解析响应：
     *     <ul>
     *       <li>200/201/202 — 成功</li>
     *       <li>204 — 成功无响应体（DELETE 操作）</li>
     *       <li>401 — Token 过期，刷新后重试一次</li>
     *       <li>429 — 频率限制，抛出 BusinessException</li>
     *       <li>其他 — 解析平台错误码返回</li>
     *     </ul>
     *   </li>
     *   <li>记录 X-Tps-trace-ID 用于问题排查</li>
     * </ol>
     *
     * @param method    HTTP 方法（GET/POST/PUT/PATCH/DELETE）
     * @param appId     机器人 AppID
     * @param appSecret 机器人 AppSecret 明文
     * @param envType   环境类型
     * @param path      API 路径
     * @param body      请求体 JSON（可为 null）
     * @return 响应 JSON
     * @throws BusinessException API 调用失败时抛出（包含错误码和排查建议）
     */
    private ObjectNode sendRequest(String method, String appId, String appSecret,
                                   String envType, String path, ObjectNode body,
                                   boolean isNewOpenBot) {
        // 1. 获取 AccessToken（缓存优先，过期自动刷新）
        String accessToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);

        // 2. 根据平台版本和环境类型选择 API 基地址
        String apiBase = QqPlatformConfig.getApiBaseUrl(isNewOpenBot, envType);
        String url = apiBase + path;

        // 3. 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // 文档鉴权格式: Authorization: "QQBot {ACCESS_TOKEN}"
                .header("Authorization", "QQBot " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));

        // 根据 HTTP 方法设置请求体
        switch (method.toUpperCase()) {
            case "POST":
                requestBuilder.POST(body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "PUT":
                requestBuilder.PUT(body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "PATCH":
                requestBuilder.method("PATCH", body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            case "GET":
                requestBuilder.GET();
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }

        try {
            log.debug("QQ API请求: {} {} body={}", method, path,
                    body != null ? body.toString() : "null");

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            int statusCode = response.statusCode();

            // 记录链路追踪ID（文档7.4: OpenAPI返回HTTP头 X-Tps-trace-ID）
            String traceId = response.headers().firstValue("X-Tps-trace-ID").orElse("N/A");

            // 完整打印响应报文（不截断），便于排查开放平台返回字段（如 /users/@me 的 union_openid 缺失问题）
            log.debug("QQ API响应: status={}, traceId={}, body={}",
                    statusCode, traceId, responseBody);

            // 4. 处理响应
            if (statusCode == 200 || statusCode == 201 || statusCode == 202) {
                // 成功（文档7.1: 201/202异步操作成功）
                onApiSuccess();
                if (responseBody == null || responseBody.isEmpty()) {
                    return Json.obj();
                }
                return Json.parseObj(responseBody);

            } else if (statusCode == 204) {
                // 成功无响应体（文档7.1: 204 成功，无包体，删除操作）
                onApiSuccess();
                return Json.obj();

            } else if (statusCode == 401) {
                // 认证失败（文档7.1: 401 认证失败）
                // Token过期，强制刷新后重试一次
                log.warn("AccessToken过期(401)，刷新重试: appId={}, path={}, traceId={}",
                        appId, path, traceId);
                accessTokenService.refreshAccessToken(appId, appSecret, envType, isNewOpenBot);
                // 重试一次（不递归，避免死循环）
                return sendRequestOnce(method, appId, appSecret, envType, path, body, accessToken, isNewOpenBot);

            } else if (statusCode == 429) {
                // 频率限制（文档7.1: 429 频率限制）
                log.warn("QQ API频率限制(429): path={}, traceId={}", path, traceId);
                throw new BusinessException(429, "QQ平台接口频率限制，请稍后重试");

            } else if (statusCode == 404) {
                // 未找到API（文档7.1: 404 未找到API）
                log.error("QQ API不存在(404): path={}, traceId={}", path, traceId);
                throw new BusinessException(404, "QQ平台API不存在: " + path);

            } else if (statusCode == 500 || statusCode == 504) {
                // 处理失败（文档7.1: 500/504 处理失败）
                log.error("QQ API服务端错误({}): path={}, traceId={}, body={}",
                        statusCode, path, traceId, responseBody);
                throw new BusinessException(statusCode, "QQ平台服务处理失败，请稍后重试");

            } else {
                // 其他错误 — 尝试解析平台错误码
                log.error("QQ API错误: status={}, path={}, traceId={}, body={}",
                        statusCode, path, traceId, responseBody);
                String errorMsg = parseErrorMessage(responseBody, statusCode);
                throw new BusinessException(statusCode, errorMsg);
            }

        } catch (BusinessException e) {
            onApiFailure(); // 含 429/404/500 等平台错误
            throw e; // 业务异常直接抛出
        } catch (InterruptedException e) {
            onApiFailure();
            Thread.currentThread().interrupt();
            log.warn("QQ API请求被中断: method={}, path={}", method, path);
            throw new BusinessException(500,
                    "QQ平台API请求被中断（可能因插件执行超时）");
        } catch (Exception e) {
            onApiFailure();
            log.error("QQ API请求异常: method={}, path={}, error={}", method, path, e.getMessage(), e);
            throw new BusinessException(500,
                    "QQ平台API请求失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 带自定义超时的请求发送方法
     *
     * <p>用于媒体上传等需要较长超时的请求。
     */
    private ObjectNode sendRequestWithTimeout(String method, String appId, String appSecret,
                                               String envType, String path, ObjectNode body,
                                               boolean isNewOpenBot, int timeoutSeconds) {
        String accessToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);
        String apiBase = QqPlatformConfig.getApiBaseUrl(isNewOpenBot, envType);
        String url = apiBase + path;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "QQBot " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds));

        requestBuilder.POST(body != null
                ? HttpRequest.BodyPublishers.ofString(body.toString())
                : HttpRequest.BodyPublishers.noBody());

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            int statusCode = response.statusCode();
            String traceId = response.headers().firstValue("X-Tps-trace-ID").orElse("N/A");

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isEmpty()) return Json.obj();
                return Json.parseObj(responseBody);
            } else {
                log.error("QQ API错误: status={}, path={}, traceId={}, body={}", statusCode, path, traceId, responseBody);
                String errorMsg = parseErrorMessage(responseBody, statusCode);
                throw new BusinessException(statusCode, errorMsg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("QQ API请求异常: method={}, path={}, error={}", method, path, e.getMessage(), e);
            throw new BusinessException(500, "QQ平台API请求失败: " + e.getMessage());
        }
    }

    /**
     * 重试请求（使用新 Token，不再次刷新）
     *
     * <p>当首次请求因 401 失败后，刷新 Token 再调用此方法重试一次。
     * 不会再次触发 401 重试，避免无限递归。
     *
     * @param method    HTTP 方法
     * @param appId     AppID
     * @param appSecret AppSecret
     * @param envType   环境类型
     * @param path      API 路径
     * @param body      请求体
     * @param oldToken  旧的 Token（用于日志记录，实际使用新 Token）
     * @return 响应 JSON
     * @throws BusinessException 重试仍失败时抛出
     */
    private ObjectNode sendRequestOnce(String method, String appId, String appSecret,
                                       String envType, String path, ObjectNode body,
                                       String oldToken, boolean isNewOpenBot) {
        // 获取新 Token（已由调用方刷新）
        String newToken = accessTokenService.getAccessToken(appId, appSecret, envType, isNewOpenBot);

        // 根据平台版本和环境类型选择 API 基地址
        String apiBase = QqPlatformConfig.getApiBaseUrl(isNewOpenBot, envType);
        String url = apiBase + path;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "QQBot " + newToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));

        switch (method.toUpperCase()) {
            case "POST":
                requestBuilder.POST(body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "PUT":
                requestBuilder.PUT(body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "PATCH":
                requestBuilder.method("PATCH", body != null
                        ? HttpRequest.BodyPublishers.ofString(body.toString())
                        : HttpRequest.BodyPublishers.noBody());
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            case "GET":
                requestBuilder.GET();
                break;
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();
            String traceId = response.headers().firstValue("X-Tps-trace-ID").orElse("N/A");

            log.info("QQ API重试响应: status={}, traceId={}", statusCode, traceId);

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isEmpty()) {
                    return Json.obj();
                }
                return Json.parseObj(responseBody);
            }

            throw new BusinessException(statusCode,
                    parseErrorMessage(responseBody, statusCode) + " (重试后仍失败, traceId=" + traceId + ")");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "QQ API重试请求失败: " + e.getMessage());
        }
    }

    /**
     * 解析平台错误信息
     *
     * <p>从 QQ 平台的错误响应体中提取错误码和消息，
     * 并使用 {@link dev.xuanji.adapter.qqbot.enums.QqApiErrorCode} 枚举
     * 提供结构化的错误信息和排查建议。
     *
     * @param responseBody QQ 平台响应体（JSON 格式）
     * @param statusCode   HTTP 状态码
     * @return 格式化的错误信息字符串
     */
    private String parseErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "QQ平台API调用失败, HTTP " + statusCode;
        }
        try {
            ObjectNode errorJson = Json.parseObj(responseBody);
            int code = errorJson.path("code").asInt(statusCode);
            String message = errorJson.path("message").asText("未知错误");
            String traceId = errorJson.path("trace_id").asText("");

            // 查找错误码枚举，获取排查建议
            var errorCode = dev.xuanji.adapter.qqbot.enums.QqApiErrorCode.of(code);
            if (errorCode != null) {
                return String.format("QQ平台错误 [%d] %s: %s | 排查: %s (traceId=%s)",
                        code, errorCode.getName(), message, errorCode.getSuggestion(), traceId);
            }

            return String.format("QQ平台错误 [%d]: %s (traceId=%s)", code, message, traceId);
        } catch (Exception e) {
            return "QQ平台API调用失败, HTTP " + statusCode + ", body=" + responseBody;
        }
    }

    /**
     * 解析 QQ 平台错误码为结构化对象
     *
     * <p>静态工具方法，可直接从响应体解析错误码枚举。
     *
     * @param responseBody QQ 平台响应体
     * @return 错误码信息，解析失败返回 null
     */
    public static dev.xuanji.adapter.qqbot.enums.QqApiErrorCode parseErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return null;
        try {
            ObjectNode errorJson = Json.parseObj(responseBody);
            int code = errorJson.path("code").asInt(0);
            return dev.xuanji.adapter.qqbot.enums.QqApiErrorCode.of(code);
        } catch (Exception e) {
            return null;
        }
    }
}
