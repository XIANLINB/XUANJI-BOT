package XuanJi.api.plugin;

import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.sender.XuanJiSendReceipt;

import java.util.List;
import java.util.Map;

/**
 * 插件能力门面 — 插件访问框架服务（LLM / 群管 / 主动发送）的统一入口。
 *
 * <p>插件命令方法声明本类型参数即由框架自动注入（与 {@link PluginStorage} 同理）：
 * <pre>
 *   &#64;Command("问") public String ask(GroupMessageEvent e, PluginServices svc) {
 *       return svc.chat(e.getPlainText());
 *   }
 * </pre>
 *
 * <p><b>botKey 参数约定</b>：群管/主动发送需要绑定具体机器人上下文（多机器人时必填，
 * 可用空串让框架回退到第一个机器人）。LLM 对话使用全局配置的默认供应商/模型。
 */
public interface PluginServices {

    // ──────────── LLM 能力 ────────────

    /** 单轮对话（用户消息），使用全局配置默认模型。 */
    String chat(String user);

    /** 带系统指令的单轮对话。 */
    String chat(String system, String user);

    // ──────────── 主动发送 ────────────

    /** 主动向群发送消息链（需绑定 botKey）。 */
    XuanJiSendReceipt sendToGroup(String botKey, String groupOpenid, XuanJiMessage chain);

    /** 主动向私聊发送消息链（需绑定 botKey）。 */
    XuanJiSendReceipt sendToPrivate(String botKey, String openid, XuanJiMessage chain);

    // ──────────── 群管（需绑定 botKey） ────────────

    /**
     * 入群申请审批。
     *
     * @param botKey       机器人标识（空串回退第一个机器人）
     * @param groupOpenid  目标群 openid
     * @param memberOpenid 申请者 openid
     * @param approve      true=同意入群 false=拒绝
     * @param reason       拒绝理由（拒绝时可选）
     * @return 执行结果（含成功提示或失败原因，可面向用户）
     */
    OpResult approveGroupJoin(String botKey, String groupOpenid, String memberOpenid,
                              boolean approve, String reason);

    /**
     * 入群申请列表（平台原始报文转 Map）。
     *
     * @param botKey      机器人标识
     * @param groupOpenid 目标群 openid
     * @return 平台原始 data 字段；平台不支持或失败时返回 null
     */
    Map<String, Object> listGroupJoinRequests(String botKey, String groupOpenid);

    /**
     * 群成员禁言。
     *
     * <p>时长参数为<b>分钟</b>（分钟→秒的换算由平台适配器内部完成，插件无需 ×60）。
     *
     * @param minutes 禁言分钟数（&lt;=0 解除禁言）
     * @return 执行结果（成功含禁言时长/解除提示；失败含具体原因，如
     *         机器人不是群管理、不能禁言群主/管理员/其他机器人、参数不合法等）
     */
    OpResult muteMember(String botKey, String groupOpenid, String memberOpenid, int minutes);

    /**
     * 批量群成员禁言（支持多个目标：可传 {@code Mention} 列表的 userId，
     * 或插件自己收集的 memberOpenid 列表）。
     *
     * <p>每个目标独立执行（一个失败不影响其它），返回汇总结果：
     * 成功 N 人、失败明细（成员 + 原因）。已解除/机器人类目标的过滤由调用方（插件）
     * 按消息字段自行处理，框架只负责执行与结果汇总。
     *
     * @param memberOpenids 目标成员 openid 列表（非空；单个目标请用 {@link #muteMember}）
     * @param minutes       禁言分钟数（&lt;=0 解除禁言）
     * @return 汇总结果（含成功数与失败明细）
     */
    OpResult muteMembers(String botKey, String groupOpenid, List<String> memberOpenids, int minutes);

    /** 撤回群消息。 */
    OpResult recallMessage(String botKey, String groupOpenid, String msgId);

    /**
     * 撤回群内某成员最近 N 条消息（框架负责查库与校验）。
     *
     * <p>框架内部完成：① 校验机器人必须为群管理；② 查该成员最近 {@code count} 条入站消息；
     * ③ 逐条判断是否在 2 分钟撤回窗口内（超时跳过）；④ 撤回成功并标记已撤回。
     *
     * @param count 撤回条数（默认 1，上限 50）
     * @return 汇总结果（成功条数 / 跳过条数与原因，如超 2 分钟、平台拒绝）
     */
    OpResult recallRecentMessages(String botKey, String groupOpenid, String memberOpenid, int count);

    /** 撤回单聊消息。 */
    OpResult recallPrivateMessage(String botKey, String openid, String msgId);

    // ──────────── 平台信息查询 ────────────

    /**
     * 查询群基本信息（平台原始报文转 Map）。
     *
     * @param botKey       机器人标识（空串回退第一个机器人）
     * @param groupOpenid  目标群 openid
     * @return 平台原始 data 字段；平台不支持或失败时返回 null
     */
    Map<String, Object> getGroupInfo(String botKey, String groupOpenid);

    /**
     * 查询群本地档案（查平台库，不调远程接口，避免限频）。
     * 适用于高频场景（如入群/退群提示）；返回 {@code {found, group_name, member_count}}。
     */
    Map<String, Object> getLocalGroupInfo(String botKey, String groupOpenid);

    /**
     * 查询机器人在群内的状态（平台原始报文转 Map）。
     *
     * @param botKey       机器人标识（空串回退第一个机器人）
     * @param groupOpenid  目标群 openid
     * @return 平台原始 data 字段；平台不支持或失败时返回 null
     */
    Map<String, Object> getBotGroupState(String botKey, String groupOpenid);

    /**
     * 查询群禁言状态（restrict_chat_setting 原始报文转 Map）。
     *
     * @return 平台原始 data 字段；平台不支持或失败时返回 null
     */
    Map<String, Object> getGroupMuteStatus(String botKey, String groupOpenid);

    /**
     * 列出群成员（查本地库，不调远程接口，避免限频）。
     *
     * @return 成员档案列表；平台不支持或失败时返回 null
     */
    List<Map<String, Object>> listGroupMembers(String botKey, String groupOpenid);

    /**
     * 列出机器人所在群（查本地库）。
     *
     * @return 群档案列表；平台不支持或失败时返回 null
     */
    List<Map<String, Object>> listGroups(String botKey);

    /**
     * 查询机器人在群内的角色（查本地库，如 member/owner/admin…）。
     *
     * @return 含 role 字段的 Map；平台不支持或失败时返回 null
     */
    Map<String, Object> getGroupBotRole(String botKey, String groupOpenid);

    /**
     * 列出单聊用户（查本地库）。
     *
     * @return 用户档案列表；平台不支持或失败时返回 null
     */
    List<Map<String, Object>> listUsers(String botKey);
}
