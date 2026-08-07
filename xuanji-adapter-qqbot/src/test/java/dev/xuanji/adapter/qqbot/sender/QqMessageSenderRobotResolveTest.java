package dev.xuanji.adapter.qqbot.sender;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.api.sender.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 主动发送的 robotId / envType 解析回归。
 *
 * <p>核心铁律（用户硬性要求）：<b>任何事件 / 消息收发必须 100% 使用当前事件的 bot</b>。
 * 当前事件的 bot 百分百存在（它是事件来源）；若取不到，说明链路没有把 bot 上下文传递到位，
 * 必须<b>抛错暴露</b>，绝不能静默改用注册表里的别的 bot（多机器人部署会串台、掩盖链路 bug）。
 *
 * <p>本测试只覆盖解析逻辑，不触达真实发送，故 {@code QqApiService} 传 null。
 */
@DisplayName("QqMessageSenderImpl robotId/envType 解析")
class QqMessageSenderRobotResolveTest {

    private static Robot robot(String id, String activeEnv, Integer status) {
        Robot r = new Robot();
        r.setId(id);
        r.setAppId(id);              // 全框架口径：robotId == appId
        r.setAppSecretEncrypted("secret-" + id);
        r.setActiveEnv(activeEnv);
        r.setStatus(status);
        return r;
    }

    private static QqMessageSenderImpl senderWith(Robot... robots) {
        RobotRegistry registry = new RobotRegistry();
        for (Robot r : robots) registry.registerRobot(r);
        return new QqMessageSenderImpl(null, registry);
    }

    private static BotEvent eventOf(String selfId) {
        Bot bot = new Bot("qq:" + selfId, "qq", selfId, Bot.Status.ONLINE, Set.of());
        return new BotEvent("evt", EventType.MESSAGE_GROUP, bot, null, null, null, null,
                Json.obj(), "GROUP_MESSAGE_CREATE", "PRODUCTION");
    }

    // ==================== robotId 解析：100% 用当前事件 bot ====================

    @Test
    @DisplayName("有事件上下文 → 复用当前 bot，即使注册表里还有别的机器人（多机器人部署防串台）")
    void contextBotWinsOverRegistry() {
        QqMessageSenderImpl sender = senderWith(
                robot("aaa", "PRODUCTION", 1),
                robot("zzz", "PRODUCTION", 1));

        // 绑定 zzz 的事件后，必须还用 zzz 发出，绝不按注册表排序另选
        String picked = ScopedValue.where(BotContext.currentEvent, eventOf("zzz"))
                .call(sender::resolveRobotId);
        assertEquals("zzz", picked);
    }

    @Test
    @DisplayName("当前事件 bot 未在注册表 → 仍以事件 bot 为准（下游凭证查找会明确报错，而非串台到别的 bot）")
    void unknownContextBotIsStillUsed() {
        QqMessageSenderImpl sender = senderWith(robot("aaa", "PRODUCTION", 1));

        String picked = ScopedValue.where(BotContext.currentEvent, eventOf("ghost"))
                .call(sender::resolveRobotId);
        assertEquals("ghost", picked, "事件由 ghost 收到，主动发送就该由 ghost 发出，绝不回退到 aaa");
    }

    @Test
    @DisplayName("无事件上下文 → 抛 IllegalStateException 暴露链路断点，而非静默选机器人")
    void noContextThrowsLinkBroken() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> senderWith(robot("aaa", "PRODUCTION", 1)).resolveRobotId());
        assertTrue(ex.getMessage().contains("链路没有传递到位"),
                "异常信息应明确指出链路没有传递到位，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("注册表为空 + 无上下文 → 同样抛链路断点异常（不再返回 null 拿空串撞凭证）")
    void emptyRegistryNoContextThrows() {
        assertThrows(IllegalStateException.class, () -> senderWith().resolveRobotId());
    }

    @Test
    @DisplayName("send() 在无上下文时返回 fail 收据（含链路断点信息），不抛异常破坏调用方")
    void sendReturnsFailWhenLinkBroken() {
        SendReceipt receipt = senderWith(robot("aaa", "PRODUCTION", 1))
                .send(new Target.Private("openid-x"), MessageChain.EMPTY);
        assertFalse(receipt.success(), "无上下文时应返回失败收据");
        assertTrue(receipt.errorMessage() != null && receipt.errorMessage().contains("链路没有传递到位"),
                "失败信息应点明链路断点，实际：" + receipt.errorMessage());
    }

    @Test
    @DisplayName("Bot.id 形态（qq:xxx）被误传也能归一化出 appId")
    void normalizeStripsPlatformPrefix() {
        assertEquals("102915166", QqMessageSenderImpl.normalizeRobotId("qq:102915166"));
        assertEquals("102915166", QqMessageSenderImpl.normalizeRobotId("102915166"));
        assertNull(QqMessageSenderImpl.normalizeRobotId(""));
        assertNull(QqMessageSenderImpl.normalizeRobotId(null));
    }

    // ==================== envType 解析 ====================

    @Test
    @DisplayName("envType 取自 Robot.activeEnv，不由连接状态推测")
    void envTypeComesFromActiveEnv() {
        QqMessageSenderImpl sender = senderWith(
                robot("sandboxBot", "SANDBOX", 1),
                robot("prodBot", "PRODUCTION", 1));

        // 关键回归：两个 bot 都是「在线」的，旧逻辑会把 sandboxBot 也判成 PRODUCTION
        assertEquals("SANDBOX", sender.envTypeOf("sandboxBot"));
        assertEquals("PRODUCTION", sender.envTypeOf("prodBot"));
    }

    @Test
    @DisplayName("activeEnv 缺失/机器人不存在 → 回退 SANDBOX（宁可打沙箱也不误发正式）")
    void envTypeFallsBackToSandbox() {
        QqMessageSenderImpl sender = senderWith(
                robot("blankEnv", "", 1),
                robot("nullEnv", null, 1));

        assertEquals("SANDBOX", sender.envTypeOf("blankEnv"));
        assertEquals("SANDBOX", sender.envTypeOf("nullEnv"));
        assertEquals("SANDBOX", sender.envTypeOf("notRegistered"));
        assertEquals("SANDBOX", sender.envTypeOf(null));
    }
}
