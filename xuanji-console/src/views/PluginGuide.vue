<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard } from 'naive-ui'
import MarkdownIt from 'markdown-it'
import PageHero from '../components/PageHero.vue'

const md = new MarkdownIt({ html: false, linkify: true })

const content = ref('')

const DOC = `
# 璇玑插件开发指南

插件是扩展机器人能力的单元（打 jar 放入 \`plugins/\` 目录即可热加载）。完整可运行示例请看自带的「演示插件」源码：\`xuanji-plugin-demo\`。

## 一、插件目录结构

一个插件 = 一个 Maven 模块，关键文件：

\`\`\`
xuanji-plugin-xxx/
├── pom.xml                          # 依赖 xuanji-sdk / xuanji-api（provided）
└── src/main/java/.../XxxPlugin.java  # 插件主类
\`\`\`

\`pom.xml\` 的 jar 插件必须声明（决定插件 ID / 入口类）：

\`\`\`xml
<plugin>
  <artifactId>maven-jar-plugin</artifactId>
  <configuration>
    <archive>
      <manifestEntries>
        <Plugin-Id>demo-plugin</Plugin-Id>
        <Plugin-Class>XuanJi.plugin.demo.DemoPlugin</Plugin-Class>
      </manifestEntries>
    </archive>
  </configuration>
</plugin>
\`\`\`

## 二、插件主类骨架

\`\`\`java
public class DemoPlugin extends XuanJiPluginBase {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }
    @Override public void onEnable()  { /* 插件启用时 */ }
    @Override public void onDisable() { /* 插件停用时 */ }

    // @XuanJiPlugin 注册插件元信息 + 命令/事件方法所在的静态类
    @XuanJiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0",
        author = "XuanJi Team", description = "说明文字")
    public static class Commands {
        // 命令 / 事件方法写在这里
    }
}
\`\`\`

## 三、命令（@Command）

\`Command.Scope\`：\`GROUP\`（仅群聊）/ \`PRIVATE\`（仅单聊）/ \`BOTH\`（群聊+单聊）。

\`\`\`java
// 群聊专属命令
@Command(value = "群聊命令", scope = Command.Scope.GROUP)
public String groupOnly(GroupMessageEvent e) {
    return "群聊专属，当前群：" + e.getGroupId();
}

// 单聊专属命令
@Command(value = "私聊命令", scope = Command.Scope.PRIVATE)
public String privateOnly(PrivateMessageEvent e) {
    return "单聊专属，你的 ID：" + e.getSenderId();
}

// 群聊+单聊通用（参数用 MessageEvent 判断类型）
@Command(value = "ping", scope = Command.Scope.BOTH)
public String ping(Bot bot) {
    return "pong！机器人：" + bot.selfId();
}
\`\`\`

**方法返回值 String 会作为回复发给用户；返回 null 则不回复。**

### 参数注入

| 参数类型 | 说明 |
|---|---|
| \`GroupMessageEvent\` / \`PrivateMessageEvent\` / \`MessageEvent\` | 当前消息/事件 |
| \`Bot\` | 当前机器人（reply/sendGroup 等） |
| \`PluginStorage\` | 持久化存储（按插件隔离，落库） |
| \`PluginConfig\` | 读取配置面板的值 |
| \`PluginServices\` | 框架能力（发消息/群管/群信息） |

### 命令参数（@Arg）

\`\`\`java
// rest=true：取剩余全部（含空格），作为最后一个参数
@Command(value = "回声", scope = Command.Scope.BOTH)
public String echo(@Arg(value = "内容", required = true, rest = true) String content) {
    return "你说的是：" + content;
}

// required=false：可选参数，缺省为 null
@Command(value = "问候", scope = Command.Scope.BOTH)
public String greet(@Arg(value = "名字", required = false) String name) {
    return name == null ? "你好！" : "你好，" + name + "！";
}
\`\`\`

### 权限与触发条件

\`\`\`java
// 仅 owner/admin 角色
@Command(value = "管理", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String admin(GroupMessageEvent e) { return "你有管理员权限"; }

// 必须 @ 机器人 才触发
@Command(value = "@我", scope = Command.Scope.GROUP, at = AtMode.NEED)
public String needAt(GroupMessageEvent e) { return "你 @ 了我"; }

// 前缀触发（@MessageFilter startWith）
@Command(value = "", scope = Command.Scope.BOTH)
@MessageFilter(startWith = "前缀")
public String prefix(MessageEvent e) { return "前缀命令：" + e.getPlainText(); }
\`\`\`

## 四、收到消息（自动响应）

\`\`\`java
// 收到群消息（不抢 @Command，order 靠后）
@GroupMessage(order = 200)
public void onGroupMsg(GroupMessageEvent e, Bot bot) {
    if (e.getPlainText().contains("群消息")) bot.reply("收到群消息：" + e.getPlainText());
}

// 收到单聊消息
@PrivateMessage(order = 200)
public void onPrivateMsg(PrivateMessageEvent e, Bot bot) {
    if (e.getPlainText().contains("私聊消息")) bot.reply("收到单聊消息");
}

// 收到任意消息监听（群+私聊）
@OnMessage(priority = 80)
public void onAny(MessageEvent e) {
    System.out.println("任意消息：" + e.getPlainText());
}
\`\`\`

## 五、收到事件（@GroupEvent / @PrivateEvent）

\`\`\`java
// 群事件：成员进群/退群等（e.getEventType() 区分）
@GroupEvent(order = 10)
public void onGroupEvent(GroupMessageEvent e, PluginServices svc, PluginConfig cfg) {
    String et = e.getEventType();
    if ("GROUP_MEMBER_ADD".equals(et)) {
        svc.sendToGroup(e.getBotId(), e.getGroupId(),
            XuanJiMessage.text(cfg.getString("welcomeWord", "欢迎新成员！")));
    } else if ("GROUP_MEMBER_REMOVE".equals(et)) {
        System.out.println("成员退群：" + e.getSenderId());
    }
}

// 私聊事件（好友添加/删除等）
@PrivateEvent(order = 10)
public void onPrivateEvent(PrivateMessageEvent e) {
    System.out.println("私聊事件：" + e.getEventType());
}
\`\`\`

### 能收到的事件清单

**@GroupEvent（群事件，QQ 平台）** — 用 \`e.getEventType()\` 区分：

| 事件类型 | 触发时机 | 关键字段 |
|---|---|---|
| \`GROUP_MEMBER_ADD\` | 成员进群 | group_openid / member_openid / username |
| \`GROUP_MEMBER_REMOVE\` | 成员退群 | group_openid / member_openid |
| \`GROUP_JOIN_REQUEST\` | 用户申请入群 | group_openid / member_openid |
| \`GROUP_ADD_ROBOT\` | 机器人被拉入群 | group_openid / timestamp |
| \`GROUP_DEL_ROBOT\` | 机器人被移出群 | group_openid |
| \`GROUP_MSG_REJECT\` | 群聊消息接收被**关闭** | group_openid |
| \`GROUP_MSG_RECEIVE\` | 群聊消息接收被**开启** | group_openid |

> \`GroupMessageEvent\` 额外提供：\`getGroupId()\` / \`getSenderId()\`（成员 openid）/ \`getSenderName()\` / \`getSenderRole()\` / \`getEventType()\` / \`getBotId()\`（事件所属机器人，多机器人必用）。

**@PrivateMessage / @PrivateEvent（单聊）**：

| 事件类型 | 触发时机 |
|---|---|
| \`FRIEND_ADD\` | 好友添加机器人 |
| \`FRIEND_DEL\` | 好友删除机器人 |
| 单聊消息（@PrivateMessage） | 用户私聊机器人 |

> 平台事件统一经过框架分发，**插件 @GroupEvent/@PrivateEvent 都能收到**（含多机器人）。

## 六、消息构建全类型（XuanJiMessage）

\`\`\`java
// ① 纯文本
XuanJiMessage.text("你好");
XuanJiMessage.builder().text("你好").build();

// ② Markdown（自定义 markdown，QQ 原生 content 渲染）
XuanJiMessage.builder().markdown("**加粗**、*斜体*、\`代码\`、- 列表项").build();
// 或直接回复：bot.replyMarkdown("**加粗**") / bot.sendGroupMarkdown(groupId, "**加粗**")

// ③ ARK 卡片（模板卡片，templateId + 参数 JSON）
XuanJiMessage.builder().add(new XuanJiMessageElement.Ark(25, null)).build();
// 或：bot.replyArk(25, "{\\"desc\\":\\"内容\\"}");

// ④ 富媒体：图片 / 语音 / 视频 / 文件（URL 或本地文件引用）
XuanJiMessage.builder()
    .image("https://example.com/a.png")                 // 图片
    .add(new XuanJiMessageElement.Voice("https://.../a.mp3", 10))   // 语音（时长秒）
    .add(new XuanJiMessageElement.Video("https://.../a.mp4"))       // 视频
    .add(new XuanJiMessageElement.File("https://.../a.pdf", "a.pdf", 1024))  // 文件
    .build();

// ⑤ 组合消息：文本 + @ + 表情 + 回复
XuanJiMessage.builder()
    .text("你好 ")
    .at(userId)         // @某人
    .face(1)            // 表情
    .reply(msgId)       // 回复指定消息
    .build();

// ⑥ 键盘按钮（markdown 附键盘）
XuanJiMessage.builder().markdown("点按钮").add(new XuanJiMessageElement.Keyboard(keyboardJson)).build();
\`\`\`

发送方式：
\`\`\`java
// 回复当前消息（最简单）：方法返回 String 自动回复，或
bot.reply("文本");
bot.replyMarkdown("**markdown**");
bot.replyImage(url);
bot.replyVideo(url);
bot.replyArk(templateId, arkJson);

// 主动发送（botKey 必须是事件所属机器人，不能传空串！）
svc.sendToGroup(bot.selfId(), groupId, XuanJiMessage.text("主动消息"));
bot.sendGroup(groupId, "文本");
bot.sendGroupMarkdown(groupId, "**markdown**");
bot.sendGroupImage(groupId, url);
\`\`\`

## 七、持久化存储（PluginStorage）

落库 \`xuanji_plugin_kv\`，按插件 ID 隔离，key 自己带业务维度：

\`\`\`java
@Command(value = "签到", scope = Command.Scope.GROUP)
public String sign(GroupMessageEvent e, PluginStorage store) {
    String uid = e.getSenderId();
    long n = store.getLong("coins:" + uid, 0) + 1;
    store.set("coins:" + uid, String.valueOf(n));
    return "累计签到 " + n + " 次";
}
\`\`\`

## 八、配置面板（PluginConfigProvider）

插件类实现 \`PluginConfigProvider\`，控制台「插件管理 → 配置」自动生成表单：

\`\`\`java
public static class Commands implements PluginConfigProvider {
    @Override public List<PluginConfigField> configSchema() {
        return List.of(
            new PluginConfigField("coinPerCheckin", "每次金币", PluginConfigField.Type.NUMBER, "10", null, "说明"),
            new PluginConfigField("enableCheckin", "开启签到", PluginConfigField.Type.BOOLEAN, "true", null, "说明"),
            new PluginConfigField("welcomeWord", "欢迎词", PluginConfigField.Type.STRING, "欢迎！", null, "说明"));
    }
}
\`\`\`

方法里注入 \`PluginConfig\` 读取：\`cfg.getBoolean("enableCheckin", true)\` / \`getInt\` / \`getString\`。

## 九、框架能力（PluginServices）

\`\`\`java
@Command(value = "机器人信息", scope = Command.Scope.BOTH)
public String info(MessageEvent e, PluginServices svc, Bot bot) {
    if (!(e instanceof GroupMessageEvent g)) return "请到群里使用";
    Map<String, Object> info = svc.getLocalGroupInfo(bot.selfId(), g.getGroupId());
    // 主动发群消息（botKey 必须是事件所属机器人，不能传空串！）
    svc.sendToGroup(bot.selfId(), g.getGroupId(), XuanJiMessage.text("主动消息"));
    return "群成员数：" + info.get("member_count");
}
\`\`\`

> ⚠️ **多机器人必看**：主动发送/查群信息时 \`botKey\` 必须传**事件所属机器人**（\`bot.selfId()\` 或 \`e.getBotId()\`）。传空串会回退到第一个机器人，导致发错群（QQ 报 11255）。

### 可用动作（PluginServices 能力）全清单

| 方法 | 说明 | 参数 |
|---|---|---|
| \`sendToGroup(botKey, groupOpenid, msg)\` | 主动发群消息 | 机器人 / 群 / 消息链 |
| \`sendToPrivate(botKey, openid, msg)\` | 主动发单聊 | 机器人 / 用户 / 消息链 |
| \`chat(system, user)\` | LLM 对话 | 系统提示 / 用户输入，返回文本 |
| \`muteMember(botKey, groupOpenid, memberOpenid, seconds)\` | **群成员禁言**（0=解除） | 机器人 / 群 / 成员 / 秒 |
| \`recallMessage(botKey, groupOpenid, msgId)\` | **撤回消息** | 机器人 / 群 / 消息 ID |
| \`approveGroupJoin(botKey, groupOpenid, memberOpenid, approve, reason)\` | **入群申请审批** | 机器人 / 群 / 申请者 / 是否同意 / 理由 |
| \`listGroupJoinRequests(botKey, groupOpenid)\` | **入群申请列表** | 机器人 / 群 |
| \`getGroupInfo(botKey, groupOpenid)\` | 群信息（调平台 API） | 机器人 / 群 |
| \`getLocalGroupInfo(botKey, groupOpenid)\` | 群信息（查本地库，高频用） | 机器人 / 群 |
| \`getBotGroupState(botKey, groupOpenid)\` | 机器人在群内状态 | 机器人 / 群 |

动作示例（撤回消息 / 禁言 / 审批 / 申请列表）：

\`\`\`java
@Command(value = "撤回", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String recall(GroupMessageEvent e, PluginServices svc, Bot bot,
                     @Arg(value = "消息id", required = true) String msgId) {
    boolean ok = svc.recallMessage(bot.selfId(), e.getGroupId(), msgId);
    return ok ? "已撤回" : "撤回失败（超时或权限不足）";
}

@Command(value = "禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String mute(GroupMessageEvent e, PluginServices svc, Bot bot,
                   @Arg(value = "目标", required = true) String member,
                   @Arg(value = "秒数", required = true) String sec) {
    boolean ok = svc.muteMember(bot.selfId(), e.getGroupId(), member, Integer.parseInt(sec));
    return ok ? "已禁言 " + sec + " 秒" : "禁言失败";
}

@Command(value = "同意", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String approve(GroupMessageEvent e, PluginServices svc, Bot bot,
                      @Arg(value = "申请者", required = true) String openid) {
    boolean ok = svc.approveGroupJoin(bot.selfId(), e.getGroupId(), openid, true, null);
    return ok ? "已同意入群：" + openid : "审批失败";
}

@Command(value = "申请列表", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String joinList(GroupMessageEvent e, PluginServices svc, Bot bot) {
    Map<String, Object> list = svc.listGroupJoinRequests(bot.selfId(), e.getGroupId());
    return "入群申请：" + (list == null ? "查询失败" : list);
}
\`\`\`

> 群管命令要求机器人是群管理员；撤回消息超过 2 分钟的平台不支持。

## 十、完整示例

参考自带「演示插件」（\`xuanji-plugin-demo\`）：群聊/单聊/两者命令、参数 rest、角色权限、@模式、签到持久化、@GroupMessage/@PrivateMessage/@OnMessage、@GroupEvent 进群欢迎、@PrivateEvent、PluginServices、富媒体、Markdown，全部有可运行用例。

> 开发流程：新建模块 → 写插件类 → \`mvn package\` → 把 jar 放入运行目录 \`plugins/\` → 控制台「插件管理」启用 → 群里发「演示帮助」。
`

