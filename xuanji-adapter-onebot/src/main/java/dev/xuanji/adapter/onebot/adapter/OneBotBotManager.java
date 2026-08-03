package dev.xuanji.adapter.onebot.adapter;

import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OneBot Bot 管理器 — {@link BotManager} 的 OneBot 实现。
 *
 * <p>与 QQ 适配器的关键差异：QQ 的 bot 实例来自配置文件（启动即已知 appId），
 * 而 OneBot 的 self_id 由对端连接时才带过来（反向 WS 尤其如此），
 * 因此这里支持 {@link #findOrCreate} 动态注册。
 */
@Slf4j
public class OneBotBotManager implements BotManager {

    /** OneBot v11 实现（Napcat/Lagrange 等）通用具备的能力位 */
    public static final Set<String> ONEBOT_CAPABILITIES = Set.of(
            "can_recall",       // delete_msg
            "can_ban",          // set_group_ban
            "can_set_card",     // set_group_card
            "can_kick",         // set_group_kick
            "can_at",           // at 消息段
            "can_get_member",   // get_group_member_info
            "can_handle_request" // set_friend_add_request / set_group_add_request
    );

    private final Map<String, Bot> bots = new ConcurrentHashMap<>();

    public static String botId(String selfId) {
        return "onebot:" + (selfId == null ? "unknown" : selfId);
    }

    @Override
    public void register(Bot bot) {
        bots.put(bot.id(), bot);
        log.info("[OneBot-BotManager] 已注册: {}", bot.id());
    }

    @Override
    public void unregister(String botId) {
        Bot removed = bots.remove(botId);
        if (removed != null) {
            log.info("[OneBot-BotManager] 已移除: {}", botId);
        }
    }

    @Override
    public Optional<Bot> find(String botId) {
        return Optional.ofNullable(bots.get(botId));
    }

    @Override
    public Collection<Bot> all() {
        return List.copyOf(bots.values());
    }

    @Override
    public Collection<Bot> byPlatform(String platform) {
        return bots.values().stream().filter(b -> b.platform().equals(platform)).toList();
    }

    @Override
    public int onlineCount() {
        return (int) bots.values().stream().filter(Bot::isOnline).count();
    }

    // ==================== OneBot 专属：按连接动态登记 ====================

    /** 按 selfId 取 Bot，不存在则以 ONLINE 状态创建（连接已建立才会走到这里） */
    public Bot findOrCreate(String selfId) {
        String id = botId(selfId);
        return bots.computeIfAbsent(id, k -> {
            log.info("[OneBot-BotManager] 首次发现 bot: {}", id);
            return new Bot(id, "onebot", selfId, Bot.Status.ONLINE, ONEBOT_CAPABILITIES);
        });
    }

    public void markOnline(String selfId) {
        updateStatus(selfId, Bot.Status.ONLINE);
    }

    public void markOffline(String selfId) {
        updateStatus(selfId, Bot.Status.OFFLINE);
    }

    private void updateStatus(String selfId, Bot.Status status) {
        String id = botId(selfId);
        Bot old = bots.get(id);
        if (old == null) {
            bots.put(id, new Bot(id, "onebot", selfId, status, ONEBOT_CAPABILITIES));
            return;
        }
        if (old.status() != status) {
            bots.put(id, new Bot(old.id(), old.platform(), old.selfId(), status, old.capabilities()));
            log.info("[OneBot-BotManager] {} 状态变更: {} → {}", id, old.status(), status);
        }
    }
}
