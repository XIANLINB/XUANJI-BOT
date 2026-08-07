package dev.xuanji.core.metric;

/**
 * QPS 计量器 — 环形 60 秒窗口。
 *
 * <p>入站事件统一在 {@code BotPipeline.proceed} 调用 {@link #hit()} 计数；
 * 运行监控页经 {@code /console/metrics/qps} 拉取近 N 秒曲线。
 *
 * <p>实现：60 个槽位环形复用，每槽记录「对应 epoch 秒」与「该秒计数」；
 * 命中时若槽位秒数与当前秒不符则清零再计数（跨秒自动重置），
 * 快照按「槽位秒数 == 目标秒」判断该秒是否有数据，避免旧数据残留。
 * 锁粒度为一整秒的并发入站，纳秒级开销，可忽略。
 */
public final class QpsMeter {

    private static final int SLOTS = 60;
    private static final long[] SLOT_SECONDS = new long[SLOTS];
    private static final long[] SLOT_COUNTS = new long[SLOTS];

    private QpsMeter() {}

    /** 记录一次入站事件（线程安全）。 */
    public static void hit() {
        long nowSec = System.currentTimeMillis() / 1000L;
        int slot = (int) Math.floorMod(nowSec, SLOTS);
        synchronized (QpsMeter.class) {
            if (SLOT_SECONDS[slot] != nowSec) {
                SLOT_SECONDS[slot] = nowSec;
                SLOT_COUNTS[slot] = 0;
            }
            SLOT_COUNTS[slot]++;
        }
    }

    /** 当前秒 QPS。 */
    public static long current() {
        long nowSec = System.currentTimeMillis() / 1000L;
        int slot = (int) Math.floorMod(nowSec, SLOTS);
        synchronized (QpsMeter.class) {
            return SLOT_SECONDS[slot] == nowSec ? SLOT_COUNTS[slot] : 0;
        }
    }

    /**
     * 最近 {@code seconds} 秒的逐秒 QPS（旧 → 新）。
     *
     * @param seconds 1..60，超出钳制
     */
    public static long[] snapshot(int seconds) {
        int n = Math.min(Math.max(seconds, 1), SLOTS);
        long nowSec = System.currentTimeMillis() / 1000L;
        long[] out = new long[n];
        synchronized (QpsMeter.class) {
            for (int i = 0; i < n; i++) {
                long sec = nowSec - (n - 1 - i);
                int slot = (int) Math.floorMod(sec, SLOTS);
                out[i] = SLOT_SECONDS[slot] == sec ? SLOT_COUNTS[slot] : 0;
            }
        }
        return out;
    }

    /** 最近 {@code seconds} 秒峰值 QPS。 */
    public static long peak(int seconds) {
        long[] s = snapshot(seconds);
        long max = 0;
        for (long v : s) {
            if (v > max) max = v;
        }
        return max;
    }

    /** 最近 {@code seconds} 秒平均 QPS（保留 2 位小数）。 */
    public static double avg(int seconds) {
        long[] s = snapshot(seconds);
        long sum = 0;
        for (long v : s) sum += v;
        return Math.round(sum * 100.0 / seconds) / 100.0;
    }
}
