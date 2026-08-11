package XuanJi.core.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具 — 业务时间统一 BIGINT epoch 秒（Batch 3-③ 铁律）。
 *
 * <p>禁止散写 {@code System.currentTimeMillis()/1000}；新增时间写入必须走本类。
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /** 当前时间 epoch 秒。 */
    public static long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * ISO 时间字符串 → epoch 秒。
     *
     * <p>兼容多种形态：纯数字原样返回、ISO-8601 带时区（{@code 2026-08-05T12:00:00+08:00}）、
     * 无时区时间（按系统默认时区解释）。解析失败返回 0。
     */
    public static long toEpochSeconds(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        String s = iso.trim();
        // 已是数字（epoch 秒/毫秒）
        if (s.matches("\\d+")) {
            long v = Long.parseLong(s);
            return v > 10_000_000_000L ? v / 1000 : v;   // 毫秒自动换算为秒
        }
        try {
            return Instant.parse(s).getEpochSecond();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(s).toEpochSecond();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
