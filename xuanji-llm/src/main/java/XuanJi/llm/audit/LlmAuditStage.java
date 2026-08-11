package XuanJi.llm.audit;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.pipeline.PipelineStage;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 审核阶段 —— order=41（紧邻 core 的 content-safety 40 空转占位之后）。
 *
 * <p>配置 {@code aiAudit} 开启时，对群消息做 LLM 审核：违规 → {@link Result#ABORT}
 * 中断整条流水线（命令不派发、AI 不回复），并记录拦截日志；未开启 → 直接放行。
 */
@Slf4j
@Component
public class LlmAuditStage implements PipelineStage {

    private final LlmConfigStore configStore;
    private final LlmAuditService auditService;

    public LlmAuditStage(LlmConfigStore configStore, LlmAuditService auditService) {
        this.configStore = configStore;
        this.auditService = auditService;
    }

    @Override public String name() { return "llm-audit"; }
    @Override public int order() { return 41; }

    @Override
    public Result handle(XuanJiEvent event, PipelineChain chain) {
        if (!event.isGroupEvent() || event.message() == null) {
            return chain.proceed();
        }
        if (!configStore.get().isAiAudit()) {
            return chain.proceed();
        }
        String text = event.message().plainText();
        String botKey = event.bot() != null ? event.bot().selfId() : "";
        String groupId = event.group().groupId();
        String userId = event.sender() != null ? event.sender().id() : null;
        boolean pass = auditService.check(botKey, groupId, userId, text);
        if (!pass) {
            log.info("[FLOW] 🔍 AI审核 group={} result=BLOCK", groupId);
            log.info("[AUDIT] 拦截违规消息: group={}, user={}", groupId, userId);
            return Result.ABORT;
        }
        log.info("[FLOW] 🔍 AI审核 group={} result=PASS", groupId);
        return chain.proceed();
    }
}
