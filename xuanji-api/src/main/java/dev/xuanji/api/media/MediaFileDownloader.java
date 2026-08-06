package dev.xuanji.api.media;

import dev.xuanji.api.annotation.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 媒体下载落盘工具（P1-D convertToFilePath 落地）。
 *
 * <p><b>空间可控机制</b>：
 * <ol>
 *   <li><b>按需 + 开关</b>：只有插件显式调用 {@link #download} 才下载；开关按「bot 级 &gt; 全局」
 *       （{@link #refreshConfig} 注入，core 定时刷新），未开启直接返回 null；</li>
 *   <li><b>内容级去重</b>：先落临时文件，算<b>文件内容 SHA-256</b>，最终文件名 = 内容哈希——
 *       同一张图即使 URL 不同（QQ 会给不同 bot/消息不同地址）也只存 1 份；</li>
 *   <li><b>TTL + 配额清理</b>：{@link #cleanup()} 删过期文件、超总配额删最旧（core 定时调用）。</li>
 * </ol>
 *
 * <p>未配置（默认关闭）时 {@link #download} 返回 null，不影响现有逻辑。
 */
public final class MediaFileDownloader {

    private static final Logger log = LoggerFactory.getLogger(MediaFileDownloader.class);

    private static volatile boolean configured = false;
    private static volatile Path baseDir;
    private static volatile long maxFileBytes = 200L * 1024 * 1024;   // 单文件上限 200MB
    private static volatile long ttlMillis = 7L * 24 * 3600 * 1000;   // 保留 7 天
    private static volatile long maxTotalBytes = 4L * 1024 * 1024 * 1024; // 总配额 4GB

    // 开关快照：bot 级显式设置优先；无 bot 级设置时用全局
    private static volatile boolean globalEnabled = false;
    private static final Set<String> BOT_ENABLED = ConcurrentHashMap.newKeySet();
    private static final Set<String> BOT_DISABLED = ConcurrentHashMap.newKeySet();

    /** 按 URL 去重的下载锁：同一 URL 并发下载只执行一次（多机器人同时收到同一张图） */
    private static final Map<String, Object> URL_LOCKS = new ConcurrentHashMap<>();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MediaFileDownloader() {}

    /** core 启动时注入存储配置；重复调用更新参数。 */
    public static synchronized void configure(Path dir, long maxFileBytes, long ttlDays, long maxTotalBytes) {
        baseDir = dir;
        if (maxFileBytes > 0) MediaFileDownloader.maxFileBytes = maxFileBytes;
        if (ttlDays > 0) MediaFileDownloader.ttlMillis = ttlDays * 24 * 3600 * 1000L;
        if (maxTotalBytes > 0) MediaFileDownloader.maxTotalBytes = maxTotalBytes;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException ignored) { }
        configured = true;
    }

    /** core 定时刷新开关快照：globalEnabled + 每 bot 显式开关（bot 级优先于全局）。 */
    public static synchronized void refreshConfig(boolean globalEnabled, Map<String, Boolean> perBot) {
        MediaFileDownloader.globalEnabled = globalEnabled;
        BOT_ENABLED.clear();
        BOT_DISABLED.clear();
        if (perBot != null) {
            perBot.forEach((bot, en) -> {
                if (Boolean.TRUE.equals(en)) BOT_ENABLED.add(bot);
                else BOT_DISABLED.add(bot);
            });
        }
    }

    /** 是否已配置启用。 */
    public static boolean isEnabled() { return configured && baseDir != null; }

    /** 某 bot 的下载开关（bot 级 &gt; 全局）。 */
    public static boolean isEnabledFor(String botKey) {
        if (botKey != null) {
            if (BOT_DISABLED.contains(botKey)) return false;
            if (BOT_ENABLED.contains(botKey)) return true;
        }
        return globalEnabled;
    }

    /**
     * 下载 URL 到本地（幂等 + 内容级去重）。仅支持 http(s)，超大小上限返回 null。
     *
     * @param url     http/https 媒体地址
     * @param type    媒体类型（决定默认扩展名）
     * @param botKey  当前机器人（null = 只看全局开关）
     * @return 本地文件；未启用 / 非法 URL / 超限 / 下载失败返回 null
     */
    public static Path download(String url, MediaType type, String botKey) {
        if (!isEnabled() || !isEnabledFor(botKey)) {
            log.info("[媒体下载] 跳过: 未启用 (configured={}, global={}, bot={})", configured, globalEnabled, botKey);
            return null;
        }
        if (url == null || url.isBlank()) { log.info("[媒体下载] 跳过: URL 为空"); return null; }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            log.info("[媒体下载] 跳过: 非 http(s) URL ({})", shorten(url));
            return null;
        }
        String ext = extFor(type, url);

        // 同一 URL 并发去重：多机器人同时收到同一张图时，只让一个线程下载，其余等待后复用
        Object lock = URL_LOCKS.computeIfAbsent(url, k -> new Object());
        synchronized (lock) {
            return downloadLocked(url, type, botKey, ext);
        }
    }

    private static Path downloadLocked(String url, MediaType type, String botKey, String ext) {
        Path tmp = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("User-Agent", "XuanJi-Bot/1.0")
                    .GET().build();
            HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.info("[媒体下载] 跳过: HTTP {} (bot={}, url={})", resp.statusCode(), botKey, shorten(url));
                return null;
            }
            long len = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (len > maxFileBytes) { logSkip(url, "Content-Length 超限 " + len + "B"); return null; }

            // createTempFile 已创建空文件，打开必须用 WRITE（不能用 CREATE_NEW——文件已存在必然冲突）
            tmp = Files.createTempFile(baseDir, "dl-" + java.util.UUID.randomUUID() + "-", ".part");
            long written = 0;
            try (InputStream in = resp.body();
                 java.io.OutputStream out = Files.newOutputStream(tmp, java.nio.file.StandardOpenOption.WRITE)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    written += n;
                    if (written > maxFileBytes) { logSkip(url, "流式超限 " + written + "B"); return null; }
                    out.write(buf, 0, n);
                }
            }
            if (written <= 0) return null;

            // 内容级去重：按文件内容 SHA-256 命名，同一内容不同 URL 只存 1 份
            String contentHash = sha256File(tmp);
            Path target = baseDir.resolve(contentHash.substring(0, 24) + ext);
            if (Files.exists(target) && size(target) > 0) {
                // 已存在（同一内容已落盘）→ 删临时，复用
                Files.deleteIfExists(tmp);
                tmp = null;
                log.info("[媒体下载] 内容去重命中: {} → {} (复用, {}B)", url, target.getFileName(), size(target));
                return target;
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            log.info("[媒体下载] 已落盘: {} → {} ({}B, bot={})", url, target.getFileName(), size(target), botKey);
            return target;
        } catch (Exception e) {
            log.info("[媒体下载] 跳过: 下载异常 {} (bot={}, url={})", e.getClass().getSimpleName(), botKey, shorten(url));
            if (log.isDebugEnabled()) log.debug("[媒体下载] 下载异常详情", e);
            return null;
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
    }

    private static void logSkip(String url, String reason) {
        log.info("[媒体下载] 跳过: {} ({})", reason, shorten(url));
    }

    /** 日志截断：超 160 字符省略中间，避免刷屏（完整 URL 会打印在成功/跳过详情里可按需放开）。 */
    private static String shorten(String url) {
        if (url == null) return "null";
        if (url.length() <= 160) return url;
        return url.substring(0, 120) + "…(" + url.length() + "字符)";
    }

    /** TTL 清理 + 总配额超限时删最旧。返回删除数量。 */
    public static synchronized long cleanup() {
        if (!isEnabled()) return 0;
        long removed = 0;
        long now = System.currentTimeMillis();
        try (var stream = Files.list(baseDir)) {
            var files = stream.filter(Files::isRegularFile).sorted(Comparator.comparingLong(p -> lastModified(p))).toList();
            long total = files.stream().mapToLong(p -> size(p)).sum();
            for (Path p : files) {
                boolean expired = now - lastModified(p) > ttlMillis;
                boolean overQuota = total > maxTotalBytes;
                if (!expired && !overQuota) break;
                if (p.getFileName().toString().startsWith("dl-")) {
                    // 下载中/刚失败的临时文件：仅删超过 1 小时的残留（异常中断遗留），避免误删进行中下载
                    if (now - lastModified(p) < 3600_000L) continue;
                }
                long sz = size(p);
                try {
                    Files.deleteIfExists(p);
                    total -= sz;
                    removed++;
                } catch (IOException ignored) { }
            }
            if (removed > 0) {
                log.info("[媒体下载] 清理完成: 删除 {} 个过期/超配额文件 (总占用 {}B / 上限 {}B)", removed, total, maxTotalBytes);
            }
        } catch (Exception ignored) { }
        return removed;
    }

    /** 存储现状（健康页/日志用）。 */
    public static synchronized Map<String, Object> stats() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("enabled", globalEnabled);
        m.put("botEnabled", BOT_ENABLED.size());
        m.put("botDisabled", BOT_DISABLED.size());
        m.put("maxFileBytes", maxFileBytes);
        m.put("ttlDays", ttlMillis / 24 / 3600 / 1000);
        m.put("maxTotalBytes", maxTotalBytes);
        long files = 0, total = 0;
        try (var stream = Files.list(baseDir)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                if (p.getFileName().toString().startsWith("dl-")) continue;
                files++;
                total += size(p);
            }
        } catch (Exception ignored) { }
        m.put("files", files);
        m.put("usedBytes", total);
        return m;
    }

    private static long lastModified(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0; } }
    private static long size(Path p) { try { return Files.size(p); } catch (Exception e) { return 0; } }

    /** URL 有扩展名用 URL 的；否则按类型给默认扩展名。 */
    private static String extFor(MediaType type, String url) {
        try {
            String path = URI.create(url).getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String e = path.substring(dot).toLowerCase();
                if (e.matches("\\.[a-z0-9]{1,8}")) return e;
            }
        } catch (Exception ignored) { }
        return switch (type == null ? MediaType.FILE : type) {
            case IMAGE -> ".jpg";
            case VOICE -> ".mp3";
            case VIDEO -> ".mp4";
            default -> ".bin";
        };
    }

    private static String sha256File(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(p.hashCode());
        }
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
