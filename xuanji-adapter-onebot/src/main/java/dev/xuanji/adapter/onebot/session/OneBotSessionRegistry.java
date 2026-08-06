/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.xuanji.adapter.onebot.session;

import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.core.storage.FrameworkBotRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotSessionRegistry {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotSessionRegistry.class);
    private final Map<String, OneBotSession> sessions = new ConcurrentHashMap<String, OneBotSession>();
    private final FrameworkBotRepository frameworkBotRepository;

    public OneBotSessionRegistry(FrameworkBotRepository frameworkBotRepository) {
        this.frameworkBotRepository = frameworkBotRepository;
    }

    public void register(OneBotSession session) {
        OneBotSession old = this.sessions.put(session.selfId(), session);
        if (old != null && old != session && old.isOpen()) {
            log.info("[OneBot] selfId={} \u51fa\u73b0\u65b0\u8fde\u63a5\uff0c\u5173\u95ed\u65e7\u4f1a\u8bdd({})", (Object)session.selfId(), (Object)old.direction());
            try {
                old.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (!OneBotSession.isPlaceholderId(session.selfId())) {
            try {
                this.frameworkBotRepository.upsert("onebot", session.selfId(), "onebot", "ONLINE");
            }
            catch (Exception e) {
                log.warn("[OneBot] \u6ce8\u518c xuanji_bot \u5931\u8d25: selfId={}, {}", (Object)session.selfId(), (Object)e.getMessage());
            }
        }
        log.info("[OneBot] \u4f1a\u8bdd\u5df2\u6ce8\u518c: selfId={}, direction={}, \u5f53\u524d\u5728\u7ebf={}", new Object[]{session.selfId(), session.direction(), this.sessions.size()});
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void rebind(OneBotSession session, String realSelfId) {
        if (session == null || realSelfId == null || realSelfId.isBlank()) {
            return;
        }
        if (realSelfId.equals(session.selfId())) {
            return;
        }
        OneBotSessionRegistry oneBotSessionRegistry = this;
        synchronized (oneBotSessionRegistry) {
            String current = session.selfId();
            if (realSelfId.equals(current)) {
                return;
            }
            if (!OneBotSession.isPlaceholderId(current)) {
                log.warn("[OneBot] \u4f1a\u8bdd selfId \u51b2\u7a81: \u5df2\u7ed1\u5b9a={}, \u62a5\u6587={}\uff0c\u5ffd\u7565\u56de\u7ed1", (Object)current, (Object)realSelfId);
                return;
            }
            if (!session.rebindSelfId(realSelfId)) {
                log.warn("[OneBot] \u4f1a\u8bdd\u4e0d\u652f\u6301 selfId \u56de\u7ed1: direction={}", (Object)session.direction());
                return;
            }
            this.sessions.remove(current, session);
            log.info("[OneBot] selfId \u56de\u7ed1: {} \u2192 {} ({})", new Object[]{current, realSelfId, session.direction()});
            this.register(session);
        }
    }

    public void unregister(OneBotSession session) {
        boolean removed = this.sessions.remove(session.selfId(), session);
        if (removed) {
            if (!OneBotSession.isPlaceholderId(session.selfId())) {
                try {
                    this.frameworkBotRepository.setStatus("onebot", session.selfId(), "OFFLINE");
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            log.info("[OneBot] \u4f1a\u8bdd\u5df2\u6ce8\u9500: selfId={}, direction={}, \u5269\u4f59\u5728\u7ebf={}", new Object[]{session.selfId(), session.direction(), this.sessions.size()});
        }
    }

    public Optional<OneBotSession> find(String selfId) {
        if (selfId == null || selfId.isBlank()) {
            return this.any();
        }
        OneBotSession s = this.sessions.get(selfId);
        return s != null && s.isOpen() ? Optional.of(s) : Optional.empty();
    }

    public Optional<OneBotSession> any() {
        return this.sessions.values().stream().filter(OneBotSession::isOpen).findFirst();
    }

    public Collection<OneBotSession> all() {
        return List.copyOf(this.sessions.values());
    }

    public int onlineCount() {
        return (int)this.sessions.values().stream().filter(OneBotSession::isOpen).count();
    }
}

