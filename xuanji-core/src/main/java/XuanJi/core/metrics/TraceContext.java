package XuanJi.core.metrics;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 消息全链路 MDC 上下文。
 *
 * <p>每条消息事件生成唯一 traceId，注入 eventId/botId/pluginId，
 * 后续所有日志自动携带，控制台可按 traceId 拉出完整链路。
 */
public class TraceContext {

    private static final ThreadLocal<String> traceIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> eventIdTL = new ThreadLocal<>();

    /** 进入新事件时调用：生成 traceId，写入 MDC */
    public static void enter(String eventId, String botId) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        traceIdTL.set(traceId);
        eventIdTL.set(eventId);
        MDC.put("traceId", traceId);
        MDC.put("eventId", eventId != null ? (eventId.length() > 20 ? eventId.substring(0, 20) : eventId) : "");
        MDC.put("botId", botId != null ? botId : "");
    }

    /** 设置当前插件 ID */
    public static void plugin(String pluginId) {
        MDC.put("pluginId", pluginId != null ? pluginId : "");
    }

    /** 退出事件时调用：清除 MDC */
    public static void exit() {
        traceIdTL.remove();
        eventIdTL.remove();
        MDC.remove("traceId");
        MDC.remove("eventId");
        MDC.remove("botId");
        MDC.remove("pluginId");
    }

    public static String currentTraceId() { return traceIdTL.get(); }
}
