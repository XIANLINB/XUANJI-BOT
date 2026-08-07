package dev.xuanji.core.sender;

import dev.xuanji.api.sender.SendReceipt;

/**
 * 定时推送通道 SPI：平台适配器实现并注册（@Component），
 * 供 xuanji-scheduler 的 BOT_PUSH 任务在无事件上下文时向指定群/用户推送消息。
 *
 * <p>实现约定：targetType 取 {@code "GROUP"}（群）或 {@code "C2C"}（单聊/私聊），
 * targetId 为对应平台的目标标识（群 openid / 用户 openid）。
 * 实现内部必须自行绑定发送上下文（如 qqbot 用 MessageSender.runWithRobotContext），
 * 不依赖当前线程的 BotContext 事件上下文。
 */
public interface BotPushSender {

    /** 平台标识，如 {@code "qqbot"} / {@code "onebot"}。 */
    String platform();

    /**
     * 向指定目标推送一条文本消息。
     *
     * @param botKey     机器人标识（平台内唯一）
     * @param targetType GROUP / C2C
     * @param targetId   群 openid 或用户 openid
     * @param content    消息内容
     * @return 发送回执
     */
    SendReceipt push(String botKey, String targetType, String targetId, String content);
}
