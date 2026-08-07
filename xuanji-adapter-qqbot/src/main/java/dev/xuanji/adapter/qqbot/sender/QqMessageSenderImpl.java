package dev.xuanji.adapter.qqbot.sender;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qqbot.api.QqApiService;
import dev.xuanji.adapter.qqbot.converter.QqMessageConverter;
import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.sender.MessageSender;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.api.sender.Target;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 消息发送器 — 实现统一 {@link MessageSender}，封装 QqApiService。
 *
 * <h3>robotId / envType 的唯一解析口径</h3>
 * <p>全框架以 <b>appId 作为 QQ 侧机器人唯一标识</b>（见 {@code XuanjiBotRunner#registerAndStart}）：
 * {@code Robot.id == appId == Bot.selfId}，而 {@code Bot.id} 形如 {@code "qq:<appId>"}。
 * {@link QqApiService} 的 robotId 参数即 {@link RobotRegistry} 的 key，也就是 appId。
 *
 * <p>envType 一律取自 {@link Robot#getActiveEnv()}，<b>不再由连接状态推测</b> ——
 * 在线与否和沙箱/正式是两件毫不相干的事，用 {@code isOnline()} 去猜会让沙箱机器人
 * 把请求打到正式环境的 API 基地址（{@code QqPlatformConfig#getApiBaseUrl} 按 envType 选址）。
 */
@Slf4j
@Component
public class QqMessageSenderImpl implements MessageSender {

    private final QqApiService qqApiService;
    private final RobotRegistry robotRegistry;

    public QqMessageSenderImpl(QqApiService qqApiService, RobotRegistry robotRegistry) {
        this.qqApiService = qqApiService;
        this.robotRegistry = robotRegistry;
    }

    /** 链路未把 bot 上下文传递到位时使用的错误前缀 —— 一旦出现即说明调用链有断点。 */
    private static final String LINK_BROKEN = "[QQ消息] 当前事件未携带 bot 上下文（链路没有传递到位）";

    @Override
    public SendReceipt reply(MessageChain chain) {
        String robotId = currentRobotId();           // 缺当前事件 bot 即抛，暴露链路断点
        BotEvent event = BotContext.current();         // currentRobotId 已校验：必为当前事件且 bot 非 null

        if (event.isGroupEvent() && event.group() != null) {
            return doSend(robotId, envTypeOf(robotId),
                    "/v2/groups/" + event.group().groupId() + "/messages", chain);
        } else {
            return doSend(robotId, envTypeOf(robotId),
                    "/v2/users/" + event.sender().platformUserId() + "/messages", chain);
        }
    }

    @Override
    public SendReceipt send(Target target, MessageChain chain) {
        String robotId;
        try {
            robotId = resolveRobotId();
        } catch (IllegalStateException e) {
            // 链路没有把 bot 上下文传递到位 —— 明确报错，不静默改用别的 bot（防多机器人串台）
            log.error("[QQ消息] 主动发送失败: {}", e.getMessage());
            return SendReceipt.fail(e.getMessage(), 0);
        }

        String path = switch (target) {
            case Target.Private p -> "/v2/users/" + p.openid() + "/messages";
            case Target.Group g   -> "/v2/groups/" + g.groupOpenid() + "/messages";
            case Target.Guild g   -> "/channels/" + g.channelId() + "/messages";
        };

        return doSend(robotId, envTypeOf(robotId), path, chain);
    }

    // ==================== robotId / envType 解析 ====================

    /**
     * 解析主动发送使用的 robotId。
     *
     * <p><b>铁律：任何事件 / 消息收发必须 100% 使用当前事件的 bot。</b>
     * 在事件处理线程里调 {@code send()}（如插件收到 A 机器人的消息后主动向别处推送），
     * 必须还由 A 发出去，否则多机器人部署会串台。
     *
     * <p>当前事件的 bot <b>百分百存在</b>——它是事件的来源。若取不到，说明是
     * <b>链路没有把 bot 上下文传递到位</b>（未绑定 / 事件缺 bot / selfId 为空），
     * 此时直接抛错暴露问题，绝不静默改用注册表里的别的 bot（那会掩盖链路 bug 并可能串台）。
     *
     * @return robotId（即 appId）
     * @throws IllegalStateException 当未绑定 / 缺失当前事件 bot（链路断点）
     */
    String resolveRobotId() { // package-private：供同包单测直接调用解析逻辑
        return currentRobotId();
    }

    /**
     * 统一从当前事件上下文取出 robotId；缺失即抛，暴露链路断点。
     */
    private String currentRobotId() {
        if (!BotContext.currentEvent.isBound()) {
            throw new IllegalStateException(LINK_BROKEN + "：未绑定 BotContext，必须在事件处理线程内调用 send/reply，或显式绑定 BotContext。");
        }
        BotEvent event = BotContext.current();
        if (event == null || event.bot() == null) {
            throw new IllegalStateException(LINK_BROKEN + "：当前事件缺少 bot 实例（链路没有传递到位）。");
        }
        String current = normalizeRobotId(event.bot().selfId());
        if (current == null || current.isBlank()) {
            throw new IllegalStateException(LINK_BROKEN + "：当前事件的 bot.selfId 为空，无法解析 robotId（链路没有传递到位）。");
        }
        return current;
    }

    /**
     * 归一化 robotId：兼容 {@code "qq:<appId>"} 形态的 Bot.id 被误传进来的情况。
     * 正常路径下 {@code Bot.selfId()} 本就是纯 appId，此处仅作防御。
     */
    static String normalizeRobotId(String selfId) {
        if (selfId == null || selfId.isBlank()) return null;
        int idx = selfId.indexOf(':');
        return idx >= 0 ? selfId.substring(idx + 1) : selfId;
    }

    /** 取机器人的激活环境；查不到时回退 SANDBOX（与 {@link RobotRegistry#getActiveEnvironment} 同口径，宁可打沙箱也不误发正式）。 */
    String envTypeOf(String robotId) {
        Robot robot = robotId == null ? null : robotRegistry.getRobot(robotId);
        if (robot == null || robot.getActiveEnv() == null || robot.getActiveEnv().isBlank()) {
            log.debug("[QQ消息] 机器人 {} 未登记激活环境，回退 SANDBOX", robotId);
            return "SANDBOX";
        }
        return robot.getActiveEnv();
    }

    // ==================== 实际发送 ====================

    private SendReceipt doSend(String robotId, String envType, String path, MessageChain chain) {
        ObjectNode payload = QqMessageConverter.toQqPayload(chain);
        long start = System.currentTimeMillis();
        try {
            qqApiService.post(robotId, envType, path, payload);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[QQ消息] 发送成功: robotId={}, env={}, path={}, {}ms", robotId, envType, path, elapsed);
            return SendReceipt.ok("", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[QQ消息] 发送失败: robotId={}, env={}, path={}, error={}", robotId, envType, path, e.getMessage());
            return SendReceipt.fail(e.getMessage(), elapsed);
        }
    }
}
