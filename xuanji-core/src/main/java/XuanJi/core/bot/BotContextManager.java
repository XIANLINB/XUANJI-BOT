package XuanJi.core.bot;

/**
 * 适配器上下文管理器 — 注入到 EventDispatcher，避免 core 直接依赖 QQ 适配器。
 */
public interface BotContextManager {
    void setCurrentBot(String robotId, String envType);
    void clearCurrentBot();
}
