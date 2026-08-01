package dev.xuanji.adapter.qq.webhook;

import dev.xuanji.adapter.qq.model.Robot;
import dev.xuanji.adapter.qq.model.RobotEnvironment;
import dev.xuanji.adapter.qq.registry.RobotRegistry;
import dev.xuanji.adapter.qq.webhook.WebhookPayload;
import dev.xuanji.adapter.qq.webhook.WebhookService;
import dev.xuanji.adapter.qq.webhook.SignatureVerifier;
import dev.xuanji.core.pipeline.BotPipeline;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.adapter.qq.converter.QqEventConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 服务实现
 *
 * <p>处理 QQ 平台通过 HTTP 推送的事件，是 Webhook 模式的核心处理逻辑。
 * 与 WebSocket 模式共享同一个 {@link EventSink} 进行事件分发。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li><b>查找机器人</b> — 根据 robotId 从 {@link RobotRegistry} 查找配置，
 *       不存在或已停用则忽略</li>
 *   <li><b>查找环境配置</b> — 获取对应的环境配置（密钥、Webhook URL 等）</li>
 *   <li><b>解析 Payload</b> — 将 JSON 字符串解析为 {@link WebhookPayload}</li>
 *   <li><b>按 OpCode 处理</b>：
 *     <ul>
 *       <li>OpCode 13 — 回调地址验证，调用 {@link SignatureVerifier} 返回签名</li>
 *       <li>OpCode 0 — 事件推送：验签 -> 去重 -> 同步分发</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>事件去重</h3>
 * <p>使用内存中的 {@link ConcurrentHashMap}（通过 {@code ConcurrentHashMap.newKeySet()}）存储已处理的事件 ID。
 * 每个事件 ID 在 5 分钟后自动清理（通过守护线程延迟执行）。
 * 防止 QQ 平台因网络重试导致同一事件被处理多次。
 *
 * <h3>与 WebSocket 模式的区别</h3>
 * <ul>
 *   <li>Webhook — QQ 平台主动推送，需要验签，有事件去重需求</li>
 *   <li>WebSocket — 客户端主动连接，通过长连接接收事件，无需验签</li>
 * </ul>
 *
 * @see WebhookService         接口定义
 * @see WebhookController      HTTP 入口
 * @see SignatureVerifier      签名验证
 * @see EventSink        事件分发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    /** 机器人注册表，用于查找机器人和环境配置 */
    private final RobotRegistry robotRegistry;

    /** 签名验证器，用于回调验证和事件签名校验 */
    private final SignatureVerifier signatureVerifier;

    /** 流水线，事件统一入口（Whitelist/RateLimit/权限等阶段在此生效） */
    private final BotPipeline botPipeline;

    /**
     * 事件去重集合（内存版）
     * <p>存储已处理的事件 ID，防止重复处理。
     * 使用 ConcurrentHashMap.newKeySet() 创建线程安全的 Set。
     * 注意：应用重启后去重记录会丢失，但 QQ 平台的重试窗口通常在几分钟内，
     * 重启期间的短暂重复处理是可接受的。
     */
    private final Set<String> dedupSet = ConcurrentHashMap.newKeySet();

    @Override
    public String handleWebhook(String robotId, String envType, String body,
                                String signHeader, String tsHeader) {
        // 1. 查找机器人配置
        Robot robot = robotRegistry.getRobot(robotId);
        if (robot == null) {
            log.warn("[Webhook] 机器人不存在: robotId={}", robotId);
            return null;
        }

        // 检查机器人状态（status=1 表示正常，其他值表示已停用）
        if (robot.getStatus() != null && robot.getStatus() != 1) {
            log.warn("[Webhook] 机器人已停用: robotId={}, status={}", robotId, robot.getStatus());
            return null;
        }

        // 2. 查找环境配置（密钥、Webhook URL 等）
        RobotEnvironment env = robotRegistry.getEnvironment(robotId, envType.toUpperCase());
        if (env == null) {
            log.warn("[Webhook] 环境配置不存在: robotId={}, appId={}, env={}", robotId, robot.getAppId(), envType);
            return null;
        }

        // 3. 解析 Payload
        WebhookPayload payload;
        try {
            payload = WebhookPayload.parse(body);
        } catch (Exception e) {
            log.error("[Webhook] Payload 解析失败: robotId={}, error={}", robotId, e.getMessage());
            return null;
        }

        // 4. 根据 OpCode 处理
        if (payload.getOp() == 13) {
            // ===== 回调地址验证（OpCode 13）=====
            // QQ 平台首次注册回调地址时发送此请求
            ObjectNode d = payload.getD();
            String plainToken = d.path("plain_token").asText("");
            String eventTs = d.path("event_ts").asText("");
            String verifyResponse = signatureVerifier.handleVerifyRequest(
                    robotId, envType.toUpperCase(), plainToken, eventTs);
            log.info("[Webhook] 回调验证完成: robotId={}, appId={}, env={}", robotId, robot.getAppId(), envType);
            return verifyResponse;

        } else if (payload.getOp() == 0) {
            // ===== 正常事件推送（OpCode 0）=====

            // 签名校验（如果请求携带了签名头）
            if (signHeader != null && tsHeader != null) {
                boolean valid = signatureVerifier.verifyEventSignature(
                        robotId, envType.toUpperCase(), signHeader, tsHeader, body);
                if (!valid) {
                    log.warn("[Webhook] 签名校验失败: robotId={}, env={}", robotId, envType);
                    return null; // 签名无效，丢弃事件
                }
            }

            // 事件去重（基于事件 ID）
            String eventId = payload.getId();
            if (eventId != null && !eventId.isEmpty()) {
                if (!dedupSet.add(eventId)) {
                    // add 返回 false 表示已存在，说明是重复事件
                    log.debug("[Webhook] 重复事件已忽略: eventId={}", eventId);
                    return null;
                }
                // 5 分钟后清理去重记录，避免内存无限增长
                cleanDedupLater(eventId);
            }

            // 同步分发事件到对应的 Handler（经 Pipeline 阶段）
            String eventType = payload.getT();
            ObjectNode data = payload.getD();
            if (eventType != null && data != null) {
                try {
                    Bot bot = new Bot("qq:" + robotId, "qq", robotId, Bot.Status.ONLINE, Set.of());
                    BotEvent be = QqEventConverter.convert(bot, eventType, envType.toUpperCase(), data, eventId);
                    botPipeline.proceed(be);
                } catch (Exception e) {
                    log.error("[Webhook] 事件分发异常: type={}, robotId={}, error={}",
                            eventType, robotId, e.getMessage(), e);
                }
            }

            return null; // 返回 null 表示 HTTP 200 ACK（无需响应体）

        } else {
            // 未知 OpCode，忽略
            log.debug("[Webhook] 忽略的 OpCode={}: robotId={}", payload.getOp(), robotId);
            return null;
        }
    }

    /**
     * 延迟清理去重记录
     *
     * <p>启动一个守护线程，在 5 分钟后从去重集合中移除指定的事件 ID。
     * 使用守护线程而非定时任务，因为去重清理是低优先级的后台操作。
     *
     * @param eventId 要清理的事件 ID
     */
    private void cleanDedupLater(String eventId) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000); // 5 分钟后清理
                dedupSet.remove(eventId);
            } catch (InterruptedException ignored) {}
        }, "dedup-cleaner");
        thread.setDaemon(true); // 守护线程，不阻止 JVM 退出
        thread.start();
    }
}
