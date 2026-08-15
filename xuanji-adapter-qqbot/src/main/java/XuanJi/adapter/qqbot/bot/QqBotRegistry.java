package XuanJi.adapter.qqbot.bot;

import XuanJi.api.adapter.BotContextBinder;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import XuanJi.sdk.bot.Bot;
import XuanJi.sdk.bot.BotRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * QQ 官方机器人门面注册表 — 实现 {@link BotRegistry}。
 *
 * <p>按 botKey 解析出 appId，向 {@link QqBotFactory} 取一个「无事件」主动机器人门面。
 * botKey 形如 {@code qq:1905134745}（前缀 {@code qq:} 仅作平台标识，内部剥除后取 appId）；
 * 空串 / 缺省时取 {@link RobotRegistry} 中第一个机器人（单机器人部署的便捷写法）。
 */
@Slf4j
@Component
public class QqBotRegistry implements BotRegistry {

    private final QqBotFactory factory;
    private final RobotRegistry robotRegistry;

    public QqBotRegistry(QqBotFactory factory, RobotRegistry robotRegistry) {
        this.factory = factory;
        this.robotRegistry = robotRegistry;
    }

    @Override
    public Bot get(String botKey) {
        String appId = resolveAppId(botKey);
        if (appId == null) {
            log.warn("[BotRegistry] 无法解析 botKey={}，无可用 QQ 机器人", botKey);
            return null;
        }
        return factory.proactive(appId);
    }

    @Override
    public boolean has(String botKey) {
        return resolveAppId(botKey) != null;
    }

    /** botKey → appId：剥 {@code qq:} 前缀；空则取第一个已注册机器人。 */
    private String resolveAppId(String botKey) {
        if (botKey != null && !botKey.isBlank()) {
            String id = botKey.startsWith("qq:") ? botKey.substring("qq:".length()) : botKey;
            // 直接是 appId，或能按 appId 命中注册表（兼容未带前缀写法）
            if (robotRegistry.findByAppId(id) != null) return id;
            // 也可能 botKey 直接是 robotId（注册表 key）
            if (robotRegistry.getRobot(id) != null) return robotRegistry.getRobot(id).getAppId();
            return null;
        }
        // 空串：单机器人便捷写法
        List<String> ids = robotRegistry.getAllRobots().values().stream().map(r -> r.getAppId()).toList();
        return ids.isEmpty() ? null : ids.get(0);
    }
}
