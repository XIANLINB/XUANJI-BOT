package XuanJi.plugin.groupnotify;

import XuanJi.api.annotation.*;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.*;
import XuanJi.sdk.bot.Bot;
import XuanJi.sdk.event.GroupMessageEvent;
import org.pf4j.PluginWrapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 入群退群提示插件。
 *
 * <p>群内命令（仅 owner/admin，默认作用于当前群）：
 * <pre>
 *   开启入群提示 | 关闭入群提示
 *   开启退群提示 | 关闭退群提示
 *   设置入群提示语 [文本|markdown] &lt;内容&gt;
 *   设置退群提示语 [文本|markdown] &lt;内容&gt;
 *   查看入群提示语 | 查看退群提示语
 *   删除入群提示语 | 删除退群提示语
 * </pre>
 * 变量：{user_name} {user_id} {user_role} {group_name} {group_member_count} {time}
 */
public class GroupNotifyPlugin extends XuanJiPluginBase {

    public GroupNotifyPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanJiPlugin(id = "groupnotify-plugin", name = "入群退群提示", version = "1.0.0",
            author = "XuanJi Team", description = "入群/退群提示，群内命令配置提示语（文本/markdown，按群保存）", rateLimit = 0)
    public static class Commands implements PluginConfigProvider {

        @Override
        public List<PluginConfigField> configSchema() {
            return List.of(
                    new PluginConfigField("welcomeText", "入群提示语", PluginConfigField.Type.STRING,
                            "欢迎 {user_name} 加入群聊！", null, "新成员入群时发送（{user_name} 等变量可用）"),
                    new PluginConfigField("leaveText", "退群提示语", PluginConfigField.Type.STRING,
                            "{user_name} 退出了群聊", null, "成员退群时发送"));
        }

        private static final String FEATURE_WELCOME = "welcome";
        private static final String FEATURE_LEAVE = "leave";

        // ==================== key 与工具 ====================

        /** 配置 key（按机器人 + 群独立保存）。 */
        private static String key(String feature, String part, String botId, String groupId) {
            return "notify." + feature + "." + part + ":" + botId + ":" + groupId;
        }

        private static String botId(Bot bot) {
            return bot != null && bot.selfId() != null ? bot.selfId() : "";
        }

        /** 解析设置命令内容：可选格式词前缀（markdown/文本），剩余为提示语内容。 */
        private record Parsed(String format, String text) {}

        private static Parsed parseContent(String raw) {
            String s = raw == null ? "" : raw.trim();
            String format = "text";
            if (s.startsWith("markdown ")) {
                format = "markdown";
                s = s.substring("markdown".length()).trim();
            } else if (s.startsWith("markdown")) {
                format = "markdown";
                s = s.substring("markdown".length()).trim();
            } else if (s.startsWith("文本") || s.startsWith("text")) {
                format = "text";
                s = s.substring(s.startsWith("文本") ? "文本".length() : "text".length()).trim();
            }
            return new Parsed(format, s);
        }

        private static boolean enabled(PluginStorage store, String feature, String botId, String groupId) {
            return "1".equals(store.getString(key(feature, "enabled", botId, groupId), "0"));
        }

        private static Map.Entry<String, String> resolveContent(PluginStorage store, String feature, String botId, String groupId) {
            String content = store.getString(key(feature, "content", botId, groupId), null);
            if (content == null || content.isBlank()) return null;
            String format = store.getString(key(feature, "format", botId, groupId), "text");
            return Map.entry(content, format);
        }

