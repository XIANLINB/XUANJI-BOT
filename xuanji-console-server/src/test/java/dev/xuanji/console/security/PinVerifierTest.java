package dev.xuanji.console.security;

import dev.xuanji.core.security.PinCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PinVerifier 读取 xuanji_setup 校验 PIN")
class PinVerifierTest {

    private JdbcTemplate jdbc;
    private PinVerifier verifier;

    @BeforeEach
    void setUp() {
        // 每个用例用独立的匿名内存库，避免 DB_CLOSE_DELAY 导致的表残留相互干扰
        String dbName = "pinverify_" + java.util.UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE xuanji_setup ("
                + "id INT PRIMARY KEY, pin_salt VARCHAR(64), pin_hash VARCHAR(128), "
                + "step INT DEFAULT 0, completed BOOLEAN DEFAULT FALSE)");
        verifier = new PinVerifier(jdbc);
    }

    @Test
    @DisplayName("无记录时 pinConfigured=false 且 verify=false")
    void empty() {
        assertFalse(verifier.pinConfigured());
        assertFalse(verifier.verify("123456"));
    }

    @Test
    @DisplayName("落库 PIN 后可校验通过，错误 PIN 失败")
    void roundtrip() {
        String salt = PinCrypto.generateSalt();
        String hash = PinCrypto.hashPin("654321", salt);
        jdbc.update("MERGE INTO xuanji_setup (id, pin_salt, pin_hash, step) KEY(id) VALUES (1,?,?,1)",
                salt, hash);
        assertTrue(verifier.pinConfigured());
        assertTrue(verifier.verify("654321"));
        assertFalse(verifier.verify("000000"));
    }
}
