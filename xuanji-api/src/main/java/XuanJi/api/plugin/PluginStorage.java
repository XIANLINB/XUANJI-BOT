package XuanJi.api.plugin;

/**
 * 插件持久化存储 — {@code @Command}/{@code @GroupEvent} 等方法声明本类型参数时由框架自动注入。
 *
 * <p>存储按插件 id 隔离（落框架库 {@code xuanji_plugin_kv} 表），
 * 不同插件互不可见；同一插件的群聊/私聊/多个命令共享同一命名空间。
 *
 * <p>示例：
 * <pre>{@code
 * @Command("签到")
 * public String sign(GroupMessageEvent e, PluginStorage store) {
 *     long total = store.getLong("total:" + e.getSenderId(), 0);
 *     store.set("total:" + e.getSenderId(), String.valueOf(total + 1));
 *     return "累计签到 " + (total + 1) + " 次";
 * }
 * }</pre>
 */
public interface PluginStorage {

    /** 读字符串，key 不存在返回 defaultValue。 */
    String getString(String key, String defaultValue);

    /** 读长整型，key 不存在或值非法返回 defaultValue。 */
    long getLong(String key, long defaultValue);

    /** 读整型，key 不存在或值非法返回 defaultValue。 */
    int getInt(String key, int defaultValue);

    /** 读布尔（"true"/"1" 视为真），key 不存在返回 defaultValue。 */
    boolean getBoolean(String key, boolean defaultValue);

    /** 写入（覆盖）。value 为 null 时等价于 remove。 */
    void set(String key, String value);

    /** 删除指定 key。 */
    void remove(String key);
}
