package dev.xuanji.adapter.qq.adapter;

import dev.xuanji.adapter.qq.registry.RobotRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QQ Bot 管理器 — 在现有 RobotRegistry 之上实现 {@link BotManager} 接口。
 *
 * <p>P2 过渡形态：复用已有 RobotRegistry 的注册逻辑，同时维护 Bot 实例视图。
 * P3 时期 RobotRegistry 的职责将完全迁移到此处。
 */
@Component
public class QqBotManager implements BotManager {

    private final RobotRegistry robotRegistry;
    private final Map<String, Bot> bots = new ConcurrentHashMap<>();

    public QqBotManager(RobotRegistry robotRegistry) {
        this.robotRegistry = robotRegistry;
    }

    @Override
    public void register(Bot bot) {
        bots.put(bot.id(), bot);
    }

    @Override
    public void unregister(String botId) {
        bots.remove(botId);
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
        return bots.values().stream()
                .filter(b -> b.platform().equals(platform))
                .toList();
    }

    @Override
    public int onlineCount() {
        return (int) bots.values().stream()
                .filter(Bot::isOnline)
                .count();
    }
}
