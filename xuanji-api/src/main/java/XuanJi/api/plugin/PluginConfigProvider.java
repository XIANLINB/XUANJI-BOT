package XuanJi.api.plugin;

import java.util.List;

/**
 * 插件配置声明接口 — 实现后控制台自动生成该插件的配置面板。
 *
 * <p>示例：
 * <pre>{@code
 * public class CheckinPlugin extends XuanJiPluginBase implements PluginConfigProvider {
 *     @Override
 *     public List<PluginConfigField> configSchema() {
 *         return List.of(
 *             new PluginConfigField("coinPerCheckin", "每次签到金币", PluginConfigField.Type.NUMBER, "10", null, "默认 10 金币"),
 *             new PluginConfigField("enableStreak", "连续签到加成", PluginConfigField.Type.BOOLEAN, "true", null, "开启后连续签到有额外奖励"));
 *     }
 * }
 * }</pre>
 *
 * <p>配置值存于框架库 {@code xuanji_plugin_kv}（按插件隔离）；读取优先级：
 * 控制台配置值 &gt; 本 schema 的 defaultValue &gt; 代码调用处的兜底默认值。
 */
public interface PluginConfigProvider {

    /** 声明本插件的全部配置字段（控制台动态表单数据源）。 */
    List<PluginConfigField> configSchema();
}
