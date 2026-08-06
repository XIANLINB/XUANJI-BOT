/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.api.adapter.Bot
 *  dev.xuanji.api.adapter.Bot$Status
 *  dev.xuanji.api.adapter.BotManager
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.xuanji.adapter.onebot.adapter;

import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotBotManager
implements BotManager {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotBotManager.class);
    public static final Set<String> ONEBOT_CAPABILITIES = Set.of("can_recall", "can_ban", "can_set_card", "can_kick", "can_at", "can_get_member", "can_handle_request");
    private final Map<String, Bot> bots = new ConcurrentHashMap<String, Bot>();

    public static String botId(String selfId) {
        return "onebot:" + (selfId == null ? "unknown" : selfId);
    }

    public void register(Bot bot) {
        this.bots.put(bot.id(), bot);
        log.info("[OneBot-BotManager] \u5df2\u6ce8\u518c: {}", (Object)bot.id());
    }

    public void unregister(String botId) {
        Bot removed = this.bots.remove(botId);
        if (removed != null) {
            log.info("[OneBot-BotManager] \u5df2\u79fb\u9664: {}", (Object)botId);
        }
    }

    public Optional<Bot> find(String botId) {
        return Optional.ofNullable(this.bots.get(botId));
    }

    public Collection<Bot> all() {
        return List.copyOf(this.bots.values());
    }

    public Collection<Bot> byPlatform(String platform) {
        return this.bots.values().stream().filter(b -> b.platform().equals(platform)).toList();
    }

    public int onlineCount() {
        return (int)this.bots.values().stream().filter(Bot::isOnline).count();
    }

    public Bot findOrCreate(String selfId) {
        String id = OneBotBotManager.botId(selfId);
        return this.bots.computeIfAbsent(id, k -> {
            log.info("[OneBot-BotManager] \u9996\u6b21\u53d1\u73b0 bot: {}", (Object)id);
            return new Bot(id, "onebot", selfId, Bot.Status.ONLINE, ONEBOT_CAPABILITIES);
        });
    }

    public void markOnline(String selfId) {
        this.updateStatus(selfId, Bot.Status.ONLINE);
    }

    public void markOffline(String selfId) {
        this.updateStatus(selfId, Bot.Status.OFFLINE);
    }

    private void updateStatus(String selfId, Bot.Status status) {
        String id = OneBotBotManager.botId(selfId);
        Bot old = this.bots.get(id);
        if (old == null) {
            this.bots.put(id, new Bot(id, "onebot", selfId, status, ONEBOT_CAPABILITIES));
            return;
        }
        if (old.status() != status) {
            this.bots.put(id, new Bot(old.id(), old.platform(), old.selfId(), status, old.capabilities()));
            log.info("[OneBot-BotManager] {} \u72b6\u6001\u53d8\u66f4: {} \u2192 {}", new Object[]{id, old.status(), status});
        }
    }
}

