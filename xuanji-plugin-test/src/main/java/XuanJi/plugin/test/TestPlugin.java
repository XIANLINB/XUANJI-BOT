package XuanJi.plugin.test;

import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.plugin.XuanJiPluginBase;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
         */
        @Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String mute(GroupMessageEvent e, PluginServices svc) {
            List<String> targets = e.getMentionedUserIds();
            if (targets == null || targets.isEmpty()) {
                return "用法：#禁言 @成员 <分钟>，例如：#禁言 @小明 5。仅群主/管理员可用，机器人需为群管理。";
            }
            // 从消息纯文本提取分钟数（QQ 的 @ 占位不含纯数字，提取到的数字即分钟数；默认 10，上限 7 天）
            // muteMember 时长参数为「分钟」，秒级换算由 qqbot 适配器内部完成
            int minutes = 10;
            Matcher m = Pattern.compile("(\\d+)").matcher(e.getPlainText() == null ? "" : e.getPlainText());
            if (m.find()) {
                int v = Integer.parseInt(m.group(1));
                if (v > 0) minutes = Math.min(v, 10080);
            }
            int ok = 0;
            List<String> failed = new ArrayList<>();
            for (String uid : targets) {
                try {
                    if (svc.muteMember(e.getBotId(), e.getGroupId(), uid, minutes)) {
                        ok++;
                    } else {
                        failed.add(uid);
                    }
                } catch (Exception ex) {
                    failed.add(uid);
                }
            }
            StringBuilder sb = new StringBuilder();
            if (ok > 0) {
                sb.append("已禁言 ").append(ok).append(" 人 ").append(minutes).append(" 分钟");
            }
            if (!failed.isEmpty()) {
                if (sb.length() > 0) sb.append("；");
                sb.append("禁言失败 ").append(failed.size()).append(" 人（机器人需为群管理）");
            }
            return sb.toString();
        }
    }
}