onMounted(() => {
  content.value = DOC
})
</script>

<template>
  <div>
    <PageHero title="插件开发指南" subtitle="璇玑插件开发全能力说明（命令/事件/参数/存储/配置/服务/富媒体）" />
    <NCard size="small">
      <div class="guide" v-html="md.render(content)" />
    </NCard>
  </div>
</template>

<style scoped>
.guide { font-size: 14px; line-height: 1.8; }
.guide :deep(h1) { font-size: 22px; border-bottom: 1px solid #eee; padding-bottom: 8px; margin: 24px 0 12px; }
.guide :deep(h2) { font-size: 18px; margin: 20px 0 8px; }
.guide :deep(h3) { font-size: 15px; margin: 16px 0 6px; }
.guide :deep(p) { margin: 6px 0; }
.guide :deep(code) { background: #f2f3f5; padding: 1px 6px; border-radius: 4px; font-size: 13px; }
.guide :deep(pre) { background: #1d2129; color: #e6e8eb; padding: 14px; border-radius: 8px; overflow: auto; margin: 10px 0; }
.guide :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.guide :deep(a) { color: #2080f0; }
.guide :deep(ul), .guide :deep(ol) { padding-left: 22px; margin: 6px 0; }
.guide :deep(table) { border-collapse: collapse; margin: 10px 0; }
.guide :deep(th), .guide :deep(td) { border: 1px solid #e5e6eb; padding: 6px 12px; }
.guide :deep(blockquote) { border-left: 3px solid #2080f0; padding-left: 12px; color: #666; margin: 8px 0; background: #f5f9ff; padding: 8px 12px; border-radius: 4px; }
</style>
