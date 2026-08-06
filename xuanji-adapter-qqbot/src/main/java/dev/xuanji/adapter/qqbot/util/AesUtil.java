package dev.xuanji.adapter.qqbot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 *
 * <p>用于加密存储 AppSecret、Ed25519 密钥等敏感信息。
 * 使用 AES-256-GCM 模式，同时提供机密性和完整性保护（防篡改）。
 *
 * <h3>加密算法</h3>
 * <ul>
 *   <li><b>算法</b>：AES/GCM/NoPadding</li>
 *   <li><b>密钥长度</b>：256 位（32 字节）</li>
 *   <li><b>IV 长度</b>：12 字节（GCM 推荐长度）</li>
 *   <li><b>认证标签长度</b>：128 位（16 字节）</li>
 * </ul>
 *
 * <h3>加密输出格式</h3>
 * <p>Base64(IV[12字节] + 密文 + AuthTag[16字节])
 * <p>解密时从 Base64 字符串中提取 IV、密文和认证标签。
 *
 * <h3>密钥配置</h3>
 * <p>通过 application.yml 的 {@code xuanji.aes.key} 配置十六进制格式的 256 位密钥。
 * 示例：{@code xuanji.aes.key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}
 *
 * @see Ed25519Util  Ed25519 签名工具（密钥使用本类加密存储）
 */
@Slf4j
@Component
public class AesUtil {

    /** 加密算法：AES/GCM/NoPadding（GCM 模式自带认证，无需额外填充） */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /** GCM 推荐的 IV 长度（12 字节 = 96 位） */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM 认证标签长度（128 位 = 16 字节） */
    private static final int GCM_TAG_LENGTH = 128;

    /** AES 密钥规格（从配置的十六进制密钥初始化） */
    private final SecretKeySpec secretKey;

    /** 安全随机数生成器（用于生成随机 IV） */
    private final SecureRandom secureRandom;

    /**
     * 构造函数，从十六进制密钥字符串初始化
     *
     * @param hexKey 十六进制格式的 256 位 AES 密钥（64 个十六进制字符）
     */
    public AesUtil(@Value("${xuanji.aes.key:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String hexKey) {
        // 将十六进制密钥字符串转为字节数组
        byte[] keyBytes = hexStringToByteArray(hexKey);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文
     *
     * <p>流程：
     * <ol>
     *   <li>生成 12 字节的随机 IV</li>
     *   <li>使用 AES-256-GCM 加密明文</li>
     *   <li>拼接 IV + 密文 + 认证标签</li>
     *   <li>Base64 编码输出</li>
     * </ol>
     *
     * @param plainText 明文字符串
     * @return Base64 编码的密文（包含 IV 和认证标签）
     * @throws RuntimeException 加密失败时抛出
     */
    public String encrypt(String plainText) {
        try {
            // 生成随机 IV（每次加密使用不同的 IV，确保相同明文产生不同密文）
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文（密文中已包含认证标签）
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密密文
     *
     * <p>流程：
     * <ol>
     *   <li>Base64 解码</li>
     *   <li>提取前 12 字节作为 IV</li>
     *   <li>剩余部分为密文（含认证标签）</li>
     *   <li>使用 AES-256-GCM 解密并验证完整性</li>
     * </ol>
     *
     * @param cipherTextBase64 Base64 编码的密文
     * @return 解密后的明文字符串
     * @throws RuntimeException 解密失败时抛出（密钥错误、密文被篡改等）
     */
    public String decrypt(String cipherTextBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherTextBase64);

            // 提取 IV（前 12 字节）
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // 提取密文（剩余部分，包含认证标签）
            byte[] cipherText = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 十六进制字符串转字节数组
     *
     * <p>每两个十六进制字符转换为一个字节。
     * 例如："01ab" -> [0x01, 0xab]
     *
     * @param hex 十六进制字符串（偶数长度）
     * @return 字节数组
     */
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
