package XuanJi.console.controller;

import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 控制台 · 媒体代理（Q12）。
 *
 * <p>QQ 富媒体（图片/语音/视频/文件）的原始 URL 通常带鉴权/有效期限制，浏览器无法直连。
 * 前端把媒体 src 指向 {@code /console/media?ref=<原始URL>}，由后端同源代理拉取并回流字节，
 * 使 {@code <img>/<video>/<audio>} 可随同源 Cookie 直接加载。
 *
 * <p>安全边界：仅代理 http/https；单文件上限 {@value #MAX_BYTES} 字节；连接/读取超时兜底；
 * 超限返回 413。后续可将「下载到本地文件存储（{@code data/xuanji/media}）后转存」接入本端点，
 * 实现对已落盘媒体的本地直读（当前先做直通代理，兼容存量消息里的远程 URL）。
 */
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleMediaController {

    private static final long MAX_BYTES = 25L * 1024 * 1024; // 25MB

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @GetMapping("/media")
    public void media(@RequestParam String ref, HttpServletResponse resp) {
        if (ref == null || ref.isBlank()) {
            resp.setStatus(400);
            return;
        }
        if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
            resp.setStatus(400);
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ref))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "XuanJi-Console-MediaProxy/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> hr = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int status = hr.statusCode();
            if (status >= 400) {
                resp.setStatus(status);
                return;
            }
            long len = hr.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (len > MAX_BYTES) {
                resp.setStatus(413);
                return;
            }
            String ct = hr.headers().firstValue("Content-Type").orElse("application/octet-stream");
            resp.setStatus(200);
            resp.setContentType(ct);
            try (InputStream in = hr.body(); OutputStream out = resp.getOutputStream()) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) {
                        throw new java.io.IOException("media exceeds " + MAX_BYTES + " bytes");
                    }
                    out.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            if (!resp.isCommitted()) {
                resp.setStatus(502);
            }
        }
    }
}
