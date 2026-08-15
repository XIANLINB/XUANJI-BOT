package XuanJi.api.plugin;

/**
 * 插件能力门面 — 插件访问框架服务（仅 LLM 对话）的统一入口。
 *
 * <p>群管 / 主动发送 / 平台查询等能力已统一收敛到 {@link XuanJi.sdk.bot.Bot} 门面，
 * 插件命令方法声明 {@code Bot} 参数即可由框架自动注入（携带当前事件上下文）。
 *
 * <p>插件命令方法声明本类型参数即由框架自动注入（与 {@link PluginStorage} 同理）：
 * <pre>
 *   &#64;Command("问") public String ask(GroupMessageEvent e, PluginServices svc) {
 *       return svc.chat(e.getPlainText());
 *   }
 * </pre>
 */
public interface PluginServices {

    /** 单轮对话（用户消息），使用全局配置默认模型。 */
    String chat(String user);

    /** 带系统指令的单轮对话。 */
    String chat(String system, String user);
}
