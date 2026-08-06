package dev.xuanji.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时事件面板环形缓冲区 — 取代原先挂在 ConsoleApiController 上的静态方法 recordEvent。
 *
 * <p>事件分发/消息落库路径调用 {@link #record}，控制台 {@code /console/events} 经 {@link #snapshot} 读取。
 */
@Slf4j
@Component
public class MessageEventRecorder {

    private static final int MAX = 200;

    private final Deque<Map<String, Object>> buffer = new ArrayDeque<>(MAX);

    /**
     * 记录一条实时事件。
     *
     * @param level     日志级别（LOG/WARN/ERROR）
     * @param category  分类（事件分发/匹配处理器/处理完成/消息入库等）
     * @param message   摘要
     * @param botId     机器人实例 id（可能为空）
     * @param eventType 事件类型（可能为空）
     * @param detail    详情（可能为空）
     */
    public void record(String level, String category, String message,
                       String botId, String eventType, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", Instant.now().toString());
        m.put("level", level);
        m.put("category", category);
        m.put("message", message);
        m.put("botId", botId == null ? "" : botId);
        m.put("eventType", eventType == null ? "" : eventType);
        m.put("detail", detail == null ? "" : detail);
        synchronized (buffer) {
            if (buffer.size() >= MAX) buffer.removeFirst();
            buffer.addLast(m);
        }
    }

    /** 最近全部事件快照（最多 200 条，按时间正序）。 */
    public List<Map<String, Object>> snapshot() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }
}
