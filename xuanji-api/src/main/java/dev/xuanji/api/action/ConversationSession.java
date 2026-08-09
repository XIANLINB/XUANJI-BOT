package dev.xuanji.api.action;

import java.time.Duration;

/**
 * 多轮会话状态存储 — 让插件实现"连续追问"式交互。
 *
 * <p>与旧的阻塞式 {@code awaitResponse} 不同，本实现为<b>状态机式</b>：
 * 不阻塞事件线程，插件在命令处理中存状态，后续消息到达时读取状态决定下一步。
 *
 * <h3>用法示例（订会议室流程）</h3>
 * <pre>{@code
 *   @Command("订会议室")
 *   public String book(ConversationSession session, Bot bot) {
 *     // 第一步：发提示并开始会话
 *     bot.sendGroupText("哪天开会？(如 明天上午)");
 *     session.begin("booking", Map.of("step", 1));
 *     return null;
 *   }
 *
 *   @Command("订会议室")  // 同一个命令在用户回复后再次进入
 *   public String bookNext(ConversationSession session) {
 *     Map st = session.get("booking", Map.class);
 *     if (st == null) return null;      // 不在流程中
 *     session.end("booking");           // 流程结束
 *     return "已预订";
 *   }
 * }</pre>
 *
 * <p>会话键 = 当前 {@code botKey + groupId(单聊为 userId) + userId}，
 * 由 {@code CommandRegistry} 自动从事件上下文解析，插件无需关心。
 */
public interface ConversationSession {

    /** 会话默认过期时间（无回复自动清除，防内存泄漏）。 */
    Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * 开始一个多轮会话（覆盖同流程的旧状态）。状态自动在 {@link #DEFAULT_TTL} 后过期。
     *
     * @param flow  流程名（如 "booking"），同用户可并行多个流程
     * @param state 任意状态对象（JSON 可序列化更佳）
     */
    void begin(String flow, Object state);

    /** 当前用户是否正处在该流程中（未过期）。 */
    boolean active(String flow);

    /** 取会话状态；不存在或已过期返回 null。 */
    <T> T get(String flow, Class<T> type);

    /** 更新会话状态（延长过期时间）。 */
    <T> void update(String flow, T state);

    /** 结束会话并清理。 */
    void end(String flow);

    /** 该流程剩余有效秒数（未开始返回 0）。 */
    long ttlSeconds(String flow);
}
