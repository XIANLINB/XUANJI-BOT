package dev.xuanji.plugin.demo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.api.annotation.*;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.core.command.CommandRegistry;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * 璇玑演示插件 — 展示 SDK 全部能力。
 *
 * <h3>插件能收到什么</h3>
 * <ul>
 *   <li>{@link BotEvent} 参数注入 — 事件类型、事件 ID、消息 ID、群 ID、发送者、纯文本、是否@机器人</li>
 *   <li>{@link CommandRegistry} 静态方法 — botKey、群 ID、消息 ID、用户 member_openid</li>
 *   <li>{@code @Arg} 绑定 — 命令参数自动解析</li>
 * </ul>
 *
 * <h3>插件能发送什么</h3>
 * <ul>
 *   <li>文本 — {@code return "文本内容";}</li>
 *   <li>Markdown / 图片 / 语音 / 视频 — 注入 {@link MessageSender} 直接调用</li>
 * </ul>
 */
public class DemoPlugin extends Plugin {

    public DemoPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0")
    public static class Commands {

        // ==================== 基础 ====================

        @Command("ping")
        public String ping() {
            return "pong! 璇玑框架运行正常";
        }

        @Command(value = "hello", alias = "��好")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        @Command("时间")
        public String time() {
            return "当前时间: " + java.time.LocalDateTime.now().toString().replace("T", " ");
        }

        // ==================== 事件数据读取 ====================

        /** 演示 BotEvent 参数注入——插件可以拿到完整事件数据 */
        @Command("事件信息")
        public String eventInfo(BotEvent event) {
            if (event == null) return "BotEvent 为 null（旧 handler 未传递）";
            return String.format("""
                    事件类型: %s
                    事件 ID: %s
                    群 ID: %s
                    发送者: %s
                    @了机器人: %s
                    消息内容: %s
                    """,
                    event.type() != null ? event.type() : "未知",
                    truncate(event.eventId(), 40),
                    event.group() != null ? event.group().groupId() : "私聊",
                    event.sender() != null ? event.sender().nickname() : "?",
                    event.isAtBot(),
                    event.message() != null ? event.message().plainText() : "");
        }

        /** 演示通过 CommandRegistry 获取当前上下文 */
        @Command("我的ID")
        public String myId() {
            return String.format("""
                    botKey: %s
                    群 ID: %s
                    你的 member_openid: %s
                    消息 ID: %s
                    """,
                    CommandRegistry.getCurrentBotKey(),
                    CommandRegistry.getCurrentGroupId(),
                    CommandRegistry.getCurrentUser(),
                    truncate(CommandRegistry.getCurrentMsgId(), 30));
        }

        // ==================== 富消息发送 ====================

        /** 演示 Markdown 消息（注入 MessageSender 直接发送） */
        @Command("md")
        public String sendMarkdown(MessageSender sender) {
            String gid = CommandRegistry.getCurrentGroupId();
            if (sender == null || gid == null) return "无法发送";
            ObjectNode md = new ObjectNode(JsonNodeFactory.instance);
            md.put("content",
                    "# 璇玑 Markdown 示例\n" +
                    "> 这是插件发送的 *Markdown* 消息\n" +
                    "---\n" +
                    "支持 **粗体** *斜体* `代码` [链接](https://bot.q.qq.com)");
            sender.sendGroupMarkdown(gid, md, null, CommandRegistry.getCurrentMsgId());
            return null;
        }

        /** 演示图片消息 */
        @Command("图")
        public String sendImage(MessageSender sender) {
            String gid = CommandRegistry.getCurrentGroupId();
            if (sender == null || gid == null) return "无法发送";
            sender.sendGroupImage(gid,
                    "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg",
                    CommandRegistry.getCurrentMsgId());
            return null;
        }

        /** 演示语音消息 */
        @Command("语音")
        public String sendAudio(MessageSender sender) {
            String gid = CommandRegistry.getCurrentGroupId();
            if (sender == null || gid == null) return "无法发送";
            sender.sendGroupAudio(gid,
                    "http://music.163.com/song/media/outer/url?id=862101001.mp3",
                    CommandRegistry.getCurrentMsgId());
            return null;
        }

        // ==================== 帮助 ====================

        @Command("帮助")
        public String help() {
            return """
                    璇玑插件演示:
                    ping | hello <名字> | 时间
                    事件信息 | 我的ID
                    md | 图 | 语音
                    """;
        }

        private static String truncate(String s, int max) {
            if (s == null) return "null";
            return s.length() <= max ? s : s.substring(0, max) + "...";
        }
    }
}
