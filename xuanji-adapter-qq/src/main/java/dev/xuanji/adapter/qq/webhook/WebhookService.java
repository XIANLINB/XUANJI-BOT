package dev.xuanji.adapter.qq.webhook;

/**
 * Webhook 服务接口
 *
 * <p>定义 Webhook 回调处理的核心契约，由 {@link WebhookServiceImpl} 实现。
 * 分离接口与实现便于测试（可使用 Mock 实现）和未来扩展。
 *
 * <h3>处理逻辑</h3>
 * <ul>
 *   <li>OpCode 13 — 回调地址验证，返回 Ed25519 签名 JSON</li>
 *   <li>OpCode 0  — 正常事件推送，验签后分发到 EventDispatcher，返回 null（HTTP 200 ACK）</li>
 * </ul>
 *
 * @see WebhookServiceImpl  具体实现
 * @see WebhookController   HTTP 入口
 */
public interface WebhookService {

    /**
     * 处理 Webhook 回调请求
     *
     * <p>根据 OpCode 类型执行不同的处理逻辑：
     * <ul>
     *   <li>OpCode 13（验证请求）— 返回包含签名的 JSON 字符串</li>
     *   <li>OpCode 0（事件推送）— 验签、去重、分发后返回 null</li>
     *   <li>其他 OpCode — 忽略，返回 null</li>
     * </ul>
     *
     * @param robotId     机器人 ID，用于查找密钥和配置
     * @param envType     环境类型（SANDBOX / PRODUCTION）
     * @param body        请求体原始 JSON 字符串
     * @param signHeader  X-Signature-Ed25519 头的值（可为 null，表示不验签）
     * @param tsHeader    X-Signature-Timestamp 头的值（可为 null）
     * @return 验证请求时返回签名 JSON，普通事件返回 null（表示 HTTP 200 ACK）
     */
    String handleWebhook(Long robotId, String envType, String body,
                         String signHeader, String tsHeader);
}
