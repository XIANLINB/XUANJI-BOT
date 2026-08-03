package dev.xuanji.adapter.onebot.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.adapter.onebot.converter.OneBotEventConverter;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotAdapter;
import dev.xuanji.api.adapter.BotConfig;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;
import lombok.extern.slf4j.Slf4j;

/**
 * OneBot v11 适配器 — {@link BotAdapter} 的第二个实现，用于验证核心抽象的平台无关性。
 *
 * <p>设计参考 Shiro 连接 Napcat 的范式：<b>不为具体实现（Napcat / Lagrange / go-cqhttp）
 * 写专门适配器</b>，只认 OneBot v11 标准协议，任何兼容实现都能直接接入。
 *
 * <p>连接由 WS 接入层负责（反向 WS 服务端 + 正向 WS 客户端），
 * 本类聚焦协议转换契约与 bot 实例登记。
 */
@Slf4j
public class OneBotAdapter implements BotAdapter {

    private final OneBotBotManager botManager;
    private final OneBotSessionRegistry sessionRegistry;

    public OneBotAdapter(OneBotBotManager botManager, OneBotSessionRegistry sessionRegistry) {
        this.botManager = botManager;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String platform() {
        return "onebot";
    }

    /**
     * 登记一个 OneBot bot 实例。
     *
     * <p>OneBot 的连接是被动的（反向 WS 等对端来连）或由接入层驱动的（正向 WS），
     * 此处只负责建立实例描述符；appId 为空时表示 selfId 待连接后才确定。
     */
    @Override
    public Bot connect(BotConfig config) {
        String selfId = config.appId() == null || config.appId().isBlank() ? "pending" : config.appId();
        Bot bot = new Bot(OneBotBotManager.botId(selfId), "onebot", selfId,
                Bot.Status.CONNECTING, OneBotBotManager.ONEBOT_CAPABILITIES);
        botManager.register(bot);
        log.info("[OneBot适配器] {} 配置已加载 (key={}, method={})",
                bot.id(), config.key(), config.connectionMethod());
        return bot;
    }

    @Override
    public void disconnect(Bot bot) {
        sessionRegistry.find(bot.selfId()).ifPresent(s -> {
            log.info("[OneBot适配器] 主动断开 {} ({})", bot.id(), s.direction());
            s.close();
        });
        botManager.markOffline(bot.selfId());
    }

    @Override
    public BotEvent toEvent(Bot bot, JsonNode rawPayload) {
        return OneBotEventConverter.convert(bot, rawPayload);
    }

    @Override
    public Object toPayload(Bot bot, MessageChain chain) {
        return OneBotMessageConverter.toSegments(chain);
    }
}
