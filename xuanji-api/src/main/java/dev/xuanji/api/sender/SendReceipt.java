package dev.xuanji.api.sender;

/**
 * 消息发送回执 — 携带平台消息 ID、耗时、失败原因。
 */
public record SendReceipt(
        String platformMsgId,
        long elapsedMs,
        boolean success,
        String errorMessage
) {
    public static SendReceipt ok(String platformMsgId, long elapsedMs) {
        return new SendReceipt(platformMsgId, elapsedMs, true, null);
    }

    public static SendReceipt fail(String errorMessage, long elapsedMs) {
        return new SendReceipt(null, elapsedMs, false, errorMessage);
    }
}
