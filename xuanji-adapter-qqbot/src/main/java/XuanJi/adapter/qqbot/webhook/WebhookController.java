package XuanJi.adapter.qqbot.webhook;

import XuanJi.adapter.qqbot.model.Robot;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import XuanJi.adapter.qqbot.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Webhook 回调控制器
 *
 * <p>接收 QQ 平台通过 HTTP POST 推送的事件通知，是 Webhook 模式的入口。
 * QQ 平台会将事件推送到配置的回调地址，本控制器负责接收、校验和转发。
 *
 * <h3>回调地址格式</h3>
 * <p>{@code POST /webhook/xuanji/{robotId}}
 * <ul>
 *   <li>robotId — 机器人 ID，用于查找对应的密钥和配置</li>
 *   <li>环境类型从机器人的 activeEnv 字段获取</li>
 * </ul>
 *
 * <h3>请求头</h3>
 * <ul>
 *   <li>{@code X-Signature-Ed25519} — Ed25519 签名（用于验签）</li>
 *   <li>{@code X-Signature-Timestamp} — 时间戳（用于验签）</li>
 * </ul>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>根据 robotId 查找机器人配置（不存在返回 404）</li>
 *   <li>获取环境类型（默认 SANDBOX）</li>
 *   <li>读取请求体（限制 1MB，防止恶意大请求）</li>
 *   <li>提取签名头，交给 {@link WebhookService} 处理</li>
 *   <li>返回响应（验证请求返回签名 JSON，普通事件返回空 200）</li>
 * </ol>
 *
 * @see WebhookService      处理具体的验证和事件分发逻辑
 * @see SignatureVerifier   签名验证
 * @see RobotRegistry       机器人配置查找
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    /** Webhook 服务，处理验证和事件分发 */
    private final WebhookService webhookService;

    /** 机器人注册表，用于查找机器人配置 */
    private final RobotRegistry robotRegistry;

    /**
     * 处理 Webhook 回调请求
     *
     * @param robotId 机器人 ID（路径参数）
     * @param request HTTP 请求（用于读取请求体和签名头）
     * @return 验证请求返回签名 JSON，普通事件返回空 200，错误返回 400/404
     */
    /**
     * Webhook 回调入口（按 appId）
     * 路径: POST /webhook/{appId}
     */
    @PostMapping(value = "/{appId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleWebhookByAppId(
            @PathVariable String appId,
            HttpServletRequest request) {
        Robot robot = robotRegistry.findByAppId(appId);
        if (robot == null) {
            log.warn("[Webhook] AppID 未注册: appId={}", appId);
            return ResponseEntity.notFound().build();
        }
        return doHandle(robot.getId(), robot, request);
    }

    /**
     * Webhook 回调入口（按 robotId，向后兼容）
     * 路径: POST /webhook/xuanji/{robotId}
     */
    @PostMapping(value = "/xuanji/{robotId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleWebhook(
            @PathVariable String robotId,
            HttpServletRequest request) {
        Robot robot = robotRegistry.getRobot(robotId);
        if (robot == null) {
            log.warn("[Webhook] 机器人不存在: robotId={}", robotId);
            return ResponseEntity.notFound().build();
        }
        return doHandle(robotId, robot, request);
    }

    private ResponseEntity<String> doHandle(String robotId, Robot robot, HttpServletRequest request) {

        // 1. 获取环境类型（默认 SANDBOX）
        String envType = robot.getActiveEnv();
        if (envType == null || envType.isBlank()) envType = "SANDBOX";

        // 3. 读取请求体（限制 1MB，防止恶意大请求导致内存溢出）
        String body;
        try {
            body = request.getReader().lines().collect(Collectors.joining());
            if (body.length() > 1024 * 1024) {
                log.warn("[Webhook] 请求体过大: robotId={}, size={}", robotId, body.length());
                return ResponseEntity.badRequest().build();
            }
        } catch (IOException e) {
            log.error("[Webhook] 读取请求体失败: robotId={}", robotId);
            return ResponseEntity.badRequest().build();
        }

        // 4. 提取签名头
        String signHeader = request.getHeader("X-Signature-Ed25519");
        String tsHeader = request.getHeader("X-Signature-Timestamp");

        // 5. 交给 WebhookService 处理
        String response = webhookService.handleWebhook(robotId, envType, body, signHeader, tsHeader);

        // 6. 返回响应（验证请求返回签名 JSON，普通事件返回空 200）
        if (response != null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } else {
            return ResponseEntity.ok().build();
        }
    }
}
