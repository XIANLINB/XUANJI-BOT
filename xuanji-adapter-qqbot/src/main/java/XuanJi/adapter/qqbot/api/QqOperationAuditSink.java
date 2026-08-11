package XuanJi.adapter.qqbot.api;

import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.api.action.OperationAuditSink;
import XuanJi.api.action.PlatformActions;
import XuanJi.core.command.CommandRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * QQ 平台管理操作审计实现 — 将 {@link XuanJi.api.action.PlatformActionHub} 分发结果
 * 落库到 {@code qqbot_op_log}（禁言/撤回/审批等出站操作，成功与失败/被拒全记）。
 *
 * <p>操作人信息优先取命令上下文（群命令场景，{@link CommandRegistry#getCurrentUser()}），
 * 调用方也可在 params 中显式传 {@code operatorId / operatorName / operatorRole / source}。
 */
@Component
public class QqOperationAuditSink implements OperationAuditSink {

    private final QqBotRepository repository;

    public QqOperationAuditSink(QqBotRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(String botKey, String action, Map<String, Object> params, Map<String, Object> result) {
        if (action == null || botKey == null || botKey.isBlank()) return;
        String opType = opTypeOf(action);
        if (opType == null) return; // 非管理操作（查询类）不记录

        Map<String, Object> p = params == null ? Map.of() : params;
        boolean ok = result != null && Boolean.TRUE.equals(result.get("ok"));
        String error = result == null ? null : str(result.get("error"));

        String groupId = str(p.get("groupOpenid"));
        String userId = str(p.get("memberOpenid"));
        String targetMsgId = str(p.get("msgId"));
        String detail = null;
        if (result != null && result.get("data") != null) {
            try {
                detail = XuanJi.api.json.Json.mapper().writeValueAsString(result.get("data"));
            } catch (Exception ignored) { }
        }

        Long durationSec = null;
        String act = null;
        switch (opType) {
            case "mute" -> {
                long minutes = num(p.get("minutes"));
                long seconds = num(p.get("seconds"));
                long effective = seconds > 0 ? seconds : minutes * 60;
                durationSec = effective;
                act = effective > 0 ? "add" : "del";
            }
            case "join_approve" -> act = Boolean.TRUE.equals(p.get("approve")) ? "approve" : "reject";
            case "recall_private" -> { userId = str(p.get("openid")); act = "recall"; }
            case "recall" -> act = "recall";
            default -> { }
        }
        if (act == null) act = "run";

        // 操作人：显式参数优先，否则取命令上下文（群命令触发者）
        String operatorId = str(p.get("operatorId"));
        String operatorName = str(p.get("operatorName"));
        String operatorRole = str(p.get("operatorRole"));
        String source = str(p.get("source"));
        if (source == null || source.isBlank()) {
            source = CommandRegistry.getCurrentUser() == null ? "console" : "group_command";
        }
        if (operatorId == null || operatorId.isBlank()) {
            operatorId = CommandRegistry.getCurrentUser();
        }

        repository.insertOpLog(botKey, opType, act, groupId, userId, targetMsgId, durationSec,
                operatorId, operatorName, operatorRole, source,
                ok ? "success" : "failed", error, detail, System.currentTimeMillis());
    }

    /** 平台动作 → 操作日志类型；非管理操作返回 null（不记录）。 */
    private static String opTypeOf(String action) {
        return switch (action) {
            case PlatformActions.GROUP_MUTE -> "mute";
            case PlatformActions.GROUP_RECALL -> "recall";
            case PlatformActions.GROUP_RECALL_PRIVATE -> "recall_private";
            case PlatformActions.GROUP_APPROVE -> "join_approve";
            default -> null;
        };
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static long num(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
