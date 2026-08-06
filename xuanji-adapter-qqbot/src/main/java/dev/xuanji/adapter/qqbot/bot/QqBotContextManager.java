package dev.xuanji.adapter.qqbot.bot;

import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.core.bot.BotContextManager;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QqBotContextManager implements BotContextManager {
    private final MessageSender messageSender;

    @Override public void setCurrentBot(String robotId, String envType) {
        messageSender.setCurrentContext(robotId, envType);
    }
    @Override public void clearCurrentBot() {
        messageSender.clearCurrentContext();
    }
}
