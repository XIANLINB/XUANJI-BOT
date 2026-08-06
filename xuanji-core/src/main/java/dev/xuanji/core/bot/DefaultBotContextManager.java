package dev.xuanji.core.bot;

/**
 * 适配器上下文默认兜底实现 — QQ 适配器关闭时由 {@code ObjectProvider.getIfAvailable(DefaultBotContextManager::new)}
 * 构造，避免事件分发因缺少 BotContextManager Bean 而崩溃（纯无操作）。
 */
public class DefaultBotContextManager implements BotContextManager {

    @Override
    public void setCurrentBot(String robotId, String envType) {
        // 无操作：QQ 适配器未启用时没有线程上下文需要维护
    }

    @Override
    public void clearCurrentBot() {
        // 无操作
    }
}
