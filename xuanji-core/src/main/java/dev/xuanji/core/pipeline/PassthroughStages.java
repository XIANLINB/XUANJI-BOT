package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.permission.PermissionService;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 白名单/黑名单阶段（order=20）— L4 黑名单一票否决 + L0 主人/超管放行。
 */
@Slf4j
@Component
class WhitelistStage implements PipelineStage {

    private final PermissionService permission;
    private final XuanjiRobotProperties props;

    WhitelistStage(PermissionService permission, XuanjiRobotProperties props) {
        this.permission = permission;
        this.props = props;
    }

    @Override public String name() { return "whitelist"; }
    @Override public int order() { return 20; }

    @Override
    public Result handle(BotEvent e, PipelineChain c) {
        if (e.sender() == null) return c.proceed();
        String botKey = resolveBotKey(e);
        String groupId = e.group() != null ? e.group().id() : null;
        String memberId = e.sender().platformUserId();
        if (!permission.check(botKey, groupId, memberId, null)) {
            log.debug("[whitelist] 拦截: bot={}, user={}", botKey, memberId);
            return Result.ABORT;
        }
        return c.proceed();
    }

    private String resolveBotKey(BotEvent e) {
        var bots = props.getRobots();
        if (bots == null || e.bot() == null) return "bot1";
        for (var kv : bots.entrySet()) {
            if (e.bot().selfId().equals(kv.getValue().getAppId())) return kv.getKey();
        }
        return "bot1";
    }
}

/**
 * 限流阶段（order=30）— 每用户简单冷却（全局 2 秒）。
 */
@Slf4j
@Component
class RateLimitStage implements PipelineStage {

    private final java.util.Map<String, Long> lastAccess = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public String name() { return "rate-limit"; }
    @Override public int order() { return 30; }

    @Override
    public Result handle(BotEvent e, PipelineChain c) {
        String key = e.sender() != null ? e.sender().platformUserId() : String.valueOf(e.hashCode());
        long now = System.currentTimeMillis();
        Long last = lastAccess.get(key);
        if (last != null && (now - last) < 2000) {
            log.debug("[rate-limit] 用户过频: {}", key);
            return Result.ABORT;
        }
        lastAccess.put(key, now);
        return c.proceed();
    }
}

/**
 * 事件幂等去重阶段（order=25）— 同一事件 ID 在 TTL 内只处理一次。
 *
 * <p>横切关注点，原应存在于 Pipeline 而非 Handler。QQ 平台偶发重推（网络抖动/WSS 重连），
 * 在此拦截可避免指令重复执行、消息重复回复。去重键优先用 {@link BotEvent#eventId()}，
 * 缺失时退化为「平台 + 原始类型 + 原生数据指纹」。
 *
 * <p>实现为 JVM 内 TTL 窗口（默认 5 分钟），惰性清理过期条目，单实例场景足够；
 * 多实例部署后续可下沉为 DB {@code xuanji_dedup} 表（见设计文档三级域隔离）。
 */
@Slf4j
@Component
class DedupStage implements PipelineStage {

    private final JdbcTemplate jdbc;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private static final long TTL_MS = 5 * 60 * 1000L;

    DedupStage(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String name() { return "dedup"; }
    @Override public int order() { return 25; }

    @Override
    public Result handle(BotEvent e, PipelineChain c) {
        String key = dedupKey(e);
        if (key == null) return c.proceed(); // 无法构造去重键则放行，避免误丢

        long now = System.currentTimeMillis();
        // 惰性清理过期条目，防止内存无限增长
        seen.entrySet().removeIf(en -> now - en.getValue() > TTL_MS);

        Long prev = seen.putIfAbsent(key, now);
        if (prev != null && (now - prev) < TTL_MS) {
            log.debug("[dedup] 丢弃重复事件(本地): {}", key);
            return Result.ABORT;
        }
        // 跨实例幂等：事件 ID 写入 xuanji_event_dedup（event_id 为主键）。
        // 主键冲突即代表另一实例已处理过该事件，丢弃；DB 不可用时降级为仅本地去重。
        String platform = e.bot() != null ? e.bot().platform() : "?";
        try {
            jdbc.update("INSERT INTO xuanji_event_dedup (event_id, platform) VALUES (?, ?)", key, platform);
        } catch (DuplicateKeyException dke) {
            seen.put(key, now);
            log.debug("[dedup] 丢弃重复事件(跨实例): {}", key);
            return Result.ABORT;
        } catch (Exception ex) {
            log.warn("[dedup] 写 xuanji_event_dedup 失败，降级本地去重: {}", ex.getMessage());
        }
        return c.proceed();
    }

    private String dedupKey(BotEvent e) {
        if (e.eventId() != null && !e.eventId().isEmpty()) return e.eventId();
        // 退化键：平台 + 原始类型 + 原生数据字符串指纹（确定性）
        try {
            String botId = e.bot() != null ? e.bot().selfId() : "?";
            String raw = e.rawEventType() != null ? e.rawEventType() : "?";
            String payload = e.platformData() != null ? e.platformData().toString() : "";
            return botId + ":" + raw + ":" + payload.hashCode();
        } catch (Exception ex) {
            return null;
        }
    }
}

/**
 * 透传阶段 — P3 后续迭代实现。
 */
@Component class WakingCheckStage    implements PipelineStage { public String name() { return "waking-check"; } public int order() { return 10; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ContentSafetyStage  implements PipelineStage { public String name() { return "content-safety"; } public int order() { return 40; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ResultDecorateStage implements PipelineStage { public String name() { return "result-decorate"; } public int order() { return 70; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class RespondStage        implements PipelineStage { public String name() { return "respond"; } public int order() { return 80; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
