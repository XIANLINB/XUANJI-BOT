package XuanJi.console.security;

import XuanJi.core.security.PinCrypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 访问口令校验器 —— 读取安装向导落库的 {@code xuanji_setup} 表，用 PBKDF2 校验明文 PIN。
 *
 * <p>表由 {@code SetupController} 在构造期用 {@code CREATE TABLE IF NOT EXISTS} 兜底建好，
 * 因此不依赖外部迁移；任何查询异常一律按「校验不通过」处理，避免泄露内部状态。
 */
@Component
public class PinVerifier {

    private final JdbcTemplate jdbc;

    public PinVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 校验明文 PIN 是否正确；任何异常一律返回 false。 */
    public boolean verify(String pin) {
        try {
            String saltB64 = jdbc.queryForObject(
                    "SELECT pin_salt FROM xuanji_setup WHERE id=1", String.class);
            String hashB64 = jdbc.queryForObject(
                    "SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            return PinCrypto.verify(pin, saltB64, hashB64);
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否已设置访问口令（PIN 哈希已落库）。 */
    public boolean pinConfigured() {
        try {
            String hashB64 = jdbc.queryForObject(
                    "SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            return hashB64 != null && !hashB64.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
