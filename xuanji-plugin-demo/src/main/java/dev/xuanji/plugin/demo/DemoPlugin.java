package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.annotation.XuanjiPlugin.Perm;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.msg.*;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class DemoPlugin extends Plugin {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0",
        author = "XuanJi Team", description = "展示 SDK 全部消息能力",
        permissions = {Perm.NETWORK, Perm.PROACTIVE_MESSAGE})
    public static class Commands {

        // ===== 精确匹配 =====
        @Command("ping")
        public String ping() { return "pong!"; }

        @Command(value = "hello", match = Command.Match.PREFIX)
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        // ===== 匹配模式演示 =====
        @Command(value = "^算.*", match = Command.Match.REGEX)
        public String calc(@Arg("表达式") String expr) {
            return "正则匹配: " + expr;
        }

        @Command(value = "天气", match = Command.Match.PREFIX)
        public String weather(@Arg("城市") String city) {
            return "前缀匹配 天气 " + (city != null ? city : "?");
        }

        // ===== Shiro 风格自由监听 =====
        @GroupMessageHandler @HandlerFilter(cmd = "签到|打卡", at = AtMode.NEED)
        public void onSign(GroupMessageEvent e, Bot bot) {
            bot.reply(e.getSenderName() + " 签到成功！");
        }

        @GroupMessageHandler @HandlerFilter(startWith = "!")
        public void onBang(GroupMessageEvent e, Bot bot) {
            bot.reply("感叹号: " + e.getPlainText().substring(1));
        }

        // ===== 事件信息（返回 raw JSON） =====
        @Command("事件")
        public void eventInfo(GroupMessageEvent e, Bot bot) {
            if (e == null) { bot.reply("无事件数据"); return; }
            bot.reply("消息ID: " + e.getMessageId() + "\n群: " + e.getGroupId()
                    + "\n发送者: " + e.getSenderName() + "(" + e.getSenderId() + ")"
                    + "\n角色: " + e.getSenderRole() + " @:" + e.isAtBot()
                    + "\n\n原始事件:\n" + e.raw().toString());
        }

        // ===== Markdown + 按钮 =====
        @Command("功能")
        public void menu(Bot bot) {
            String kb = Keyboard.create()
                    .row().btn("sign", "签到", "签到").btn("bank", "银行", "银行").endRow()
                    .row().btn("help", "帮助", "help").endRow().build();
            bot.replyMarkdown(Markdown.create().h2("功能菜单").text("请选择：").build(), kb);
        }

        @Command("md")
        public void md(Bot bot) {
            bot.replyMarkdown(Markdown.create().h1("璇玑").text("**Markdown** 消息").build());
        }

        @Command("图")
        public void img(Bot bot) {
            bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg");
        }

        // ===== Ark + Card =====
        @Command("ark24")
        public void ark24(Bot bot) {
            bot.replyArk(24, Ark.Ark24.create().title("璇玑框架").desc("Java Spring Boot")
                    .prompt("查看").img("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg")
                    .link("https://bot.q.qq.com").build());
        }

        @Command("卡片")
        public void card(Bot bot) {
            bot.replyCard(Card.create().title("QQ开放平台").desc("2分钟创建Bot")
                    .picUrl("https://qqminiapp.cdn-go.cn/qq-open-platform/9b9327f1/assets/33-2-GiI9drV8.png")
                    .url("https://q.qq.com").build());
        }

        @Command("帮助")
        public void help(Bot bot) {
            bot.reply("""
                    @机器人 签到|打卡 | !xxx
                    命令: ping | hello <名字> | 天气 <城市> | ^算.*(正则)
                    富媒体: 功能 | md | 图 | ark24 | 卡片 | 事件""");
        }
    }
}
