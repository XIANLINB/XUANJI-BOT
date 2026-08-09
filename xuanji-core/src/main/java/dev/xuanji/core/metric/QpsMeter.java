package dev.xuanji.core.metric;

/**
 * QPS 计量器 — 环形 60 秒窗口（入站 / 出站双通道）。
 *
 * <p>入站事件统一在 {@code BotPipeline.proceed} 调用 {@link #hit()} 计数；
 * 出站事件在平台消息发送器（如 {@code QqMessageSenderImpl}）发送成功后调用 {@link #hitOut()} 计数；
 * 运行监控页经 {@code /console/metrics/qps} 拉取近 N 秒曲线（含入站 + 出站）。
 *
 * <p>实现：60 个槽位环形复用，每槽记录「对应 epoch 秒」与「该秒计数」；
 * 命中时若槽位秒数与当前秒不符则清零再计数（跨秒自动重置），
 * 快照按「槽位秒数 == 目标秒」判断该秒是否有数据，避免旧数据残留。
 * 锁粒度为一整秒的并发命中，纳秒级开销，可忽略。
 */
public final class QpsMeter {

    private static final int SLOTS = 60;
    private static final long[] SLOT_SECONDS = new long[SLOTS];
    private static final long[] SLOT_COUNTS = new long[SLOTS];
    private static final long[] OUT_SLOT_SECONDS = new long[SLOTS];
    private static final long[] OUT_SLOT_COUNTS = new long[SLOTS];

    private QpsMeter() {}

    /** 记录一次入站事件（线程安全）。 */
    public static void hit() {
        hitSlot(SLOT_SECONDS, SLOT_COUNTS);
    }

    /** 记录一次出站事件（线程安全）。 */
    public static void hitOut() {
        hitSlot(OUT_SLOT_SECONDS, OUT_SLOT_COUNTS);
    }

    private static void hitSlot(long[] seconds, long[] counts) {
        long nowSec = System.currentTimeMillis() / 1000L;
        int slot = (int) Math.floorMod(nowSec, SLOTS);
        synchronized (QpsMeter.class) {
            if (seconds[slot] != nowSec) {
                seconds[slot] = nowSec;
                counts[slot] = 0;
            }
            counts[slot]++;
        }
    }

    /** 当前秒入站 QPS。 */
    public static long current() {
        return currentOf(SLOT_SECONDS, SLOT_COUNTS);
    }

    /** 当前秒出站 QPS。 */
    public static long currentOut() {
        return currentOf(OUT_SLOT_SECONDS, OUT_SLOT_COUNTS);
    }

    private static long currentOf(long[] seconds, long[] counts) {
        long nowSec = System.currentTimeMillis() / 1000L;
        int slot = (int) Math.floorMod(nowSec, SLOTS);
        synchronized (QpsMeter.class) {
            return seconds[slot] == nowSec ? counts[slot] : 0;
        }
    }

    /**
     * 最近 {@code seconds} 秒的逐秒入站 QPS（旧 → 新）。
     *
     * @param seconds 1..60，超出钳制
     */
    public static long[] snapshot(int seconds) {
        return snapshotOf(SLOT_SECONDS, SLOT_COUNTS, seconds);
    }

    /**
     * 最近 {@code seconds} 秒的逐秒出站 QPS（旧 → 新）。
     *
     * @param seconds 1..60，超出钳制
     */
    public static long[] snapshotOut(int seconds) {
        return snapshotOf(OUT_SLOT_SECONDS, OUT_SLOT_COUNTS, seconds);
    }

    private static long[] snapshotOf(long[] seconds, long[] counts, int secondsParam) {
        int n = Math.min(Math.max(secondsParam, 1), SLOTS);
        long nowSec = System.currentTimeMillis() / 1000L;
        long[] out = new long[n];
        synchronized (QpsMeter.class) {
            for (int i = 0; i < n; i++) {
                long sec = nowSec - (n - 1 - i);
                int slot = (int) Math.floorMod(sec, SLOTS);
                out[i] = seconds[slot] == sec ? counts[slot] : 0;
            }
        }
        return out;
    }

    /** 最近 {@code seconds} 秒入站峰值 QPS。 */
    public static long peak(int seconds) {
        return peakOf(snapshot(seconds));
    }

    /** 最近 {@code seconds} 秒出站峰值 QPS。 */
    public static long peakOut(int seconds) {
        return peakOf(snapshotOut(seconds));
    }

    private static long peakOf(long[] s) {
        long max = 0;
        for (long v : s) {
            if (v > max) max = v;
        }
        return max;
    }

    /** 最近 {@code seconds} 秒入站平均 QPS（保留 2 位小数）。 */
    public static double avg(int seconds) {
        return avgOf(snapshot(seconds), seconds);
    }

    /** 最近 {@code seconds} 秒出站平均 QPS（保留 2 位小数）。 */
    public static double avgOut(int seconds) {
        return avgOf(snapshotOut(seconds), seconds);
    }

    private static double avgOf(long[] s, int seconds) {
        long sum = 0;
        for (long v : s) sum += v;
        return Math.round(sum * 100.0 / seconds) / 100.0;
    }

    /** 最近 {@code seconds} 秒入站事件总数。 */
    public static long total(int seconds) {
        return totalOf(snapshot(seconds));
    }

    /** 最近 {@code seconds} 秒出站事件总数。 */
    public static long totalOut(int seconds) {
        return totalOf(snapshotOut(seconds));
    }

    private static long totalOf(long[] s) {
        long sum = 0;
        for (long v : s) sum += v;
        return sum;
    }
}
