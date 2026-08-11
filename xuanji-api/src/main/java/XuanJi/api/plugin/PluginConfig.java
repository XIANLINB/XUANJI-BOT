package XuanJi.api.plugin;

/**
 * 插件配置读取 — {@code @Command}/{@code @GroupEvent} 等方法声明本类型参数时由框架自动注入。
 *
 * <p>读取优先级：控制台配置值（{@code xuanji_plugin_kv}） &gt;
 * 插件 {@link PluginConfigProvider} 声明的默认值 &gt; 代码调用处的兜底默认值。
 *
 * <p>示例：
 * <pre>{@code
 * @Command("签到")
 * public String sign(GroupMessageEvent e, PluginConfig cfg) {
 *     long coins = cfg.getLong("coinPerCheckin", 10); // 控制台可改，默认 10
 *     ...
 * }
 * }</pre>
 */
public interface PluginConfig {

    /** 读字符串配置，无配置时依次回退 schema 默认值、defaultValue。 */
    String getString(String key, String defaultValue);

    /** 读长整型配置。 */
    long getLong(String key, long defaultValue);

    /** 读整型配置。 */
    int getInt(String key, int defaultValue);

    /** 读布尔配置（"true"/"1" 视为真）。 */
    boolean getBoolean(String key, boolean defaultValue);
}
