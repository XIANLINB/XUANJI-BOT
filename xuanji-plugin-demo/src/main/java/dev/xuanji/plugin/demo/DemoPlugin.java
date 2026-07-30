package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.annotation.XuanjiPlugin.Perm;
import dev.xuanji.sdk.bot.XjBot;
import dev.xuanji.sdk.event.XjGroupMessageEvent;
import dev.xuanji.sdk.msg.*;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class DemoPlugin extends Plugin {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanjiPlugin(
        id = "demo-plugin", name = "演示插件", version = "1.0.0",
        author = "XuanJi Team", description = "展示璇玑 SDK 全部消息能力",
        permissions = {Perm.NETWORK, Perm.PROACTIVE_MESSAGE}
    )
    public static class Commands {

        // ==================== 基础 ====================
        @Command("ping")
        public String ping() { return "pong! 璇玑框架运行正常"; }

        @Command(value = "hello", alias = "你好")
        public String hello(@Arg("名字") String name) { return "你好, " + (name != null ? name : "世界") + "!"; }

        // ==================== @GroupMessageHandler 自由监听 ====================
        @GroupMessageHandler
        @HandlerFilter(cmd = "签到|打卡", at = AtMode.NEED)
        public void onSign(XjGroupMessageEvent e, XjBot bot) {
            bot.reply(e.getSenderName() + " 签到成功！角色: " + e.getSenderRole());
        }

        @GroupMessageHandler
        @HandlerFilter(startWith = "!")
        public void onBang(XjGroupMessageEvent e, XjBot bot) {
            bot.reply("收到感叹号命令: " + e.getPlainText().substring(1));
        }

        // ==================== Markdown + 多行按钮 ====================
        @Command("功能")
        public void menu(XjBot bot) {
            String md = XjMarkdown.create().h2("璇玑功能菜单").text("请选择：").build();
            String kb = XjKeyboard.create()
                    .row().btn("sign", "签到", "签到").btn("bank", "银行", "银行").btn("md", "Markdown", "md").endRow()
                    .row().btn("info", "事件信息", "event").btn("help", "帮助", "help").endRow()
                    .build();
            bot.replyMarkdown(md, kb);
        }

        @Command("事件信息")
        public void eventInfo(XjGroupMessageEvent e, XjBot bot) {
            if (e == null) { bot.reply("无事件数据"); return; }
            bot.reply(String.format("消息ID:%s | 群:%s | %s(%s) | 角色:%s | @:%s",
                    e.getMessageId(), e.getGroupId(), e.getSenderName(),
                    e.getSenderId(), e.getSenderRole(), e.isAtBot()));
        }

        @Command("md")
        public void markdown(XjBot bot) {
            bot.replyMarkdown(XjMarkdown.create().h1("璇玑").text("**Markdown** 消息").build());
        }

        @Command("图")
        public void image(XjBot bot) {
            bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg");
        }

        @Command("语音")
        public void audio(XjBot bot) {
            bot.replyAudio("http://music.163.com/song/media/outer/url?id=862101001.mp3");
        }

        // ==================== Ark 消息 ====================
        @Command("ark24")
        public void ark24(XjBot bot) {
            String json = XjArk.Ark24.create()
                    .title("璇玑机器人框架").desc("Java Spring Boot 跨平台机器人框架")
                    .prompt("查看详情").img("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg")
                    .link("https://bot.q.qq.com").subtitle("XuanJi Framework")
                    .build();
            bot.replyArk(24, json);
        }

        @Command("ark37")
        public void ark37(XjBot bot) {
            String json = XjArk.Ark37.create()
                    .prompt("通知提醒").metaTitle("璇玑框架更新")
                    .metaSubtitle("v1.0.0 已发布").metaCover("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg")
                    .metaUrl("https://bot.q.qq.com")
                    .build();
            bot.replyArk(37, json);
        }

        // ==================== 图文卡片 ====================
        @Command("卡片")
        public void card(XjBot bot) {
            String json = XjCard.create()
                    .title("QQ开放平台").desc("2分钟完成注册并创建QQBot")
                    .picUrl("https://qqminiapp.cdn-go.cn/qq-open-platform/9b9327f1/assets/33-2-GiI9drV8.png")
                    .url("https://q.qq.com/#/")
                    .build();
            bot.replyCard(json);
        }

        // ==================== 帮助 ====================
        @Command("帮助")
        public void help(XjBot bot) {
            bot.reply("""
                    @机器人说 签到/打卡 | 前缀 !xxx
                    命令: ping | hello <名字> | 功能 | 事件信息
                    富媒体: md | 图 | 语音
                    Ark: ark24 | ark37
                    卡片: 卡片
                    """);
        }
    }
}
