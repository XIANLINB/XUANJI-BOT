package dev.xuanji.starter;

import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.security.PinCrypto;
import dev.xuanji.core.storage.FrameworkBotRepository;
import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首次安装向导 — 设置访问口令、录入第一个机器人、标记安装完成。
 *
 * <p>口令使用 PBKDF2WithHmacSHA256 存储（16 字节随机盐 + 10 万次迭代 + 256 位派生密钥），
 * 库中只留 Base64 的盐与哈希，明文 PIN 不落盘；校验走 {@link MessageDigest#isEqual} 常量时间比较，
 * 避免计时侧信道。
 *
 * <p>表 {@code xuanji_setup} 在构造期用 {@code CREATE TABLE IF NOT EXISTS} 兜底建好，
 * 因此不依赖任何外部迁移脚本；建表/初始化行失败时静默吞掉，交由后续接口的 try-catch 降级处理。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/setup")
public class SetupController {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<QqBotRepository> qqBotRepository;
    private final FrameworkBotRepository frameworkBotRepository;

    public SetupController(JdbcTemplate jdbc,
                           XuanjiRobotProperties robotProperties,
                           ObjectProvider<QqBotRepository> qqBotRepository,
                           FrameworkBotRepository frameworkBotRepository) {
        this.jdbc = jdbc;
        this.robotProperties = robotProperties;
        this.qqBotRepository = qqBotRepository;
        this.frameworkBotRepository = frameworkBotRepository;

        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS xuanji_setup ("
                    + "id INT PRIMARY KEY, pin_salt VARCHAR(64), pin_hash VARCHAR(128), "
                    + "step INT DEFAULT 0, completed BOOLEAN DEFAULT FALSE)");
        } catch (Exception ignored) {
            // 建表失败不阻断启动，后续接口各自 try-catch 降级
        }
        // 防御性补齐：若历史库 / 其他初始化器先用 2 列 schema 建了表，这里补齐缺失列，
        // 保证 setPin / verify / status 所需的列一定存在，彻底消除对 Bean 初始化顺序的依赖。
        try {
            jdbc.execute("ALTER TABLE xuanji_setup ADD COLUMN IF NOT EXISTS pin_salt VARCHAR(64)");
            jdbc.execute("ALTER TABLE xuanji_setup ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(128)");
            jdbc.execute("ALTER TABLE xuanji_setup ADD COLUMN IF NOT EXISTS completed BOOLEAN DEFAULT FALSE");
        } catch (Exception ignored) {
            // 列已存在 / 不支持时忽略
        }
        try {
            jdbc.update("MERGE INTO xuanji_setup (id) VALUES (1)");
        } catch (Exception ignored) {
            // 初始化单行失败同上
        }
    }

    /** 查询安装进度：completed=是否走完向导，step=当前处于第几步。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        try {
            Boolean completed = jdbc.queryForObject(
                    "SELECT completed FROM xuanji_setup WHERE id=1", Boolean.class);
            Integer step = jdbc.queryForObject(
                    "SELECT step FROM xuanji_setup WHERE id=1", Integer.class);
            return Map.of(
                    "completed", completed != null && completed,
                    "step", step != null ? step : 0);
        } catch (Exception e) {
            return Map.of("completed", false, "step", 0);
        }
    }

    /** 设置 6 位数字访问口令，落库只存盐与 PBKDF2 哈希。 */
    @PostMapping("/pin")
    public Map<String, Object> setPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (pin == null || !pin.matches("\\d{6}")) {
            return Map.of("error", "访问口令必须是 6 位数字");
        }
        // 防重复设置：口令已存在（或安装已完成）时拒绝重置，避免安装后被人改口令接管。
        try {
            String existing = jdbc.queryForObject(
                    "SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            if (existing != null && !existing.isBlank()) {
                return Map.of("error", "访问口令已设置，无法重复设置");
            }
        } catch (Exception ignored) {
            // 查询失败视为未设置，继续
        }

        String saltB64 = PinCrypto.generateSalt();
        String hashB64 = PinCrypto.hashPin(pin, saltB64);
        jdbc.update("MERGE INTO xuanji_setup (id, pin_salt, pin_hash, step) KEY(id) VALUES (1,?,?,1)",
                saltB64, hashB64);
        return Map.of("status", "ok");
    }

    /** 校验访问口令是否正确，任何异常一律返回 ok=false，不泄露内部状态。 */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        try {
            String saltB64 = jdbc.queryForObject(
                    "SELECT pin_salt FROM xuanji_setup WHERE id=1", String.class);
            String hashB64 = jdbc.queryForObject(
                    "SELECT pin_hash FROM xuanji_setup WHERE id=1", String.class);
            return Map.of("ok", PinCrypto.verify(pin, saltB64, hashB64));
        } catch (Exception e) {
            return Map.of("ok", false);
        }
    }

    /** 向导第二步：录入第一个机器人，写平台库 + 框架库，并热更新配置。 */
    @PostMapping("/bot")
    public Map<String, Object> setupBot(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String secret = body.get("clientSecret");
        boolean sandbox = "true".equals(body.get("sandbox"));
        String method = body.getOrDefault("connectionMethod", "websocket");
        String domain = body.getOrDefault("domain", "");

        if (appId == null || appId.isBlank()) {
            return Map.of("error", "AppID 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            return Map.of("error", "AppSecret 不能为空");
        }

        try {
            String webhookUrl = "webhook".equalsIgnoreCase(method) && !domain.isBlank() ? domain : null;
            QqBotRepository repo = qqBotRepository.getIfAvailable();
            if (repo == null) {
                return Map.of("error", "QQ 适配器未启用 (xuanji.qqbot.enabled=false)");
            }
            repo.upsertBot(appId, secret, method, sandbox, "ONLINE", webhookUrl);

            try {
                frameworkBotRepository.upsert("qqbot", appId, "qqbot", "ONLINE");
            } catch (Exception e) {
                log.warn("[Setup] 写入 xuanji_bot 失败: appId={}, {}", appId, e.getMessage());
            }

            robotProperties.reload();
            jdbc.update("UPDATE xuanji_setup SET step=2 WHERE id=1");
            return Map.of("status", "ok");
        } catch (Exception e) {
            return Map.of("error", "写入失败: " + e.getMessage());
        }
    }

    /** 向导收尾：标记安装完成。 */
    @PostMapping("/complete")
    public Map<String, Object> complete() {
        jdbc.update("UPDATE xuanji_setup SET step=3, completed=TRUE WHERE id=1");
        return Map.of("status", "ok");
    }
}
