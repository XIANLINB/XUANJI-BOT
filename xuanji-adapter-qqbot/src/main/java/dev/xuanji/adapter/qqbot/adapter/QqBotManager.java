package dev.xuanji.adapter.qqbot.adapter;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * QQ Bot 管理器 — {@link RobotRegistry} 之上的 {@link BotManager} 只读视图。
 *
 * <h3>为什么是视图而不是独立存储</h3>
 * <p>原实现自带一份 {@code Map<String, Bot>}，但全项目<b>没有任何地方调用它的 register()</b>，
 * 于是 {@code all()} / {@code onlineCount()} 永远返回空和 0 ——
 * 谁按 {@code BotManager} 类型注入就会拿到一份「看起来正常的假数据」。
 * QQ 侧机器人的唯一真相是 {@link RobotRegistry}（由 {@code XuanjiBotRunner} 与
 * {@code BotConfigController} 写入），因此这里改为实时投影，不再维护第二份状态。
 *
 * <h3>ID 口径</h3>
 * <ul>
 *   <li>{@code Robot.id == appId == Bot.selfId}（全框架以 appId 标识 QQ 机器人）</li>
 *   <li>{@code Bot.id == "qq:" + appId}，与 {@code WebhookServiceImpl} / {@code QqBotWsClient}
 *       构造事件用的 Bot 完全一致</li>
 *   <li>{@link #find(String)} 两种形态都收，省得调用方自己拼</li>
 * </ul>
 */
@Slf4j
@Component
public class QqBotManager implements BotManager {

    /** QQ 官方 Bot 的能力集（与 {@code QqAdapter#connect} 同源）。 */
    public static final Set<String> QQ_CAPABILITIES = Set.of("can_recall", "can_ban", "can_set_card");

    public static final String PLATFORM = "qq";

    private final RobotRegistry robotRegistry;

    public QqBotManager(RobotRegistry robotRegistry) {
        this.robotRegistry = robotRegistry;
    }

    /** 由 appId 拼出 Bot.id。 */
    public static String botId(String appId) {
        return PLATFORM + ":" + (appId == null ? "unknown" : appId);
    }

    /** 剥掉 {@code "qq:"} 前缀还原 appId；本就是 appId 则原样返回。 */
    private static String toAppId(String botIdOrAppId) {
        if (botIdOrAppId == null || botIdOrAppId.isBlank()) return null;
        return botIdOrAppId.startsWith(PLATFORM + ":")
                ? botIdOrAppId.substring(PLATFORM.length() + 1)
                : botIdOrAppId;
    }

    /** Robot（配置态）→ Bot（运行态视图）。status==1 视为在线。 */
    private static Bot toBot(Robot robot) {
        return new Bot(
                botId(robot.getId()),
                PLATFORM,
                robot.getId(),
                robot.getStatus() != null && robot.getStatus() == 1 ? Bot.Status.ONLINE : Bot.Status.OFFLINE,
                QQ_CAPABILITIES);
    }

    /**
     * QQ 侧注册走 {@link RobotRegistry#registerRobot}（需要 appSecret 等 Bot 携带不了的凭证信息），
     * 本方法无法凭一个 {@link Bot} 造出完整配置，故不做静默丢弃、只告警。
     */
    @Override
    public void register(Bot bot) {
        log.warn("[QQ-BotManager] 忽略 register({}): QQ 机器人注册请走 RobotRegistry.registerRobot（需要 appSecret 等凭证）",
                bot == null ? "null" : bot.id());
    }

    @Override
    public void unregister(String botId) {
        String appId = toAppId(botId);
        if (appId == null) return;
        robotRegistry.unregisterRobot(appId);
    }

    @Override
    public Optional<Bot> find(String botId) {
        String appId = toAppId(botId);
        if (appId == null) return Optional.empty();
        return Optional.ofNullable(robotRegistry.getRobot(appId)).map(QqBotManager::toBot);
    }

    @Override
    public Collection<Bot> all() {
        return robotRegistry.getAllRobots().values().stream()
                .sorted(Comparator.comparing(Robot::getId))  // 顺序稳定，便于「取第一个」类调用
                .map(QqBotManager::toBot)
                .toList();
    }

    @Override
    public Collection<Bot> byPlatform(String platform) {
        return PLATFORM.equals(platform) ? all() : java.util.List.of();
    }

    @Override
    public int onlineCount() {
        return (int) robotRegistry.getAllRobots().values().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .count();
    }
}
