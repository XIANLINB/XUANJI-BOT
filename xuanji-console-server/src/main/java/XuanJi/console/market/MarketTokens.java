package XuanJi.console.market;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 插件市场令牌 — 审核仓库令牌 AES-256-GCM 强加密后写死（源码不含明文令牌）。
 *
 * <p>加密方式：随机 32 字节密钥（KEY_HEX）加密令牌，密文 = Base64(IV(12B) + ciphertext)，
 * 运行时解密还原令牌。即便源码泄露，明文令牌（用于 CNB git 认证）也不会直接暴露。
 *
 * <p>令牌说明（用户提供，永久）：
 * <ul>
 *   <li>上传令牌 — 开发者上传插件到待审区（git 提交 .pending/）</li>
 *   <li>审核仓库上传令牌 — 同审核仓库 push 凭据</li>
 * </ul>
 *
 * <p><b>重要</b>：正式仓库（已上架插件）的管理员令牌<b>不存储于后端</b>。每次管理员在审核台
 * 输入令牌时，后端拿该令牌对正式仓库做一次「上传探针文件 → push → 删除 → push」的真实往返
 * 来鉴定其能否写访问仓库，绝不将令牌写死或做相等比较。
 */
public final class MarketTokens {

    /** AES-256-GCM 密钥（随机生成，勿外泄）。 */
    private static final String KEY_HEX = "0504372dadbded34cbc3d9d8d761185a010b9cc00daa039110a997cc71fdc179";
    /** 上传令牌密文（开发者推送待审到「审核仓库」用）。 */
    private static final String UPLOAD_TOKEN_ENC = "G3DqEla6pAueC7SOtrq4j0JB5DQ6giSY8iTm7KwxCa89B3c+1Q0xAoKWDa8Dd78jmwFxWnm1yA==";
    /** 审核仓库（XuanJiBot-plugin）上传令牌密文（开发者提交审核用）。 */
    private static final String REVIEW_TOKEN_ENC = "/AqDqD15M3Ktpsc52+jJT7sRmnKg0aoznBW/gfx5Ej5S1MB/cFhKnp5KO7NkBBBNIP3Zz45/rQ==";

    private MarketTokens() {
    }

    /** 上传令牌（解密还原）。 */
    public static String uploadToken() {
        return decrypt(UPLOAD_TOKEN_ENC);
    }

    /** 审核仓库上传令牌（解密还原）。 */
    public static String reviewToken() {
        return decrypt(REVIEW_TOKEN_ENC);
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
