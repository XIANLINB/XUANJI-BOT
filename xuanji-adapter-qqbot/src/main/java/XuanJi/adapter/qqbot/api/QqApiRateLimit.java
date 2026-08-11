package XuanJi.adapter.qqbot.api;

import XuanJi.core.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QQ 开放平台接口限频器 —— 按「机器人(appId) + 接口类别」分桶的限速层。
 *
 * <p>QQ 官方接口均有限频（QPS 与 QPM 两类指标），本组件在 {@link QqApiService} 出口统一拦截：
 * <ul>
 *   <li><b>QPS 桶</b>（发送消息/撤回/机器人详情/分享链接/互动响应）：令牌桶，每秒补 N 个令牌、容量 N，允许 1 秒小突发</li>
 *   <li><b>QPM 桶</b>（群信息/群状态/入群申请/禁言/审批策略）：分钟窗口计数器</li>
 *   <li><b>429 降档兜底</b>：某 (appId, 类别) 命中平台 429 后，先冷却 {@link #COOLDOWN_MS}（该桶全拒），
 *       随后切换为「兜底档」运行 {@link #FALLBACK_DURATION_MS}（QPS 类 5/s、QPM 类 5/min），
 *       兜底期结束后尝试恢复正常档——宁可少调也不再次触发平台限频</li>
 * </ul>
 *
 * <p>限频桶按 {@code appId} 维度隔离（官方按单机器人统计限频），多机器人互不影响。
 * 全局开关：{@code tune.qq_rate_limit=false} 关闭（默认开启）。
 */
@Slf4j
@Component
public class QqApiRateLimit {

    /** 命中平台 429 后的冷却时长（该桶直接拒绝）。 */
    private static final long COOLDOWN_MS = 10 * 60 * 1000;
    /** 兜底档持续时长（冷却结束后进入兜底档运行），之后尝试恢复。 */
    private static final long FALLBACK_DURATION_MS = 30 * 60 * 1000;

    /** 限频指标类型。 */
    public enum Type { QPS, QPM }

    /** 接口限频类别：正常档 = 官方值；兜底档 = 触发 429 后的保守值。 */
    public enum Category {
        SEND_C2C_MSG(Type.QPS, 100, 5),        // 发送单聊消息
        SEND_GROUP_MSG(Type.QPS, 100, 5),      // 发送群聊消息
        RECALL_MSG(Type.QPS, 10, 5),           // 撤回消息（单聊/群聊）
        BOT_PROFILE(Type.QPS, 50, 5),          // 获取机器人详情 /users/@me
        SHARE_LINK(Type.QPS, 50, 5),           // 生成分享链接
        INTERACTION_REPLY(Type.QPS, 50, 5),    // 互动事件响应
        GROUP_INFO(Type.QPM, 30, 5),           // 获取群基本信息
        BOT_STATE(Type.QPM, 30, 5),            // 获取机器人群内状态
        JOIN_REQUEST_LIST(Type.QPM, 30, 5),    // 入群申请列表拉取
        JOIN_REQUEST_APPROVE(Type.QPM, 60, 5), // 入群申请审批
        MUTE_QUERY(Type.QPM, 30, 5),           // 查询群禁言状态
        MUTE_SET(Type.QPM, 60, 5),             // 设置群成员禁言
        APPROVAL_STRATEGY(Type.QPM, 60, 5);    // 入群自动审批策略（6 接口共用）

        final Type type;
        final int limit;
        final int fallback;

        Category(Type type, int limit, int fallback) {
            this.type = type;
            this.limit = limit;
            this.fallback = fallback;
        }
    }

    private final ConfigService configService;
    private volatile boolean enabled = true;

    /** key = appId + '\u0001' + categoryName → 桶。 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    /** 类别 → 被本地限频拒绝次数（观测）。 */
    private final Map<String, AtomicLong> rejectedCounts = new ConcurrentHashMap<>();

    public QqApiRateLimit(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    void init() {
        try {
            String v = configService.getGlobalConfig().get("tune.qq_rate_limit");
            if (v != null && "false".equalsIgnoreCase(v.trim())) {
                enabled = false;
                log.info("[QQ限频] 已通过 tune.qq_rate_limit=false 关闭");
            } else {
                log.info("[QQ限频] 已启用（按 appId+接口类别分桶，QPS/QPM 双档限速）");
            }
        } catch (Exception ignored) { /* 配置不可用用默认开启 */ }
    }

    /**
     * 尝试获取一次调用额度。
     *
     * @return true = 放行；false = 被限频（调用方应拒绝/跳过本次）
     */
    public boolean acquire(String appId, String path) {
        if (!enabled) return true;
        Category cat = categorize(path);
        if (cat == null) return true;
        String key = key(appId, cat);
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(cat));
        boolean ok = b.acquire(System.currentTimeMillis());
        if (!ok) {
            rejectedCounts.computeIfAbsent(cat.name(), k -> new AtomicLong()).incrementAndGet();
        }
        return ok;
    }

    /** 平台返回 429（限频）：通知对应桶降档兜底。 */
    public void on429(String appId, String path) {
        if (!enabled) return;
        Category cat = categorize(path);
        if (cat == null) return;
        Bucket b = buckets.get(key(appId, cat));
        if (b != null) {
            b.on429(System.currentTimeMillis());
            log.warn("[QQ限频] {} 命中平台 429，进入兜底模式（冷却 {}s → 兜底档 {} {})",
                    cat, COOLDOWN_MS / 1000, cat.fallback, cat.type);
        }
    }

    /** 限频命中统计（供控制台健康页展示）。 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("buckets", buckets.size());
        Map<String, Object> byCat = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            AtomicLong n = rejectedCounts.get(c.name());
            byCat.put(c.name(), n == null ? 0L : n.get());
        }
        m.put("rejectedByCategory", byCat);
        return m;
    }

    private static String key(String appId, Category cat) {
        return (appId == null ? "_" : appId) + '\u0001' + cat.name();
    }

    /** path → 限频类别；未识别返回 null（不限制）。 */
    static Category categorize(String path) {
        if (path == null) return null;
        if (path.startsWith("/users/@me")) return Category.BOT_PROFILE;
        if (path.startsWith("/v2/users/")) {
            if (path.contains("/messages/")) return Category.RECALL_MSG;
            if (path.contains("/messages")) return Category.SEND_C2C_MSG;
            return null;
        }
        if (path.startsWith("/v2/groups/")) {
            if (path.contains("/messages/")) return Category.RECALL_MSG;
            if (path.contains("/messages")) return Category.SEND_GROUP_MSG;
            if (path.contains("/approval_join_requests")) return Category.JOIN_REQUEST_APPROVE;
            if (path.contains("/join_requests")) return Category.JOIN_REQUEST_LIST;
            if (path.contains("/join_approval_strategy")) return Category.APPROVAL_STRATEGY;
            if (path.contains("/bot_state")) return Category.BOT_STATE;
            if (path.contains("/info")) return Category.GROUP_INFO;
            return null;
        }
        if (path.startsWith("/v2/generate_url_link")) return Category.SHARE_LINK;
        if (path.startsWith("/interactions/")) return Category.INTERACTION_REPLY;
        if (path.startsWith("/guilds/")) {
            if (path.contains("/members/") && path.contains("/mute")) return Category.MUTE_SET;
            if (path.contains("/mute")) return Category.MUTE_QUERY;
            return null;
        }
        return null;
    }

    /** 单桶限速状态。 */
    private static final class Bucket {
        final Category cat;
        // QPS 令牌桶
        double tokens;
        long lastRefillNanos;
        // QPM 分钟窗口
        long windowStartMs;
        int windowCount;
        // 降档状态
        long fallbackUntil;
        long cooldownUntil;

        Bucket(Category cat) {
            this.cat = cat;
        }

        synchronized boolean acquire(long nowMs) {
            if (cooldownUntil > nowMs) {
                return false; // 429 冷却中，直接拒绝
            }
            long limit = fallbackUntil > nowMs ? cat.fallback : cat.limit;
            if (cat.type == Type.QPS) {
                long nanos = System.nanoTime();
                if (lastRefillNanos == 0) lastRefillNanos = nanos;
                double refill = (nanos - lastRefillNanos) / 1_000_000_000.0 * limit;
                tokens = Math.min(limit, tokens + refill);
                lastRefillNanos = nanos;
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return true;
                }
                return false;
            } else {
                if (nowMs - windowStartMs >= 60_000) {
                    windowStartMs = nowMs;
                    windowCount = 0;
                }
                if (windowCount < limit) {
                    windowCount++;
                    return true;
                }
                return false;
            }
        }

        synchronized void on429(long nowMs) {
            fallbackUntil = nowMs + FALLBACK_DURATION_MS;
            cooldownUntil = nowMs + COOLDOWN_MS;
            tokens = 0;
            windowStartMs = nowMs;
            windowCount = 0;
        }
    }
}
