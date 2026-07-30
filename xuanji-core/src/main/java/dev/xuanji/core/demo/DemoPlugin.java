package dev.xuanji.core.demo;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.annotation.XuanjiPlugin;
import dev.xuanji.api.json.Json;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.permission.PermissionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 璇玑演示插件 — 全部 9 条测试命令 @Command 实现，消灭旧 switch。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@XuanjiPlugin(id = "xuanji.demo", name = "璇玑演示", version = "1.0.0")
public class DemoPlugin {

    private final CommandRegistry commandRegistry;
    private final PermissionService permissionService;
    private final MessageSender messageSender;

    @PostConstruct void init() { commandRegistry.register(this); }

    @Command("ping") public String ping() { return "pong!"; }

    @Command(value = "hello", alias = "hi")
    public String hello(@Arg("名字") String name) { return "你好, " + (name != null ? name : "世界") + "!"; }

    @Command("文本") public String text() { return "文本消息\n时间: " + java.time.LocalDateTime.now(); }

    @Command("markdown")
    public String markdown(ObjectNode data, String groupOpenid, String msgId) {
        String md = MarkdownBuilder.create().h1("Markdown 测试").text("普通文本")
                .bold("加粗", "内容").link("文档", "https://bot.q.qq.com").build();
        messageSender.sendGroupMarkdown(groupOpenid, md, null, msgId);
        return null;
    }

    @Command("按钮")
    public String keyboard(ObjectNode data, String groupOpenid, String msgId) {
        String md = MarkdownBuilder.create().h2("功能菜单").text("选择：").build();
        messageSender.sendGroupMarkdown(groupOpenid, md,
                KeyboardBuilder.create().addButton("sign_in", "签到", "签到").build(), msgId);
        return null;
    }

    @Command("图片") public String image(ObjectNode d, String g, String m) { messageSender.sendGroupImage(g, IMG, m); return null; }
    @Command("语音") public String audio(ObjectNode d, String g, String m) { messageSender.sendGroupAudio(g, AUDIO, m); return null; }
    @Command("视频") public String video(ObjectNode d, String g, String m) { messageSender.sendGroupVideo(g, VIDEO, m); return null; }

    @Command("ark23") public String ark23(ObjectNode d, String g, String m) { sendArk(g, m, 23, ark23kv()); return null; }
    @Command("ark24") public String ark24(ObjectNode d, String g, String m) { sendArk(g, m, 24, ark24kv()); return null; }
    @Command("ark37") public String ark37(ObjectNode d, String g, String m) { sendArk(g, m, 37, ark37kv()); return null; }

    @Command("帮助") public String help() {
        return "璇玑演示: ping|hello|文本|markdown|按钮|图片|语音|视频|ark23|ark24|ark37|黑名单|帮助";
    }

    @Command(value = "黑名单", description = "添加黑名单（仅主人）")
    public String blacklist(@Arg("用户ID") String userId) {
        if (!permissionService.isMaster(userId)) return "权限不足";
        permissionService.addBlacklist("framework", "user", userId, "手动", "cmd");
        return "已加黑名单: " + userId;
    }

    private static final String IMG = "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg";
    private static final String AUDIO = "http://music.163.com/song/media/outer/url?id=862101001.mp3";
    private static final String VIDEO = "https://alimov2.a.kwimgs.com/upic/2023/05/29/12/BMjAyMzA1MjkxMjA4MzBfODc4ODYzMTE3XzEwNDI0ODk2ODEwMl8xXzM=_b_Bbb1823a5e9bee463527153837d84de6d.mp4";

    private void sendArk(String g, String m, int tid, ArrayNode kv) {
        ObjectNode ark = Json.obj().put("template_id", tid);
        ark.set("kv", kv);
        messageSender.sendGroupArk(g, ark, m);
    }
    private ArrayNode ark23kv() {
        ArrayNode a = Json.arr();
        a.add(Json.obj().put("key","#LIST#").put("value","功能1|功能2|功能3"));
        a.add(Json.obj().put("key","#TITLE#").put("value","璇玑机器人"));
        return a;
    }
    private ArrayNode ark24kv() {
        ArrayNode a = Json.arr();
        a.add(Json.obj().put("key","#TITLE#").put("value","文本+缩略图模板"));
        a.add(Json.obj().put("key","#IMG#").put("value",IMG));
        return a;
    }
    private ArrayNode ark37kv() {
        ArrayNode a = Json.arr();
        a.add(Json.obj().put("key","#PROMPT#").put("value","Ark 大图模板"));
        a.add(Json.obj().put("key","#METATITLE#").put("value","璇玑机器人"));
        a.add(Json.obj().put("key","#METACOVER#").put("value",IMG));
        return a;
    }
}
