package dev.xuanji.adapter.onebot.adapter;

import tools.jackson.databind.JsonNode;
import dev.xuanji.adapter.onebot.adapter.OneBotBotManager;
import dev.xuanji.adapter.onebot.converter.OneBotEventConverter;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotAdapter;
import dev.xuanji.api.adapter.BotConfig;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotAdapter
implements BotAdapter {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotAdapter.class);
    private final OneBotBotManager botManager;
    private final OneBotSessionRegistry sessionRegistry;

    public OneBotAdapter(OneBotBotManager botManager, OneBotSessionRegistry sessionRegistry) {
        this.botManager = botManager;
        this.sessionRegistry = sessionRegistry;
    }

    public String platform() {
        return "onebot";
    }

    public Bot connect(BotConfig config) {
        String selfId = config.appId() == null || config.appId().isBlank() ? "pending" : config.appId();
        Bot bot = new Bot(OneBotBotManager.botId(selfId), "onebot", selfId, Bot.Status.CONNECTING, OneBotBotManager.ONEBOT_CAPABILITIES);
        this.botManager.register(bot);
        log.info("[OneBot\u9002\u914d\u5668] {} \u914d\u7f6e\u5df2\u52a0\u8f7d (key={}, method={})", new Object[]{bot.id(), config.key(), config.connectionMethod()});
        return bot;
    }

    public void disconnect(Bot bot) {
        this.sessionRegistry.find(bot.selfId()).ifPresent(s -> {
            log.info("[OneBot\u9002\u914d\u5668] \u4e3b\u52a8\u65ad\u5f00 {} ({})", (Object)bot.id(), (Object)s.direction());
            s.close();
        });
        this.botManager.markOffline(bot.selfId());
    }

    public BotEvent toEvent(Bot bot, JsonNode rawPayload) {
        return OneBotEventConverter.convert(bot, rawPayload);
    }

    public Object toPayload(Bot bot, MessageChain chain) {
        return OneBotMessageConverter.toSegments(chain);
    }
}

