package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import dev.xuanji.core.permission.PermissionService;
import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * 透传阶段 — P3 后续迭代实现。
 */
@Component class WakingCheckStage    implements PipelineStage { public String name() { return "waking-check"; } public int order() { return 10; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ContentSafetyStage  implements PipelineStage { public String name() { return "content-safety"; } public int order() { return 40; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ResultDecorateStage implements PipelineStage { public String name() { return "result-decorate"; } public int order() { return 70; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class RespondStage        implements PipelineStage { public String name() { return "respond"; } public int order() { return 80; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
