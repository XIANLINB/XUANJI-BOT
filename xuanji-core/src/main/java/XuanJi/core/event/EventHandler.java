package XuanJi.core.event;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.json.Json;

/**
 * 事件处理器接口
 *
 * <p>定义事件处理的统一契约，每种事件类型对应一个 Handler 实现。
 * 所有事件处理器都实现此接口，并通过 {@link EventDispatcher} 自动注册。
 *
 * <h3>实现方式</h3>
 * <p>使用 Spring 的 {@code @Component} 注解标记实现类，
 * {@link EventDispatcher} 在构造时通过 Spring 的依赖注入收集所有实现。
 *
 * <h3>现有实现</h3>
 * <ul>
 *   <li>{@link XuanJi.core.event.handler.C2cMessageHandler} — 单聊消息</li>
 *   <li>{@link XuanJi.core.event.handler.GroupMessageHandler} — 群聊消息</li>
 *   <li>{@link XuanJi.core.event.handler.GuildMessageHandler} — 频道消息</li>
 *   <li>{@link XuanJi.core.event.handler.DmsMessageHandler} — 频道私信</li>
 *   <li>{@link XuanJi.core.event.handler.FriendEventHandler} — 好友事件</li>
 *   <li>{@link XuanJi.core.event.handler.GroupEventHandler} — 群事件</li>
 *   <li>{@link XuanJi.core.event.handler.AudioEventHandler} — 音频事件</li>
 *   <li>{@link XuanJi.core.event.handler.ForumEventHandler} — 论坛事件</li>
 *   <li>{@link XuanJi.core.event.handler.MessageDeleteHandler} — 消息删除</li>
 *   <li>{@link XuanJi.core.event.handler.SystemEventHandler} — 系统事件</li>
 *   <li>{@link XuanJi.core.event.handler.InteractionEventHandler} — 交互事件</li>
 * </ul>
 *
 * @see EventDispatcher 事件分发器，负责将事件路由到对应的 Handler
 */
public interface EventHandler {

    /**
     * 获取该处理器负责的事件类型
     *
     * <p>返回值作为 {@link EventDispatcher} 的路由 key。
     * 一个处理器可以被注册到多个事件类型（通过 EventDispatcher.registerExtra）。
     *
     * @return 事件类型字符串，如 "C2C_MESSAGE_CREATE"、"GROUP_AT_MESSAGE_CREATE"
     */
    String getEventType();

    /**
     * 处理事件
     *
     * <p>由 {@link EventDispatcher#dispatch} 调用，传入事件数据。
     * 实现类应在此方法中解析 data 并执行业务逻辑。
     *
     * <p>注意：data 中已被 EventDispatcher 注入了两个元数据字段：
     * <ul>
     *   <li>{@code _eventType} — 原始事件类型字符串</li>
     *   <li>{@code _eventId} — 事件 ID（用于去重）</li>
     * </ul>
     *
     * @param robotId 机器人 ID，用于查找配置和调用 API
     * @param envType 环境类型（SANDBOX / PRODUCTION）
     * @param data    事件数据（QQ 平台 Payload 中的 d 字段，已注入元数据）
     */
    void handle(XuanJiEvent event);
}
