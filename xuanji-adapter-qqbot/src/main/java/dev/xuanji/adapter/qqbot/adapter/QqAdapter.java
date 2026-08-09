package dev.xuanji.adapter.qqbot.adapter;

import tools.jackson.databind.JsonNode;
import dev.xuanji.adapter.qqbot.converter.QqMessageConverter;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotAdapter;
import dev.xuanji.api.adapter.BotConfig;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 官方 Bot API 适配器 — 实现 {@link BotAdapter}。
 *
 * <p>P2 过渡形态：WebSocket 连接仍由 XuanjiBotRunner 旧流程驱动。
 * P3 后接管完整连接生命周期。
 */
@Slf4j
@Component
public class QqAdapter implements BotAdapter {

    @Override
    public String platform() {
        return QqBotManager.PLATFORM;
    }

    @Override
    public Bot connect(BotConfig config) {
        String botId = QqBotManager.botId(config.appId());
        Bot bot = new Bot(botId, QqBotManager.PLATFORM, config.appId(),
                Bot.Status.CONNECTING,
                QqBotManager.QQ_CAPABILITIES);
        log.info("[QQ适配器] {} 配置已加载", botId);
        return bot;
    }

    @Override
    public void disconnect(Bot bot) {
        log.info("[QQ适配器] {} 断开", bot.id());
    }

    @Override
    public BotEvent toEvent(Bot bot, JsonNode rawPayload) {
        throw new UnsupportedOperationException("QQ 事件在 WS/Webhook 内部直接转换");
    }

    @Override
    public Object toPayload(Bot bot, MessageChain chain) {
        return QqMessageConverter.toQqPayload(chain);
    }
}
