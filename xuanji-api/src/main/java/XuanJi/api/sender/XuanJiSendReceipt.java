package XuanJi.api.sender;

/**
 * 消息发送回执 — 携带平台消息 ID、耗时、失败原因。
 */
public record XuanJiSendReceipt(
        String platformMsgId,
        long elapsedMs,
        boolean success,
        String errorMessage
) {
    public static XuanJiSendReceipt ok(String platformMsgId, long elapsedMs) {
        return new XuanJiSendReceipt(platformMsgId, elapsedMs, true, null);
    }

    public static XuanJiSendReceipt fail(String errorMessage, long elapsedMs) {
        return new XuanJiSendReceipt(null, elapsedMs, false, errorMessage);
    }
}
