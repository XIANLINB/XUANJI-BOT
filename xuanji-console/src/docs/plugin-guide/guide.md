# 璇玑插件开发指南

插件是扩展机器人能力的单元（打 jar 放入 `plugins/` 目录即可热加载）。完整可运行示例请看自带「群管插件」源码：`xuanji-plugin-groupadmin`。

## 一、插件目录结构

一个插件 = 一个 Maven 模块，关键文件：

```
xuanji-plugin-xxx/
├── pom.xml                          # 依赖 xuanji-sdk / xuanji-api（provided）
└── src/main/java/.../XxxPlugin.java  # 插件主类
```

`pom.xml` 的 jar 插件必须声明（决定插件 ID / 入口类）：

```xml
<plugin>
  <artifactId>maven-jar-plugin</artifactId>
  <configuration>
    <archive>
      <manifestEntries>
        <Plugin-Id>groupadmin-plugin</Plugin-Id>
        <Plugin-Class>XuanJi.plugin.groupadmin.GroupAdminPlugin</Plugin-Class>
      </manifestEntries>
    </archive>
  </configuration>
</plugin>
```

## 二、插件主类骨架

```java
public class DemoPlugin extends XuanJiPluginBase {
    public DemoPlugin(PluginWrapper wrapper) { super(wrapper); }
    @Override public void onEnable()  { /* 插件启用时 */ }
    @Override public void onDisable() { /* 插件停用时 */ }

    // @XuanJiPlugin 注册插件元信息 + 命令/事件方法所在的静态类
    @XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",
        author = "XuanJi Team", description = "说明文字")
    public static class Commands {
        // 命令 / 事件方法写在这里
    }
}
```

## 三、命令（@Command）

`Command.Scope`：`GROUP`（仅群聊）/ `PRIVATE`（仅单聊）/ `BOTH`（群聊+单聊）。

```java
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

// 群聊+单聊通用
@Command(value = "ping", scope = Command.Scope.BOTH)
public String ping(Bot bot) {
    return "pong！机器人：" + bot.selfId();
}
```

**方法返回值 String 会作为回复发给用户；返回 null 则不回复。**

### 参数注入

| 参数类型 | 说明 |
|---|---|
| `GroupMessageEvent` / `PrivateMessageEvent` / `MessageEvent` | 当前消息/事件（字段见「事件」Tab） |
| `Bot` | 机器人门面（被动回复 reply* / 主动发送 send* / 群管 / 查询，见「动作」Tab） |
| `PluginStorage` | 持久化存储（按插件隔离，落库） |
| `PluginConfig` | 读取配置面板的值 |
| `PluginServices` | 仅 LLM 对话能力（`svc.chat(...)`） |

### 命令参数（@Arg）

```java
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
```

### 权限与触发条件

```java
// 仅 owner/admin 角色
@Command(value = "管理", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String admin(GroupMessageEvent e) { return "你有管理员权限"; }

// 必须 @ 机器人 才触发
@Command(value = "@我", scope = Command.Scope.GROUP, at = AtMode.NEED)
public String needAt(GroupMessageEvent e) { return "你 @ 了我"; }

// 前缀触发
@Command(value = "", scope = Command.Scope.BOTH)
@MessageFilter(startWith = "前缀")
public String prefix(MessageEvent e) { return "前缀命令：" + e.getPlainText(); }
```

## 四、收到消息（自动响应）

```java
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
```

## 五、收到事件（@GroupEvent / @PrivateEvent）

```java
// 群事件：成员进群/退群/入群申请等（e.getEventType() 区分）
@GroupEvent(order = 10)
public void onGroupEvent(GroupMessageEvent e, Bot bot, PluginConfig cfg) {
    String et = e.getEventType();
    if ("GROUP_MEMBER_ADD".equals(et)) {
        bot.sendGroup(e.getGroupId(),
            XuanJiMessage.text(cfg.getString("welcomeWord", "欢迎新成员！")));
    }
}

// 私聊事件（好友添加/删除等）
@PrivateEvent(order = 10)
public void onPrivateEvent(PrivateMessageEvent e) {
    System.out.println("私聊事件：" + e.getEventType());
}
```

> 完整事件类型清单与事件对象字段见「事件」Tab。

## 六、消息构建全类型（XuanJiMessage）

