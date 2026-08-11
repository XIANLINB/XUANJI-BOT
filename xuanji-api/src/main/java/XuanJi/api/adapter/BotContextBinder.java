package XuanJi.api.adapter;

/**
 * 机器人上下文绑定 SPI — 让核心/能力层在<b>无事件上下文</b>时也能主动向指定机器人发消息/执行动作。
 *
 * <p>各平台适配器实现（如 qqbot 的 {@code MessageSender.runWithRobotContext}）：
 * 在任务执行期间把当前线程的机器人上下文切换为 {@code botKey} 对应的实例，
 * 使 {@link XuanJiMessageSender} 的主动发送/动作方法能解析到正确的 token 与环境。
 */
public interface BotContextBinder {

    /** 在指定机器人上下文内执行任务（结束后自动还原上下文）。 */
    void runWith(String botKey, Runnable task);
}
