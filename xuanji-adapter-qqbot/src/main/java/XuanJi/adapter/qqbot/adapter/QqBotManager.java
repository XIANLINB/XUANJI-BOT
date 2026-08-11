package XuanJi.adapter.qqbot.adapter;

import XuanJi.adapter.qqbot.model.Robot;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import XuanJi.api.adapter.PlatformCapability;
import XuanJi.api.adapter.XuanJiBot;
import XuanJi.api.adapter.XuanJiBotManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * QQ XuanJiBot 管理器 — {@link RobotRegistry} 之上的 {@link XuanJiBotManager} 只读视图。
 *
 * <h3>为什么是视图而不是独立存储</h3>
 * <p>原实现自带一份 {@code Map<String, XuanJiBot>}，但全项目<b>没有任何地方调用它的 register()</b>，
 * 于是 {@code all()} / {@code onlineCount()} 永远返回空和 0 ——
 * 谁按 {@code XuanJiBotManager} 类型注入就会拿到一份「看起来正常的假数据」。
 * QQ 侧机器人的唯一真相是 {@link RobotRegistry}（由 {@code XuanJiBotRunner} 与
 * {@code BotConfigController} 写入），因此这里改为实时投影，不再维护第二份状态。
 *
 * <h3>ID 口径</h3>
 * <ul>
 *   <li>{@code Robot.id == appId == XuanJiBot.selfId}（全框架以 appId 标识 QQ 机器人）</li>
 *   <li>{@code XuanJiBot.id == "qq:" + appId}，与 {@code WebhookServiceImpl} / {@code QqBotWsClient}
 *       构造事件用的 XuanJiBot 完全一致</li>
 *   <li>{@link #find(String)} 两种形态都收，省得调用方自己拼</li>
 * </ul>
 */
@Slf4j
@Component
public class QqBotManager implements XuanJiBotManager {

    /** QQ 官方 XuanJiBot 的能力集（与 {@code QqAdapter#connect} 同源）。 */
    public static final Set<PlatformCapability> QQ_CAPABILITIES = Set.of(
            PlatformCapability.RECALL); // QQ 官方开放平台：支持撤回，无成员管理（禁言/踢人/改名片）

    public static final String PLATFORM = "qq";

    private final RobotRegistry robotRegistry;

    public QqBotManager(RobotRegistry robotRegistry) {
        this.robotRegistry = robotRegistry;
    }

    /** 由 appId 拼出 XuanJiBot.id。 */
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

    /** Robot（配置态）→ XuanJiBot（运行态视图）。status==1 视为在线。 */
    private static XuanJiBot toBot(Robot robot) {
        return new XuanJiBot(
                botId(robot.getId()),
                PLATFORM,
                robot.getId(),
                robot.getStatus() != null && robot.getStatus() == 1 ? XuanJiBot.Status.ONLINE : XuanJiBot.Status.OFFLINE,
                QQ_CAPABILITIES);
    }

    /**
     * QQ 侧注册走 {@link RobotRegistry#registerRobot}（需要 appSecret 等 XuanJiBot 携带不了的凭证信息），
     * 本方法无法凭一个 {@link XuanJiBot} 造出完整配置，故不做静默丢弃、只告警。
     */
    @Override
    public void register(XuanJiBot bot) {
        log.warn("[QQ-XuanJiBotManager] 忽略 register({}): QQ 机器人注册请走 RobotRegistry.registerRobot（需要 appSecret 等凭证）",
                bot == null ? "null" : bot.id());
    }

    @Override
    public void unregister(String botId) {
        String appId = toAppId(botId);
        if (appId == null) return;
        robotRegistry.unregisterRobot(appId);
    }

    @Override
    public Optional<XuanJiBot> find(String botId) {
        String appId = toAppId(botId);
        if (appId == null) return Optional.empty();
        return Optional.ofNullable(robotRegistry.getRobot(appId)).map(QqBotManager::toBot);
    }

    @Override
    public Collection<XuanJiBot> all() {
        return robotRegistry.getAllRobots().values().stream()
                .sorted(Comparator.comparing(Robot::getId))  // 顺序稳定，便于「取第一个」类调用
                .map(QqBotManager::toBot)
                .toList();
    }

    @Override
    public Collection<XuanJiBot> byPlatform(String platform) {
        return PLATFORM.equals(platform) ? all() : java.util.List.of();
    }

    @Override
    public int onlineCount() {
        return (int) robotRegistry.getAllRobots().values().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .count();
    }
}
