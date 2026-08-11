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
     * @return 平台是否支持并执行成功
     */
    boolean approveGroupJoin(String botKey, String groupOpenid, String memberOpenid,
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
     */
    boolean muteMember(String botKey, String groupOpenid, String memberOpenid, int minutes);

    /** 撤回群消息。 */
    boolean recallMessage(String botKey, String groupOpenid, String msgId);

    /** 撤回单聊消息。 */
    boolean recallPrivateMessage(String botKey, String openid, String msgId);

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
