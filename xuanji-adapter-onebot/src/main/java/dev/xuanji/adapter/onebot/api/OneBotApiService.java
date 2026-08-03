package dev.xuanji.adapter.onebot.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.json.Json;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OneBot v11 API 服务 — 在 WS 通道上发起 action 调用并等待 echo 回执。
 *
 * <p>OneBot 的 WS API 是异步请求/响应模型：
 * <pre>
 * 请求 → {"action":"send_group_msg","params":{...},"echo":"uuid-1"}
 * 响应 ← {"status":"ok","retcode":0,"data":{"message_id":123},"echo":"uuid-1"}
 * </pre>
 * 本类用 echo 关联请求与响应，把异步模型包成同步调用给上层使用。
 *
 * <p>响应由接入层（OneBotWsServer / OneBotWsClient）识别后回调 {@link #completeResponse}。
 */
@Slf4j
public class OneBotApiService {

    private final OneBotSessionRegistry registry;
    private final OneBotProperties props;

    /** echo → 等待中的请求 */
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    public OneBotApiService(OneBotSessionRegistry registry, OneBotProperties props) {
        this.registry = registry;
        this.props = props;
    }

    /**
     * 同步调用一个 OneBot action。
     *
     * @param selfId 目标 bot 的 QQ 号；为空时取任意在线会话
     * @param action action 名，如 {@code send_group_msg}
     * @param params 参数对象
     * @return 响应中的 data 节点；失败抛 {@link OneBotApiException}
     */
    public JsonNode call(String selfId, String action, ObjectNode params) {
        OneBotSession session = registry.find(selfId)
                .orElseThrow(() -> new OneBotApiException(
                        "无可用 OneBot 连接" + (selfId == null || selfId.isBlank() ? "" : "（selfId=" + selfId + "）")));

        String echo = UUID.randomUUID().toString();
        ObjectNode req = Json.obj();
        req.put("action", action);
        req.set("params", params == null ? Json.obj() : params);
        req.put("echo", echo);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(echo, future);

        long start = System.currentTimeMillis();
        try {
            session.sendText(req.toString());
            JsonNode resp = future.get(props.getApiTimeoutMs(), TimeUnit.MILLISECONDS);

            String status = resp.path("status").asText("");
            int retcode = resp.path("retcode").asInt(-1);
            if ("failed".equals(status) || (retcode != 0 && retcode != 1)) {
                // retcode: 0=成功, 1=异步执行中（也视为受理成功）
                throw new OneBotApiException("OneBot API 调用失败: action=" + action
                        + ", retcode=" + retcode + ", msg=" + resp.path("message").asText(resp.path("wording").asText("")));
            }
            log.debug("[OneBot-API] {} 成功, {}ms", action, System.currentTimeMillis() - start);
            return resp.path("data");

        } catch (TimeoutException e) {
            throw new OneBotApiException("OneBot API 超时(" + props.getApiTimeoutMs() + "ms): action=" + action);
        } catch (OneBotApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OneBotApiException("OneBot API 调用被中断: action=" + action);
        } catch (Exception e) {
            throw new OneBotApiException("OneBot API 异常: action=" + action + ", " + e.getMessage(), e);
        } finally {
            pending.remove(echo);
        }
    }

    /** 发后不理（不关心回执的调用，如日志上报类 action） */
    public void callAsync(String selfId, String action, ObjectNode params) {
        registry.find(selfId).ifPresentOrElse(session -> {
            ObjectNode req = Json.obj();
            req.put("action", action);
            req.set("params", params == null ? Json.obj() : params);
            session.sendText(req.toString());
        }, () -> log.warn("[OneBot-API] 无可用连接，丢弃 action={}", action));
    }

    /**
     * 接入层收到带 echo 的响应报文时回调本方法。
     *
     * @return true 表示这条报文确实是某个在途请求的响应
     */
    public boolean completeResponse(JsonNode resp) {
        String echo = resp.path("echo").asText("");
        if (echo.isEmpty()) {
            return false;
        }
        CompletableFuture<JsonNode> future = pending.remove(echo);
        if (future == null) {
            log.debug("[OneBot-API] 收到无主响应 echo={}（可能已超时）", echo);
            return true;
        }
        future.complete(resp);
        return true;
    }

    /** 连接断开时让所有在途请求快速失败，避免线程挂满超时 */
    public void failAllPending(String reason) {
        pending.forEach((echo, future) ->
                future.completeExceptionally(new OneBotApiException("连接断开: " + reason)));
        pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }

    /** OneBot API 调用异常 */
    public static class OneBotApiException extends RuntimeException {
        public OneBotApiException(String message) {
            super(message);
        }

        public OneBotApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
