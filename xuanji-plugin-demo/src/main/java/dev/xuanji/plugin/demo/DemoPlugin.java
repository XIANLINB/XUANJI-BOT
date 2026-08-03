package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.plugin.XuanjiPluginBase;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.msg.*;
import org.pf4j.PluginWrapper;

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

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0",
        author = "XuanJi Team", description = "展示璇玑 SDK 全部能力", rateLimit = 5)
    public static class Commands {

        // ===== 群聊消息 =====
        @GroupMessage
        @MessageFilter(cmd = "ping")
        public String ping() { return "pong!"; }

        @GroupMessage
        @MessageFilter(cmd = "hello|你好", startWith = "hello")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        @GroupMessage
        @MessageFilter(cmd = "信息")
        public void botInfo(Bot bot) {
            var info = bot.getBotInfo();
            bot.reply("群数量: " + bot.getGroupCount()
                    + "\n好友: " + bot.getUserCount()
                    + "\n名称: " + info.getOrDefault("USERNAME", "?"));
        }

        @GroupMessage
        @MessageFilter(cmd = "签到|打卡", at = AtMode.NEED)
        public void onSign(GroupMessageEvent e, Bot bot) {
            bot.reply(e.getSenderName() + " 签到成功！");
        }

        @GroupMessage
        @MessageFilter(cmd = "踢人|禁言", roles = {"owner", "admin"})
        public void adminCmd(GroupMessageEvent e, Bot bot) {
            bot.reply("管理员命令，仅 owner/admin 可用（你的角色: " + e.getSenderRole() + "）");
        }

        @GroupMessage
        @MessageFilter(startWith = "!")
        public void onBang(GroupMessageEvent e, Bot bot) {
            bot.reply("感叹号: " + e.getPlainText().substring(1));
        }

        @GroupMessage
        @MessageFilter(cmd = "事件")
        public void event(GroupMessageEvent e, Bot bot) {
            bot.reply(String.format("ID:%s 群:%s %s(%s) 角色:%s @:%s\n\n%s",
                    e.getMessageId(), e.getGroupId(), e.getSenderName(),
                    e.getSenderId(), e.getSenderRole(), e.isAtBot(), e.raw().toString()));
        }

        // ===== 私聊消息 =====
        @PrivateMessage
        @MessageFilter(cmd = "ping")
        public String pingPrivate() { return "pong from private!"; }

        @PrivateMessage
        @MessageFilter(cmd = "你好")
        public String helloPrivate() { return "你好！这是私聊～"; }

        // ===== 富媒体 =====
        @GroupMessage
        @MessageFilter(cmd = "功能")
        public void menu(Bot bot) {
            String kb = Keyboard.create()
                    .row().btn("sign", "签到", "签到").btn("help", "帮助", "help").endRow().build();
            bot.replyMarkdown(Markdown.create().h2("功能菜单").text("请选择：").build(), kb);
        }

        @GroupMessage
        @MessageFilter(cmd = "md")
        public void md(Bot bot) { bot.replyMarkdown(Markdown.create().h1("璇玑").text("**Markdown**").build()); }

        @GroupMessage
        @MessageFilter(cmd = "图")
        public void img(Bot bot) { bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg"); }

        @GroupMessage @MessageFilter(cmd = "ark24")
        public void ark24(Bot bot) {
            bot.replyArk(24, Ark.Ark24.create().title("璇玑").desc("框架")
                    .prompt("查看").img("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg")
                    .link("https://bot.q.qq.com").build());
        }

        @GroupMessage @MessageFilter(cmd = "卡片")
        public void card(Bot bot) {
            bot.replyCard(Card.create().title("QQ开放平台").desc("2分钟创建Bot")
                    .picUrl("https://qqminiapp.cdn-go.cn/qq-open-platform/9b9327f1/assets/33-2-GiI9drV8.png")
                    .url("https://q.qq.com").build());
        }

        @GroupMessage
        @MessageFilter(cmd = "帮助")
        public void help(Bot bot) {
            bot.reply("群聊: ping | hello <名> | @bot 签到 | !xxx | 功能 | md | 图 | ark24 | 卡片 | 事件\n私聊: ping | 你好");
        }
    }
}
