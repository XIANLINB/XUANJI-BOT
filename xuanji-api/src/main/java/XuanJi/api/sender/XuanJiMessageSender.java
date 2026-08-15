package XuanJi.api.sender;

import XuanJi.api.message.XuanJiMessage;

/**
 * 消息发送服务 — 收发分离（框架「动作指令」统一出口）。
 *
 * <ul>
 *   <li>{@link #reply} — 被动回复（自动带 msg_id，仅事件处理链内可用）；</li>
 *   <li>{@link #send}  — 主动发送（定时推送、跨会话通知等场景）；</li>
 *   <li>{@link #recall} — 撤回一条消息（默认不支持 → 返回 fail，由能力位守卫降级）。</li>
 * </ul>
 *
 * <p>群管 / 审批 / 查询等动作已统一收敛到 {@code XuanJi.sdk.bot.Bot} 门面（经
 * {@code PlatformActionHub} 分发），不再由本发送接口承载。
 *
 * <p>实现由各平台适配器提供，通过 BotContext 绑定到当前 bot 实例。
 */
public interface XuanJiMessageSender {

    /** 被动回复当前事件 */
    XuanJiSendReceipt reply(XuanJiMessage chain);

    /** 主动向指定目标发送 */
    XuanJiSendReceipt send(XuanJiTarget target, XuanJiMessage chain);

    /** 撤回一条消息（target 为消息所在群/会话）。 */
    default XuanJiSendReceipt recall(XuanJiTarget target, String msgId) {
        return XuanJiSendReceipt.fail("平台不支持撤回消息", 0);
    }
}
