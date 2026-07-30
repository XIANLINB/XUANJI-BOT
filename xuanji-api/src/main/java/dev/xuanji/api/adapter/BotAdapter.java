package dev.xuanji.api.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.message.MessageChain;

/**
 * 平台适配器 — 接入层的契约。
 *
 * <p>每个平台一个实现模块（如 xuanji-adapter-qq）：
 * <ul>
 *   <li>把平台报文转换为统一的 {@link BotEvent}；</li>
 *   <li>把统一的 {@link MessageChain} 转换为平台可发送的报文；</li>
 *   <li>管理底层连接（WS/Webhook/轮询），对核心层透明。</li>
 * </ul>
 *
 * <p>适配器模块通过 {@code @ConditionalOnProperty} 激活，用户加依赖即接入。
 */
public interface BotAdapter {

    /** 平台标识 */
    String platform();

    /** 根据配置建立连接，返回 Bot 实例 */
    Bot connect(BotConfig config);

    /** 断开连接 */
    void disconnect(Bot bot);

    /** 平台原始报文 → 统一事件 */
    BotEvent toEvent(Bot bot, JsonNode rawPayload);

    /** 统一消息链 → 平台可发送的报文 */
    Object toPayload(Bot bot, MessageChain chain);
}
