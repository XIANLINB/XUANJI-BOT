package dev.xuanji.console.service;

import dev.xuanji.core.web.RealtimeEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 日志文件实时尾随服务 —— 把 {@code logs/xuanji-bot.log} 的新增行通过
 * {@link RealtimeEventPublisher} 以 {@code type=log} 推送，供控制台 SSE 实时展示日志。
 *
 * <p>设计要点：
 * <ul>
 *   <li>只推<b>连接后</b>的新行（连接时记录当前文件末尾，不回放历史），避免刷屏。</li>
 *   <li>按字节偏移读取，遇到换行才解码——部分多字节 UTF-8 字符不会在读取边界被截断解码，
 *       跨次读取的半行字节留在 {@link #pendingBytes} 中，下次拼接后整体解码。</li>
 *   <li>文件被截断/轮转（size 变小）时重置偏移与缓冲，从新内容起点继续。</li>
 *   <li>用虚拟线程轮询（500ms），无 WatchService 在 Windows 下的边界怪异。</li>
 * </ul>
 */
@Slf4j
@Component
public class LogTailService {

    private static final int POLL_MS = 500;
    private static final long MAX_PENDING_BYTES = 4L * 1024 * 1024; // 单行异常巨大时的兜底上限
    private static final int READ_CHUNK = 8 * 1024 * 1024;

    private final RealtimeEventPublisher publisher;
    private final Path logFile;
    private volatile long lastPos = 0;
    private volatile boolean running = true;
    private Thread watchThread;
    private final ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

    public LogTailService(RealtimeEventPublisher publisher) {
        this.publisher = publisher;
        this.logFile = Paths.get("logs", "xuanji-bot.log").toAbsolutePath();
    }

    @PostConstruct
    public void start() {
        try {
            if (Files.exists(logFile)) lastPos = Files.size(logFile);
        } catch (IOException e) {
            lastPos = 0;
        }
        watchThread = Thread.ofVirtual().name("xuanji-log-tail").start(this::run);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
    }

    private void run() {
        while (running) {
            try {
                if (!Files.exists(logFile)) {
                    sleep(POLL_MS);
                    continue;
                }
                long size = Files.size(logFile);
                if (size < lastPos) {
                    // 文件被清空/轮转：从头开始
                    lastPos = size;
                    synchronized (pendingBytes) { pendingBytes.reset(); }
                    continue;
                }
                if (size > lastPos) {
                    long avail = size - lastPos;
                    int cap = (int) Math.min(avail, READ_CHUNK);
                    byte[] buf = new byte[cap];
                    int read = readFrom(lastPos, buf);
                    if (read > 0) {
                        lastPos += read;
                        synchronized (pendingBytes) { pendingBytes.write(buf, 0, read); }
                        drainCompleteLines();
                    }
                }
                sleep(POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
        } catch (Exception e) {
            log.debug("[日志实时推送] 读取异常: {}", e.getMessage());
            try { sleep(POLL_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        }
    }

    /** 解码 pendingBytes 中到最后一个 '\n' 为止的完整行并推送；剩余半行留作缓冲。 */
    private void drainCompleteLines() throws IOException {
        byte[] all;
        synchronized (pendingBytes) { all = pendingBytes.toByteArray(); }
        int lastNl = lastIndexOf(all, (byte) '\n');
        if (lastNl < 0) {
            // 还没有完整行：超长单行保护，避免缓冲无限增长
            if (all.length > MAX_PENDING_BYTES) {
                synchronized (pendingBytes) { pendingBytes.reset(); pendingBytes.write(all, all.length - 1024, 1024); }
                log.warn("[日志实时推送] 单行超过 {} 字节，已丢弃前置片段", MAX_PENDING_BYTES);
            }
            return;
        }
        byte[] complete = Arrays.copyOfRange(all, 0, lastNl);
        byte[] rest = Arrays.copyOfRange(all, lastNl + 1, all.length);
        synchronized (pendingBytes) { pendingBytes.reset(); pendingBytes.write(rest, 0, rest.length); }

        String text = new String(complete, StandardCharsets.UTF_8);
        for (String line : text.split("\n", -1)) {
            if (!line.isEmpty()) {
                publisher.publish(Map.of("type", "log", "line", line));
            }
        }
    }

    private int readFrom(long pos, byte[] buf) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(pos);
            return raf.read(buf);
        }
    }

    private static int lastIndexOf(byte[] arr, byte b) {
        for (int i = arr.length - 1; i >= 0; i--) {
            if (arr[i] == b) return i;
        }
        return -1;
    }

    private static void sleep(long ms) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(ms);
    }
}
