package XuanJi.core.plugin;

import java.util.List;

/**
 * 插件-机器人绑定服务 — 控制台插件管理页调用。
 *
 * <p>空列表 = 全局插件；绑定后插件仅对指定 (platform, botKey) 生效。
 */
public interface PluginBotBindingService {

    /** 列出某插件的全部绑定记录。 */
    List<PluginBotBinding> list(String pluginId);

    /** 绑定插件到指定 (platform, botKey)；已存在则幂等覆盖。 */
    void bind(String pluginId, String platform, String botKey);

    /** 解绑；解绑全部记录后恢复全局生效。 */
    void unbind(String pluginId, String platform, String botKey);

    /** 删除某插件的全部绑定（卸载插件时清数据）。 */
    void deleteAll(String pluginId);

    /**
     * 判断某插件是否对指定 (platform, botKey) 生效：
     * 无绑定记录 = 全局生效（true）；有绑定 = 仅命中 (platform, botKey) 才 true。
     */
    boolean isAllowedForBot(String pluginId, String platform, String botKey);
}
