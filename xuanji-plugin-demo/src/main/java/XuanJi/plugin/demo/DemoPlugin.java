package XuanJi.plugin.demo;

import XuanJi.api.annotation.*;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.plugin.*;
import XuanJi.sdk.bot.Bot;
import XuanJi.sdk.event.GroupMessageEvent;
import XuanJi.sdk.event.MessageEvent;
import XuanJi.sdk.event.PrivateMessageEvent;
import org.pf4j.PluginWrapper;

import java.util.List;
import java.util.Map;

/**
 * 璇玑演示插件 v2.0 — 插件开发全能力测试用例。
 *
 * <p>覆盖：命令（群聊/单聊/两者）、参数注入（{@code @Arg} 含 rest 剩余全部）、角色权限、
 * {@code AtMode}（@ 才触发）、媒体消息、持久化 {@link PluginStorage}、配置面板 {@link PluginConfigProvider}、
 * 收到消息（{@code @GroupMessage} / {@code @PrivateMessage} / {@code @OnMessage}）、
 * 收到事件（{@code @GroupEvent} / {@code @PrivateEvent}）、框架能力 {@link PluginServices}、
 * 富媒体构造 {@link XuanJiMessage}、生命周期钩子。
 *
 * <p>用法：群/私聊发「演示帮助」查看全部命令；源码即最佳教程。
 */
public class DemoPlugin extends XuanJiPluginBase {

    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @Override public void onEnable()  { System.out.println("[DemoPlugin] onEnable() — 插件启用"); }
    @Override public void onDisable() { System.out.println("[DemoPlugin] onDisable() — 插件停用"); }

    @XuanJiPlugin(id = "demo-plugin", name = "演示插件", version = "2.0.0",
            author = "XuanJi Team", description = "插件开发全能力测试用例（命令/事件/参数/存储/配置/服务/富媒体）", rateLimit = 0)
    public static class Commands implements PluginConfigProvider {

        // ============ 配置面板（控制台「插件管理 → 配置」动态生成表单） ============
        @Override
        public List<PluginConfigField> configSchema() {
            return List.of(
                    new PluginConfigField("enableCheckin", "开启签到", PluginConfigField.Type.BOOLEAN,
                            "true", null, "关闭后签到命令不生效"),
                    new PluginConfigField("coinPerCheckin", "每次签到金币", PluginConfigField.Type.NUMBER,
                            "10", null, "签到奖励金币数"),
                    new PluginConfigField("welcomeWord", "欢迎词", PluginConfigField.Type.STRING,
                            "欢迎新成员！", null, "群成员进群时自动发送（事件演示）"));
        }

        // ============ 1. 群聊命令（scope=GROUP） ============
        @Command(value = "群聊命令", scope = Command.Scope.GROUP)
        public String groupOnly(GroupMessageEvent e) {
            return "这是【群聊专属】命令，单聊里发无效。当前群：" + e.getGroupId();
        }

        // ============ 2. 单聊命令（scope=PRIVATE） ============
        @Command(value = "私聊命令", scope = Command.Scope.PRIVATE)
        public String privateOnly(PrivateMessageEvent e) {
            return "这是【单聊专属】命令，群里发无效。你的 ID：" + e.getSenderId();
        }

        // ============ 3. 群聊 + 单聊通用命令（scope=BOTH） ============
        @Command(value = "ping", scope = Command.Scope.BOTH)
        public String ping(Bot bot) {
            return "pong！当前机器人：" + bot.selfId();
        }

        // ============ 4. 参数注入：rest 剩余全部（含空格） ============
        @Command(value = "回声", scope = Command.Scope.BOTH)
        public String echo(@Arg(value = "内容", required = true, rest = true) String content) {
            return "你说的是：" + content;
        }

