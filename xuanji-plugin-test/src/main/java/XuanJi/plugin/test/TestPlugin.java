package XuanJi.plugin.test;

import XuanJi.api.annotation.Arg;
import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.GroupEvent;
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
         * 监听用户申请加群事件（GROUP_JOIN_REQUEST），由<b>插件</b>实现审批。
         *
         * <p>框架只负责把入群申请的完整字段下发给插件（{@link GroupMessageEvent#getJoinRequestInfo()}）：
         * {@code memberOpenid / username / applyAt / applySource / joinRequestId /
         * verifyInfo(原始) / verifyParsed(解析后: method/verifyMessage/question/answer/qaMode)}，
         * 审批判定逻辑由插件自行实现。
         */
        @GroupEvent(order = 10)
        public void onGroupJoinRequest(GroupMessageEvent e, PluginServices svc) {
            if (!"GROUP_JOIN_REQUEST".equals(e.getEventType())) return;
            String groupId = e.getGroupId();
            Map<String, Object> req = e.getJoinRequestInfo();
            System.out.println("[TestPlugin] 收到入群申请事件: 群=" + groupId + ", 完整字段=" + req);
            if (req == null) return;
            String memberId = String.valueOf(req.get("memberOpenid"));
            // 解析后的验证信息（框架已解析：method/verifyMessage/question/answer/qaMode）
            @SuppressWarnings("unchecked")
            Map<String, Object> vp = req.get("verifyParsed") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : java.util.Map.of();
            boolean qaMode = Boolean.TRUE.equals(vp.get("qaMode"));
            String answer = vp.get("answer") == null ? "" : String.valueOf(vp.get("answer"));
            // 插件自定审批规则：无入群问题 → 通过；有问题 → 答案等于 2 才通过
            boolean pass;
            String reason = null;
            if (!qaMode) {
                pass = true; // 未设置入群问题（仅验证消息）→ 自动通过
            } else {
                pass = "2".equalsIgnoreCase(answer.trim());
                if (!pass) reason = "入群问题答案不正确";
            }
            OpResult r = svc.approveGroupJoin(e.getBotId(), groupId, memberId,
                    String.valueOf(req.get("joinRequestId")), pass, reason);
            System.out.println("[TestPlugin] 审批结果: " + (pass ? "通过" : "拒绝") + " → " + r.message());
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
