package XuanJi.plugin.test;

import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.PluginServices;
import XuanJi.api.plugin.XuanJiPluginBase;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

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

    @XuanJiPlugin(id = "test-plugin", name = "接口测试插件", version = "1.0.0",
            author = "XuanJi Team", description = "一键测试群基本信息 / 机器人群内状态接口", rateLimit = 0)
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
    }
}
