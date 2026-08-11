package XuanJi.plugin.test;

import XuanJi.api.annotation.Arg;
import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.plugin.XuanJiPluginBase;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

import java.util.List;
import java.util.Map;

/**
 * 接口测试插件 — 一键测试 QQ 群基本信息和机器人群内状态两个接口。
 *
 * <p>发送「一键测试」命令后，依次调用框架暴露的
 * {@link PluginServices#getGroupInfo} / {@link PluginServices#getBotGroupState}，
 * 把两个接口的<b>原始报文</b>以 markdown 格式返回（两个代码块）。
 *
 * <pre>
 *   @Command("一键测试") → markdown：
 *     ## 一键接口测试
 *     ### 群基本信息（原始报文）
 *     ```json { ... } ```
 *     ### 机器人群内状态（原始报文）
 *     ```json { ... } ```
 * </pre>
 */
public class TestPlugin extends XuanJiPluginBase {

    public TestPlugin(PluginWrapper wrapper) { super(wrapper); }

    @Override public void onEnable() { System.out.println("[Test] onEnable()"); }
    @Override public void onDisable() { System.out.println("[Test] onDisable()"); }

    @XuanJiPlugin(id = "test-plugin", name = "接口测试插件", version = "1.1.0",
            author = "XuanJi Team", description = "一键测试群基本信息 / 机器人群内状态 / 群成员禁言接口", rateLimit = 0)
    public static class Commands {

        /**
         * 一键测试：调用群信息 + 机器人群内状态两个接口，返回 markdown（两个原始报文代码块）。
         */
        @Command(value = "一键测试", scope = Command.Scope.GROUP)
        public void oneClickTest(GroupMessageEvent e, PluginServices svc) {
            String groupId = e.getGroupId();
            Map<String, Object> groupInfo = svc.getGroupInfo("", groupId);
            Map<String, Object> botState = svc.getBotGroupState("", groupId);

            String md = buildMarkdown(groupId, groupInfo, botState);
            svc.sendToGroup("", groupId, XuanJiMessage.builder().markdown(md).build());
        }

        private String buildMarkdown(String groupId, Map<String, Object> groupInfo, Map<String, Object> botState) {
            return "## 一键接口测试\n\n"
                    + "**群**：`" + groupId + "`\n\n"
                    + "### 群基本信息（原始报文）\n"
                    + codeBlock(groupInfo) + "\n"
                    + "### 机器人群内状态（原始报文）\n"
                    + codeBlock(botState);
        }

