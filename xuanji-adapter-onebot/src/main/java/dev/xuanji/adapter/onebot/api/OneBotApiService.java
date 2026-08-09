package dev.xuanji.adapter.onebot.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.json.Json;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotApiService {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotApiService.class);
    private final OneBotSessionRegistry registry;
    private final OneBotProperties props;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<String, CompletableFuture<JsonNode>>();

    public OneBotApiService(OneBotSessionRegistry registry, OneBotProperties props) {
        this.registry = registry;
        this.props = props;
    }

    public JsonNode call(String selfId, String action, ObjectNode params) {
        OneBotSession session = this.registry.find(selfId).orElseThrow(() -> new OneBotApiException("\u65e0\u53ef\u7528 OneBot \u8fde\u63a5" + (String)(selfId == null || selfId.isBlank() ? "" : "\uff08selfId=" + selfId + "\uff09")));
        String echo = UUID.randomUUID().toString();
        ObjectNode req = Json.obj();
        req.put("action", action);
        req.set("params", (JsonNode)(params == null ? Json.obj() : params));
        req.put("echo", echo);
        CompletableFuture future = new CompletableFuture();
        this.pending.put(echo, future);
        long start = System.currentTimeMillis();
        try {
            session.sendText(req.toString());
            JsonNode resp = (JsonNode)future.get(this.props.getApiTimeoutMs(), TimeUnit.MILLISECONDS);
            String status = resp.path("status").asText("");
            int retcode = resp.path("retcode").asInt(-1);
            if ("failed".equals(status) || retcode != 0 && retcode != 1) {
                throw new OneBotApiException("OneBot API \u8c03\u7528\u5931\u8d25: action=" + action + ", retcode=" + retcode + ", msg=" + resp.path("message").asText(resp.path("wording").asText("")));
            }
            log.debug("[OneBot-API] {} \u6210\u529f, {}ms", (Object)action, (Object)(System.currentTimeMillis() - start));
            JsonNode jsonNode = resp.path("data");
            return jsonNode;
        }
        catch (TimeoutException e) {
            throw new OneBotApiException("OneBot API \u8d85\u65f6(" + this.props.getApiTimeoutMs() + "ms): action=" + action);
        }
        catch (OneBotApiException e) {
            throw e;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OneBotApiException("OneBot API \u8c03\u7528\u88ab\u4e2d\u65ad: action=" + action);
        }
        catch (Exception e) {
            throw new OneBotApiException("OneBot API \u5f02\u5e38: action=" + action + ", " + e.getMessage(), e);
        }
        finally {
            this.pending.remove(echo);
        }
    }

    public void callAsync(String selfId, String action, ObjectNode params) {
        this.registry.find(selfId).ifPresentOrElse(session -> {
            ObjectNode req = Json.obj();
            req.put("action", action);
            req.set("params", (JsonNode)(params == null ? Json.obj() : params));
            session.sendText(req.toString());
        }, () -> log.warn("[OneBot-API] \u65e0\u53ef\u7528\u8fde\u63a5\uff0c\u4e22\u5f03 action={}", (Object)action));
    }

    public boolean completeResponse(JsonNode resp) {
        String echo = resp.path("echo").asText("");
        if (echo.isEmpty()) {
            return false;
        }
        CompletableFuture<JsonNode> future = this.pending.remove(echo);
        if (future == null) {
            log.debug("[OneBot-API] \u6536\u5230\u65e0\u4e3b\u54cd\u5e94 echo={}\uff08\u53ef\u80fd\u5df2\u8d85\u65f6\uff09", (Object)echo);
            return true;
        }
        future.complete(resp);
        return true;
    }

    public void failAllPending(String reason) {
        this.pending.forEach((echo, future) -> future.completeExceptionally(new OneBotApiException("\u8fde\u63a5\u65ad\u5f00: " + reason)));
        this.pending.clear();
    }

    public int pendingCount() {
        return this.pending.size();
    }

    public static class OneBotApiException
    extends RuntimeException {
        public OneBotApiException(String message) {
            super(message);
        }

        public OneBotApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

