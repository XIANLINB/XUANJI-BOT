package XuanJi.api.sender;

import XuanJi.api.message.XuanJiMessage;

/**
 * 消息发送服务 — 收发分离（框架「动作指令」统一出口）。
 *
 * <ul>
 *   <li>{@link #reply} — 被动回复（自动带 msg_id，仅事件处理链内可用）；</li>
 *   <li>{@link #send}  — 主动发送（定时推送、跨会话通知等场景）；</li>
 *   <li>{@link #recall} / {@link #kick} / {@link #mute} / {@link #approve} / {@link #setCard}
 *       — 动作类操作（默认不支持 → 返回 fail，由能力位守卫降级）。</li>
 * </ul>
 *
 * <p>实现由各平台适配器提供，通过 BotContext 绑定到当前 bot 实例。
 * 核心只调用本接口方法，动作能否执行由适配器按 {@code XuanJiBot.capabilities} 决定。
 */
public interface XuanJiMessageSender {

    /** 被动回复当前事件 */
    XuanJiSendReceipt reply(XuanJiMessage chain);

    /** 主动向指定目标发送 */
    XuanJiSendReceipt send(XuanJiTarget target, XuanJiMessage chain);

    // ──────────── 动作类（默认不支持，适配器按能力覆盖）────────────

    /** 撤回一条消息（target 为消息所在群/会话）。 */
    default XuanJiSendReceipt recall(XuanJiTarget target, String msgId) {
        return XuanJiSendReceipt.fail("平台不支持撤回消息", 0);
    }

    /** 踢出群成员。 */
    default XuanJiSendReceipt kick(XuanJiTarget.Group group, String userId) {
        return XuanJiSendReceipt.fail("平台不支持踢人", 0);
    }

    /** 禁言群成员（seconds=0 表示解除禁言）。 */
    default XuanJiSendReceipt mute(XuanJiTarget.Group group, String userId, int seconds) {
        return XuanJiSendReceipt.fail("平台不支持禁言", 0);
    }

    /** 审批加群 / 好友请求（requestId 由入站事件透传）。 */
    default XuanJiSendReceipt approve(XuanJiTarget target, String requestId, boolean accept) {
        return XuanJiSendReceipt.fail("平台不支持请求审批", 0);
    }

    /** 设置群名片。 */
    default XuanJiSendReceipt setCard(XuanJiTarget.Group group, String userId, String card) {
        return XuanJiSendReceipt.fail("平台不支持设置群名片", 0);
    }
}
