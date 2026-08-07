package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import dev.xuanji.api.plugin.PluginConfig;
import dev.xuanji.api.plugin.PluginConfigField;
import dev.xuanji.api.plugin.PluginConfigProvider;
import dev.xuanji.api.plugin.PluginStorage;
import dev.xuanji.api.plugin.XuanjiPluginBase;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import dev.xuanji.sdk.msg.*;
import org.pf4j.PluginWrapper;

import java.util.List;

/**
 * 璇玑演示插件 v1.2 — 全面覆盖 {@code @Command} 语法糖（P2-F）验收点：
 *
 * <ul>
 *   <li>G1  scope=GROUP 群命令（依赖 GroupMessageEvent 参数）</li>
 *   <li>G2  scope=BOTH 纯文本命令（无事件参数，群/私聊均可用）</li>
 *   <li>G4  media=NEED 媒体订阅（纯图片消息 content 为空也能命中）</li>
 *   <li>G5  @Arg 参数注入（独立游标，剥掉命令词后取参数）</li>
 *   <li>G8  全量消息监听（空命令词，插件内部自行判断）</li>
 *   <li>G9  插件持久化 PluginStorage + 配置 PluginConfig（签到示例）</li>
 * </ul>
 */
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

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.2.0",
        author = "XuanJi Team", description = "展示璇玑 SDK 全部能力（@Command 语法糖 + 持久化 + 配置面板）", rateLimit = 0)
    public static class Commands implements PluginConfigProvider {

        // ===== 配置 schema（控制台「插件管理 → 配置」动态生成表单）=====
        @Override
        public List<PluginConfigField> configSchema() {
            return List.of(
                    new PluginConfigField("coinPerCheckin", "每次签到金币", PluginConfigField.Type.NUMBER,
                            "10", null, "签到一次奖励的金币数，控制台可改"),
                    new PluginConfigField("streakBonus", "连续签到加成", PluginConfigField.Type.NUMBER,
                            "5", null, "连续签到满 3 天后每次额外奖励"),
                    new PluginConfigField("enableCheckin", "开启签到", PluginConfigField.Type.BOOLEAN,
                            "true", null, "关闭后签到命令不生效"));
        }

        // ===== G8: 全量消息监听（空命令词 = 任何消息都进方法，插件内部自行判断）=====
        // 返回 null 不回复、不拦截后续处理器；order=100 靠后执行，避免抢具体命令
        @Command(value = "", scope = Command.Scope.BOTH, order = 100)
        public String onAnyMessage(GroupMessageEvent e) {
            String text = e.getPlainText();
            if (text.contains("你好"))    return "你好呀，" + e.getSenderName() + "（全量监听示例）";
            if (text.contains("123"))     return "你发了数字 123（全量监听示例）";
            if (text.contains("打电话"))   return "演示环境不支持打电话（全量监听示例）";
            return null; // 不匹配 → 不回复
        }

        // ===== G9: 签到（PluginStorage 持久化 + PluginConfig 配置，自动注入）=====
        // at 默认 IGNORE：直接发「签到」即可触发（无需 @机器人）
        @Command(value = "签到", scope = Command.Scope.GROUP)
        public String sign(GroupMessageEvent e, PluginStorage store, PluginConfig cfg) {
            if (!cfg.getBoolean("enableCheckin", true)) {
                return "签到功能已由管理员关闭";
            }
            String uid = e.getSenderId();
            String today = java.time.LocalDate.now().toString();
            String last = store.getString("last:" + uid, "");
            long total = store.getLong("total:" + uid, 0);
            if (today.equals(last)) {
                return e.getSenderName() + " 今天已经签过到啦！累计金币 " + total;
            }
            long coins = cfg.getLong("coinPerCheckin", 10);
            long streak = "1".equals(last)
                    ? store.getLong("streak:" + uid, 0) + 1
                    : 1;
            if (streak >= 3) coins += cfg.getLong("streakBonus", 5);
            total += coins;
            store.set("last:" + uid, today);
            store.set("streak:" + uid, String.valueOf(streak));
            store.set("total:" + uid, String.valueOf(total));
            return e.getSenderName() + " 签到成功！金币 +" + coins
                    + "（累计 " + total + "，连续 " + streak + " 天）";
        }

        // ===== G2: BOTH 纯文本命令（无事件参数，群聊+私聊都注册）=====
        @Command("ping")
        public String ping() { return "修改pong! (scope=BOTH)!"; }

        // ===== G5: @Arg 注入（hello 后的剩余文本作为「名字」）=====
        @Command(value = "hello", startWith = "hello")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null && !name.isBlank() ? name.trim() : "世界") + "!";
        }

        // ===== 群管理员命令（roles 过滤：owner/admin 小写）=====
        @Command(value = "管理", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
        public void adminCmd(GroupMessageEvent e, Bot bot) {
            bot.reply("管理员命令，仅 owner/admin 可用（你的角色: " + e.getSenderRole() + "）");
        }

        // ===== startWith 前缀命令（! 开头）=====
        @Command(scope = Command.Scope.GROUP, startWith = "!")
        public void bang(GroupMessageEvent e, Bot bot) {
            bot.reply("感叹号命令: " + e.getPlainText().substring(1));
        }

        // ===== G4+G7: 媒体订阅（纯图片/语音/视频消息，content 为空也能命中）=====
        // 媒体下载由框架层自动完成（开关在运行设置 → 全局/机器人配置），插件无需关心。
        // m.resolve(platform) 拿到的 form 直接是 FILE_PATH（下载成功）或 URL（失败/未启用）。
        @Command(scope = Command.Scope.GROUP,
                media = MediaMode.NEED, mediaTypes = {MediaType.IMAGE, MediaType.VOICE, MediaType.VIDEO})
        public void onMedia(GroupMessageEvent e, Bot bot) {
            MessageChain chain = e.getChain();
            if (chain == null || !chain.hasMedia()) {
                bot.reply("收到媒体标记但消息链为空");
                return;
            }
            StringBuilder sb = new StringBuilder("收到媒体 (").append(chain.medias().size()).append("):\n");
            for (MessageElement.Media m : chain.medias()) {
                var ref = m.resolve(e.getPlatform());
                String type = m.mediaType() != null ? m.mediaType().name() : m.getClass().getSimpleName();
                String form = ref != null ? ref.form().name() : "?";
                String raw = ref != null && ref.raw() != null ? ref.raw() : "";
                // QQ 群文本消息有长度限制（超长报 11255 invalid request），URL 截断显示，完整地址已存数据库 raw_json
                if (raw.length() > 120) raw = raw.substring(0, 120) + "…";
                sb.append("· ").append(type).append(" [").append(form).append("] ").append(raw).append("\n");
            }
            sb.append("(完整地址已存数据库 raw_json，控制台可查)");
            bot.reply(sb.toString().trim());
        }

        // ===== 事件信息（群事件 DTO 全字段）=====
        @Command(value = "事件", scope = Command.Scope.GROUP)
        public void event(GroupMessageEvent e, Bot bot) {
            bot.reply(String.format("ID:%s 群:%s %s(%s) 角色:%s @机器人:%s",
                    e.getMessageId(), e.getGroupId(), e.getSenderName(),
                    e.getSenderId(), e.getSenderRole(), e.isAtBot()));
        }

        // ===== Bot 数据接口（统计来自 per-bot 实例库）=====
        @Command(value = "信息", scope = Command.Scope.GROUP)
        public void botInfo(Bot bot) {
            var info = bot.getBotInfo();
            bot.reply("群数量: " + bot.getGroupCount()
                    + "\n好友: " + bot.getUserCount()
                    + "\n名称: " + info.getOrDefault("NAME", info.getOrDefault("name", "?")));
        }

        // ===== 富媒体回显 =====
        @Command(value = "功能", scope = Command.Scope.GROUP)
        public void menu(Bot bot) {
            String kb = Keyboard.create()
                    .row().btn("sign", "签到", "签到").btn("help", "帮助", "帮助").endRow().build();
            bot.replyMarkdown(Markdown.create().h2("功能菜单").text("点击按钮或输入命令：").build(), kb);
        }

        @Command(value = "md", scope = Command.Scope.GROUP)
        public void md(Bot bot) {
            bot.replyMarkdown(Markdown.create().h1("璇玑").text("**Markdown 富文本** 演示").build());
        }

        @Command(value = "图", scope = Command.Scope.GROUP)
        public void img(Bot bot) {
            bot.replyImage("https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg");
        }

        // ===== 帮助（BOTH，方便群/私聊查看）=====
        @Command(value = "帮助", scope = Command.Scope.BOTH)
        public void help(Bot bot) {
            bot.reply("""
                群聊命令:
                ping | hello <名> | @bot 签到 | !xxx | 信息 | 事件 | 功能 | md | 图 | 帮助
                私聊命令:
                ping | hello <名> | 帮助
                富媒体: 直接发图片/语音/视频触发媒体回显五态（无需命令词）
                管理员(owner/admin): 管理
                """);
        }
    }
}
