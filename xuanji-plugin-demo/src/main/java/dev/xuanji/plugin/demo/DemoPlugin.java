package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.sdk.bot.XjBot;
import dev.xuanji.sdk.event.XjGroupMessageEvent;
import dev.xuanji.sdk.msg.XjKeyboard;
import dev.xuanji.sdk.msg.XjMarkdown;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class DemoPlugin extends Plugin {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0")
    public static class Commands {

        // ==================== @Command 前缀匹配 ====================

        @Command("ping")
        public String ping() { return "pong! 璇玑框架运行正常"; }

        @Command(value = "hello", alias = "你好")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        // ==================== @GroupMessageHandler 自由监听（Shiro 风格） ====================

        /** 监听"签到"或"打卡"——必须 @机器人 */
        @GroupMessageHandler
        @HandlerFilter(cmd = "签到|打卡", at = AtMode.NEED)
        public void onSign(XjGroupMessageEvent e, XjBot bot) {
            bot.reply(e.getSenderName() + " 签到成功！\n角色: " + e.getSenderRole());
        }

        /** 监听以 "!" 开头的命令 */
        @GroupMessageHandler
        @HandlerFilter(startWith = "!")
        public void onBang(XjGroupMessageEvent e, XjBot bot) {
            String cmd = e.getPlainText().substring(1);
            bot.reply("收到感叹号命令: " + cmd);
        }

        /** 任何包含"你好"的消息都回复（不需 @） */
        @GroupMessageHandler
        @HandlerFilter(cmd = "你好")
        public void onHello(XjGroupMessageEvent e, XjBot bot) {
            bot.reply("你好呀 " + e.getSenderName() + "！");
        }

        // ==================== Markdown + 多行按钮 ====================

        @Command("功能")
        public void menu(XjBot bot) {
            String md = XjMarkdown.create()
                    .h2("璇玑功能菜单")
                    .text("请选择功能：")
                    .build();
            String kb = XjKeyboard.create()
                    .row()
                        .btn("sign", "签到", "签到")
                        .btn("bank", "银行", "银行")
                        .btn("md", "Markdown", "md")
                    .endRow()
                    .row()
                        .btn("info", "事件信息", "event")
                        .btn("help", "帮助", "help")
                    .endRow()
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

        @Command("帮助")
        public void help(XjBot bot) {
            bot.reply("@机器人说 签到/打卡 | 前缀 !xxx | 包含你好 \n命令: ping | hello | 功能 | md | 图 | 事件信息");
        }
    }
}
