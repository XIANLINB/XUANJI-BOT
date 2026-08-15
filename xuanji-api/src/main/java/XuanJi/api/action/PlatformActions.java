package XuanJi.api.action;

/**
 * 平台统一动作名常量表 — 框架与插件只认这些动作名，不感知平台实现。
 *
 * <p>动作协议：{@code dispatch(botKey, action, params) → {"ok":bool, "data":…|"error":…}}。
 * 各平台适配器（qqbot/onebot/…）在启动时向 {@link PlatformActionHub} 注册同名动作、
 * 各自实现（如 QQ 的 {@code GET /v2/groups/{id}/info} vs onebot 的 {@code get_group_info}）。
 * 新增平台能力 = 适配器内部多注册一个动作，<b>框架与插件零改动</b>。
 *
 * <p>动作名用两级命名空间 {@code 域.动作}（如 {@code group.info}），字符串开放扩展、不做死枚举。
 */
public final class PlatformActions {

    // ──────────── 群信息 ────────────

    /** 群基本信息。参数：groupOpenid。返回：平台原始 data。 */
    public static final String GROUP_INFO = "group.info";

    /** 群本地档案（查库，不调平台 API，避免限频）。参数：groupOpenid。返回：{found, group_name, member_count}。 */
    public static final String GROUP_LOCAL_INFO = "group.local_info";

    /** 机器人在群内的状态。参数：groupOpenid。返回：平台原始 data（仅支持方实现）。 */
    public static final String GROUP_BOT_STATE = "group.bot_state";

    // ──────────── 群管动作 ────────────

    /** 群成员禁言。参数：groupOpenid、memberOpenid、seconds（&lt;=0 解除禁言）。 */
    public static final String GROUP_MUTE = "group.mute";

    /** 入群申请审批。参数：groupOpenid、memberOpenid、approve(bool)、reason(可选)。 */
    public static final String GROUP_APPROVE = "group.approve";

    /** 撤回群消息。参数：groupOpenid、msgId。 */
    public static final String GROUP_RECALL = "group.recall";

    /** 撤回群内某成员最近 N 条消息。参数：groupOpenid、memberOpenid、count(默认1)。 */
    public static final String GROUP_RECALL_RECENT = "group.recall_recent";

    /** 撤回单聊消息。参数：openid、msgId。返回：平台原始 data。 */
    public static final String GROUP_RECALL_PRIVATE = "group.recall_private";

    /** 入群申请列表。参数：groupOpenid、start(可选)、limit(可选)。返回：平台原始 data。 */
    public static final String GROUP_JOIN_REQUEST_LIST = "group.join_request_list";

    /**
     * 踢出群成员。参数：groupOpenid、memberOpenid。
     * <b>注意：QQ 官方机器人开放接口不支持该能力</b>，QQ 适配器注册为「能力不支持 → 明确失败」。
     */
    public static final String GROUP_KICK = "group.kick";

    /** 设置群成员名片（昵称）。参数：groupOpenid、memberOpenid、card。QQ 开放接口不支持 → 降级失败。 */
    public static final String GROUP_SET_CARD = "group.set_card";

    /** 设置/取消群管理员。参数：groupOpenid、memberOpenid、setAdmin(bool)。QQ 开放接口不支持 → 降级失败。 */
    public static final String GROUP_SET_ADMIN = "group.set_admin";

    /** 发布群公告。参数：groupOpenid、text（公告内容）、imageUrl(可选)。QQ 开放接口不支持 → 降级失败。 */
    public static final String GROUP_ANNOUNCEMENT = "group.announcement";

    // ──────────── 好友 / 用户 ────────────

    /** 好友申请审批。参数：openid、approve(bool)、reason(可选)。 */
    public static final String FRIEND_APPROVE = "friend.approve";

    /** 查询单用户资料。参数：openid。返回：平台原始 data（如昵称、头像）。 */
    public static final String USER_INFO = "user.info";

    // ──────────── 群查询（已实现未暴露） ────────────

    /** 群禁言状态（restrict_chat_setting）。参数：groupOpenid。返回：平台原始 data。 */
    public static final String GROUP_MUTE_STATUS = "group.mute_status";

    /** 群成员列表（查本地库，免限频）。参数：groupOpenid。返回：{data:[…]}。 */
    public static final String GROUP_MEMBER_LIST = "group.member_list";

    /** 机器人所在群列表（查本地库）。参数：无。返回：{data:[…]}。 */
    public static final String GROUP_LIST = "group.list";

    /** 机器人在群内角色（查本地库，如 member/owner/admin）。参数：groupOpenid。返回：{data:{role}}。 */
    public static final String GROUP_BOT_ROLE = "group.bot_role";

    /** 单聊用户列表（查本地库）。参数：无。返回：{data:[…]}。 */
    public static final String USER_LIST = "user.list";

    // ──────────── 机器人级动作 ────────────

    /** 生成机器人分享链接（邀请用户添加机器人为好友）。参数：callbackData(可选,≤32字符)。返回：{urlLink}。 */
    public static final String GENERATE_SHARE_LINK = "bot.generate_share_link";

    private PlatformActions() {}
}
