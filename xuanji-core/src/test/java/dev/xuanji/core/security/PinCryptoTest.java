package dev.xuanji.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PinCrypto PBKDF2 派生与校验")
class PinCryptoTest {

    @Test
    @DisplayName("相同 PIN + 盐，hashPin 可复现且 verify 通过")
    void verifyMatchesSamePin() {
        String salt = PinCrypto.generateSalt();
        String hash = PinCrypto.hashPin("123456", salt);
        assertTrue(PinCrypto.verify("123456", salt, hash), "正确 PIN 应校验通过");
        assertFalse(PinCrypto.verify("000000", salt, hash), "错误 PIN 应校验失败");
    }

    @Test
    @DisplayName("不同盐得到的哈希不同（防重放/防彩虹表）")
    void differentSaltDifferentHash() {
        String h1 = PinCrypto.hashPin("123456", PinCrypto.generateSalt());
        String h2 = PinCrypto.hashPin("123456", PinCrypto.generateSalt());
        assertNotEquals(h1, h2, "相同 PIN 不同盐应得到不同哈希");
    }

    @Test
    @DisplayName("参数为 null/空一律返回 false，不抛异常")
    void nullSafe() {
        assertFalse(PinCrypto.verify(null, "s", "h"));
        assertFalse(PinCrypto.verify("123456", null, "h"));
        assertFalse(PinCrypto.verify("123456", "s", null));
        assertFalse(PinCrypto.verify("123456", "!!not-base64!!", "h"));
    }

    @Test
    @DisplayName("生成盐为合法 Base64 且非空")
    void saltFormat() {
        String salt = PinCrypto.generateSalt();
        assertNotNull(salt);
        assertFalse(salt.isBlank());
        // 应能解码回 16 字节
        assertEquals(16, java.util.Base64.getDecoder().decode(salt).length);
    }
}
