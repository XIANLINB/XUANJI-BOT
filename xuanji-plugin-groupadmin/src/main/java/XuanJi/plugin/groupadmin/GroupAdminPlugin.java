package XuanJi.plugin.groupadmin;

import XuanJi.api.annotation.Arg;
import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.GroupMessage;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.plugin.JoinRequest;
import XuanJi.api.plugin.JoinRequestList;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.plugin.PluginStorage;
import XuanJi.api.plugin.XuanJiPluginBase;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

import java.util.List;

/**
 * 群管插件（正式版）— 禁言 / 撤回 / 针对撤回 / 入群申请审批。
 *
 * <p>命令（仅群主/管理员可用，机器人需为群管理）：
 * <ul>
 *   <li>{@code #群管帮助} — 查看全部命令用法</li>
 *   <li>{@code #禁言 @用户 <分钟>} — 禁言指定用户（缺省 10 分钟，0 解除，上限 7 天）</li>
 *   <li>{@code #解禁 @用户} — 解除用户禁言</li>
 *   <li>{@code #撤回 @用户 [条数]} — 撤回该用户最近 N 条消息（缺省 1，上限 50）</li>
 *   <li>{@code #针对撤回 @用户} — 该用户每次发消息都自动撤回</li>
 *   <li>{@code #解除针对 @用户} — 解除针对撤回</li>
 *   <li>{@code #入群申请列表} — 查看待处理入群申请（含入群问题/答案）</li>
 *   <li>{@code #同意 @用户|<openid>} — 同意该用户入群</li>
 *   <li>{@code #拒绝 @用户|<openid> [理由]} — 拒绝该用户入群（可选理由）</li>
 *   <li>{@code #全部同意} / {@code #全部拒绝} — 批量处理全部待审申请</li>
 * </ul>
 *
 * <p>框架职责（插件无需关心）：目标成员过滤（getMentionedUserIds 已排除机器人/自己）、
 * 分钟语义换算、2 分钟撤回窗口、join_request_id 补全、权限/失败原因透传。
 */
public class GroupAdminPlugin extends XuanJiPluginBase {

    /** 针对撤回名单 key 前缀（PluginStorage 按插件隔离）：target-recall:{groupId}:{userId} → "1" */
    private static final String TARGET_RECALL_PREFIX = "target-recall:";

    public GroupAdminPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override public void onEnable() { System.out.println("[GroupAdmin] 群管插件已启用"); }
    @Override public void onDisable() { System.out.println("[GroupAdmin] 群管插件已停用"); }

