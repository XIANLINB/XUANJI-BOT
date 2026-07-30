package dev.xuanji.api.context;

import dev.xuanji.api.event.BotEvent;

/**
 * 机器人上下文 — 基于 JDK 25 ScopedValue 的事件载体。
 *
 * <p>事件处理期间绑定，任意位置的插件代码可通过 {@link #current()} 获取当前事件，
 * 无需层层传参。处理完成后随作用域自动释放。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   ScopedValue.where(BotContext.currentEvent, event).run(() -> {
 *       BotContext ctx = BotContext.current();
 *       // ctx.event / ctx.user / ctx.group / ctx.bot
 *   });
 * }</pre>
 */
public final class BotContext {

    /**
     * 当前事件绑定的 ScopedValue。
     *
     * <p>事件入口由框架绑定，插件直接调用静态方法即可。
     */
    public static final java.lang.ScopedValue<BotEvent> currentEvent = java.lang.ScopedValue.newInstance();

    private BotContext() {}

    /** 取当前上下文的 BotEvent */
    public static BotEvent current() {
        return currentEvent.get();
    }

    /** 便捷：当前 bot 实例 */
    public static dev.xuanji.api.adapter.Bot bot() {
        return current().bot();
    }

    /** 便捷：当前发送者 */
    public static dev.xuanji.api.event.XuanjiUser user() {
        return current().sender();
    }

    /** 便捷：当前群组（私聊返回 null） */
    public static dev.xuanji.api.event.XuanjiGroup group() {
        return current().group();
    }
}
