package XuanJi.adapter.qqbot.adapter;

import tools.jackson.databind.JsonNode;
import XuanJi.adapter.qqbot.converter.QqMessageConverter;
import XuanJi.api.adapter.XuanJiBot;
import XuanJi.api.adapter.BotAdapter;
import XuanJi.api.adapter.BotConfig;
import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.message.XuanJiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 官方 XuanJiBot API 适配器 — 实现 {@link BotAdapter}。
 *
 * <p>P2 过渡形态：WebSocket 连接仍由 XuanJiBotRunner 旧流程驱动。
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
    public XuanJiBot connect(BotConfig config) {
        String botId = QqBotManager.botId(config.appId());
        XuanJiBot bot = new XuanJiBot(botId, QqBotManager.PLATFORM, config.appId(),
                XuanJiBot.Status.CONNECTING,
                QqBotManager.QQ_CAPABILITIES);
        log.info("[QQ适配器] {} 配置已加载", botId);
        return bot;
    }

    @Override
    public void disconnect(XuanJiBot bot) {
        log.info("[QQ适配器] {} 断开", bot.id());
    }

    @Override
    public XuanJiEvent toEvent(XuanJiBot bot, JsonNode rawPayload) {
        throw new UnsupportedOperationException("QQ 事件在 WS/Webhook 内部直接转换");
    }

    @Override
    public Object toPayload(XuanJiBot bot, XuanJiMessage chain) {
        return QqMessageConverter.toQqPayload(chain);
    }
}
