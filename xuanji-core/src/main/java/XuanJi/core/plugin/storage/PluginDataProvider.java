package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.PluginData;

/**
 * 插件结构化存储的编排入口：负责在插件加载期扫描实体、建表/迁移、登记元数据，
 * 并按 {@code pluginId} 提供隔离的 {@link PluginData}（供命令/定时任务参数注入）。
 *
 * <p>一切以 {@code pluginId} 为信任边界：同一插件返回的 {@link PluginRepository} 只能访问
 * 自己前缀的表，无法触碰框架表或其它插件表。
 */
public interface PluginDataProvider {

    /**
     * 扫描插件 classpath 上的 @PluginEntity、建表/迁移、登记元数据，并返回该插件的
     * {@link PluginData}。单插件声明 >1 个实体时抛异常（拒绝加载）。
     *
     * @param pluginId 插件 id（来自 wrapper.getPluginId()，信任边界）
     * @param pluginClassLoader 插件类加载器（仅扫自身 jar）
     */
    PluginData scanAndRegister(String pluginId, ClassLoader pluginClassLoader);

    /** 取已注册的 {@link PluginData}（注入用）；未注册返回 null。 */
    PluginData get(String pluginId);

    /** 卸载/停用清理（移除内存中的元数据缓存）。 */
    void unregister(String pluginId);
}
