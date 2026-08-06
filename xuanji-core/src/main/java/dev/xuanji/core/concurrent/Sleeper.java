package dev.xuanji.core.concurrent;

/**
 * 可注入的睡眠器 — 解耦 {@link Thread#sleep}，使节奏逻辑可在测试中以毫秒级确定性推进时钟。
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}
