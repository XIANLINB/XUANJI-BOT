package dev.xuanji.api.action;

import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.capability.ServiceRegistry;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.sender.MessageSender;

/**
 * 插件上下文 — 插件可访问能力的统一门面与权限收敛点。
 */
public interface PluginContext {

    /** 所属 Bot 实例 */
    Bot bot();

    /** 当前事件（可能为 null，如定时任务触发时） */
    BotContext context();

    /** 消息发送器 */
    MessageSender sender();

    /** 能力注册表 */
    ServiceRegistry services();

    /** 插件自身配置 */
    <T> T config(Class<T> type);
}
