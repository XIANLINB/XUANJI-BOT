package XuanJi.core.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 通用凭据落库加密（AES-256-GCM）—— 用于 QQ 开放平台 clientSecret 等在库中的可逆加密存储。
 *
 * <p>算法与 {@code XuanJi.llm.provider.LlmCredentialCipher} 完全一致（AES-256-GCM + PBKDF2 派生，
 * 10 万次迭代、256 位密钥、12 字节 IV、128 位 GCM 标签），口令复用同一环境变量
 * {@code xuanji.llm.crypto.passphrase}（或启动参数 {@code -Dxuanji.llm.crypto.passphrase}），
 * 未配置时回退内置默认口令并打告警（此时仅提供「防明文泄露」级别的混淆，建议运维显式配置口令）。
 *
 * <p>之所以放在 {@code xuanji-core} 而非直接复用 LLM 的 cipher：QQ 适配器只依赖 core、不依赖 llm，
 * 在 core 提供通用实现可避免模块间耦合。
 *
 * <p><b>密文格式</b>：{@code enc:<Base64(12字节IV || 密文+GCM标签)>}。
 * 读出时若不以 {@code enc:} 开头则按旧明文原样返回，保证旧库明文数据正向兼容、升级无需迁移。
 */
public final class CredentialCipher {

    /** 密文前缀；用于区分旧明文与新密文。 */
    public static final String PREFIX = "enc:";

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITER = 100_000;
    private static final int KEY_BITS = 256;
    /** 应用级固定盐（自托管场景足够；如需更强可改为每实例随机并持久化）。 */
    private static final byte[] SALT = "XUANJI-CORE-CRED-SALT-v1".getBytes(StandardCharsets.UTF_8);

    private static final SecretKey KEY;

    static {
        String pass = System.getenv("xuanji.llm.crypto.passphrase");
        if (pass == null || pass.isBlank()) {
            pass = System.getProperty("xuanji.llm.crypto.passphrase");
        }
        if (pass == null || pass.isBlank()) {
            pass = "xuanji-core-default-passphrase-change-me";
            System.out.println("[CRED-CIPHER] 警告：未配置 xuanji.llm.crypto.passphrase，"
                    + "凭据仅做默认口令混淆，请配置环境变量/启动参数以获得真实加密保护。");
        }
        KEY = deriveKey(pass);
    }

    private CredentialCipher() {}

    /**
     * 明文 → 密文（{@code enc:...}）。
     * <ul>
     *   <li>null / 空白：原样返回（避免把空串加密成噪声）。</li>
     *   <li>已带 {@link #PREFIX}：原样返回（幂等，防止重复加密）。</li>
     * </ul>
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /** 存储值 → 明文；非密文（旧明文）原样返回；解密失败回退原值不阻断业务。 */
    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] out = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(out, 0, IV_BYTES);
            byte[] ct = Arrays.copyOfRange(out, IV_BYTES, out.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            // 密文损坏/口令不匹配：回退原值，避免阻断（运维应排查）
            System.err.println("[CRED-CIPHER] 凭据解密失败，回退原值: " + e.getMessage());
            return stored;
        } catch (java.security.GeneralSecurityException e) {
            System.err.println("[CRED-CIPHER] 凭据解密失败，回退原值: " + e.getMessage());
            return stored;
        }
    }

    private static SecretKey deriveKey(String passphrase) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = f.generateSecret(new PBEKeySpec(passphrase.toCharArray(), SALT, PBKDF2_ITER, KEY_BITS))
                    .getEncoded();
            return new SecretKeySpec(key, KEY_ALGO);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("凭据密钥派生失败", e);
        }
    }
}
