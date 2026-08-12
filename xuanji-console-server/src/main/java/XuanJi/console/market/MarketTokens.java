package XuanJi.console.market;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 插件市场令牌 — 上传/审核令牌 AES-256-GCM 强加密后写死（源码不含明文令牌）。
 *
 * <p>加密方式：随机 32 字节密钥（KEY_HEX）加密令牌，密文 = Base64(IV(12B) + ciphertext)，
 * 运行时解密还原令牌。即便源码泄露，明文令牌（用于 CNB git 认证）也不会直接暴露。
 *
 * <p>令牌说明（用户提供，永久）：
 * <ul>
 *   <li>上传令牌 — 所有框架实例使用者上传插件到待审区（git 提交 .pending/）</li>
 *   <li>审核令牌 — 仅管理员持有，审核台验证 + 通过/拒绝</li>
 * </ul>
 */
public final class MarketTokens {

    /** AES-256-GCM 密钥（随机生成，勿外泄）。 */
    private static final String KEY_HEX = "0504372dadbded34cbc3d9d8d761185a010b9cc00daa039110a997cc71fdc179";
    /** 上传令牌密文。 */
    private static final String UPLOAD_TOKEN_ENC = "G3DqEla6pAueC7SOtrq4j0JB5DQ6giSY8iTm7KwxCa89B3c+1Q0xAoKWDa8Dd78jmwFxWnm1yA==";
    /** 审核令牌密文。 */
    private static final String ADMIN_TOKEN_ENC = "EDSlYr4zhS7z039Qc86x4ebz5KJ1O3o6FZcexp3y/SObp9aM/6HiMtK7xkcJBwdVq9Up9Ng32g==";

    private MarketTokens() {
    }

    /** 上传令牌（解密还原）。 */
    public static String uploadToken() {
        return decrypt(UPLOAD_TOKEN_ENC);
    }

    /** 审核令牌（解密还原）。 */
    public static String adminToken() {
        return decrypt(ADMIN_TOKEN_ENC);
    }

    private static String decrypt(String enc) {
        try {
            byte[] data = Base64.getDecoder().decode(enc);
            byte[] iv = new byte[12];
            byte[] ct = new byte[data.length - 12];
            System.arraycopy(data, 0, iv, 0, 12);
            System.arraycopy(data, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hexBytes(KEY_HEX), "AES"),
                    new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("市场令牌解密失败", e);
        }
    }

    private static byte[] hexBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
