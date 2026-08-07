package dev.xuanji.core.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 线程池 / 连接池注册表 — 运行监控页展示各池实时状态。
 *
 * <p>各组件创建池后调用 {@link #register(String, Supplier)} 注册，
 * 监控接口经 {@link #snapshot()} 拉取全部池的实时快照。
 * supplier 延迟执行（读取瞬间状态），异常被吞掉返回空条目，不影响监控主流程。
 */
public final class ThreadPoolRegistry {

    /** 池快照：core/max 为线程或连接数（-1=不适用/未知），active/poolSize/queueSize/completed 同理。 */
    public record PoolInfo(String name, String type, int core, int max,
                           int active, int poolSize, int queueSize, long completed, String note) {}

    private static final Map<String, Supplier<PoolInfo>> REG = new ConcurrentHashMap<>();

    private ThreadPoolRegistry() {}

    /** 注册池（同名覆盖）。supplier 返回 null 表示暂不可用，快照跳过。 */
    public static void register(String name, Supplier<PoolInfo> supplier) {
        REG.put(name, supplier);
    }

    /** 全部池快照（Map 列表，字段名固定，供 JSON 序列化）。 */
    public static List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        REG.forEach((name, sup) -> {
            try {
                PoolInfo p = sup.get();
                if (p == null) return;
                out.add(Map.of(
                        "name", p.name(),
                        "type", p.type(),
                        "core", p.core(),
                        "max", p.max(),
                        "active", p.active(),
                        "poolSize", p.poolSize(),
                        "queueSize", p.queueSize(),
                        "completed", p.completed(),
                        "note", p.note() == null ? "" : p.note()));
            } catch (Exception ignored) {
                // 池尚未初始化/已关闭：跳过该条
            }
        });
        return out;
    }
}
