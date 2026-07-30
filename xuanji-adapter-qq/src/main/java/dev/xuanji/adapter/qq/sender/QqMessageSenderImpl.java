package dev.xuanji.adapter.qq.sender;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.QqApiService;
import dev.xuanji.adapter.qq.converter.QqMessageConverter;
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
 */
@Slf4j
@Component
public class QqMessageSenderImpl implements MessageSender {

    private final QqApiService qqApiService;

    public QqMessageSenderImpl(QqApiService qqApiService) {
        this.qqApiService = qqApiService;
    }

    @Override
    public SendReceipt reply(MessageChain chain) {
        BotEvent event = BotContext.current();
        String envType = event.bot().isOnline() ? "PRODUCTION" : "SANDBOX";
        Long robotId = (long) event.bot().selfId().hashCode();

        if (event.isGroupEvent() && event.group() != null) {
            return doSend(robotId, envType,
                    "/v2/groups/" + event.group().groupId() + "/messages", chain);
        } else {
            return doSend(robotId, envType,
                    "/v2/users/" + event.sender().platformUserId() + "/messages", chain);
        }
    }

    @Override
    public SendReceipt send(Target target, MessageChain chain) {
        String envType = "PRODUCTION";
        Long robotId = 0L; // 简化：从 BotManager 查找第一个在线 bot

        String path = switch (target) {
            case Target.Private p -> "/v2/users/" + p.openid() + "/messages";
            case Target.Group g   -> "/v2/groups/" + g.groupOpenid() + "/messages";
            case Target.Guild g   -> "/channels/" + g.channelId() + "/messages";
        };

        return doSend(robotId, envType, path, chain);
    }

    private SendReceipt doSend(Long robotId, String envType, String path, MessageChain chain) {
        ObjectNode payload = QqMessageConverter.toQqPayload(chain);
        long start = System.currentTimeMillis();
        try {
            qqApiService.post(robotId, envType, path, payload);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[QQ消息] 发送成功: path={}, {}ms", path, elapsed);
            return SendReceipt.ok("", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[QQ消息] 发送失败: path={}, error={}", path, e.getMessage());
            return SendReceipt.fail(e.getMessage(), elapsed);
        }
    }
}
