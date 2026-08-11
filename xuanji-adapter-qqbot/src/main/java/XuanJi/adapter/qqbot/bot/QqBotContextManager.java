package XuanJi.adapter.qqbot.bot;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.core.bot.BotContextManager;
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
