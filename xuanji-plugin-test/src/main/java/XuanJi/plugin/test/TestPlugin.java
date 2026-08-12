package XuanJi.plugin.test;

import XuanJi.api.annotation.Arg;
import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.message.XuanJiMessageElement;
import XuanJi.api.plugin.BotGroupState;
import XuanJi.api.plugin.GroupInfo;
import XuanJi.api.plugin.JoinRequest;
import XuanJi.api.plugin.JoinRequestList;
import XuanJi.api.plugin.OpResult;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.plugin.XuanJiPluginBase;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            GroupInfo groupInfo = svc.getGroupInfo("", groupId);
            BotGroupState botState = svc.getBotGroupState("", groupId);

            String md = buildMarkdown(groupId, groupInfo, botState);
            svc.sendToGroup("", groupId, XuanJiMessage.builder().markdown(md).build());
        }

        private String buildMarkdown(String groupId, GroupInfo groupInfo, BotGroupState botState) {
            return "## 一键接口测试\n\n"
                    + "**群**：`" + groupId + "`\n\n"
                    + "### 群基本信息（原始报文）\n"
                    + codeBlock(groupInfo == null ? null : groupInfo.raw()) + "\n"
                    + "### 机器人群内状态（原始报文）\n"
                    + codeBlock(botState == null ? null : botState.raw());
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
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            if (list.isEmpty()) return "当前无待处理的入群申请";
            StringBuilder sb = new StringBuilder("入群申请列表（" + list.size() + " 条）：\n");
            for (JoinRequest req : list.requests()) {
                sb.append("· ").append(req.username())
                        .append(" (").append(req.memberOpenid()).append(")");
                if (req.isQaMode()) {
                    sb.append(" 问题=").append(req.getQuestion()).append(" 答案=").append(req.getAnswer());
                } else {
                    sb.append(" 验证=").append(req.getVerifyMessage());
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
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            for (JoinRequest req : list.requests()) {
                if (memberOpenid.equals(req.memberOpenid())) {
                    OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(),
                            memberOpenid, req.joinRequestId(), approve, null);
                    return r.ok() ? r.message() : ("审批失败：" + r.message());
                }
            }
            return "未找到该成员的入群申请：" + memberOpenid;
        }

        /** 遍历列表逐条审批（全部同意/全部拒绝）。 */
        private String approveAllBy(PluginServices svc, GroupMessageEvent e, boolean approve) {
            JoinRequestList list = svc.listGroupJoinRequests(e.getBotId(), e.getGroupId());
            if (list.isEmpty()) return "当前无待处理的入群申请";
            int ok = 0;
            StringBuilder sb = new StringBuilder("已" + (approve ? "同意" : "拒绝") + " ");
            for (JoinRequest req : list.requests()) {
                OpResult r = svc.approveGroupJoin(e.getBotId(), e.getGroupId(),
                        req.memberOpenid(), req.joinRequestId(), approve, null);
                if (r.ok()) {
                    ok++;
                } else {
                    sb.append('\n').append(req.username()).append(" 失败: ").append(r.message());
                }
            }
            return sb.toString().replaceFirst("已", "已" + ok + " 人");
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

        /**
         * 内嵌键盘（keyboard）测试①：三种 action 类型 + 四种渲染样式 + 权限。
         *
         * <p>对照官方 {@code keyboard.content.rows[].buttons[]} 结构（原样透传，见
         * {@link XuanJiMessageElement.Keyboard}）：发出一条 Markdown + 键盘消息。
         * <ul>
         *   <li>action.type：0=跳转(URL) / 1=回调(data 回传后台) / 2=指令(输入框插入 @bot data)</li>
         *   <li>render_data.style：0=灰线框 1=蓝线框 2=白字 3=蓝底白字</li>
         *   <li>permission.type：0=指定用户 1=仅管理员 2=所有人</li>
         * </ul>
         */
        @Command(value = "#按钮", scope = Command.Scope.GROUP)
        public String button(GroupMessageEvent e, PluginServices svc) {
            Map<String, Object> row1Btn1 = btn("url_1", "跳转官网", 1,
                    Map.of("type", 0, "data", "https://bot.q.qq.com"));
            Map<String, Object> row1Btn2 = btn("cb_1", "回调", 1,
                    Map.of("type", 1, "data", "btn_cb_1"));
            Map<String, Object> row1Btn3 = btn("cmd_1", "指令签到", 3,
                    Map.of("type", 2, "data", "/签到", "permission", Map.of("type", 2), "enter", true));
            Map<String, Object> row2Btn1 = btn("style_0", "灰线框", 0,
                    Map.of("type", 1, "data", "style_0"));
            Map<String, Object> row2Btn2 = btn("style_2", "白字", 2,
                    Map.of("type", 1, "data", "style_2"));
            Map<String, Object> row2Btn3 = btn("admin_1", "仅管理员", 1,
                    Map.of("type", 1, "data", "admin_only", "permission", Map.of("type", 1)));
            XuanJiMessageElement.Keyboard kb = keyboard(List.of(
                    List.of(row1Btn1, row1Btn2, row1Btn3),
                    List.of(row2Btn1, row2Btn2, row2Btn3)));
            return sendKeyboard(svc, e, "**按钮测试①**（action 跳转/回调/指令 + 样式 0/1/2/3 + 权限）", kb);
        }

        /**
         * 内嵌键盘（keyboard）测试②：visited_label / unsupport_tips / 指定用户权限 / 多行布局。
         *
         * <ul>
         *   <li>visited_label：点击后按钮文字（不传则保持不变）</li>
         *   <li>unsupport_tips：客户端版本过低时的提示文案</li>
         *   <li>permission.type=0 + specify_user_ids：仅指定成员可点</li>
         * </ul>
         */
        @Command(value = "#按钮2", scope = Command.Scope.GROUP)
        public String button2(GroupMessageEvent e, PluginServices svc) {
            Map<String, Object> visitedRender = new LinkedHashMap<>();
            visitedRender.put("label", "点击我");
            visitedRender.put("visited_label", "已点击");
            visitedRender.put("style", 3);
            Map<String, Object> row1Btn1 = Map.of(
                    "id", "visit_1",
                    "render_data", visitedRender,
                    "action", Map.of("type", 1, "data", "visited",
                            "unsupport_tips", "请升级 QQ 后再试"));
            Map<String, Object> row1Btn2 = btn("only_me", "仅我", 1,
                    Map.of("type", 1, "data", "only_me",
                            "permission", Map.of("type", 0, "specify_user_ids", List.of(e.getSenderId()))));
            Map<String, Object> row2Btn1 = btn("enter_cmd", "发送指令", 3,
                    Map.of("type", 2, "data", "#按钮2", "permission", Map.of("type", 2), "enter", true));
            Map<String, Object> row2Btn2 = btn("blue_1", "蓝线框", 1,
                    Map.of("type", 1, "data", "blue_1"));
            XuanJiMessageElement.Keyboard kb = keyboard(List.of(
                    List.of(row1Btn1, row1Btn2),
                    List.of(row2Btn1, row2Btn2)));
            return sendKeyboard(svc, e, "**按钮测试②**（点击后文字 / 版本提示 / 指定成员可点 / 指令按钮）", kb);
        }

        /** 发送 Markdown + 键盘消息；成功回提示，失败打控制台不发群。 */
        private String sendKeyboard(PluginServices svc, GroupMessageEvent e, String md, XuanJiMessageElement.Keyboard kb) {
            XuanJiMessage msg = XuanJiMessage.builder().markdown(md).add(kb).build();
            XuanJiSendReceipt r = svc.sendToGroup(e.getBotId(), e.getGroupId(), msg);
            if (r.success()) return "已发送：" + md;
            System.out.println("[TestPlugin] 按钮消息发送失败: " + r.errorMessage());
            return null;
        }

        /**
         * 内嵌键盘测试③：验证「permission 必传」修复 + 全部渲染样式 + visited_label 对空白的影响。
         *
         * <p>测试结论依据（#按钮/#按钮2 实测）：不带 {@code permission} 的按钮点击一律
         * 「无权限操作」（客户端本地拦截，后端收不到 INTERACTION_CREATE）；必须显式传
         * {@code permission:{type:2}}（所有人）才能点击。
         * 本命令所有按钮都显式带 permission.type=2，用于复测 跳转/回调/指令 三种 action，
         * 并对比 回调按钮有/无 visited_label 的点击后表现。
         */
        @Command(value = "#按钮3", scope = Command.Scope.GROUP)
        public String button3(GroupMessageEvent e, PluginServices svc) {
            Map<String, Object> row1Btn1 = btn("jump_3", "跳转官网", 1,
                    new LinkedHashMap<>() {{
                        put("type", 0);
                        put("data", "https://bot.q.qq.com");
                        put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row1Btn2 = btn("cb_no_visited", "回调(无visited)", 1,
                    new LinkedHashMap<>() {{
                        put("type", 1);
                        put("data", "cb_no_visited");
                        put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> visitedRender = new LinkedHashMap<>();
            visitedRender.put("label", "回调(有visited)");
            visitedRender.put("visited_label", "已点击✓");
            visitedRender.put("style", 3);
            Map<String, Object> row1Btn3 = Map.of(
                    "id", "cb_visited",
                    "render_data", visitedRender,
                    "action", new LinkedHashMap<>() {{
                        put("type", 1);
                        put("data", "cb_visited");
                        put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row2Btn1 = btn("cmd_3", "指令签到", 3,
                    new LinkedHashMap<>() {{
                        put("type", 2);
                        put("data", "/签到");
                        put("permission", Map.of("type", 2));
                        put("enter", true);
                    }});
            Map<String, Object> row2Btn2 = btn("st0_3", "style0灰线", 0,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "st0"); put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row2Btn3 = btn("st1_3", "style1蓝线", 1,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "st1"); put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row3Btn1 = btn("st2_3", "style2白字", 2,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "st2"); put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row3Btn2 = btn("st3_3", "style3蓝底白字", 3,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "st3"); put("permission", Map.of("type", 2));
                    }});
            XuanJiMessageElement.Keyboard kb = keyboard(List.of(
                    List.of(row1Btn1, row1Btn2, row1Btn3),
                    List.of(row2Btn1, row2Btn2, row2Btn3),
                    List.of(row3Btn1, row3Btn2)));
            return sendKeyboard(svc, e, "**按钮测试③**（全部显式 permission=所有人；对比回调 无/有 visited_label）", kb);
        }

        /**
         * 内嵌键盘测试④：权限矩阵对比（同一批按钮分别配置 所有人/管理员/指定用户），
         * 验证权限拦截边界——请分别用群主 与 普通成员 各点一遍。
         *
         * <ul>
         *   <li>所有人按钮：普通成员也应能点（若仍无权限则说明客户端缓存/平台限制）</li>
         *   <li>仅管理员按钮：群主可点、普通成员「无权限操作」</li>
         *   <li>仅我按钮（指定本群命令发送者）：只有发送者本人可点</li>
         *   <li>跳转按钮 + 所有人：验证跳转 URL 在权限修复后是否正常</li>
         *   <li>指令按钮 + enter：插入输入框 @bot data</li>
         * </ul>
         */
        @Command(value = "#按钮4", scope = Command.Scope.GROUP)
        public String button4(GroupMessageEvent e, PluginServices svc) {
            Map<String, Object> row1Btn1 = btn("perm_all", "所有人", 1,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "perm_all"); put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row1Btn2 = btn("perm_admin", "仅管理员", 1,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "perm_admin"); put("permission", Map.of("type", 1));
                    }});
            Map<String, Object> row1Btn3 = btn("perm_me", "仅我", 1,
                    new LinkedHashMap<>() {{
                        put("type", 1); put("data", "perm_me");
                        put("permission", Map.of("type", 0, "specify_user_ids", List.of(e.getSenderId())));
                    }});
            Map<String, Object> row2Btn1 = btn("jump_4", "跳转(所有人)", 1,
                    new LinkedHashMap<>() {{
                        put("type", 0); put("data", "https://bot.q.qq.com/wiki/develop/api-v2/");
                        put("permission", Map.of("type", 2));
                    }});
            Map<String, Object> row2Btn2 = btn("cmd_4", "指令(所有人)", 3,
                    new LinkedHashMap<>() {{
                        put("type", 2); put("data", "#按钮4"); put("permission", Map.of("type", 2)); put("enter", true);
                    }});
            XuanJiMessageElement.Keyboard kb = keyboard(List.of(
                    List.of(row1Btn1, row1Btn2, row1Btn3),
                    List.of(row2Btn1, row2Btn2)));
            return sendKeyboard(svc, e, "**按钮测试④**（权限矩阵：所有人/仅管理员/仅我，请用群主+普通成员各点一遍）", kb);
        }

        /** 构造单个按钮（render_data + action）。 */
        private static Map<String, Object> btn(String id, String label, int style, Map<String, Object> action) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", id);
            b.put("render_data", Map.of("label", label, "style", style));
            if (action != null) b.put("action", action);
            return b;
        }

        /** 把多行按钮包装成官方 keyboard 结构（content.rows[].buttons[]）。 */
        private static XuanJiMessageElement.Keyboard keyboard(List<List<Map<String, Object>>> rows) {
            List<Map<String, Object>> rowList = new ArrayList<>();
            for (List<Map<String, Object>> row : rows) {
                rowList.add(Map.of("buttons", row));
            }
            return new XuanJiMessageElement.Keyboard(Map.of("content", Map.of("rows", rowList)));
        }
    }
}
