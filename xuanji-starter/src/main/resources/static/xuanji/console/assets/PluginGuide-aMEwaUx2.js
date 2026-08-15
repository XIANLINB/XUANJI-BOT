import{P as d}from"./PageHero-nVK1xp6W.js";import{r as t}from"./markdown-CC9Obcx7.js";import{f as p,z as l,B as e,C as r,D as n,G as m,K as S,O as c,U as v,A as i,a0 as M}from"./index-B7VD0f6B.js";import{N as I}from"./Tag-BJzm2Mb5.js";import{a as E,N as o}from"./Tabs-j6pvKHbS.js";import"./Add-Ckwl9kpz.js";import"./toNumber-CF9QIw_o.js";const b=`# 璇玑插件开发指南

插件是扩展机器人能力的单元（打 jar 放入 \`plugins/\` 目录即可热加载）。完整可运行示例请看自带「群管插件」源码：\`xuanji-plugin-groupadmin\`。

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
        <Plugin-Id>groupadmin-plugin</Plugin-Id>
        <Plugin-Class>XuanJi.plugin.groupadmin.GroupAdminPlugin</Plugin-Class>
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
    @XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",
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

// 群聊+单聊通用
@Command(value = "ping", scope = Command.Scope.BOTH)
public String ping(Bot bot) {
    return "pong！机器人：" + bot.selfId();
}
\`\`\`

**方法返回值 String 会作为回复发给用户；返回 null 则不回复。**

### 参数注入

| 参数类型 | 说明 |
|---|---|
| \`GroupMessageEvent\` / \`PrivateMessageEvent\` / \`MessageEvent\` | 当前消息/事件（字段见「事件」Tab） |
| \`Bot\` | 机器人门面（被动回复 reply* / 主动发送 send* / 群管 / 查询，见「动作」Tab） |
| \`PluginStorage\` | 持久化存储（按插件隔离，落库） |
| \`PluginConfig\` | 读取配置面板的值 |
| \`PluginServices\` | 仅 LLM 对话能力（\`svc.chat(...)\`） |

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

// 前缀触发
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
\`\`\`

> 完整事件类型清单与事件对象字段见「事件」Tab。

## 六、消息构建全类型（XuanJiMessage）

\`\`\`java
// ① 纯文本
XuanJiMessage.text("你好");
XuanJiMessage.builder().text("你好").build();

// ② Markdown
XuanJiMessage.builder().markdown("**加粗**、*斜体*、\`代码\`、- 列表项").build();
// 或：bot.replyMarkdown("**加粗**") / bot.sendGroupMarkdown(groupId, "**加粗**")

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

## 九、框架能力（Bot 门面）

插件方法注入 \`Bot bot\` 即可调用全部框架能力（被动回复 / 主动发送 / 群管 / 撤回 / 审批 / 查询），返回类型化对象（告别裸 JSON）。LLM 对话注入 \`PluginServices svc\`（\`svc.chat(...)\`）。

\`\`\`java
@Command(value = "机器人信息", scope = Command.Scope.BOTH)
public String info(GroupMessageEvent e, Bot bot) {
    GroupInfo info = bot.getLocalGroupInfo(e.getGroupId());  // 查本地库，高频用
    return "群：" + info.groupName() + "，成员数：" + info.memberCount();
}
\`\`\`

> ⚠️ **权限闸门**：主动发送（\`send*\`/\`sendToGroup\`/\`sendToPrivate\`）需声明 \`@XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)\`；群管/撤回/审批（\`mute*\`/\`kick*\`/\`approve*\`/\`recall*\`）需声明 \`Perm.GROUP_ADMIN\`。未声明时调用会抛异常。被动回复（\`reply*\`）与只读查询不受限。

**完整动作清单见「动作」Tab**：每个方法的参数、返回值、失败原因（\`OpResult\`）都有说明。

## 十、完整示例

参考自带「群管插件」（\`xuanji-plugin-groupadmin\`）：\`#禁言\`/\`#解禁\`（批量 + @Arg 分钟）、\`#撤回\`、\`#针对撤回\`/\`#解除针对\`（@GroupMessage 监听 + PluginStorage 持久化名单）、\`#入群申请列表\`/\`#同意\`/\`#拒绝\`/\`#全部同意\`/\`#全部拒绝\`（@用户 或 openid 审批），全部走 Bot 门面类型化能力。另有「接口测试插件」（\`xuanji-plugin-test\`）覆盖键盘按钮等测试用例。

> 开发流程：新建模块 → 写插件类 → \`mvn package\` → 把 jar 放入运行目录 \`plugins/\` → 控制台「插件管理」启用 → 群里发「群管帮助」。
`,P="# 事件\n\n插件通过注解监听平台事件，事件对象均为 SDK 类型化类（`getXxx()` 直接取字段，无需解析原始 JSON）。\n\n## 一、事件注解总览\n\n| 注解 | 监听什么 | 事件对象 |\n|---|---|---|\n| `@GroupMessage` | 群聊消息 | `GroupMessageEvent` |\n| `@PrivateMessage` | 单聊消息 | `PrivateMessageEvent` |\n| `@OnMessage` | 任意消息（群+私聊，原始监听） | `MessageEvent` |\n| `@GroupEvent` | 群系统事件（进群/退群/入群申请…） | `GroupMessageEvent`（`getEventType()` 区分） |\n| `@PrivateEvent` | 私聊系统事件（好友添加/删除） | `PrivateMessageEvent`（`getEventType()` 区分） |\n\n## 二、群事件清单（@GroupEvent）\n\n用 `e.getEventType()` 区分：\n\n| 事件类型 | 触发时机 | 关键字段 |\n|---|---|---|\n| `GROUP_MEMBER_ADD` | 成员进群 | groupId / senderId / senderName |\n| `GROUP_MEMBER_REMOVE` | 成员退群 | groupId / senderId |\n| `GROUP_JOIN_REQUEST` | 用户申请入群 | groupId / senderId + `getJoinRequestInfo()` 全量字段 |\n| `GROUP_ADD_ROBOT` | 机器人被拉入群 | groupId |\n| `GROUP_DEL_ROBOT` | 机器人被移出群 | groupId |\n| `GROUP_MSG_REJECT` | 群消息接收被**关闭** | groupId |\n| `GROUP_MSG_RECEIVE` | 群消息接收被**开启** | groupId |\n\n> 平台事件统一经过框架分发（含多机器人），插件 `@GroupEvent` 都能收到。\n\n## 三、私聊事件清单（@PrivateEvent）\n\n| 事件类型 | 触发时机 |\n|---|---|\n| `FRIEND_ADD` | 好友添加机器人 |\n| `FRIEND_DEL` | 好友删除机器人 |\n| （单聊消息） | 用户私聊机器人（@PrivateMessage） |\n\n## 四、消息事件对象字段\n\n### MessageEvent（接口，群聊+单聊通用）\n\n| 方法 | 返回 | 说明 |\n|---|---|---|\n| `getMessageId()` | String | 消息 ID |\n| `getContent()` | String | 原始内容（含 @ 占位） |\n| `getPlainText()` | String | 纯文本（已剥掉所有 @占位） |\n| `getPlatform()` | String | 平台标识（qq / onebot…） |\n| `getChain()` | XuanJiMessage | 解析后的消息链 |\n| `getStripped()` | Stripped | 已裁剪命令前缀的文本 |\n| `getBotKey()` / `getUnifiedMsgOrigin()` | String | 机器人键 / 消息来源（平台差异时用） |\n\n### GroupMessageEvent（群聊消息 / 群事件）\n\n| 方法 | 返回 | 说明 |\n|---|---|---|\n| `getGroupId()` | String | 群 openid |\n| `getSenderId()` | String | 发送者 member_openid |\n| `getSenderName()` | String | 昵称 |\n| `getSenderRole()` | String | owner / admin / member |\n| `getMentionedUserIds()` | List<String> | **框架已过滤**（不含机器人/自己）的可操作目标，禁言/@ 命令直接用 |\n| `getMentionedUsers()` | List<Mention> | 过滤后的目标（含角色） |\n| `getAllMentions()` | List<Mention> | 原始 @ 列表（含机器人与自己） |\n| `isAtBot()` | boolean | 是否 @ 了机器人 |\n| `getEventType()` | String | 群事件类型（普通消息为空串） |\n| `getBotId()` | String | 事件所属机器人（多机器人必用） |\n| `getJoinRequestInfo()` | JoinRequest | 入群申请完整字段（仅 GROUP_JOIN_REQUEST） |\n| `getStripped()` / `getChain()` | — | 裁剪前缀文本 / 消息链 |\n\n`Mention`：`record Mention(String userId, boolean bot, boolean isYou, String role)`\n（`userId`=成员 openid，`bot`=是否机器人，`isYou`=是否机器人自己，`role`=成员角色）\n\n### PrivateMessageEvent（单聊消息 / 私聊事件）\n\n`getMessageId()` / `getSenderId()` / `getSenderName()` / `getMessageType()` / `getPlainText()` / `getChain()` / `getStripped()` / `getEventType()`\n\n### Stripped（已裁剪命令前缀）\n\n`record Stripped(String content, String prefix, boolean appel, boolean hasAt, boolean atSelf)`\n\n## 五、入群申请事件（GROUP_JOIN_REQUEST）\n\n`e.getJoinRequestInfo()` 返回 `JoinRequest`（框架已解析 verify_info 两种数据源差异，审批判定由插件实现）：\n\n| 方法 | 返回 | 说明 |\n|---|---|---|\n| `memberOpenid()` | String | 申请者 openid |\n| `username()` | String | 昵称 |\n| `applyAt()` / `applySource()` | String | 申请时间 / 来源（self_apply…） |\n| `joinRequestId()` | String | 申请 ID（**审批必传**） |\n| `isQaMode()` | boolean | 是否设置了入群问题 |\n| `getQuestion()` / `getAnswer()` | String | 问题 / 申请者填写的答案 |\n| `getVerifyMessage()` | String | 验证消息（无问题时即填写内容） |\n| `getMethod()` | String | verify_message / admin_review_qa |\n| `verifyInfo()` / `verifyParsed()` | Map | 平台原始 / 框架解析后的验证信息 |\n",G='# 动作（Bot 门面能力）\n\n插件命令/事件方法注入 `Bot bot` 即可调用框架全部能力；LLM 对话注入 `PluginServices svc`（`svc.chat(...)`）。\n\n**权限闸门**：\n- 主动发送（`send*` / `sendToGroup` / `sendToPrivate`）受 `PROACTIVE_MESSAGE` 闸门控制，需 `@XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)`；\n- 群管/撤回/审批写操作（`mute*` / `kick*` / `setGroupAdmin` / `approve*` / `recall*`）受 `GROUP_ADMIN` 闸门控制，需 `Perm.GROUP_ADMIN`；\n- 被动回复（`reply*`）与只读查询不受限。未声明权限调用会抛 `IllegalStateException`。\n\n## 一、被动回复（reply* 系列，仅事件处理链内可用）\n\n| 方法 | 说明 |\n|---|---|\n| `reply(String text)` | 回复文本 |\n| `replyMarkdown(String md)` / `replyMarkdown(String md, String keyboardJson)` | 回复 Markdown（可附键盘） |\n| `replyImage(String url)` / `replyAudio(String url)` / `replyVideo(String url)` | 回复图片/语音/视频 |\n| `replyArk(int templateId, String arkJson)` / `replyCard(String cardJson)` | 回复 Ark 卡片 / 图文卡片 |\n\n## 二、主动发送（send* 系列，需 PROACTIVE_MESSAGE 权限）\n\n| 方法 | 说明 |\n|---|---|\n| `sendGroup(String groupId, String text)` | 群聊文本 |\n| `sendGroupMarkdown(String groupId, String md[, String keyboardJson])` | 群聊 Markdown（可附键盘） |\n| `sendGroupImage/Audio/Video(String groupId, String url)` | 群聊图片/语音/视频 |\n| `sendGroupArk(String groupId, int templateId, String arkJson)` / `sendGroupCard(groupId, json)` | 群聊 Ark / 图文卡片 |\n| `sendPrivate(String userId, String text)` / `sendPrivateMarkdown` / `sendPrivateImage` / `sendPrivateAudio` | 私聊文本/Markdown/图片/语音 |\n| `sendToGroup(String groupId, XuanJiMessage chain)` | 群消息链，返回 `XuanJiSendReceipt`（`success()`/`platformMsgId()`/`errorMessage()`） |\n| `sendToPrivate(String userId, XuanJiMessage chain)` | 私聊消息链，返回回执 |\n\n## 三、媒体上传（返回 URL，供主动发送用）\n\n`uploadImage(String filePath)` / `uploadVideo(...)` / `uploadAudio(...)` / `uploadFile(...)`\n\n## 四、群管动作（返回 OpResult）\n\n> **OpResult**：`ok()` 是否成功；`message()` 成功提示或失败原因（如"禁言被拒：机器人必须为群管理"、"不能禁言群主或管理员"、"QQ平台错误 [10013] …"）。\n\n| 方法 | 说明 |\n|---|---|\n| `muteGroupMember(String groupId, String memberOpenid, int minutes)` | 禁言（`minutes<=0` 解除） |\n| `muteGroupMembers(String groupId, List<String> memberOpenids, int minutes)` | 批量禁言（一个失败不影响其它，汇总结果） |\n| `unmuteGroupMembers(String groupId, List<String> memberOpenids)` | 批量解除（= minutes 0 便捷重载） |\n| `kickGroupMember(String groupId, String memberOpenid)` | 踢出群成员（平台不支持返回 fail） |\n| `setGroupCard(String groupId, String memberOpenid, String card)` | 设置群成员名片 |\n| `setGroupAdmin(String groupId, String memberOpenid, boolean setAdmin)` | 设置/取消群管理员 |\n| `approveGroupJoinRequest(String groupId, String memberOpenid, String joinRequestId, boolean approve, String reason)` | 入群申请审批（`joinRequestId` 来自 `getJoinRequestInfo().joinRequestId()`） |\n| `approveFriendRequest(String openid, boolean approve, String reason)` | 好友申请审批（平台不支持返回 fail） |\n\n## 五、撤回（返回 OpResult）\n\n| 方法 | 说明 |\n|---|---|\n| `recallGroupMessage(String groupId, String messageId)` | 撤回群消息 |\n| `recallRecentMessages(String groupId, String memberOpenid, int count)` | 撤回该成员最近 N 条（count 上限 50；框架负责权限校验→查最近消息→2 分钟窗口判断→逐条撤回汇总） |\n| `recallRecentMessages(String groupId, String memberOpenid)` | 撤回最近 1 条 |\n| `recallPrivateMessage(String openid, String messageId)` | 撤回单聊消息 |\n\n## 六、平台信息查询（类型化返回；null / 空列表 = 平台不支持或失败）\n\n| 方法 | 返回类型 | 说明 |\n|---|---|---|\n| `getGroupInfo(String groupId)` | `GroupInfo` | 群信息（远程平台 API，实时） |\n| `getLocalGroupInfo(String groupId)` | `GroupInfo` | 群信息（查本地库，**高频用**，免限频） |\n| `getBotGroupState(String groupId)` | `BotGroupState` | 机器人在群内状态 |\n| `getGroupMuteStatus(String groupId)` | `GroupMuteStatus` | 群禁言状态 |\n| `listGroupJoinRequests(String groupId)` | `JoinRequestList` | 入群申请列表（含 next_cursor） |\n| `listGroupMembers(String groupId)` | `List<GroupMember>` | 群成员列表（本地库） |\n| `listGroups()` | `List<GroupInfo>` | 机器人所在群列表（本地库） |\n| `getGroupBotRole(String groupId)` | `GroupBotRole` | 机器人在群内角色 |\n| `listUsers()` | `List<UserInfo>` | 单聊用户列表（本地库） |\n| `getUserInfo(String openid)` | `UserInfo` | 单用户资料（远程平台接口） |\n\n**类型化对象字段**：\n\n| 对象 | 字段 / 便捷方法 |\n|---|---|\n| `GroupInfo` | `groupId` / `groupName` / `ownerId` / `memberCount` / `memberMax` / `found`（本地查询是否有档案） |\n| `BotGroupState` | `botState`（1=正常 2=被移出 3=群解散 4=被禁言）/ `isOnline()` / `groupName` / `memberCount` |\n| `GroupMuteStatus` | `muteExpireAt` / `muteSecondLeft` / `isMuted()` |\n| `GroupMember` | `memberId` / `nickname` / `role` / `joinTime` |\n| `UserInfo` | `userId` / `nickname` / `remark` / `unionOpenid` / `joinTime` |\n| `GroupBotRole` | `role` / `isOwner()` / `isAdmin()` / `isManager()` |\n| `JoinRequest` | 见「事件」Tab 第五节 |\n| `JoinRequestList` | `requests()`（List<JoinRequest>）/ `nextCursor()` / `isEmpty()` / `size()` |\n\n> 所有类型化对象都有 `raw()` 返回平台原始 Map，平台字段扩展时仍可读取完整数据。\n\n## 七、LLM 对话（PluginServices）\n\n注入 `PluginServices svc`：\n\n| 方法 | 说明 |\n|---|---|\n| `svc.chat(String user)` | 单轮对话（用户消息），全局默认模型 |\n| `svc.chat(String system, String user)` | 带系统指令的单轮对话 |\n\n> 使用全局配置的默认供应商/模型；429/5xx 等瞬态错误框架自动重试。\n\n## 八、Bot 信息查询\n\n`getGroupCount()` / `getUserCount()` / `getBotInfo()`（返回 Map）/ `getTodayFriendAdd()` / `getTodayFriendDel()` / `getTodayGroupAdd()` / `getTodayGroupDel()` / `getTodayMemberAdd(groupId)` / `getTodayMemberDel(groupId)`\n',f='# 注解\n\n## 一、插件级：@XuanJiPlugin\n\n标注插件命令/事件方法所在的静态类（插件元信息 + 能力声明）。\n\n**字段**：\n\n| 字段 | 说明 |\n|---|---|\n| id | 插件唯一 ID（**必填**，全局唯一；实际插件多用 `xxx-plugin` 风格，如 `groupadmin-plugin`） |\n| name / version / author / description | 插件元信息 |\n| permissions | 权限声明：`NETWORK`(联网) / `FILESYSTEM`(读写文件) / `PROACTIVE_MESSAGE`(主动发消息) / `GROUP_ADMIN`(群管/撤回/审批写操作) |\n| dependsOn | 依赖的能力（如 EconomyService） |\n| rateLimit | 消息触发频率限制（秒，0=不限制） |\n| platforms | 限定平台（空=全部） |\n| defaultBot | 插件默认机器人 botKey（非事件场景主动发送/定时任务用；事件链路自动携带当前机器人，无需声明） |\n\n```java\n@XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",\n    author = "XuanJi Team", description = "说明",\n    permissions = { Perm.PROACTIVE_MESSAGE, Perm.GROUP_ADMIN })\npublic static class Commands { ... }\n```\n\n## 二、命令：@Command\n\n标注命令方法（合并 `@GroupMessage`/`@PrivateMessage` + `@MessageFilter` 的语法糖，一条消息→一个方法）。\n\n**字段**：\n\n| 字段 | 说明 |\n|---|---|\n| value / cmd | 触发词（支持正则如 `"签到|打卡"`；空串=匹配所有消息；两者并存 `cmd` 优先） |\n| scope | `Command.Scope.GROUP`(仅群) / `PRIVATE`(仅私聊) / `BOTH`(默认，群+私聊) |\n| roles | 限定角色，如 `{"owner","admin"}`（空=不限制） |\n| at | `AtMode.IGNORE`(默认不关心) / `NEED`(必须@机器人) / `NOT`(不能@机器人) |\n| order | 优先级 |\n| groups / senders | 限定群 / 限定发送者 |\n| startWith / endWith | 前缀 / 后缀触发 |\n| media / mediaTypes | 富媒体过滤（media=MediaMode：`NEED`=必须含 / `NOT`=必须纯文本 / `IGNORE`；mediaTypes=限定具体类型） |\n| platforms / invert | 限定平台 / 反转过滤 |\n\n```java\n@Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})\npublic String mute(GroupMessageEvent e, Bot bot,\n                   @Arg(value = "分钟", required = false) Integer minutes) { ... }\n```\n\n## 三、命令参数：@Arg\n\n标注 @Command 方法的参数，框架自动从消息解析（支持 int/long/String 类型转换；QQ 的 @占位已剥掉）。\n\n**字段**：`value`(参数名，显示在帮助) / `required`(必填，默认 true) / `missing`(缺参提示) / `rest`(取剩余全部含空格，仅最后一个 @Arg)\n\n```java\n@Command("回声")\npublic String echo(@Arg(value = "内容", required = true, rest = true) String content) { ... }\n```\n\n## 四、消息监听：@GroupMessage / @PrivateMessage / @OnMessage\n\n| 注解 | 用途 | 字段 |\n|---|---|---|\n| `@GroupMessage` | 收到群聊消息 | `order` / `platforms` |\n| `@PrivateMessage` | 收到单聊消息 | `order` / `platforms` |\n| `@OnMessage` | 更底层原始消息监听（自动回复/日志/风控） | `type`(如 "message/group") / `priority` / `block`(是否阻断后续链) / `groupOnly` / `privateOnly` |\n\n```java\n@GroupMessage(order = 200)\npublic void onGroupMsg(GroupMessageEvent e, Bot bot) { ... }\n```\n\n## 五、事件监听：@GroupEvent / @PrivateEvent\n\n| 注解 | 用途 | 字段 |\n|---|---|---|\n| `@GroupEvent` | 群系统事件（进群/退群/入群申请，`getEventType()` 区分） | `order` / `platforms` |\n| `@PrivateEvent` | 私聊系统事件（好友添加/删除） | `order` / `platforms` |\n\n```java\n@GroupEvent(order = 10)\npublic void onGroupEvent(GroupMessageEvent e, Bot bot) {\n    if ("GROUP_MEMBER_ADD".equals(e.getEventType())) { ... }\n}\n```\n\n## 六、消息过滤：@MessageFilter\n\n配合 `@GroupMessage`/`@PrivateMessage` 使用（普通命令用 `@Command` 已内置，无需单独使用）：\n\n`cmd` / `startWith` / `endWith` / `at` / `groups` / `senders` / `roles` / `platforms` / `media` / `mediaTypes` / `invert`\n\n## 七、权限与限流\n\n| 注解 | 用途 | 字段 |\n|---|---|---|\n| `@RequireRole` | 权限要求（裁决：黑名单 → 特权 → 平台角色 → 权限点） | `value`(角色 BOT_MASTER/SUPER_ADMIN/OWNER/ADMIN/MEMBER) / `permissions`(权限点，OR 关系) |\n| `@RateLimit` | 限流 | `count`(窗口内次数) / `seconds`(窗口秒) / `scope`(user/group/global) |\n| `@GroupOnly` | 仅群聊响应 | — |\n| `@PrivateOnly` | 仅私聊响应（与 @GroupOnly 互斥） | — |\n\n```java\n@Command(value = "签到", scope = Command.Scope.GROUP)\n@RateLimit(count = 1, seconds = 5)   // 同一用户 5 秒内限 1 次\npublic String sign(GroupMessageEvent e) { ... }\n```\n',O=["innerHTML"],R=["innerHTML"],T=["innerHTML"],C=["innerHTML"],_="2026-08-15",y=p({__name:"PluginGuide",setup(A){const a=b,s=P,u=G,g=f;return(J,k)=>(S(),l("div",null,[e(d,{title:"插件开发指南",subtitle:"开发指南 / 事件 / 动作 / 注解 — 璇玑插件开发全能力说明"},{default:r(()=>[e(n(I),{bordered:!1,type:"info",size:"small"},{default:r(()=>[c("最后更新 "+v(_))]),_:1})]),_:1}),e(n(m),{size:"small"},{default:r(()=>[e(n(E),{type:"line",animated:""},{default:r(()=>[e(n(o),{name:"guide",tab:"开发指南"},{default:r(()=>[i("div",{class:"guide",innerHTML:n(t)(n(a),{breaks:!1})},null,8,O)]),_:1}),e(n(o),{name:"events",tab:"事件"},{default:r(()=>[i("div",{class:"guide",innerHTML:n(t)(n(s),{breaks:!1})},null,8,R)]),_:1}),e(n(o),{name:"actions",tab:"动作"},{default:r(()=>[i("div",{class:"guide",innerHTML:n(t)(n(u),{breaks:!1})},null,8,T)]),_:1}),e(n(o),{name:"annotations",tab:"注解"},{default:r(()=>[i("div",{class:"guide",innerHTML:n(t)(n(g),{breaks:!1})},null,8,C)]),_:1})]),_:1})]),_:1})]))}}),h=M(y,[["__scopeId","data-v-b2518af0"]]);export{h as default};
