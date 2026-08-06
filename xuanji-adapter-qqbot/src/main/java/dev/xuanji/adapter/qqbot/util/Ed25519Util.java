package dev.xuanji.adapter.qqbot.util;

import lombok.extern.slf4j.Slf4j;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Ed25519 签名工具类
 *
 * <p>为 QQ 机器人 Webhook 提供 Ed25519 签名和验签功能。
 * QQ 平台使用 Ed25519 算法验证 Webhook 回调的真实性和完整性。
 *
 * <h3>密钥生成方式</h3>
 * <p>直接使用 botSecret 的 UTF-8 字节作为 Ed25519 私钥种子：
 * <ul>
 *   <li>如果 botSecret 不足 32 字节 — 右侧补零</li>
 *   <li>如果 botSecret 超过 32 字节 — 截取前 32 字节</li>
 * </ul>
 *
 * <h3>签名数据格式</h3>
 * <ul>
 *   <li><b>回调验证（OpCode 13）</b>：{@code event_ts + plain_token}（直接拼接，无分隔符）</li>
 *   <li><b>事件验签</b>：{@code timestamp + body}（直接拼接）</li>
 * </ul>
 *
 * <h3>签名输出</h3>
 * <p>签名结果为十六进制小写字符串（64 字节 = 128 个十六进制字符）。
 *
 * @see dev.xuanji.adapter.qqbot.webhook.SignatureVerifier 使用本类进行签名验证
 * @see AesUtil 用于加密存储 Ed25519 密钥
 */
@Slf4j
@Component
public class Ed25519Util {

    /** Ed25519 曲线名称（IANA 标准名称） */
    private static final String CURVE_NAME = "Ed25519";

    /** 种子长度（32 字节 = 256 位） */
    private static final int SEED_LENGTH = 32;

    /**
     * 从 botSecret 生成 Ed25519 私钥
     *
     * <p>将 botSecret 的 UTF-8 字节作为 Ed25519 私钥种子：
     * <ul>
     *   <li>不足 32 字节 — 右侧补零</li>
     *   <li>超过 32 字节 — 截取前 32 字节</li>
     * </ul>
     *
     * @param botSecret QQ 机器人 AppSecret
     * @return EdDSAPrivateKey 私钥对象
     * @throws RuntimeException 密钥生成失败时抛出
     */
    public EdDSAPrivateKey generatePrivateKey(String botSecret) {
        try {
            byte[] secretBytes = botSecret.getBytes(StandardCharsets.UTF_8);
            byte[] seed = new byte[SEED_LENGTH];

            if (secretBytes.length >= SEED_LENGTH) {
                // 超过 32 字节取前 32 字节
                System.arraycopy(secretBytes, 0, seed, 0, SEED_LENGTH);
            } else {
                // 不足 32 字节，复制后右侧保持为 0
                System.arraycopy(secretBytes, 0, seed, 0, secretBytes.length);
            }

            EdDSAPrivateKeySpec privKeySpec = new EdDSAPrivateKeySpec(
                    seed, EdDSANamedCurveTable.getByName(CURVE_NAME));
            return new EdDSAPrivateKey(privKeySpec);
        } catch (Exception e) {
            throw new RuntimeException("生成Ed25519私钥失败", e);
        }
    }

    /**
     * 从私钥导出公钥
     *
     * <p>Ed25519 的公钥可以从私钥直接推导，无需单独存储。
     *
     * @param privateKey 私钥
     * @return EdDSAPublicKey 公钥对象
     */
    public EdDSAPublicKey derivePublicKey(EdDSAPrivateKey privateKey) {
        EdDSAPublicKeySpec pubKeySpec = new EdDSAPublicKeySpec(
                privateKey.getAbyte(), EdDSANamedCurveTable.getByName(CURVE_NAME));
        return new EdDSAPublicKey(pubKeySpec);
    }

    /**
     * 使用私钥签名数据
     *
     * @param privateKey 私钥
     * @param data       待签名的数据（UTF-8 字节）
     * @return 十六进制签名字符串（小写，128 个字符）
     * @throws RuntimeException 签名失败时抛出
     */
    public String sign(EdDSAPrivateKey privateKey, byte[] data) {
        try {
            net.i2p.crypto.eddsa.EdDSAEngine engine = new net.i2p.crypto.eddsa.EdDSAEngine();
            engine.initSign(privateKey);
            engine.update(data);
            byte[] signature = engine.sign();
            return HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            throw new RuntimeException("Ed25519签名失败", e);
        }
    }

    /**
     * 使用公钥验签
     *
     * @param publicKey    公钥
     * @param data         原始数据（UTF-8 字节）
     * @param signatureHex 十六进制签名字符串
     * @return true=验签通过（签名有效），false=验签失败（签名无效或数据被篡改）
     */
    public boolean verify(EdDSAPublicKey publicKey, byte[] data, String signatureHex) {
        try {
            byte[] signatureBytes = HexFormat.of().parseHex(signatureHex);
            net.i2p.crypto.eddsa.EdDSAEngine engine = new net.i2p.crypto.eddsa.EdDSAEngine();
            engine.initVerify(publicKey);
            engine.update(data);
            return engine.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("Ed25519验签异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 便捷方法：用 botSecret 签名数据
     *
     * <p>一步完成从 botSecret 生成私钥并签名，适用于回调地址验证（OpCode 13）。
     *
     * @param botSecret 机器人密钥
     * @param data      待签名的数据
     * @return 十六进制签名字符串
     */
    public String signWithBotSecret(String botSecret, byte[] data) {
        EdDSAPrivateKey privateKey = generatePrivateKey(botSecret);
        return sign(privateKey, data);
    }

    /**
     * 便捷方法：用 botSecret 验签
     *
     * <p>一步完成从 botSecret 生成私钥、导出公钥并验签，适用于事件签名验证。
     *
     * @param botSecret    机器人密钥
     * @param data         原始数据
     * @param signatureHex 十六进制签名
     * @return true=验签通过
     */
    public boolean verifyWithBotSecret(String botSecret, byte[] data, String signatureHex) {
        EdDSAPrivateKey privateKey = generatePrivateKey(botSecret);
        EdDSAPublicKey publicKey = derivePublicKey(privateKey);
        return verify(publicKey, data, signatureHex);
    }
}
