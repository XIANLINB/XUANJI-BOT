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
    @Command(value = "hello", alias = "hi") public String hello(@Arg("名字") String n) { return "你好, " + (n != null ? n : "世界") + "!"; }
    @Command("文本") public String text() { return "文本消息\n时间: " + java.time.LocalDateTime.now(); }
    @Command("markdown") public String md(ObjectNode d, String g, String m) { messageSender.sendGroupMarkdown(g, MarkdownBuilder.create().h1("MD测试").text("内容").link("文档","https://bot.q.qq.com").build(), null, m); return null; }
    @Command("按钮") public String kb(ObjectNode d, String g, String m) { messageSender.sendGroupMarkdown(g, MarkdownBuilder.create().h2("菜单").text("选择：").build(), KeyboardBuilder.create().addButton("sign_in", "签到", "签到").build(), m); return null; }
    @Command("图片") public String img(ObjectNode d, String g, String m) { messageSender.sendGroupImage(g, IMG, m); return null; }
    @Command("语音") public String aud(ObjectNode d, String g, String m) { messageSender.sendGroupAudio(g, AUDIO, m); return null; }
    @Command("视频") public String vid(ObjectNode d, String g, String m) { messageSender.sendGroupVideo(g, VIDEO, m); return null; }
    @Command("ark23") public String a23(ObjectNode d, String g, String m) { sendArk(g,m,23,ark23kv()); return null; }
    @Command("ark24") public String a24(ObjectNode d, String g, String m) { sendArk(g,m,24,ark24kv()); return null; }
    @Command("ark37") public String a37(ObjectNode d, String g, String m) { sendArk(g,m,37,ark37kv()); return null; }
    @Command("帮助") public String help() { return "ping|hello|文本|markdown|按钮|图片|语音|视频|ark23|ark24|ark37|黑名单|超管|帮助"; }

    @Command("黑名单")
    public String blacklist(@Arg("用户ID") String uid) {
        String bk = CommandRegistry.getCurrentBotKey();
        String gi = CommandRegistry.getCurrentGroupId();
        String me = CommandRegistry.getCurrentUser();
        if (!permissionService.isMaster(bk, me) && !permissionService.isSuperAdmin(bk, gi, me)) return "权限不足";
        permissionService.addBlacklist("bot:" + bk, "user", uid, "手动");
        return "已加黑名单: " + uid;
    }

    @Command("超管")
    public String superAdmin(@Arg("用户ID") String uid) {
        String bk = CommandRegistry.getCurrentBotKey();
        String gi = CommandRegistry.getCurrentGroupId();
        if (!permissionService.isMaster(bk, CommandRegistry.getCurrentUser())) return "仅主人可设超管";
        permissionService.addSuperAdmin(bk, gi, uid);
        return "已设超管: " + uid;
    }

    private static final String IMG = "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg";
    private static final String AUDIO = "http://music.163.com/song/media/outer/url?id=862101001.mp3";
    private static final String VIDEO = "https://alimov2.a.kwimgs.com/upic/2023/05/29/12/BMjAyMzA1MjkxMjA4MzBfODc4ODYzMTE3XzEwNDI0ODk2ODEwMl8xXzM=_b_Bbb1823a5e9bee463527153837d84de6d.mp4";

    private void sendArk(String g, String m, int tid, ArrayNode kv) {
        ObjectNode ark = Json.obj().put("template_id", tid); ark.set("kv", kv);
        messageSender.sendGroupArk(g, ark, m);
    }
    private ArrayNode ark23kv() { ArrayNode a = Json.arr(); a.add(Json.obj().put("key","#LIST#").put("value","功能1|功能2|功能3")); a.add(Json.obj().put("key","#TITLE#").put("value","璇玑")); return a; }
    private ArrayNode ark24kv() { ArrayNode a = Json.arr(); a.add(Json.obj().put("key","#TITLE#").put("value","文本+缩略图")); a.add(Json.obj().put("key","#IMG#").put("value",IMG)); return a; }
    private ArrayNode ark37kv() { ArrayNode a = Json.arr(); a.add(Json.obj().put("key","#PROMPT#").put("value","Ark大图")); a.add(Json.obj().put("key","#METATITLE#").put("value","璇玑")); a.add(Json.obj().put("key","#METACOVER#").put("value",IMG)); return a; }
}
