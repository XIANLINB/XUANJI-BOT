package XuanJi.api.adapter;

import java.util.Collection;
import java.util.Optional;

/**
 * XuanJiBot 实例管理器 — 所有 bot 生命周期的统一入口。
 *
 * <p>核心模块提供实现；适配器通过 XuanJiBotManager 注册/启停连接。
 * 每个 XuanJiBot 实例一个虚拟线程，单机可承载万级长连接。
 */
public interface XuanJiBotManager {

    /** 注册 XuanJiBot 实例 */
    void register(XuanJiBot bot);

    /** 移除 XuanJiBot 实例并断开连接 */
    void unregister(String botId);

    /** 按 ID 查找 */
    Optional<XuanJiBot> find(String botId);

    /** 全部已注册实例 */
    Collection<XuanJiBot> all();

    /** 按平台筛选 */
    Collection<XuanJiBot> byPlatform(String platform);

    /** 在线实例数 */
    int onlineCount();
}
