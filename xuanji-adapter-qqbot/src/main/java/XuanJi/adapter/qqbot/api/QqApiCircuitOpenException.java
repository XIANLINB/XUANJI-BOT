package XuanJi.adapter.qqbot.api;

import XuanJi.api.exception.BusinessException;

/**
 * QQ 接口熔断器打开期间抛出的快速失败异常。
 *
 * <p>继承 {@link BusinessException}（HTTP 语义 503），既能被既有调用方（如 {@code QqMessageSender}
 * 的失败回执逻辑）当作普通业务异常接住，也可通过 {@code instanceof} 区分于平台侧错误，便于上层做降级提示。
 */
public class QqApiCircuitOpenException extends BusinessException {

    public QqApiCircuitOpenException(long remainMs) {
        super(503, "QQ 接口熔断中（冷却剩余约 " + Math.max(0, remainMs / 1000) + "s），暂不下发请求，请稍后重试");
    }
}
