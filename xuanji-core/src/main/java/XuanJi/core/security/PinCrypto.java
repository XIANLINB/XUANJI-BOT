package XuanJi.core.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 访问口令（PIN）的 PBKDF2 派生与校验 —— 全框架唯一实现，供首次安装向导与控制台登录鉴权共用。
 *
 * <p>口令使用 {@code PBKDF2WithHmacSHA256}（16 字节随机盐 + 10 万次迭代 + 256 位派生密钥），
 * 库中只留 Base64 的盐与哈希，明文 PIN 不落盘；校验走 {@link MessageDigest#isEqual} 常量时间比较，
 * 避免计时侧信道。
 */
public final class PinCrypto {

    /** PBKDF2 盐长度（字节）。 */
    public static final int SALT_BYTES = 16;
    /** PBKDF2 迭代次数。 */
    public static final int ITERATIONS = 100_000;
    /** PBKDF2 派生密钥长度（位）。 */
    public static final int KEY_BITS = 256;

    private PinCrypto() {}

    /** 生成随机盐（Base64 字符串）。 */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** 由明文 PIN + Base64 盐派生哈希（Base64 字符串）。 */
    public static String hashPin(String pin, String saltB64) {
        byte[] salt = Base64.getDecoder().decode(saltB64);
        byte[] hash = pbkdf2(pin, salt);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验明文 PIN 是否与存储的盐/哈希匹配。
     *
     * @param pin     待校验明文
     * @param saltB64 存储的 Base64 盐
     * @param hashB64 存储的 Base64 哈希
     * @return 匹配返回 true；任何参数为 null/空、或解码/计算异常一律返回 false（不泄露内部状态）
     */
    public static boolean verify(String pin, String saltB64, String hashB64) {
        if (pin == null || saltB64 == null || hashB64 == null) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            byte[] expected = Base64.getDecoder().decode(hashB64);
            byte[] actual = pbkdf2(pin, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String pin, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS))
                    .getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 计算失败", e);
        }
    }
}
