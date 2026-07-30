package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.sdk.bot.XjBot;
import dev.xuanji.sdk.event.XjGroupMessageEvent;
import dev.xuanji.sdk.msg.XjKeyboard;
import dev.xuanji.sdk.msg.XjMarkdown;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * 璇玑演示插件 — 展示 SDK 全部能力（Shiro 风格 API）。
 *
 * <h3>插件能收到什么</h3>
 * <pre>
 * @Command("hi")
 * public void hi(XjGroupMessageEvent event, XjBot bot) {
 *     // event.getSenderName() → 发送者昵称
 *     // event.getGroupId()    → 群 ID
 *     // event.getSenderId()   → member_openid
 *     // event.getSenderRole() → owner/admin/member
 *     // event.getPlainText()  → 纯文本（去@标签）
 *     // event.isAtBot()       → 是否 @机器人
 * }
 * </pre>
 *
 * <h3>插件能发送什么</h3>
 * <pre>
 * bot.reply("文本");                        // 文本
 * bot.replyMarkdown(md);                   // Markdown
 * bot.replyMarkdown(md, kb);               // Markdown + 按钮
 * bot.replyImage(url);                     // 图片
 * bot.replyAudio(url);                     // 语音
 * bot.replyVideo(url);                     // 视频
 * </pre>
 */
public class DemoPlugin extends Plugin {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0")
    public static class Commands {

        // ==================== 基础 ====================

        @Command("ping")
        public String ping() {
            return "pong! 璇玑框架运行正常";
        }

        @Command(value = "hello", alias = "你好")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        // ==================== 事件数据 — 通过 XjGroupMessageEvent ====================

        @Command("事件信息")
        public void eventInfo(XjGroupMessageEvent e, XjBot bot) {
            if (e == null) { bot.reply("事件数据为空"); return; }
            bot.reply(String.format("""
                    消息 ID: %s
                    群 ID: %s
                    发送者: %s (%s)
                    角色: %s
                    @了机器人: %s
                    纯文本: %s
                    """,
                    e.getMessageId(), e.getGroupId(),
                    e.getSenderName(), e.getSenderId(),
                    e.getSenderRole() != null ? e.getSenderRole() : "unknown",
                    e.isAtBot(), e.getPlainText()));
        }

        // ==================== Markdown — 通过 XjMarkdown 构建器 ====================

        @Command("md")
        public void markdown(XjBot bot) {
            String md = XjMarkdown.create()
                    .h1("璇玑 Markdown 示例")
                    .quote("这是 SDK 的 XjMarkdown 构建器")
                    .bold("框架", "璇玑 Xuanji")
                    .divider()
                    .text("支持 **粗体** *斜体* `代码`")
                    .link("QQ 机器人文档", "https://bot.q.qq.com")
                    .build();
            bot.replyMarkdown(md);
        }

        @Command("按钮")
        public void keyboard(XjBot bot) {
            String md = XjMarkdown.create()
                    .h2("功能菜单")
                    .text("请选择功能：")
                    .build();
            String kb = XjKeyboard.create()
                    .addButton("sign_in", "签到", "签到")
                    .addButton("bank", "银行", "银行")
                    .addButton("help", "帮助", "帮助")
                    .build();
            bot.replyMarkdown(md, kb);
        }

        // ==================== 富媒体 ====================

        @Command("图")
        public void image(XjBot bot) {
            bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg");
        }

        @Command("语音")
        public void audio(XjBot bot) {
            bot.replyAudio("http://music.163.com/song/media/outer/url?id=862101001.mp3");
        }

        // ==================== 帮助 ====================

        @Command("帮助")
        public void help(XjBot bot) {
            bot.reply("""
                    璇玑 SDK 演示:
                    ping | hello <名字>
                    事件信息 | md | 按钮 | 图 | 语音
                    """);
        }
    }
}
