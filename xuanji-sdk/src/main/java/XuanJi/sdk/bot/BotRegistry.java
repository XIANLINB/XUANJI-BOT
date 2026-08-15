package XuanJi.sdk.bot;

/**
 * 机器人门面注册表 — 插件按 botKey 取得一个 {@link Bot} 实例，用于<b>非事件场景</b>
 * （{@code @Scheduled} 定时任务、{@code onEnable} 主动推送）的主动发送 / 群管 / 查询。
 *
 * <p>事件驱动的消息链路默认自动携带当前事件的 bot（A 收到 A 发），无需本注册表；
 * 但定时任务、启动时推送没有「当前事件」，插件必须显式指定用哪个机器人
 * （{@link XuanJi.api.annotation.XuanJiPlugin#defaultBot()}），再由本注册表解析成 {@link Bot}。
 *
 * <p>botKey 格式与平台相关：QQ 官方机器人 = {@code qq:<appId>}；onebot = {@code onebot:<selfId>}。
 * 解析不到对应机器人时返回 null（插件应判空或回退默认平台机器人）。
 */
public interface BotRegistry {

    /**
     * 按 botKey 取得一个机器人门面实例。
     *
     * @param botKey 机器人标识（空串 = 由框架挑默认平台第一个机器人）
     * @return 机器人门面；解析失败 / 平台未接入返回 null
     */
    Bot get(String botKey);

    /** 是否存在指定机器人。 */
    boolean has(String botKey);
}
