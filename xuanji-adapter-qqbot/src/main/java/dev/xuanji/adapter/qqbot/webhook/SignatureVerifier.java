package dev.xuanji.adapter.qqbot.webhook;

import dev.xuanji.adapter.qqbot.util.AesUtil;
import dev.xuanji.adapter.qqbot.util.Ed25519Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 签名校验器
 *
 * <p>负责处理 QQ 平台 Webhook 回调的安全验证，确保收到的请求确实来自 QQ 平台。
 * 使用 Ed25519 签名算法进行签名和验签。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li><b>回调地址验证（OpCode 13）</b> — QQ 平台首次注册 Webhook 回调地址时，
 *       会发送验证请求。本类使用 Ed25519 私钥对 {@code event_ts + plain_token} 进行签名，
 *       返回签名结果供平台验证。</li>
 *   <li><b>正常事件签名验证</b> — 后续的事件推送请求携带签名头，
 *       本类使用 Ed25519 公钥验证 {@code timestamp + body} 的签名，防止伪造请求。</li>
 * </ol>
 *
 * <h3>密钥管理</h3>
 * <p>每个机器人的 Ed25519 密钥以 AES 加密形式存储在数据库中。
 * 注册时通过 {@link AesUtil} 解密后存储在内存的 {@link #secretRegistry} 中。
 * 密钥以 {@code "robotId:envType"} 为 key，支持同一机器人在不同环境使用不同密钥。
 *
 * <h3>线程安全性</h3>
 * <p>使用 {@link ConcurrentHashMap} 存储密钥，支持并发读写。
 * 注册操作通常在启动时完成，运行时以读操作为主。
 *
 * @see Ed25519Util  底层 Ed25519 签名/验签工具
 * @see AesUtil      AES 密钥解密工具
 * @see WebhookServiceImpl  调用本类进行签名验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureVerifier {

    /** Ed25519 签名工具，提供签名和验签功能 */
    private final Ed25519Util ed25519Util;

    /** AES 解密工具，用于解密存储的加密密钥 */
    private final AesUtil aesUtil;

    /**
     * 密钥注册表
     * <p>key = "robotId:envType"，value = Ed25519 私钥（明文，用于签名和派生公钥）
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final ConcurrentHashMap<String, String> secretRegistry = new ConcurrentHashMap<>();

    /**
     * 注册机器人的 Ed25519 密钥（从 AES 加密密文解密）
     *
     * <p>从数据库读取的密钥是 AES 加密的，需要先解密再存储到内存。
     *
     * @param robotId         机器人 ID
     * @param envType         环境类型（SANDBOX / PRODUCTION）
     * @param secretEncrypted AES 加密后的 Ed25519 密钥（Base64 编码）
     */
    public void registerSecret(String robotId, String envType, String secretEncrypted) {
        try {
            String secret = aesUtil.decrypt(secretEncrypted);
            secretRegistry.put(robotId + ":" + envType, secret);
            log.info("[签名验证] 注册密钥: robotId={}, env={}", robotId, envType);
        } catch (Exception e) {
            log.error("[签名验证] 注册密钥失败: robotId={}, error={}", robotId, e.getMessage());
        }
    }

    /** 注销某机器人的全部密钥（删机器人时调用）。 */
    public void unregister(String robotId) {
        secretRegistry.entrySet().removeIf(e -> e.getKey().startsWith(robotId + ":"));
        log.info("[签名验证] 注销密钥: robotId={}", robotId);
    }

    /**
     * 注册机器人的 Ed25519 密钥（明文直接注册）
     *
     * <p>用于测试或密钥本身已是明文的场景。
     *
     * @param robotId     机器人 ID
     * @param envType     环境类型
     * @param secretPlain 明文 Ed25519 密钥
     */
    public void registerSecretPlain(String robotId, String envType, String secretPlain) {
        secretRegistry.put(robotId + ":" + envType, secretPlain);
    }

    /**
     * 处理回调地址验证请求（OpCode 13）
     *
     * <p>QQ 平台在注册 Webhook 回调地址时会发送此请求进行验证。
     * 需要使用 Ed25519 私钥对 {@code event_ts + plain_token} 进行签名，
     * 并返回包含 plain_token 和 signature 的 JSON 响应。
     *
     * @param robotId   机器人 ID
     * @param envType   环境类型
     * @param plainToken QQ 平台提供的明文令牌
     * @param eventTs    事件时间戳
     * @return 包含 plain_token 和 signature 的 JSON 字符串
     */
    public String handleVerifyRequest(String robotId, String envType, String plainToken, String eventTs) {
        String botSecret = secretRegistry.get(robotId + ":" + envType);
        if (botSecret == null) {
            log.error("[签名验证] 密钥未注册: robotId={}, env={}", robotId, envType);
            // 密钥未注册时返回空签名（验证会失败，但不会崩溃）
            return "{\"plain_token\":\"" + plainToken + "\",\"signature\":\"\"}";
        }

        // 签名数据 = event_ts + plain_token（直接拼接，无分隔符）
        byte[] signData = (eventTs + plainToken).getBytes(StandardCharsets.UTF_8);
        String signature = ed25519Util.signWithBotSecret(botSecret, signData);

        log.info("[签名验证] 回调验证成功: robotId={}, env={}", robotId, envType);

        ObjectNode result = Json.obj();
        result.put("plain_token", plainToken);
        result.put("signature", signature);
        return result.toString();
    }

    /**
     * 校验正常事件推送的签名
     *
     * <p>QQ 平台的事件推送请求会在 HTTP 头中携带签名信息：
     * <ul>
     *   <li>{@code X-Signature-Ed25519} — 签名值（十六进制）</li>
     *   <li>{@code X-Signature-Timestamp} — 时间戳</li>
     * </ul>
     * 验签数据 = timestamp + body（请求体原始字符串）
     *
     * @param robotId          机器人 ID
     * @param envType          环境类型
     * @param signatureHeader  X-Signature-Ed25519 头的值
     * @param timestampHeader  X-Signature-Timestamp 头的值
     * @param body             请求体原始 JSON 字符串
     * @return true=验签通过（请求合法），false=验签失败（请求可能被伪造）
     */
    public boolean verifyEventSignature(String robotId, String envType,
                                        String signatureHeader, String timestampHeader, String body) {
        if (signatureHeader == null || timestampHeader == null) {
            log.warn("[签名验证] 签名头缺失: robotId={}, env={}", robotId, envType);
            return false;
        }

        String botSecret = secretRegistry.get(robotId + ":" + envType);
        if (botSecret == null) {
            log.warn("[签名验证] 密钥未注册: robotId={}, env={}", robotId, envType);
            return false;
        }

        // 验签数据 = timestamp + body（直接拼接）
        byte[] verifyData = (timestampHeader + body).getBytes(StandardCharsets.UTF_8);
        return ed25519Util.verifyWithBotSecret(botSecret, verifyData, signatureHeader);
    }
}
