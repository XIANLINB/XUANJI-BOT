package XuanJi.api.action;

import java.util.Map;

/**
 * 管理操作审计接收器 SPI — 平台动作分发（{@link PlatformActionHub#dispatch}）完成后回调。
 *
 * <p>供各平台适配器实现持久化「管理操作日志」（禁言/撤回/审批等出站审计，成功与失败全记），
 * 并在 {@code params} 中补充操作人信息（{@code operatorId / operatorName / operatorRole / source}）。
 */
public interface OperationAuditSink {

    /**
     * 记录一次管理操作。
     *
     * @param botKey 机器人 key（appId）
     * @param action 平台动作标识（见 {@link PlatformActions}）
     * @param params 入参（含调用方补充的操作人信息；可能为 null）
     * @param result 分发结果（含 ok / data / error）
     */
    void record(String botKey, String action, Map<String, Object> params, Map<String, Object> result);
}