        /**
         * 入群申请审批命令组（管理侧手动操作）：
         * <ul>
         *   <li>{@code #查看入群申请列表} — 拉取并展示全部待处理申请</li>
         *   <li>{@code #同意 <member_openid>} — 同意指定成员入群</li>
         *   <li>{@code #拒绝 <member_openid>} — 拒绝指定成员入群</li>
         *   <li>{@code #全部同意} — 遍历列表逐条同意</li>
         *   <li>{@code #全部拒绝} — 遍历列表逐条拒绝</li>
         * </ul>
         * 仅群主/管理员可用；机器人需为群管理。审批的 join_request_id 由框架从列表查询补全。
         */
        @Command(value = "#查看入群申请列表", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String listJoinRequests(GroupMessageEvent e, PluginServices svc) {
            Map<String, Object> resp = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            List<Map<String, Object>> list = extractJoinRequests(resp);
            if (list.isEmpty()) return "当前无待处理的入群申请";
            StringBuilder sb = new StringBuilder("入群申请列表（" + list.size() + " 条）：\n");
            for (Map<String, Object> item : list) {
                sb.append("· ").append(String.valueOf(item.get("username")))
                        .append(" (").append(String.valueOf(item.get("member_openid"))).append(")");
                Object vp = item.get("_verify_parsed");
                if (vp instanceof Map<?, ?> parsed) {
                    sb.append(" 验证=").append(parsed.get("verifyMessage"));
                    if (Boolean.TRUE.equals(parsed.get("qaMode"))) {
                        sb.append(" 答案=").append(parsed.get("answer"));
                    }
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        @Command(value = "#同意", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String approveOne(GroupMessageEvent e, PluginServices svc,
                                 @Arg(value = "member_openid", required = true) String memberOpenid) {
            return approveByMember(e, svc, memberOpenid, true);
        }

        @Command(value = "#拒绝", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String rejectOne(GroupMessageEvent e, PluginServices svc,
                                @Arg(value = "member_openid", required = true) String memberOpenid) {
            return approveByMember(e, svc, memberOpenid, false);
        }

        @Command(value = "#全部同意", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String approveAll(GroupMessageEvent e, PluginServices svc) {
            return approveAllBy(svc, e, true);
        }

        @Command(value = "#全部拒绝", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String rejectAll(GroupMessageEvent e, PluginServices svc) {
            return approveAllBy(svc, e, false);
        }

        /** 单个成员审批：从列表查出该成员的申请（含 join_request_id）再审批。 */
        private String approveByMember(GroupMessageEvent e, PluginServices svc, String memberOpenid, boolean approve) {
            Map<String, Object> resp = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            for (Map<String, Object> item : extractJoinRequests(resp)) {
                if (memberOpenid.equals(String.valueOf(item.get("member_openid")))) {
                    String reqId = String.valueOf(item.get("join_request_id"));
                    OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(), memberOpenid, reqId, approve, null);
                    return r.ok() ? r.message() : ("审批失败：" + r.message());
                }
            }
            return "未找到该成员的入群申请：" + memberOpenid;
        }

        /** 遍历列表逐条审批（全部同意/全部拒绝）。 */
        private String approveAllBy(PluginServices svc, GroupMessageEvent e, boolean approve) {
            Map<String, Object> resp = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            List<Map<String, Object>> list = extractJoinRequests(resp);
            if (list.isEmpty()) return "当前无待处理的入群申请";
            int ok = 0;
            StringBuilder sb = new StringBuilder("已" + (approve ? "同意" : "拒绝") + " ");
            for (Map<String, Object> item : list) {
                String member = String.valueOf(item.get("member_openid"));
                String reqId = String.valueOf(item.get("join_request_id"));
                OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(), member, reqId, approve, null);
                if (r.ok()) {
                    ok++;
                } else {
                    sb.append('\n').append(String.valueOf(item.get("username"))).append(" 失败: ").append(r.message());
                }
            }
            return sb.toString().replaceFirst("已", "已" + ok + " 人");
        }

        /** 从 listGroupJoinRequests 返回中提取申请列表（兼容 {data:{list}} 与 {list} 两种结构）。 */
        private static List<Map<String, Object>> extractJoinRequests(Map<String, Object> resp) {
            if (resp == null) return List.of();
            @SuppressWarnings("unchecked")
            Object listObj = resp.get("data") instanceof Map<?, ?> dm ? dm.get("list") : resp.get("list");
            if (listObj instanceof List<?> list) {
                List<Map<String, Object>> out = new java.util.ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) m;
                        out.add(mm);
                    }
                }
                return out;
            }
            return List.of();
        }

        /** 原始报文 → ```json 代码块；null 时给出平台不支持/查询失败提示。 */
        private String codeBlock(Map<String, Object> raw) {
            if (raw == null) {
                return "```text\n(返回 null — 群不存在 / 接口失败 / 平台不支持)\n```";
            }
            try {
                String json = Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(raw);
                return "```json\n" + json + "\n```";
            } catch (Exception ex) {
                return "```text\n(序列化失败: " + ex.getMessage() + ")\n```";
            }
        }

        /**
         * 群成员禁言：调用设置群成员禁言接口（restrict_chat_setting）。
         * 仅群主/管理员可用（@Command roles）；机器人需为群管理，否则平台返回失败。
         *
         * <p>用法：{@code #禁言 @成员 <分钟>}，例如「#禁言 @小明 5」。未指定分钟默认 10，上限 7 天。
         * 两种触发形式等价：{@code #禁言@成员 1} / {@code @机器人 #禁言@成员 1}
         * （命令匹配用的 plainText 已自动剥掉所有 {@code @占位}，数字由 {@link Arg} 解析）。
         *
         * <p><b>框架负责解析与过滤</b>：
         * <ul>
         *   <li>目标成员：{@link GroupMessageEvent#getMentionedUserIds()} 已排除机器人/自己，插件拿到即"可操作目标"</li>
         *   <li>时长参数：{@link Arg} 自动从命令后文本解析（缺省 null 走默认值），无需手写正则</li>
         *   <li>群主/管理员等平台限制由适配器执行时校验并返回失败原因</li>
         * </ul>
         */
        @Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String mute(GroupMessageEvent e, PluginServices svc,
                           @Arg(value = "分钟", required = false) Integer minutes) {
            // 框架已过滤：getMentionedUserIds() 不含机器人与机器人自己
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) {
                return "用法：#禁言 @成员 <分钟>，例如：#禁言 @小明 5。仅群主/管理员可用，机器人需为群管理。";
            }
            // @Arg 解析的分钟数（缺省 10，上限 7 天）；秒级换算由 qqbot 适配器内部完成
            int m = minutes == null ? 10 : Math.min(Math.max(minutes, 0), 10080);
            // 批量禁言：成功/失败原因由框架与适配器提供（如非群管理、不能禁言群主/管理员等）
            OpResult r = svc.muteGroupMembers(e.getBotId(), e.getGroupId(), targets, m);
            // 失败信息不发送到 QQ 群（给框架开发者看日志）；成功才回群
            if (r.ok()) return r.message();
            System.out.println("[TestPlugin] 禁言结果: " + r.message());
            return null;
        }

        /**
         * 撤回群内某成员最近 N 条消息。
         * 仅群主/管理员可用（@Command roles）；机器人需为群管理，否则框架返回失败原因。
         *
         * <p>用法：{@code #撤回 @成员 [条数]}，例如「#撤回 @小明 3」= 撤回其最近 3 条消息；
         * 不带条数默认撤回最近 1 条。条数由 {@link Arg} 解析（可选）。
         * 框架层完成：权限校验（群管理）、查该成员最近消息、2 分钟窗口判断、逐条撤回并汇总。
         */
        @Command(value = "#撤回", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String recall(GroupMessageEvent e, PluginServices svc,
                             @Arg(value = "条数", required = false) Integer count) {
            // 框架已过滤：getMentionedUserIds() 不含机器人与机器人自己
            List<String> targets = e.getMentionedUserIds();
            if (targets.isEmpty()) {
                return "用法：#撤回 @成员 [条数]，例如：#撤回 @小明 3（默认撤回最近 1 条）。仅群主/管理员可用，机器人需为群管理。";
            }
            // 不带条数走框架默认（撤回最近 1 条）；带条数由框架执行批量撤回
            OpResult r = count == null
                    ? svc.recallRecentMessages(e.getBotId(), e.getGroupId(), targets.get(0))
                    : svc.recallRecentMessages(e.getBotId(), e.getGroupId(), targets.get(0), Math.min(Math.max(count, 1), 50));
            // 失败信息不发送到 QQ 群（给框架开发者看日志）；成功才回群
            if (r.ok()) return r.message();
            System.out.println("[TestPlugin] 撤回结果: " + r.message());
            return null;
        }
    }
}
