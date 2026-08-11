package XuanJi.core.plugin;

/**
 * 插件-机器人绑定记录：插件仅对 (platform, botKey) 生效；空列表 = 全局插件（对所有 bot 生效）。
 */
public record PluginBotBinding(String pluginId, String platform, String botKey, long createdAt) {
}
