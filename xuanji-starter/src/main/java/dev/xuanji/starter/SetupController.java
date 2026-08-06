/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.adapter.qqbot.storage.QqBotRepository
 *  dev.xuanji.core.config.XuanjiRobotProperties
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package dev.xuanji.starter;

import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.FrameworkBotRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/xuanji/api/setup"})
public class SetupController {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(SetupController.class);
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 100000;
    private static final int KEY_BITS = 256;
    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final FrameworkBotRepository frameworkBotRepository;

    public SetupController(JdbcTemplate jdbc, XuanjiRobotProperties robotProperties, ObjectProvider<QqBotRepository> qqBotRepository, FrameworkBotRepository frameworkBotRepository) {
        this.jdbc = jdbc;
        this.robotProperties = robotProperties;
        this.qqBotRepository = qqBotRepository;
        this.frameworkBotRepository = frameworkBotRepository;
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS xuanji_setup (id INT PRIMARY KEY, pin_salt VARCHAR(64), pin_hash VARCHAR(128), step INT DEFAULT 0, completed BOOLEAN DEFAULT FALSE)");
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            jdbc.update("MERGE INTO xuanji_setup (id) VALUES (1)");
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    @GetMapping(value={"/status"})
    public Map<String, Object> status() {
        try {
            Boolean completed = (Boolean)this.jdbc.queryForObject("SELECT completed FROM xuanji_setup WHERE id=1", Boolean.class);
            Integer step = (Integer)this.jdbc.queryForObject("SELECT step FROM xuanji_setup WHERE id=1", Integer.class);
            return Map.of("completed", completed != null && completed != false, "step", step != null ? step : 0);
        }
        catch (Exception e) {
            return Map.of("completed", false, "step", 0);
        }
    }

    @PostMapping(value={"/pin"})
    public Map<String, Object> setPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (pin == null || !pin.matches("\\d{6}")) {
            return Map.of("error", "\u8bbf\u95ee\u53e3\u4ee4\u5fc5\u987b\u662f 6 \u4f4d\u6570\u5b57");
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = SetupController.pbkdf2(pin, salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = Base64.getEncoder().encodeToString(hash);
        this.jdbc.update("MERGE INTO xuanji_setup (id, pin_salt, pin_hash, step) KEY(id) VALUES (1,?,?,1)", new Object[]{saltB64, hashB64});
        return Map.of("status", "ok");
    }

    @PostMapping(value={"/verify"})
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        try {
            String saltB64 = (String)this.jdbc.queryForObject("SELECT pin_salt FROM xuanji_setup WHERE id=1", String.class);
            String hashB64 = (String)this.jdbc.queryForObject("SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            if (saltB64 == null || hashB64 == null || pin == null) {
                return Map.of("ok", false);
            }
            byte[] salt = Base64.getDecoder().decode(saltB64);
            byte[] expected = Base64.getDecoder().decode(hashB64);
            byte[] actual = SetupController.pbkdf2(pin, salt);
            return Map.of("ok", MessageDigest.isEqual(expected, actual));
        }
        catch (Exception e) {
            return Map.of("ok", false);
        }
    }

    @PostMapping(value={"/bot"})
    public Map<String, Object> setupBot(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        boolean sandbox = "true".equals(body.get("sandbox"));
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");
        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (secret == null || secret.isBlank()) {
            return Map.of("error", "AppSecret \u4e0d\u80fd\u4e3a\u7a7a");
        }
        try {
            String webhookUrl = "webhook".equalsIgnoreCase(method) && !domain.isBlank() ? domain : null;
            QqBotRepository repo = (QqBotRepository)this.qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ \u9002\u914d\u5668\u672a\u542f\u7528 (xuanji.qqbot.enabled=false)");
            }
            repo.upsertBot(appId, secret, method, sandbox, "ONLINE", webhookUrl);
            try {
                this.frameworkBotRepository.upsert("qqbot", appId, "qqbot", "ONLINE");
            }
            catch (Exception e) {
                log.warn("[Setup] \u5199\u5165 xuanji_bot \u5931\u8d25: appId={}, {}", (Object)appId, (Object)e.getMessage());
            }
            this.robotProperties.reload();
            this.jdbc.update("UPDATE xuanji_setup SET step=2 WHERE id=1");
            return Map.of("status", "ok");
        }
        catch (Exception e) {
            return Map.of("error", "\u5199\u5165\u5931\u8d25: " + e.getMessage());
        }
    }

    @PostMapping(value={"/complete"})
    public Map<String, Object> complete() {
        this.jdbc.update("UPDATE xuanji_setup SET step=3, completed=TRUE WHERE id=1");
        return Map.of("status", "ok");
    }

    private static byte[] pbkdf2(String pin, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, 100000, 256)).getEncoded();
        }
        catch (Exception e) {
            throw new RuntimeException("PBKDF2 \u8ba1\u7b97\u5931\u8d25", e);
        }
    }
}