        // ============ 5. 可选参数（缺省不报错） ============
        @Command(value = "问候", scope = Command.Scope.BOTH)
        public String greet(@Arg(value = "名字", required = false) String name) {
            return name == null || name.isBlank() ? "你好！" : "你好，" + name + "！";
        }

        // ============ 6. 角色权限（owner/admin 才能用） ============
        @Command(value = "管理员命令", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public String adminOnly(GroupMessageEvent e) {
            return "你有管理员权限！你的群角色：" + e.getSenderRole();
        }

        // ============ 7. @ 才触发（AtMode.NEED） ============
        @Command(value = "@我", scope = Command.Scope.GROUP, at = AtMode.NEED)
        public String needAt(GroupMessageEvent e) {
            return "你 @ 了我！你的 ID：" + e.getSenderId();
        }

        // ============ 8. 持久化 PluginStorage（按群独立签到） ============
        @Command(value = "签到", scope = Command.Scope.GROUP)
        public String sign(GroupMessageEvent e, PluginStorage store, PluginConfig cfg) {
            if (!cfg.getBoolean("enableCheckin", true)) return "签到已由管理员关闭（配置面板演示）";
            String uid = e.getSenderId();
            long coins = store.getLong("coins:" + uid, 0) + cfg.getInt("coinPerCheckin", 10);
            store.set("coins:" + uid, String.valueOf(coins));
            return "签到成功，你已累计 " + coins + " 金币（PluginStorage 持久化演示）";
        }

        // ============ 9. 框架能力 PluginServices（主动发群消息/查群信息） ============
        @Command(value = "机器人信息", scope = Command.Scope.BOTH)
        public String botInfo(MessageEvent e, PluginServices svc, Bot bot) {
            if (!(e instanceof GroupMessageEvent g)) return "该命令需要在群里使用";
            try {
                Map<String, Object> info = svc.getLocalGroupInfo(bot.selfId(), g.getGroupId());
                boolean found = info != null && Boolean.TRUE.equals(info.get("found"));
                String gn = found ? String.valueOf(info.get("group_name")) : "未知";
                String mc = found ? String.valueOf(info.get("member_count")) : "?";
                // 主动发一条群消息演示（PluginServices.sendToGroup）
                svc.sendToGroup(bot.selfId(), g.getGroupId(), XuanJiMessage.text("（这是主动发送的演示消息）"));
                return "群信息：名称=" + gn + "，成员数=" + mc + "（PluginServices 演示）";
            } catch (Exception ex) {
                return "查询失败：" + ex.getMessage();
            }
        }

        // ============ 10. 富媒体消息构造（XuanJiMessage） ============
        @Command(value = "富媒体", scope = Command.Scope.BOTH)
        public String media(Bot bot, MessageEvent e) {
            XuanJiMessage.Builder chain = XuanJiMessage.builder().text("富媒体演示：\n");
            if (e instanceof GroupMessageEvent g) chain.at(g.getSenderId());
            chain.face(1);
            if (e instanceof GroupMessageEvent g) {
                bot.sendGroup(g.getGroupId(), chain.build().plainText() + "\n（@+表情组合，sendGroup 演示）");
            } else {
                bot.reply("富媒体演示：@+表情（单聊简化输出）");
            }
            return "已发送富媒体组合消息";
        }

        // ============ 11. Markdown 发送（Bot.replyMarkdown / sendGroupMarkdown） ============
        @Command(value = "markdown演示", scope = Command.Scope.BOTH)
        public String markdown(Bot bot, MessageEvent e) {
            String md = "**加粗**、*斜体*、`代码`、[链接](https://example.com)\n- 列表项1\n- 列表项2";
            if (e instanceof GroupMessageEvent g) {
                bot.sendGroupMarkdown(g.getGroupId(), md);
            } else {
                bot.replyMarkdown(md);
            }
            return "已发送 Markdown 消息";
        }

        // ============ 12. 收到群消息自动响应（@GroupMessage，order 靠后不抢命令） ============
        @GroupMessage(order = 200)
        public void onGroupMessage(GroupMessageEvent e, Bot bot) {
            String text = e.getPlainText();
            if (text != null && text.contains("群消息")) {
                bot.reply("收到群消息事件（@GroupMessage 演示）：" + text);
            }
        }

        // ============ 13. 收到单聊消息自动响应（@PrivateMessage） ============
        @PrivateMessage(order = 200)
        public void onPrivateMessage(PrivateMessageEvent e, Bot bot) {
            String text = e.getPlainText();
            if (text != null && text.contains("私聊消息")) {
                bot.reply("收到单聊消息事件（@PrivateMessage 演示）：" + text);
            }
        }

        // ============ 14. 收到任意消息监听（@OnMessage，群+私聊） ============
        @OnMessage(priority = 80)
        public void onAnyMessage(MessageEvent e) {
            String text = e.getPlainText();
            if (text != null && text.contains("任意消息")) {
                System.out.println("[DemoPlugin] @OnMessage 收到任意消息: " + text);
            }
        }

        // ============ 15. 收到群事件（@GroupEvent：成员进群/退群） ============
        @GroupEvent(order = 10)
        public void onGroupEvent(GroupMessageEvent e, PluginServices svc, PluginConfig cfg) {
            if ("GROUP_MEMBER_ADD".equals(e.getEventType())) {
                String welcome = cfg.getString("welcomeWord", "欢迎新成员！");
                svc.sendToGroup(e.getBotId(), e.getGroupId(),
                        XuanJiMessage.text(welcome + " " + (e.getSenderName() == null ? "" : e.getSenderName())));
                System.out.println("[DemoPlugin] 群事件 GROUP_MEMBER_ADD: 群=" + e.getGroupId() + " 成员=" + e.getSenderId());
            } else if ("GROUP_MEMBER_REMOVE".equals(e.getEventType())) {
                System.out.println("[DemoPlugin] 群事件 GROUP_MEMBER_REMOVE: 群=" + e.getGroupId() + " 成员=" + e.getSenderId());
            } else {
                System.out.println("[DemoPlugin] 其他群事件: " + e.getEventType());
            }
        }

        // ============ 16. 收到私聊事件（@PrivateEvent，好友事件等） ============
        @PrivateEvent(order = 10)
        public void onPrivateEvent(PrivateMessageEvent e) {
            System.out.println("[DemoPlugin] 私聊事件/消息: sender=" + e.getSenderId());
        }

        // ============ 17. 消息过滤（@MessageFilter：startWith 前缀触发） ============
        @Command(value = "", scope = Command.Scope.BOTH)
        @MessageFilter(startWith = "前缀")
        public String prefixCmd(MessageEvent e) {
            return "前缀触发命令（@MessageFilter startWith 演示）：" + e.getPlainText();
        }

        // ============ 18. 权限注解（@RequireRole 直接标注） ============
        @Command(value = "角色检查", scope = Command.Scope.GROUP)
        @RequireRole("admin")
        public String roleCheck(GroupMessageEvent e) {
            return "通过了 @RequireRole(admin) 检查，你的角色：" + e.getSenderRole();
        }

        // ============ 19. 帮助 ============
        @Command(value = "演示帮助", scope = Command.Scope.BOTH)
        public String help(Bot bot) {
            return """
                璇玑演示插件 v2.0（源码即教程）：
                【群聊专属】群聊命令 | 签到 | 管理员命令 | @我 | 角色检查
                【单聊专属】私聊命令
                【群+单聊】ping | 回声 <内容> | 问候 [名字] | 富媒体 | markdown演示 | 机器人信息 | 前缀xxx
                【事件自动触发】含"群消息"的群消息 / 含"私聊消息"的单聊 / 群成员进群发欢迎词
                【配置面板】插件管理 → 配置（开启签到/金币/欢迎词）
                """;
        }
    }
}
