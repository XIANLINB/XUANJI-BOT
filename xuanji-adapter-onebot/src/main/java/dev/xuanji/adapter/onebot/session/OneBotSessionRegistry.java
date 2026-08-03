package dev.xuanji.adapter.onebot.session;

import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OneBot 会话注册表 — selfId → 活跃会话。
 *
 * <p>同一个 selfId 只保留最新一条连接（OneBot 实现重连时覆盖旧连接）。
 * 发送消息时按 selfId 定位会话；未指定 selfId 时取任意在线会话（单 bot 场景常见）。
 *
 * <p>连接建立时把 OneBot 实例登记进 {@code xuanji_bot}（platform=onebot），
 * 让控制台 Bot 列表 / 群数统计能看见 OneBot 实例；连接断开时置为 OFFLINE。
 */
@Slf4j
public class OneBotSessionRegistry {

    private final Map<String, OneBotSession> sessions = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public OneBotSessionRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void register(OneBotSession session) {
        OneBotSession old = sessions.put(session.selfId(), session);
        if (old != null && old != session && old.isOpen()) {
            log.info("[OneBot] selfId={} 出现新连接，关闭旧会话({})", session.selfId(), old.direction());
            try {
                old.close();
            } catch (Exception ignored) {
                // 旧连接可能已半死，忽略关闭异常
            }
        }
        // 登记/刷新 OneBot 实例到 xuanji_bot 表
        try {
            jdbc.update("MERGE INTO xuanji_bot (platform, bot_identifier, bot_key, status) " +
                            "KEY (platform, bot_identifier) VALUES (?, ?, ?, ?)",
                    "onebot", session.selfId(), session.selfId(), "ONLINE");
        } catch (Exception e) {
            log.warn("[OneBot] 注册 xuanji_bot 失败: selfId={}, {}", session.selfId(), e.getMessage());
        }
        log.info("[OneBot] 会话已注册: selfId={}, direction={}, 当前在线={}",
                session.selfId(), session.direction(), sessions.size());
    }

    /** 仅当注册的就是这条会话时才移除，避免重连竞态误删新连接 */
    public void unregister(OneBotSession session) {
        boolean removed = sessions.remove(session.selfId(), session);
        if (removed) {
            try {
                jdbc.update("UPDATE xuanji_bot SET status='OFFLINE' " +
                                "WHERE platform='onebot' AND bot_identifier=?",
                        session.selfId());
            } catch (Exception ignored) {
                // 离线状态非关键，忽略
            }
            log.info("[OneBot] 会话已注销: selfId={}, direction={}, 剩余在线={}",
                    session.selfId(), session.direction(), sessions.size());
        }
    }

    public Optional<OneBotSession> find(String selfId) {
        if (selfId == null || selfId.isBlank()) {
            return any();
        }
        OneBotSession s = sessions.get(selfId);
        return s != null && s.isOpen() ? Optional.of(s) : Optional.empty();
    }

    /** 任取一条在线会话（单 bot 部署时的便捷入口） */
    public Optional<OneBotSession> any() {
        return sessions.values().stream().filter(OneBotSession::isOpen).findFirst();
    }

    public Collection<OneBotSession> all() {
        return List.copyOf(sessions.values());
    }

    public int onlineCount() {
        return (int) sessions.values().stream().filter(OneBotSession::isOpen).count();
    }
}
