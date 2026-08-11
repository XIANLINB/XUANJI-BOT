package XuanJi.adapter.qqbot.sender;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;
import XuanJi.core.sender.BotPushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 官方适配器的定时推送实现：供 xuanji-scheduler 的 BOT_PUSH 任务调用。
 *
 * <p>通过 {@link MessageSender#runWithRobotContext(String, Runnable)} 显式绑定
 * 机器人上下文（内部解析 activeEnv 并设置 ThreadLocal），再组装 {@link XuanJiMessage}
 * 交给框架统一发送出口 {@link QqXuanJiMessageSender#send}，无事件上下文也能主动发送。
 */
@Slf4j
@Component
public class QqBotPushSender implements BotPushSender {

    private final MessageSender messageSender;
    private final QqXuanJiMessageSender xuanJiSender;

    public QqBotPushSender(MessageSender messageSender, QqXuanJiMessageSender xuanJiSender) {
        this.messageSender = messageSender;
        this.xuanJiSender = xuanJiSender;
    }

    @Override
    public String platform() {
        return "qqbot";
    }

    @Override
    public XuanJiSendReceipt push(String botKey, String targetType, String targetId, String content) {
        long t0 = System.currentTimeMillis();
        XuanJiSendReceipt[] box = new XuanJiSendReceipt[1];
        try {
            messageSender.runWithRobotContext(botKey, () -> {
                XuanJiTarget target = "C2C".equalsIgnoreCase(targetType)
                        ? new XuanJiTarget.Private(targetId)
                        : new XuanJiTarget.Group(targetId);
                box[0] = xuanJiSender.send(target, XuanJiMessage.text(content));
            });
            XuanJiSendReceipt r = box[0];
            if (r == null) {
                return XuanJiSendReceipt.fail("发送无回执", System.currentTimeMillis() - t0);
            }
            log.info("[BotPush] qqbot 推送成功: bot={} type={} target={} msgId={}", botKey, targetType, targetId, r.platformMsgId());
            return r;
        } catch (Exception e) {
            log.error("[BotPush] qqbot 推送失败: bot={} type={} target={} error={}", botKey, targetType, targetId, e.getMessage());
            return XuanJiSendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }
}
