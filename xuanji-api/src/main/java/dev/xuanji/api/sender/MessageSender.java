package dev.xuanji.api.sender;

import dev.xuanji.api.message.MessageChain;

/**
 * 消息发送服务 — 收发分离。
 *
 * <ul>
 *   <li>{@link #reply} — 被动回复（自动带 msg_id，仅事件处理链内可用）；</li>
 *   <li>{@link #send}  — 主动发送（定时推送、跨会话通知等场景）。</li>
 * </ul>
 *
 * <p>实现由各平台适配器提供，通过 BotContext 绑定到当前 bot 实例。
 */
public interface MessageSender {

    /** 被动回复当前事件 */
    SendReceipt reply(MessageChain chain);

    /** 主动向指定目标发送 */
    SendReceipt send(Target target, MessageChain chain);
}
