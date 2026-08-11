package XuanJi.api.llm;

/**
 * 主动搭话发送桥接 —— 平台适配器实现，冷场时机器人主动向群发送消息。
 *
 * <p>与 {@link LlmReplySink}（响应式回复）不同，主动搭话是<b>无事件触发</b>的
 * 主动推送：由 llm 模块的定时扫描判定"该群冷场了"，调用本接口发送。
 * 平台实现负责绑定机器人上下文（如 runWithRobotContext）并发送文本 / markdown。
 */
public interface ProactiveSender {

    /**
     * 主动发送文本消息（含 @ 用户时文本内嵌 {@code <@openid>}）。
     *
     * @param botKey       机器人 key（selfId）
     * @param groupOpenid  目标群 OpenID
     * @param text         文本内容（可含 {@code <@openid>} 提及）
     * @return true=发送成功
     */
    boolean sendText(String botKey, String groupOpenid, String text);

    /**
     * 主动发送 markdown 消息（话题卡片 / 趣味内容）。
     *
     * @param botKey      机器人 key（selfId）
     * @param groupOpenid 目标群 OpenID
     * @param markdown    符合 QQ markdown 协议的原始内容
     * @return true=发送成功
     */
    boolean sendMarkdown(String botKey, String groupOpenid, String markdown);

    /**
     * 主动发送图片字节（图文日报卡片 / 渲染图）。
     *
     * <p>默认实现不支持（返回 false），平台适配器按自身能力覆盖。
     *
     * @param botKey       机器人 key（selfId）
     * @param groupOpenid  目标群 OpenID
     * @param imageBytes   PNG/JPG 图片字节
     * @return true=发送成功
     */
    default boolean sendImageBytes(String botKey, String groupOpenid, byte[] imageBytes) {
        return false;
    }
}
