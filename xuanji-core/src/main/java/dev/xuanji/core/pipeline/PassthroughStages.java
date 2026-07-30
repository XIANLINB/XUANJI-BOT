package dev.xuanji.core.pipeline;

import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.pipeline.PipelineStage;
import org.springframework.stereotype.Component;

/**
 * 流水线内置阶段集合 — P3 阶段均为透传，后续迭代实现具体逻辑。
 */
@Component class WakingCheckStage    implements PipelineStage { public String name() { return "waking-check"; } public int order() { return 10; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class WhitelistStage      implements PipelineStage { public String name() { return "whitelist"; } public int order() { return 20; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class RateLimitStage      implements PipelineStage { public String name() { return "rate-limit"; } public int order() { return 30; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ContentSafetyStage  implements PipelineStage { public String name() { return "content-safety"; } public int order() { return 40; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class ResultDecorateStage implements PipelineStage { public String name() { return "result-decorate"; } public int order() { return 70; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
@Component class RespondStage        implements PipelineStage { public String name() { return "respond"; } public int order() { return 80; } public Result handle(BotEvent e, PipelineChain c) { return c.proceed(); } }
