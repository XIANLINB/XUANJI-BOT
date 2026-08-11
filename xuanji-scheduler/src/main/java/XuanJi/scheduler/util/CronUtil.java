package XuanJi.scheduler.util;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * cron 工具 —— 归一化 / 校验 / 计算下一次触发时间。
 *
 * <p>供 {@link XuanJi.scheduler.core.TaskSchedulerService}（调度推进）
 * 与 {@link XuanJi.scheduler.store.SchedulerJobStore}（建任务即算首次 next_run）
 * 共用，避免 store 反向依赖 core 造成循环依赖。
 */
public final class CronUtil {

    private CronUtil() {
    }

    /**
     * 归一化 cron：Spring CronExpression 要求 6 位（秒 分 时 日 月 周），
     * 兼容用户常用的 5 位写法（自动补秒字段 0，如 {@code 0 8 * * *} → {@code 0 0 8 * * *}）。
     */
    public static String normalizeCron(String cron) {
        if (cron == null) return "";
        String c = cron.trim();
        String[] parts = c.split("\\s+");
        return parts.length == 5 ? "0 " + c : c;
    }

    /** 计算 cron 下一次触发时间（epoch 秒）；非法 cron 返回 -1。 */
    public static long previewNext(String cron, long fromEpochSeconds) {
        try {
            CronExpression expr = CronExpression.parse(normalizeCron(cron));
            LocalDateTime base = Instant.ofEpochSecond(Math.max(fromEpochSeconds, 1))
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime next = expr.next(base);
            return next == null ? -1 : next.atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 校验 cron 是否合法。 */
    public static boolean isValidCron(String cron) {
        return cron != null && previewNext(cron, System.currentTimeMillis() / 1000) >= 0;
    }
}
