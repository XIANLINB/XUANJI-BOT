package dev.xuanji.console.service;

import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.core.storage.PlatformDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台聚合查询公共逻辑。
 *
 * <p>被拆出来的各 Console*Controller 共享：
 * <ul>
 *   <li>机器人实例枚举（{@code xuanji_bot} 框架表）与按平台解析 {@link PlatformDataProvider}</li>
 *   <li>botKey / appId 互转、平台解析等工具</li>
 * </ul>
 *
 * <p>本类只读，不含任何写操作，避免把查询与变更逻辑混在 Controller 里。
 */
@Slf4j
@Component
public class ConsoleQueryService {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties robotProperties;
    private final ObjectProvider<PlatformDataProvider> dataProviders;

    public ConsoleQueryService(JdbcTemplate jdbc,
                               XuanjiRobotProperties robotProperties,
                               ObjectProvider<PlatformDataProvider> dataProviders) {
        this.jdbc = jdbc;
        this.robotProperties = robotProperties;
        this.dataProviders = dataProviders;
    }

    /** 框架库中登记的一个机器人实例。 */
    public record BotRef(String platform, String instanceId, String status) {}

    /** 枚举框架库 xuanji_bot 中登记的全部机器人实例（按 instance_id 去重）。 */
    public List<BotRef> botRefs() {
        // 同 instance_id 可能由于历史 platform 命名迁移（qq → qqbot）残留多行，
        // 按 instance_id 去重，优先保留能解析到 PlatformDataProvider 的行（避免重复 bot / 读到空 platform）。
        Map<String, BotRef> byInstance = new LinkedHashMap<>();
        try {
            for (var row : jdbc.queryForList("SELECT platform, instance_id, status FROM xuanji_bot")) {
                String instanceId = str(row.get("INSTANCE_ID"));
                if (instanceId == null || instanceId.isBlank()) continue;
                BotRef ref = new BotRef(
                        String.valueOf(row.getOrDefault("PLATFORM", "qqbot")),
                        instanceId,
                        String.valueOf(row.getOrDefault("STATUS", "unknown")));
                byInstance.merge(instanceId, ref, (kept, candidate) ->
                        providerFor(candidate.platform()) != null ? candidate : kept);
            }
        } catch (Exception ignored) {
            // 框架库不可用时退化为无实例
        }
        return new ArrayList<>(byInstance.values());
    }

    /** 按平台标识取数据提供方（适配器可插拔，无则 null）。 */
    public PlatformDataProvider providerFor(String platform) {
        if (platform == null) return null;
        return dataProviders.stream()
                .filter(p -> platform.equals(p.platform()))
                .findFirst().orElse(null);
    }

    /** 按 appId 反查机器人 key（v3.1 起 key 即 appId，无则退化为 appId）。 */
    public String resolveBotKey(String appId) {
        var robots = robotProperties.getRobots();
        if (robots != null) {
            for (var e : robots.entrySet()) {
                var cfg = e.getValue();
                if (cfg != null && appId.equals(cfg.getAppId())) return e.getKey();
            }
        }
        return appId;
    }

    /** 由 botKey 解析 appId（来自数据库装载的机器人清单），失败则退化为直接用 botKey 当 instance_id。 */
    public String resolveAppId(String botKey) {
        var robots = robotProperties.getRobots();
        if (robots != null) {
            var cfg = robots.get(botKey);
            if (cfg != null) return cfg.getAppId();
        }
        return botKey;
    }

    /** 按 appId 反查平台标识（xuanji_bot.platform），失败默认 qqbot。 */
    public String resolvePlatform(String appId) {
        try {
            var r = jdbc.queryForMap("SELECT platform FROM xuanji_bot WHERE instance_id=?", appId);
            return String.valueOf(r.getOrDefault("PLATFORM", "qqbot"));
        } catch (Exception e) {
            return "qqbot";
        }
    }

    /** 按 appId 读取 xuanji_bot 的 platform + status，无记录返回 null。 */
    public Map<String, String> botPlatformStatus(String appId) {
        try {
            var r = jdbc.queryForMap("SELECT platform, status FROM xuanji_bot WHERE instance_id=?", appId);
            return Map.of(
                    "platform", String.valueOf(r.getOrDefault("PLATFORM", "qqbot")),
                    "status", String.valueOf(r.getOrDefault("STATUS", "unknown")));
        } catch (Exception e) {
            return null;
        }
    }

    /** 今日 00:00:00 的 epoch 秒。 */
    public static long todayStartEpochSeconds() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
    }

    public static long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 大小写兼容取值：依次尝试多个 key，返回首个非空字符串，否则空串。 */
    public static String strOrEmpty(Object... vals) {
        for (Object v : vals) {
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    /** 框架库整数查询，失败返回 0（容错）。 */
    public int qInt(String sql, Object... args) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, args);
            return v != null ? v : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
