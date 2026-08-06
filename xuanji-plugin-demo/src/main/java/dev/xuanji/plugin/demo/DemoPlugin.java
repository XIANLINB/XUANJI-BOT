package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import dev.xuanji.api.plugin.XuanjiPluginBase;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.msg.*;
import org.pf4j.PluginWrapper;

/**
 * 璇玑演示插件 v1.1 — 全面覆盖 {@code @Command} 语法糖（P2-F）验收点：
 *
 * <ul>
 *   <li>G1  scope=GROUP 群命令（依赖 GroupMessageEvent 参数）</li>
 *   <li>G2  scope=BOTH 纯文本命令（无事件参数，群/私聊均可用）</li>
 *   <li>G4  media=NEED 媒体订阅（纯图片消息 content 为空也能命中）</li>
 *   <li>G5  @Arg 参数注入（独立游标，剥掉命令词后取参数）</li>
 *   <li>G6  Stripped 消息（仅文本参与匹配）</li>
 *   <li>G7  媒体五态归一化（chain.medias() + resolve(platform) → MediaRef）</li>
 * </ul>
 */
public class DemoPlugin extends XuanjiPluginBase {

    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @Override
    public void onEnable() {
        System.out.println("[DemoPlugin] onEnable() 钩子已触发 — 插件进入启用态");
    }

    @Override
    public void onDisable() {
        System.out.println("[DemoPlugin] onDisable() 钩子已触发 — 插件进入停用态");
    }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.1.0",
        author = "XuanJi Team", description = "展示璇玑 SDK 全部能力（@Command 语法糖）", rateLimit = 5)
    public static class Commands {

        // ===== G2: BOTH 纯文本命令（无事件参数，群聊+私聊都注册）=====
        @Command("ping")
        public String ping() { return "pong! (scope=BOTH)"; }

        // ===== G5: @Arg 注入（hello 后的剩余文本作为「名字」）=====
        @Command(value = "hello", startWith = "hello")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null && !name.isBlank() ? name.trim() : "世界") + "!";
        }

        // ===== G1: scope=GROUP 群命令（方法签名依赖 GroupMessageEvent → 必须 GROUP）=====
        @Command(value = "签到", scope = Command.Scope.GROUP, at = AtMode.NEED)
        public void sign(GroupMessageEvent e, Bot bot) {
            bot.reply(e.getSenderName() + " 签到成功！(scope=GROUP, @机器人触发)");
        }

        // ===== 群管理员命令（roles 过滤：owner/admin 小写）=====
        @Command(value = "管理", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public void adminCmd(GroupMessageEvent e, Bot bot) {
            bot.reply("管理员命令，仅 owner/admin 可用（你的角色: " + e.getSenderRole() + "）");
        }

        // ===== startWith 前缀命令（! 开头）=====
        @Command(scope = Command.Scope.GROUP, startWith = "!")
        public void bang(GroupMessageEvent e, Bot bot) {
            bot.reply("感叹号命令: " + e.getPlainText().substring(1));
        }

        // ===== G4+G7: 媒体订阅（纯图片/语音/视频消息，content 为空也能命中）=====
        // 注意：媒体订阅命令不能设 cmd——纯图片消息 content="" 时 cmd 匹配必然 miss；
        // 只靠 media=NEED + mediaTypes 订阅（懒解析：NEED 只判 hasAttachments，声明 mediaTypes 才解析链）
        @Command(scope = Command.Scope.GROUP,
                media = MediaMode.NEED, mediaTypes = {MediaType.IMAGE, MediaType.VOICE, MediaType.VIDEO})
        public void onMedia(GroupMessageEvent e, Bot bot) {
            MessageChain chain = e.getChain();
            if (chain == null || !chain.hasMedia()) {
                bot.reply("收到媒体标记但消息链为空");
                return;
            }
            StringBuilder sb = new StringBuilder("收到媒体 (").append(chain.medias().size()).append("):\n");
            for (MessageElement.Media m : chain.medias()) {
                var ref = m.resolve(e.getPlatform());
                String type = m.mediaType() != null ? m.mediaType().name() : m.getClass().getSimpleName();
                String form = ref != null ? ref.form().name() : "?";
                String raw = ref != null && ref.raw() != null ? ref.raw() : "";
                // QQ 群文本消息有长度限制（超长报 11255 invalid request），URL 截断显示，完整地址已存数据库 raw_json
                if (raw.length() > 120) raw = raw.substring(0, 120) + "…";
                sb.append("· ").append(type).append(" [").append(form).append("] ").append(raw).append("\n");
            }
            sb.append("(完整地址已存数据库 raw_json，控制台可查)");
            bot.reply(sb.toString().trim());
        }

        // ===== 事件信息（群事件 DTO 全字段）=====
        @Command(value = "事件", scope = Command.Scope.GROUP)
        public void event(GroupMessageEvent e, Bot bot) {
            bot.reply(String.format("ID:%s 群:%s %s(%s) 角色:%s @机器人:%s",
                    e.getMessageId(), e.getGroupId(), e.getSenderName(),
                    e.getSenderId(), e.getSenderRole(), e.isAtBot()));
        }

        // ===== Bot 数据接口（统计来自 per-bot 实例库）=====
        @Command(value = "信息", scope = Command.Scope.GROUP)
        public void botInfo(Bot bot) {
            var info = bot.getBotInfo();
            bot.reply("群数量: " + bot.getGroupCount()
                    + "\n好友: " + bot.getUserCount()
                    + "\n名称: " + info.getOrDefault("NAME", info.getOrDefault("name", "?")));
        }

        // ===== 富媒体回显 =====
        @Command(value = "功能", scope = Command.Scope.GROUP)
        public void menu(Bot bot) {
            String kb = Keyboard.create()
                    .row().btn("sign", "签到", "签到").btn("help", "帮助", "帮助").endRow().build();
            bot.replyMarkdown(Markdown.create().h2("功能菜单").text("点击按钮或输入命令：").build(), kb);
        }

        @Command(value = "md", scope = Command.Scope.GROUP)
        public void md(Bot bot) {
            bot.replyMarkdown(Markdown.create().h1("璇玑").text("**Markdown 富文本** 演示").build());
        }

        @Command(value = "图", scope = Command.Scope.GROUP)
        public void img(Bot bot) {
            bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg");
        }

        // ===== 帮助（BOTH，方便群/私聊查看）=====
        @Command(value = "帮助", scope = Command.Scope.BOTH)
        public void help(Bot bot) {
            bot.reply("""
                群聊命令:
                ping | hello <名> | @bot 签到 | !xxx | 信息 | 事件 | 功能 | md | 图 | 帮助
                私聊命令:
                ping | hello <名> | 帮助
                富媒体: 直接发图片/语音/视频触发媒体回显五态（无需命令词）
                管理员(owner/admin): 管理
                """);
        }
    }
}
