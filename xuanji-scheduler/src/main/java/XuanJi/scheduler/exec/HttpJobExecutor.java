package XuanJi.scheduler.exec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 执行器：定时回调指定 URL（GET / POST），带连接与读超时。
 * targetId 存 URL；content 存 POST 请求体；targetType 存请求方法（GET/POST）。
 */
@Slf4j
@Component
public class HttpJobExecutor implements JobExecutor {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() {
        return "HTTP";
    }

    @Override
    public String execute(Map<String, Object> job) throws Exception {
        String url = str(job.get("targetId"));
        String method = str(job.get("targetType")).isEmpty() ? "GET" : str(job.get("targetType")).toUpperCase();
        String body = str(job.get("content"));
        if (url.isEmpty()) {
            throw new IllegalArgumentException("HTTP 任务缺少 URL");
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "xuanji-scheduler/1.0");
        if ("POST".equals(method)) {
            rb.header("Content-Type", "application/json");
            rb.POST(body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        } else {
            rb.GET();
        }
        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        String text = resp.body();
        log.info("[Scheduler] HTTP 任务: {} {} -> {}", method, url, resp.statusCode());
        return method + " " + url + " -> " + resp.statusCode() + " (" + truncate(text, 200) + ")";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
