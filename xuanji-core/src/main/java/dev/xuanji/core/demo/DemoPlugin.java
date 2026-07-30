package dev.xuanji.core.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.XuanjiPlugin;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 璇玑演示插件 — 用 @Command 注解替代旧的 switch(content)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@XuanjiPlugin(id = "xuanji.demo", name = "璇玑演示", version = "1.0.0")
public class DemoPlugin {

    private final MessageSender messageSender;
    private final dev.xuanji.core.command.CommandRegistry commandRegistry;

    @PostConstruct
    void init() {
        commandRegistry.register(this);
    }

    @Command("文本")
    public String cmdText(BotEvent event) {
        String reply = "这是一条文本消息\n时间: " + java.time.LocalDateTime.now();
        if (event.isGroupEvent()) {
            messageSender.sendGroupText(event.group().groupId(), reply, event.replyToMsgId());
        } else {
            messageSender.sendC2cText(event.sender().platformUserId(), reply, event.replyToMsgId());
        }
        return null; // 已手动发送
    }

    @Command("markdown")
    public String cmdMarkdown(BotEvent event) {
        String md = MarkdownBuilder.create().h1("Markdown 测试").text("普通文本")
                .bold("加粗", "内容").link("文档", "https://bot.q.qq.com").build();
        ObjectNode mdNode = new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        mdNode.put("content", md);
        if (event.isGroupEvent()) {
            messageSender.sendGroupMarkdown(event.group().groupId(), mdNode, null, event.replyToMsgId());
        }
        return null;
    }

    @Command("按钮")
    public String cmdKeyboard(BotEvent event) {
        String md = MarkdownBuilder.create().h2("功能菜单").text("选择功能：").build();
        String kb = KeyboardBuilder.create()
                .addButton("sign_in", "签到", "签到")
                .addButton("help",   "帮助", "帮助").build();
        ObjectNode mdNode = new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        mdNode.put("content", md);
        ObjectNode kbNode = Json.parseObj(kb);
        if (event.isGroupEvent()) {
            messageSender.sendGroupMarkdown(event.group().groupId(), mdNode, kbNode, event.replyToMsgId());
        }
        return null;
    }

    @Command(value = "图片", alias = "image")
    public String cmdImage(BotEvent event) {
        if (event.isGroupEvent()) {
            messageSender.sendGroupImage(event.group().groupId(),
                    "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg",
                    event.replyToMsgId());
        }
        return null;
    }

    @Command(value = "语音", alias = "audio")
    public String cmdAudio(BotEvent event) {
        if (event.isGroupEvent()) {
            messageSender.sendGroupAudio(event.group().groupId(),
                    "http://music.163.com/song/media/outer/url?id=862101001.mp3",
                    event.replyToMsgId());
        }
        return null;
    }

    @Command(value = "视频", alias = "video")
    public String cmdVideo(BotEvent event) {
        if (event.isGroupEvent()) {
            messageSender.sendGroupVideo(event.group().groupId(),
                    "https://alimov2.a.kwimgs.com/upic/2023/05/29/12/BMjAyMzA1MjkxMjA4MzBfODc4ODYzMTE3XzEwNDI0ODk2ODEwMl8xXzM=_b_Bbb1823a5e9bee463527153837d84de6d.mp4",
                    event.replyToMsgId());
        }
        return null;
    }

    @Command(value = "ping", description = "测试连通性")
    public String cmdPing() {
        return "pong! 璇玑框架运行正常";
    }

    @Command(value = "hello", description = "打招呼")
    public String cmdHello(@Arg("名字") String name) {
        return "你好, " + (name != null ? name : "世界") + "!";
    }

    @Command(value = "帮助", alias = "help", description = "查看可用命令")
    public String cmdHelp() {
        return """
                --- 璇玑测试命令 ---
                文本 | markdown | 按钮
                图片 | 语音 | 视频
                ping | hello <名字>""";
    }
}
