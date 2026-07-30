package dev.xuanji.api.adapter;

import java.util.Collection;
import java.util.Optional;

/**
 * Bot 实例管理器 — 所有 bot 生命周期的统一入口。
 *
 * <p>核心模块提供实现；适配器通过 BotManager 注册/启停连接。
 * 每个 Bot 实例一个虚拟线程，单机可承载万级长连接。
 */
public interface BotManager {

    /** 注册 Bot 实例 */
    void register(Bot bot);

    /** 移除 Bot 实例并断开连接 */
    void unregister(String botId);

    /** 按 ID 查找 */
    Optional<Bot> find(String botId);

    /** 全部已注册实例 */
    Collection<Bot> all();

    /** 按平台筛选 */
    Collection<Bot> byPlatform(String platform);

    /** 在线实例数 */
    int onlineCount();
}