    @XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",
            author = "XuanJi Team", description = "群管理：禁言 / 撤回 / 针对撤回 / 入群申请审批", rateLimit = 0)
    public static class Commands {

        // ═══════════════ 帮助 ═══════════════

        /** 用法：#群管帮助 — 返回全部命令说明。 */
        @Command(value = "#群管帮助", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String help() {
            return "#群管帮助 — 查看帮助\n"
                    + "#禁言 @用户 <分钟> — 禁言（缺省10，0解除，上限7天）\n"
                    + "#解禁 @用户 — 解除禁言\n"
                    + "#撤回 @用户 [条数] — 撤回最近N条（缺省1，上限50）\n"
                    + "#针对撤回 @用户 — 该用户每次发消息自动撤回\n"
                    + "#解除针对 @用户 — 解除针对撤回\n"
                    + "#入群申请列表 — 查看入群申请\n"
                    + "#同意 @用户|<openid> — 同意入群\n"
                    + "#拒绝 @用户|<openid> [理由] — 拒绝入群\n"
                    + "#全部同意 / #全部拒绝 — 批量处理";
        }

        // ═══════════════ 禁言 / 解禁 ═══════════════

        /** 用法：#禁言 @用户 <分钟>（缺省 10，0 解除，上限 7 天）。 */
        @Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String mute(GroupMessageEvent e, PluginServices svc,
                           @Arg(value = "分钟", required = false) Integer minutes) {
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) return "用法：#禁言 @用户 <分钟>，例如：#禁言 @小明 1";
            int m = minutes == null ? 10 : Math.min(Math.max(minutes, 0), 10080);
            OpResult r = svc.muteGroupMembers(e.getBotId(), e.getGroupId(), targets, m);
            if (!r.ok()) { System.out.println("[GroupAdmin] 禁言失败: " + r.message()); return null; }
            return r.message();
        }

        /** 用法：#解禁 @用户 — 解除禁言（禁言 0 分钟）。 */
        @Command(value = "#解禁", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String unmute(GroupMessageEvent e, PluginServices svc) {
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) return "用法：#解禁 @用户";
            OpResult r = svc.muteGroupMembers(e.getBotId(), e.getGroupId(), targets, 0);
            if (!r.ok()) { System.out.println("[GroupAdmin] 解禁失败: " + r.message()); return null; }
            return r.message();
        }

        // ═══════════════ 撤回 / 针对撤回 ═══════════════

        /** 用法：#撤回 @用户 [条数]（缺省 1，上限 50）。 */
        @Command(value = "#撤回", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String recall(GroupMessageEvent e, PluginServices svc,
                             @Arg(value = "条数", required = false) Integer count) {
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) return "用法：#撤回 @用户 [条数]，例如：#撤回 @小明 1";
            OpResult r = count == null
                    ? svc.recallRecentMessages(e.getBotId(), e.getGroupId(), targets.get(0))
                    : svc.recallRecentMessages(e.getBotId(), e.getGroupId(), targets.get(0),
                            Math.min(Math.max(count, 1), 50));
            if (!r.ok()) { System.out.println("[GroupAdmin] 撤回失败: " + r.message()); return null; }
            return r.message();
        }

        /** 用法：#针对撤回 @用户 — 该用户每次发消息都自动撤回（持久化名单）。 */
        @Command(value = "#针对撤回", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String targetRecall(GroupMessageEvent e, PluginStorage store) {
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) return "用法：#针对撤回 @用户";
            int n = 0;
            for (String uid : targets) {
                store.set(TARGET_RECALL_PREFIX + e.getGroupId() + ":" + uid, "1");
                n++;
            }
            return "已对 " + n + " 人开启针对撤回，其每条消息将被自动撤回";
        }

        /** 用法：#解除针对 @用户 — 解除针对撤回。 */
        @Command(value = "#解除针对", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String untargetRecall(GroupMessageEvent e, PluginStorage store) {
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) return "用法：#解除针对 @用户";
            int n = 0;
            for (String uid : targets) {
                store.remove(TARGET_RECALL_PREFIX + e.getGroupId() + ":" + uid);
                n++;
            }
            return "已解除 " + n + " 人的针对撤回";
        }

        /** 消息监听：命中针对撤回名单的用户，其每条消息自动撤回。 */
        @GroupMessage(order = 200)
        public void onGroupMessage(GroupMessageEvent e, PluginServices svc, PluginStorage store) {
            if (e.getEventType() != null && !e.getEventType().isBlank()) return; // 系统事件跳过
            String key = TARGET_RECALL_PREFIX + e.getGroupId() + ":" + e.getSenderId();
            if ("1".equals(store.getString(key, null))) {
                OpResult r = svc.recallGroupMessage(e.getBotId(), e.getGroupId(), e.getMessageId());
                System.out.println("[GroupAdmin] 针对撤回: " + e.getSenderId() + " -> " + (r.ok() ? "已撤回" : r.message()));
            }
        }

        // ═══════════════ 入群申请 ═══════════════

        /** 用法：#入群申请列表 — 列出待处理申请。 */
        @Command(value = "#入群申请列表", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String listRequests(GroupMessageEvent e, PluginServices svc) {
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            if (list.isEmpty()) return "当前无待处理的入群申请";
            StringBuilder sb = new StringBuilder("入群申请（" + list.size() + " 条）：\n");
            for (JoinRequest req : list.requests()) {
                sb.append("· ").append(req.username()).append(" (").append(req.memberOpenid()).append(")");
                if (req.isQaMode()) {
                    sb.append(" 问题=").append(req.getQuestion()).append(" 答案=").append(req.getAnswer());
                } else {
                    sb.append(" 验证=").append(req.getVerifyMessage());
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        /** 用法：#同意 @用户 | <openid>。 */
        @Command(value = "#同意", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String approve(GroupMessageEvent e, PluginServices svc,
                              @Arg(value = "openid", required = false) String openid) {
            String target = resolveTarget(e, openid);
            if (target == null) return "用法：#同意 @用户 或 #同意 <member_openid>，可先 #入群申请列表 查看";
            return approveByMember(e, svc, target, true, null);
        }

        /** 用法：#拒绝 @用户 | <openid> [理由]。 */
        @Command(value = "#拒绝", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String reject(GroupMessageEvent e, PluginServices svc,
                             @Arg(value = "openid", required = false) String openid,
                             @Arg(value = "理由", required = false, rest = true) String reason) {
            String target = resolveTarget(e, openid);
            if (target == null) return "用法：#拒绝 @用户 或 #拒绝 <member_openid> [理由]，可先 #入群申请列表 查看";
            return approveByMember(e, svc, target, false, reason);
        }

        /** 用法：#全部同意。 */
        @Command(value = "#全部同意", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String approveAll(GroupMessageEvent e, PluginServices svc) {
            return approveAllBy(e, svc, true);
        }

        /** 用法：#全部拒绝。 */
        @Command(value = "#全部拒绝", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String rejectAll(GroupMessageEvent e, PluginServices svc) {
            return approveAllBy(e, svc, false);
        }

        /** 目标解析：优先取 @ 到的成员，否则用命令参数 openid。 */
        private static String resolveTarget(GroupMessageEvent e, String openid) {
            List<String> t = e.getMentionedUserIds();
            return t.isEmpty() ? openid : t.get(0);
        }

        /** 单个成员审批：从列表查出该成员申请（补全 join_request_id）再审批。 */
        private static String approveByMember(GroupMessageEvent e, PluginServices svc,
                                              String memberOpenid, boolean approve, String reason) {
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            for (JoinRequest req : list.requests()) {
                if (memberOpenid.equals(req.memberOpenid())) {
                    OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(),
                            memberOpenid, req.joinRequestId(), approve, reason);
                    if (r.ok()) return r.message();
                    System.out.println("[GroupAdmin] 审批失败: " + r.message());
                    return null;
                }
            }
            return "未找到该成员的入群申请：" + memberOpenid;
        }

        /** 遍历列表逐条审批（全部同意/全部拒绝）。 */
        private static String approveAllBy(GroupMessageEvent e, PluginServices svc, boolean approve) {
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            if (list.isEmpty()) return "当前无待处理的入群申请";
            int ok = 0;
            StringBuilder sb = new StringBuilder();
            for (JoinRequest req : list.requests()) {
                OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(),
                        req.memberOpenid(), req.joinRequestId(), approve, null);
                if (r.ok()) ok++;
                else sb.append('\n').append(req.username()).append(" 失败: ").append(r.message());
            }
            return "已" + (approve ? "同意" : "拒绝") + " " + ok + " 人" + sb;
        }
    }
}
