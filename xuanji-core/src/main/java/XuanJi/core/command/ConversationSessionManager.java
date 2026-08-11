package XuanJi.core.command;

import XuanJi.api.action.ConversationSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多轮会话状态存储实现 — 按「botKey + groupId(单聊=userId) + userId」维度存内存状态，TTL 过期自动清理。
 *
 * <p>非阻塞：不挂起事件线程，插件在命令处理中 {@link #begin}/{@link #get}/{@link #end} 驱动流程。
 * 定期清理过期键（每次 begin 触发一次兜底扫描，避免专用定时器）。
 */
@Slf4j
@Component
public class ConversationSessionManager implements ConversationSession {

    private record Entry(Object state, long expireAt) {}

    /** key = botKey|groupId|userId|flow */
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private String key(String flow) {
        String botKey = CommandRegistry.getCurrentBotKey();
        String groupId = CommandRegistry.getCurrentGroupId();
        String userId = CommandRegistry.getCurrentUser();
        String scope = (groupId != null && !groupId.isBlank()) ? groupId : userId;
        return (botKey == null ? "" : botKey) + "|" + (scope == null ? "" : scope) + "|" + (userId == null ? "" : userId) + "|" + flow;
    }

    @Override
    public void begin(String flow, Object state) {
        long expireAt = System.currentTimeMillis() + DEFAULT_TTL.toMillis();
        sessions.put(key(flow), new Entry(state, expireAt));
        sweep();  // 顺带清理过期键
    }

    @Override
    public boolean active(String flow) {
        return peek(flow) != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String flow, Class<T> type) {
        Entry e = peek(flow);
        return e == null ? null : (T) e.state;
    }

    @Override
    public <T> void update(String flow, T state) {
        Entry cur = peek(flow);
        if (cur == null) { begin(flow, state); return; }
        sessions.put(key(flow), new Entry(state, System.currentTimeMillis() + DEFAULT_TTL.toMillis()));
    }

    @Override
    public void end(String flow) {
        sessions.remove(key(flow));
    }

    @Override
    public long ttlSeconds(String flow) {
        Entry e = sessions.get(key(flow));
        if (e == null) return 0;
        return Math.max(0, (e.expireAt() - System.currentTimeMillis()) / 1000);
    }

    /** 取未过期条目（过期即清理）。 */
    private Entry peek(String flow) {
        String k = key(flow);
        Entry e = sessions.get(k);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expireAt()) {
            sessions.remove(k);
            return null;
        }
        return e;
    }

    /** 清理全部过期条目（低频调用，量小无压力）。 */
    private void sweep() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(ent -> now > ent.getValue().expireAt());
    }
}
