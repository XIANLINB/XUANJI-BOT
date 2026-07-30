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
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@XuanjiPlugin(id = "xuanji.demo", name = "璇玑演示", version = "1.0.0")
public class DemoPlugin {

    private final CommandRegistry commandRegistry;
    private final PermissionService permissionService;
    private final MessageSender messageSender;

    @PostConstruct void init() { commandRegistry.register(this); }

    // ========== 上下文快捷方法 ==========
    private String gid() { return CommandRegistry.getCurrentGroupId(); }
    private String mid() { return CommandRegistry.getCurrentMsgId(); }
    private String bk()  { return CommandRegistry.getCurrentBotKey(); }
    private String me()  { return CommandRegistry.getCurrentUser(); }

    // ========== 纯文本命令 ==========
    @Command("ping") public String ping() { return "pong!"; }
    @Command(value = "hello", alias = "hi") public String hello(@Arg("名字") String n) { return "你好, " + (n != null ? n : "世界") + "!"; }
    @Command("文本") public String text() { return "文本消息\n时间: " + java.time.LocalDateTime.now(); }
    @Command("帮助") public String help() { return "ping|hello|文本|markdown|按钮|图片|语音|视频|ark23|ark24|ark37|黑名单|超管|帮助"; }

    // ========== 媒体命令（从 CommandRegistry 取 groupOpenid/msgId） ==========
    @Command("markdown") public String md() {
        messageSender.sendGroupMarkdown(gid(), MarkdownBuilder.create().h1("MD测试").text("内容").link("文档","https://bot.q.qq.com").build(), null, mid()); return null;
    }
    @Command("按钮") public String kb() {
        messageSender.sendGroupMarkdown(gid(), MarkdownBuilder.create().h2("菜单").text("选择：").build(), KeyboardBuilder.create().addButton("sign_in","签到","签到").build(), mid()); return null;
    }
    @Command("图片") public String img() { messageSender.sendGroupImage(gid(), IMG, mid()); return null; }
    @Command("语音") public String aud() { messageSender.sendGroupAudio(gid(), AUDIO, mid()); return null; }
    @Command("视频") public String vid() { messageSender.sendGroupVideo(gid(), VIDEO, mid()); return null; }
    @Command("ark23") public String a23() { sendArk(23,ark23kv()); return null; }
    @Command("ark24") public String a24() { sendArk(24,ark24kv()); return null; }
    @Command("ark37") public String a37() { sendArk(37,ark37kv()); return null; }

    // ========== 权限命令 ==========
    @Command("黑名单") public String blacklist(@Arg("用户ID") String uid) {
        if (!permissionService.isMaster(bk(), me()) && !permissionService.isSuperAdmin(bk(), gid(), me())) return "权限不足";
        permissionService.addBlacklist("bot:" + bk(), "user", uid, "手动"); return "已加黑名单: " + uid;
    }
    @Command("超管") public String superAdmin(@Arg("用户ID") String uid) {
        if (!permissionService.isMaster(bk(), me())) return "仅主人可设超管";
        permissionService.addSuperAdmin(bk(), gid(), uid); return "已设超管: " + uid;
    }

    // ========== 辅助 ==========
    private void sendArk(int tid, ArrayNode kv) {
        ObjectNode ark = Json.obj().put("template_id", tid); ark.set("kv", kv);
        messageSender.sendGroupArk(gid(), ark, mid());
    }

    private static final String IMG = "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg";
    private static final String AUDIO = "http://music.163.com/song/media/outer/url?id=862101001.mp3";
    private static final String VIDEO = "https://alimov2.a.kwimgs.com/upic/2023/05/29/12/BMjAyMzA1MjkxMjA4MzBfODc4ODYzMTE3XzEwNDI0ODk2ODEwMl8xXzM=_b_Bbb1823a5e9bee463527153837d84de6d.mp4";

    private ArrayNode ark23kv() { ArrayNode a=Json.arr(); a.add(Json.obj().put("key","#LIST#").put("value","1|2|3")); a.add(Json.obj().put("key","#TITLE#").put("value","璇玑")); return a; }
    private ArrayNode ark24kv() { ArrayNode a=Json.arr(); a.add(Json.obj().put("key","#TITLE#").put("value","模板24")); a.add(Json.obj().put("key","#IMG#").put("value",IMG)); return a; }
    private ArrayNode ark37kv() { ArrayNode a=Json.arr(); a.add(Json.obj().put("key","#PROMPT#").put("value","大图")); a.add(Json.obj().put("key","#METATITLE#").put("value","璇玑")); a.add(Json.obj().put("key","#METACOVER#").put("value",IMG)); return a; }
}