```java
// ① 纯文本
XuanJiMessage.text("你好");
XuanJiMessage.builder().text("你好").build();

// ② Markdown
XuanJiMessage.builder().markdown("**加粗**、*斜体*、`代码`、- 列表项").build();
// 或：bot.replyMarkdown("**加粗**") / bot.sendGroupMarkdown(groupId, "**加粗**")

// ③ ARK 卡片（模板卡片，templateId + 参数 JSON）
XuanJiMessage.builder().add(new XuanJiMessageElement.Ark(25, null)).build();
// 或：bot.replyArk(25, "{\"desc\":\"内容\"}");

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
```

发送方式：

```java
// 被动回复当前消息（最简单）：方法返回 String 自动回复，或
bot.reply("文本");
bot.replyMarkdown("**markdown**");
bot.replyImage(url);
bot.replyVideo(url);
bot.replyArk(templateId, arkJson);

// 主动发送（需声明 @XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)）
bot.sendGroup(groupId, "文本");
bot.sendGroupMarkdown(groupId, "**markdown**");
bot.sendGroupImage(groupId, url);
bot.sendPrivate(userId, "私聊文本");
bot.sendToGroup(groupId, XuanJiMessage.text("消息链"));
```

## 七、持久化存储（PluginStorage）

落库 `xuanji_plugin_kv`，按插件 ID 隔离，key 自己带业务维度：

```java
@Command(value = "签到", scope = Command.Scope.GROUP)
public String sign(GroupMessageEvent e, PluginStorage store) {
    String uid = e.getSenderId();
    long n = store.getLong("coins:" + uid, 0) + 1;
    store.set("coins:" + uid, String.valueOf(n));
    return "累计签到 " + n + " 次";
}
```

## 八、配置面板（PluginConfigProvider）

插件类实现 `PluginConfigProvider`，控制台「插件管理 → 配置」自动生成表单：

```java
public static class Commands implements PluginConfigProvider {
    @Override public List<PluginConfigField> configSchema() {
        return List.of(
            new PluginConfigField("coinPerCheckin", "每次金币", PluginConfigField.Type.NUMBER, "10", null, "说明"),
            new PluginConfigField("enableCheckin", "开启签到", PluginConfigField.Type.BOOLEAN, "true", null, "说明"),
            new PluginConfigField("welcomeWord", "欢迎词", PluginConfigField.Type.STRING, "欢迎！", null, "说明"));
    }
}
```

方法里注入 `PluginConfig` 读取：`cfg.getBoolean("enableCheckin", true)` / `getInt` / `getString`。

## 九、框架能力（Bot 门面）

插件方法注入 `Bot bot` 即可调用全部框架能力（被动回复 / 主动发送 / 群管 / 撤回 / 审批 / 查询），返回类型化对象（告别裸 JSON）。LLM 对话注入 `PluginServices svc`（`svc.chat(...)`）。

```java
@Command(value = "机器人信息", scope = Command.Scope.BOTH)
public String info(GroupMessageEvent e, Bot bot) {
    GroupInfo info = bot.getLocalGroupInfo(e.getGroupId());  // 查本地库，高频用
    return "群：" + info.groupName() + "，成员数：" + info.memberCount();
}
```

> ⚠️ **权限闸门**：主动发送（`send*`/`sendToGroup`/`sendToPrivate`）需声明 `@XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)`；群管/撤回/审批（`mute*`/`kick*`/`approve*`/`recall*`）需声明 `Perm.GROUP_ADMIN`。未声明时调用会抛异常。被动回复（`reply*`）与只读查询不受限。

**完整动作清单见「动作」Tab**：每个方法的参数、返回值、失败原因（`OpResult`）都有说明。

## 十、完整示例

参考自带「群管插件」（`xuanji-plugin-groupadmin`）：`#禁言`/`#解禁`（批量 + @Arg 分钟）、`#撤回`、`#针对撤回`/`#解除针对`（@GroupMessage 监听 + PluginStorage 持久化名单）、`#入群申请列表`/`#同意`/`#拒绝`/`#全部同意`/`#全部拒绝`（@用户 或 openid 审批），全部走 Bot 门面类型化能力。另有「接口测试插件」（`xuanji-plugin-test`）覆盖键盘按钮等测试用例。

> 开发流程：新建模块 → 写插件类 → `mvn package` → 把 jar 放入运行目录 `plugins/` → 控制台「插件管理」启用 → 群里发「群管帮助」。
