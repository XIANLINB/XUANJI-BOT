package dev.xuanji.adapter.qqbot.sender;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.core.sender.BotPushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 官方适配器的定时推送实现：供 xuanji-scheduler 的 BOT_PUSH 任务调用。
 *
 * <p>通过 {@link MessageSender#runWithRobotContext(String, Runnable)} 显式绑定
 * 机器人上下文（内部解析 activeEnv 并设置 ThreadLocal），无事件上下文也能主动发送；
 * 发送成功后由 MessageSender 统一落 OUT 消息记录（消息监控可见）。
 */
@Slf4j
@Component
public class QqBotPushSender implements BotPushSender {

    private final MessageSender messageSender;

    public QqBotPushSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String platform() {
        return "qqbot";
    }

    @Override
    public SendReceipt push(String botKey, String targetType, String targetId, String content) {
        long t0 = System.currentTimeMillis();
        ObjectNode[] box = new ObjectNode[1];
        try {
            messageSender.runWithRobotContext(botKey, () -> {
                if ("C2C".equalsIgnoreCase(targetType)) {
                    box[0] = messageSender.sendC2cText(targetId, content, null);
                } else {
                    box[0] = messageSender.sendGroupText(targetId, content, null);
                }
            });
            ObjectNode resp = box[0];
            if (resp == null) {
                return SendReceipt.fail("发送无回执", System.currentTimeMillis() - t0);
            }
            String msgId = resp.path("id").asText(null);
            log.info("[BotPush] qqbot 推送成功: bot={} type={} target={} msgId={}", botKey, targetType, targetId, msgId);
            return msgId != null && !msgId.isBlank()
                    ? SendReceipt.ok(msgId, System.currentTimeMillis() - t0)
                    : SendReceipt.ok("", System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[BotPush] qqbot 推送失败: bot={} type={} target={} error={}", botKey, targetType, targetId, e.getMessage());
            return SendReceipt.fail(e.getMessage(), System.currentTimeMillis() - t0);
        }
    }
}
