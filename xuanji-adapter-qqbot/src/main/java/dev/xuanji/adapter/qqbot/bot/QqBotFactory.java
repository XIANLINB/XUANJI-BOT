package dev.xuanji.adapter.qqbot.bot;

import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.storage.MessageEventRecorder;
import org.springframework.stereotype.Component;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 插件出站 Bot 工厂 — 收口 {@link QqXjBot} / {@link C2cXjBot} 的创建。
 *
 * <p>v3.3（P1 收敛）：handler 不再直接 new XjBot（依赖散落），统一经本工厂注入
 * MessageSender / QqBotRepository / BotOutboundExecutor / MessageEventRecorder 四个依赖，
 * handler 只关心会话坐标（groupId / msgId / appId 等）。
 */
@Component
@ConditionalOnQqbotEnabled
public class QqBotFactory {

    private final MessageSender sender;
    private final QqBotRepository qqRepo;
    private final BotOutboundExecutor outbound;
    private final MessageEventRecorder eventRecorder;

    public QqBotFactory(MessageSender sender, QqBotRepository qqRepo,
                        BotOutboundExecutor outbound, MessageEventRecorder eventRecorder) {
        this.sender = sender;
        this.qqRepo = qqRepo;
        this.outbound = outbound;
        this.eventRecorder = eventRecorder;
    }

    /** 群聊出站 Bot。 */
    public QqXjBot group(String groupId, String msgId, String appId) {
        return new QqXjBot(sender, groupId, msgId, appId, qqRepo, outbound, eventRecorder);
    }

    /** 单聊出站 Bot。 */
    public C2cXjBot c2c(String openid, String msgId, String appId) {
        return new C2cXjBot(sender, openid, msgId, appId, qqRepo, outbound);
    }
}
