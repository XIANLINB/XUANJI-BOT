package XuanJi.adapter.qqbot.bot;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.sender.XuanJiMessageSender;
import XuanJi.core.concurrent.BotOutboundExecutor;
import XuanJi.core.storage.MessageEventRecorder;
import org.springframework.stereotype.Component;
import XuanJi.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 插件出站 XuanJiBot 工厂 — 收口 {@link QqXjBot} / {@link C2cXjBot} 的创建。
 *
 * <p>v3.3（P1 收敛）：handler 不再直接 new XjBot（依赖散落），统一经本工厂注入
 * MessageSender / QqBotRepository / BotOutboundExecutor / MessageEventRecorder /
 * PlatformActionHub（统一动作协议）/ XuanJiMessageSender（主动发送出口）六个依赖，
 * handler 只关心会话坐标（groupId / msgId / appId 等）。
 */
@Component
@ConditionalOnQqbotEnabled
public class QqBotFactory {

    private final MessageSender sender;
    private final QqBotRepository qqRepo;
    private final BotOutboundExecutor outbound;
    private final MessageEventRecorder eventRecorder;
    private final PlatformActionHub actionHub;
    private final XuanJiMessageSender messageSender;

    public QqBotFactory(MessageSender sender, QqBotRepository qqRepo,
                        BotOutboundExecutor outbound, MessageEventRecorder eventRecorder,
                        PlatformActionHub actionHub, XuanJiMessageSender messageSender) {
        this.sender = sender;
        this.qqRepo = qqRepo;
        this.outbound = outbound;
        this.eventRecorder = eventRecorder;
        this.actionHub = actionHub;
        this.messageSender = messageSender;
    }

    /** 群聊出站 XuanJiBot。 */
    public QqXjBot group(String groupId, String msgId, String appId) {
        return new QqXjBot(sender, groupId, msgId, appId, qqRepo, outbound, eventRecorder, actionHub, messageSender);
    }

    /** 单聊出站 XuanJiBot。 */
    public C2cXjBot c2c(String openid, String msgId, String appId) {
        return new C2cXjBot(sender, openid, msgId, appId, qqRepo, outbound, actionHub, messageSender);
    }

    /**
     * 主动/定时任务用的「无事件」机器人门面（无 groupId / msgId）。
     *
     * <p>用于插件 {@code @Scheduled} 定时任务、{@code onEnable} 主动推送等<b>非事件场景</b>：
     * 主动发送（sendGroup/sendPrivate/sendToGroup/sendToPrivate）需插件声明
     * {@code PROACTIVE_MESSAGE} 权限（运行时闸门 {@link QqXjBot#setProactiveAllowed} 由注入方设置）；
     * 被动回复（reply*）因无事件坐标会被静默忽略。
     */
    public QqXjBot proactive(String appId) {
        return new QqXjBot(sender, null, null, appId, qqRepo, outbound, eventRecorder, actionHub, messageSender);
    }
}
