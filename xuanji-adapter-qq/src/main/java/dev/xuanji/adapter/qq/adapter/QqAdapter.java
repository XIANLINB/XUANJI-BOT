package dev.xuanji.adapter.qq.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.adapter.qq.converter.QqEventConverter;
import dev.xuanji.adapter.qq.converter.QqMessageConverter;
import dev.xuanji.adapter.qq.websocket.QqBotWsManager;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.adapter.BotAdapter;
import dev.xuanji.api.adapter.BotConfig;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * QQ 官方 Bot API 适配器 — 实现 {@link BotAdapter}，封装 WebSocket / Webhook 连接管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QqAdapter implements BotAdapter {

    private final QqBotWsManager wsManager;

    @Override
    public String platform() {
        return "qq";
    }

    @Override
    public Bot connect(BotConfig config) {
        String botId = "qq:" + config.appId();
        Bot bot = new Bot(botId, "qq", config.appId(),
                Bot.Status.CONNECTING,
                Set.of("can_recall", "can_ban", "can_set_card"));

        if ("webhook".equalsIgnoreCase(config.connectionMethod())) {
            log.info("[QQ适配器] {} Webhook 模式，等待平台推送", botId);
        } else {
            // WebSocket 模式
            Long robotId = (long) config.appId().hashCode();
            String envType = config.sandbox() ? "SANDBOX" : "PRODUCTION";
            wsManager.registerRobot(robotId, envType, config.appId(), config.secret(), 0);
            wsManager.start(robotId, envType);
            log.info("[QQ适配器] {} WebSocket 连接已启动", botId);
        }

        return bot;
    }

    @Override
    public void disconnect(Bot bot) {
        log.info("[QQ适配器] {} 断开连接", bot.id());
        // WS 管理器内部处理断开
    }

    @Override
    public BotEvent toEvent(Bot bot, JsonNode rawPayload) {
        // 由 QqEventConverter 处理，适配器入口调用时直接抛给核心
        throw new UnsupportedOperationException("QQ 事件由 WS/Webhook 内部直接转换");
    }

    @Override
    public Object toPayload(Bot bot, MessageChain chain) {
        return QqMessageConverter.toQqPayload(chain);
    }
}