        private static String render(String tpl, GroupMessageEvent e, String groupName, String memberCount) {
            String out = tpl;
            out = out.replace("{user_name}", e.getSenderName() == null ? "" : e.getSenderName());
            out = out.replace("{username}", e.getSenderName() == null ? "" : e.getSenderName());
            out = out.replace("{user_id}", e.getSenderId() == null ? "" : e.getSenderId());
            out = out.replace("{userid}", e.getSenderId() == null ? "" : e.getSenderId());
            out = out.replace("{user_role}", e.getSenderRole() == null ? "member" : e.getSenderRole());
            out = out.replace("{group_id}", e.getGroupId() == null ? "" : e.getGroupId());
            out = out.replace("{groupid}", e.getGroupId() == null ? "" : e.getGroupId());
            out = out.replace("{group_name}", groupName == null ? "" : groupName);
            out = out.replace("{groupname}", groupName == null ? "" : groupName);
            out = out.replace("{group_member_count}", memberCount == null ? "" : memberCount);
            out = out.replace("{member_count}", memberCount == null ? "" : memberCount);
            out = out.replace("{time}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(Instant.now().atZone(ZoneId.systemDefault())));
            out = out.replace("{at}", e.getSenderId() == null ? "" : "<@" + e.getSenderId() + ">");
            return out;
        }

        // ==================== 开启/关闭 ====================

        @Command(value = "开启入群提示", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String enableWelcome(GroupMessageEvent e, Bot bot, PluginStorage store) {
            store.set(key(FEATURE_WELCOME, "enabled", botId(bot), e.getGroupId()), "1");
            return "已开启本群入群提示";
        }

        @Command(value = "关闭入群提示", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String disableWelcome(GroupMessageEvent e, Bot bot, PluginStorage store) {
            store.set(key(FEATURE_WELCOME, "enabled", botId(bot), e.getGroupId()), "0");
            return "已关闭本群入群提示";
        }

        @Command(value = "开启退群提示", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String enableLeave(GroupMessageEvent e, Bot bot, PluginStorage store) {
            store.set(key(FEATURE_LEAVE, "enabled", botId(bot), e.getGroupId()), "1");
            return "已开启本群退群提示";
        }

        @Command(value = "关闭退群提示", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String disableLeave(GroupMessageEvent e, Bot bot, PluginStorage store) {
            store.set(key(FEATURE_LEAVE, "enabled", botId(bot), e.getGroupId()), "0");
            return "已关闭本群退群提示";
        }

        // ==================== 设置提示语 ====================

        @Command(value = "设置入群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String setWelcome(GroupMessageEvent e, Bot bot, PluginStorage store,
                                 @Arg(value = "内容", required = true, rest = true) String content) {
            Parsed p = parseContent(content);
            String botId = botId(bot);
            store.set(key(FEATURE_WELCOME, "content", botId, e.getGroupId()), p.text());
            store.set(key(FEATURE_WELCOME, "format", botId, e.getGroupId()), p.format());
            return "已设置本群入群提示语（" + p.format() + "）：" + p.text();
        }

        @Command(value = "设置退群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String setLeave(GroupMessageEvent e, Bot bot, PluginStorage store,
                               @Arg(value = "内容", required = true, rest = true) String content) {
            Parsed p = parseContent(content);
            String botId = botId(bot);
            store.set(key(FEATURE_LEAVE, "content", botId, e.getGroupId()), p.text());
            store.set(key(FEATURE_LEAVE, "format", botId, e.getGroupId()), p.format());
            return "已设置本群退群提示语（" + p.format() + "）：" + p.text();
        }

        // ==================== 查看 ====================

        @Command(value = "查看入群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String viewWelcome(GroupMessageEvent e, Bot bot, PluginStorage store) {
            String botId = botId(bot);
            Map.Entry<String, String> c = resolveContent(store, FEATURE_WELCOME, botId, e.getGroupId());
            if (c == null) return "本群入群提示语未设置（使用插件配置默认值）";
            boolean on = enabled(store, FEATURE_WELCOME, botId, e.getGroupId());
            return "本群入群提示语（" + c.getValue() + " / " + (on ? "开启" : "关闭") + "）：" + c.getKey();
        }

        @Command(value = "查看退群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String viewLeave(GroupMessageEvent e, Bot bot, PluginStorage store) {
            String botId = botId(bot);
            Map.Entry<String, String> c = resolveContent(store, FEATURE_LEAVE, botId, e.getGroupId());
            if (c == null) return "本群退群提示语未设置（使用插件配置默认值）";
            boolean on = enabled(store, FEATURE_LEAVE, botId, e.getGroupId());
            return "本群退群提示语（" + c.getValue() + " / " + (on ? "开启" : "关闭") + "）：" + c.getKey();
        }

        // ==================== 删除 ====================

        @Command(value = "删除入群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String delWelcome(GroupMessageEvent e, Bot bot, PluginStorage store) {
            String botId = botId(bot);
            store.set(key(FEATURE_WELCOME, "content", botId, e.getGroupId()), "");
            store.set(key(FEATURE_WELCOME, "format", botId, e.getGroupId()), "text");
            return "已删除本群入群提示语";
        }

        @Command(value = "删除退群提示语", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String delLeave(GroupMessageEvent e, Bot bot, PluginStorage store) {
            String botId = botId(bot);
            store.set(key(FEATURE_LEAVE, "content", botId, e.getGroupId()), "");
            store.set(key(FEATURE_LEAVE, "format", botId, e.getGroupId()), "text");
            return "已删除本群退群提示语";
        }

        // ==================== 事件：成员入群/退群 → 发送提示 ====================

        @GroupEvent(order = 30)
        public void onMemberEvent(GroupMessageEvent e, PluginServices svc, PluginStorage store, PluginConfig cfg) {
            String feature;
            if ("GROUP_MEMBER_ADD".equals(e.getEventType())) feature = FEATURE_WELCOME;
            else if ("GROUP_MEMBER_REMOVE".equals(e.getEventType())) feature = FEATURE_LEAVE;
            else return;

            String botId = e.getBotId();
            // 本群未开启 → 不发
            if (!enabled(store, feature, botId, e.getGroupId())) return;

            Map.Entry<String, String> resolved = resolveContent(store, feature, botId, e.getGroupId());
            String content;
            String format = "text";
            if (resolved != null) {
                content = resolved.getKey();
                format = resolved.getValue() == null ? "text" : resolved.getValue();
            } else {
                // 本群未设置提示语 → 用插件配置面板默认值
                content = cfg.getString(FEATURE_WELCOME.equals(feature) ? "welcomeText" : "leaveText", "");
            }
            if (content == null || content.isBlank()) return;

            // 群信息（名称 + 成员数）— 查本地库，不调平台 API（避免限频）
            String groupName = "";
            String memberCount = "";
            try {
                GroupInfo info = svc.getLocalGroupInfo(botId, e.getGroupId());
                if (info != null && info.found()) {
                    groupName = info.groupName();
                    memberCount = info.memberCount() == null ? "" : String.valueOf(info.memberCount());
                }
            } catch (Exception ignored) { }

            String rendered = render(content, e, groupName, memberCount);
            try {
                XuanJiMessage msg = "markdown".equals(format)
                        ? XuanJiMessage.builder().markdown(rendered).build()
                        : XuanJiMessage.text(rendered);
                // 必须用事件所属机器人（botId）发送，传空 botKey 会回退到第一个机器人导致 11255（取错机器）
                svc.sendToGroup(botId, e.getGroupId(), msg);
            } catch (Exception ex) {
                // 静默失败（不阻塞事件流）
            }
        }

        // ===== 帮助 =====
        @Command(value = "提示帮助", scope = Command.Scope.GROUP)
        public String help(Bot bot) {
            return """
                入群退群提示插件（仅 owner/admin，作用于当前群）:
                开启入群提示 | 关闭入群提示
                开启退群提示 | 关闭退群提示
                设置入群提示语 [文本|markdown] <内容>
                设置退群提示语 [文本|markdown] <内容>
                查看入群提示语 | 查看退群提示语
                删除入群提示语 | 删除退群提示语
                变量：{user_name} {user_id} {user_role} {group_name} {group_member_count} {time}
                例：设置入群提示语 markdown 欢迎 {user_name} 加入 {group_name}（当前群 {group_member_count} 人）
                """;
        }
    }
}
