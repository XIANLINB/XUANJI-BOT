<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NTabs, NTabPane } from 'naive-ui'
import MarkdownIt from 'markdown-it'
import PageHero from '../components/PageHero.vue'

const md = new MarkdownIt({ html: false, linkify: true })

const guideDoc = ref('')
const eventsDoc = ref('')
const actionsDoc = ref('')
const annotationsDoc = ref('')

const GUIDE_DOC = `
# 璇玑插件开发指南

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
| \`GroupMessageEvent\` / \`PrivateMessageEvent\` / \`MessageEvent\` | 当前消息/事件（字段见「事件」Tab） |
| \`Bot\` | 当前机器人（reply/sendGroup 等） |
| \`PluginStorage\` | 持久化存储（按插件隔离，落库） |
| \`PluginConfig\` | 读取配置面板的值 |
| \`PluginServices\` | 框架能力（发消息/群管/信息查询，见「动作」Tab） |

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
public void onGroupEvent(GroupMessageEvent e, PluginServices svc, PluginConfig cfg) {
    String et = e.getEventType();
    if ("GROUP_MEMBER_ADD".equals(et)) {
        svc.sendToGroup(e.getBotId(), e.getGroupId(),
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

插件方法注入 \`PluginServices svc\` 即可调用框架全部能力（发消息 / LLM / 群管 / 信息查询），返回类型化对象（告别裸 JSON）。

\`\`\`java
@Command(value = "机器人信息", scope = Command.Scope.BOTH)
public String info(GroupMessageEvent e, PluginServices svc, Bot bot) {
    GroupInfo info = svc.getLocalGroupInfo(bot.selfId(), e.getGroupId());  // 查本地库，高频用
    return "群：" + info.groupName() + "，成员数：" + info.memberCount();
}
\`\`\`

> ⚠️ **多机器人必看**：主动发送/查群信息时 \`botKey\` 必须传**事件所属机器人**（\`bot.selfId()\` 或 \`e.getBotId()\`）。传空串会回退到第一个机器人，导致发错群（QQ 报 11255）。

**完整动作清单见「动作」Tab**：每个方法的参数、返回值、失败原因（\`OpResult\`）都有说明。

## 十、完整示例

参考自带「群管插件」（\`xuanji-plugin-groupadmin\`）：\`#禁言\`/\`#解禁\`（批量 + @Arg 分钟）、\`#撤回\`、\`#针对撤回\`/\`#解除针对\`（@GroupMessage 监听 + PluginStorage 持久化名单）、\`#入群申请列表\`/\`#同意\`/\`#拒绝\`/\`#全部同意\`/\`#全部拒绝\`（@用户 或 openid 审批），全部走 PluginServices 类型化能力。另有「接口测试插件」（\`xuanji-plugin-test\`）覆盖键盘按钮等测试用例。

> 开发流程：新建模块 → 写插件类 → \`mvn package\` → 把 jar 放入运行目录 \`plugins/\` → 控制台「插件管理」启用 → 群里发「群管帮助」。
`

const EVENTS_DOC = `
# 事件

插件通过注解监听平台事件，事件对象均为 SDK 类型化类（\`getXxx()\` 直接取字段，无需解析原始 JSON）。

## 一、事件注解总览

| 注解 | 监听什么 | 事件对象 |
|---|---|---|
| \`@GroupMessage\` | 群聊消息 | \`GroupMessageEvent\` |
| \`@PrivateMessage\` | 单聊消息 | \`PrivateMessageEvent\` |
| \`@OnMessage\` | 任意消息（群+私聊，原始监听） | \`MessageEvent\` |
| \`@GroupEvent\` | 群系统事件（进群/退群/入群申请…） | \`GroupMessageEvent\`（\`getEventType()\` 区分） |
| \`@PrivateEvent\` | 私聊系统事件（好友添加/删除） | \`PrivateMessageEvent\`（\`getEventType()\` 区分） |

## 二、群事件清单（@GroupEvent）

用 \`e.getEventType()\` 区分：

| 事件类型 | 触发时机 | 关键字段 |
|---|---|---|
| \`GROUP_MEMBER_ADD\` | 成员进群 | groupId / senderId / senderName |
| \`GROUP_MEMBER_REMOVE\` | 成员退群 | groupId / senderId |
| \`GROUP_JOIN_REQUEST\` | 用户申请入群 | groupId / senderId + \`getJoinRequestInfo()\` 全量字段 |
| \`GROUP_ADD_ROBOT\` | 机器人被拉入群 | groupId |
| \`GROUP_DEL_ROBOT\` | 机器人被移出群 | groupId |
| \`GROUP_MSG_REJECT\` | 群消息接收被**关闭** | groupId |
| \`GROUP_MSG_RECEIVE\` | 群消息接收被**开启** | groupId |

> 平台事件统一经过框架分发（含多机器人），插件 \`@GroupEvent\` 都能收到。

## 三、私聊事件清单（@PrivateEvent）

| 事件类型 | 触发时机 |
|---|---|
| \`FRIEND_ADD\` | 好友添加机器人 |
| \`FRIEND_DEL\` | 好友删除机器人 |
| （单聊消息） | 用户私聊机器人（@PrivateMessage） |

## 四、消息事件对象字段

### MessageEvent（接口，群聊+单聊通用）

| 方法 | 返回 | 说明 |
|---|---|---|
| \`getMessageId()\` | String | 消息 ID |
| \`getContent()\` | String | 原始内容（含 @ 占位） |
| \`getPlainText()\` | String | 纯文本（已剥掉所有 @占位） |
| \`getPlatform()\` | String | 平台标识（qq / onebot…） |
| \`getChain()\` | XuanJiMessage | 解析后的消息链 |
| \`getStripped()\` | Stripped | 已裁剪命令前缀的文本 |
| \`getBotKey()\` / \`getUnifiedMsgOrigin()\` | String | 机器人键 / 消息来源（平台差异时用） |

### GroupMessageEvent（群聊消息 / 群事件）

| 方法 | 返回 | 说明 |
|---|---|---|
| \`getGroupId()\` | String | 群 openid |
| \`getSenderId()\` | String | 发送者 member_openid |
| \`getSenderName()\` | String | 昵称 |
| \`getSenderRole()\` | String | owner / admin / member |
| \`getMentionedUserIds()\` | List&lt;String&gt; | **框架已过滤**（不含机器人/自己）的可操作目标，禁言/@ 命令直接用 |
| \`getMentionedUsers()\` | List&lt;Mention&gt; | 过滤后的目标（含角色） |
| \`getAllMentions()\` | List&lt;Mention&gt; | 原始 @ 列表（含机器人与自己） |
| \`isAtBot()\` | boolean | 是否 @ 了机器人 |
| \`getEventType()\` | String | 群事件类型（普通消息为空串） |
| \`getBotId()\` | String | 事件所属机器人（多机器人必用） |
| \`getJoinRequestInfo()\` | JoinRequest | 入群申请完整字段（仅 GROUP_JOIN_REQUEST） |
| \`getStripped()\` / \`getChain()\` | — | 裁剪前缀文本 / 消息链 |

\`Mention\`：\`record Mention(String userId, boolean bot, boolean isYou, String role)\`
（\`userId\`=成员 openid，\`bot\`=是否机器人，\`isYou\`=是否机器人自己，\`role\`=成员角色）

### PrivateMessageEvent（单聊消息 / 私聊事件）

\`getMessageId()\` / \`getSenderId()\` / \`getSenderName()\` / \`getMessageType()\` / \`getPlainText()\` / \`getChain()\` / \`getStripped()\` / \`getEventType()\`

### Stripped（已裁剪命令前缀）

\`record Stripped(String content, String prefix, boolean appel, boolean hasAt, boolean atSelf)\`

## 五、入群申请事件（GROUP_JOIN_REQUEST）

\`e.getJoinRequestInfo()\` 返回 \`JoinRequest\`（框架已解析 verify_info 两种数据源差异，审批判定由插件实现）：

| 方法 | 返回 | 说明 |
|---|---|---|
| \`memberOpenid()\` | String | 申请者 openid |
| \`username()\` | String | 昵称 |
| \`applyAt()\` / \`applySource()\` | String | 申请时间 / 来源（self_apply…） |
| \`joinRequestId()\` | String | 申请 ID（**审批必传**） |
| \`isQaMode()\` | boolean | 是否设置了入群问题 |
| \`getQuestion()\` / \`getAnswer()\` | String | 问题 / 申请者填写的答案 |
| \`getVerifyMessage()\` | String | 验证消息（无问题时即填写内容） |
| \`getMethod()\` | String | verify_message / admin_review_qa |
| \`verifyInfo()\` / \`verifyParsed()\` | Map | 平台原始 / 框架解析后的验证信息 |
`

const ACTIONS_DOC = `
# 动作（PluginServices 能力）

\`PluginServices svc\` 在命令/事件方法中注入。**botKey 约定**：多机器人必须传事件所属机器人（\`bot.selfId()\` / \`e.getBotId()\`），空串回退到第一个机器人（可能发错群）。

## 一、发送消息

### svc.sendToGroup(botKey, groupOpenid, chain)

**参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| botKey | String | 机器人标识 |
| groupOpenid | String | 群 openid |
| chain | XuanJiMessage | 消息链（文本/markdown/富媒体/按钮） |

**返回值**：\`XuanJiSendReceipt\` — \`success()\` 是否成功 / \`platformMsgId()\` 平台消息 ID / \`errorMessage()\` 失败原因

### svc.sendToPrivate(botKey, openid, chain)

同上，单聊场景（openid = 用户 openid）。

## 二、LLM 对话

### svc.chat(user)

**参数**：user 用户消息。**返回值**：String 模型回复文本。

### svc.chat(system, user)

**参数**：system 系统指令 / user 用户消息。**返回值**：String 模型回复文本。

> 使用全局配置的默认供应商/模型；429/5xx 等瞬态错误框架自动重试。

## 三、群管命令（返回 OpResult）

> **OpResult**：\`ok()\` 是否成功；\`message()\` 成功提示或失败原因。失败原因由框架/适配器提供，可精确定位问题（如"禁言被拒：机器人必须为群管理"、"不能禁言群主或管理员"、"QQ平台错误 [10013] …"）。

### svc.approveGroupJoin(botKey, groupOpenid, memberOpenid, joinRequestId, approve, reason)

**参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| botKey / groupOpenid | String | 机器人 / 群 |
| memberOpenid | String | 申请者 openid |
| joinRequestId | String | 申请 ID（**必传**，来自 \`getJoinRequestInfo().joinRequestId()\`） |
| approve | boolean | true=同意 false=拒绝 |
| reason | String | 拒绝理由（拒绝时可选） |

**返回值**：\`OpResult\`（成功"已同意/已拒绝入群申请"）

### svc.muteGroupMember(botKey, groupOpenid, memberOpenid, minutes)

**参数**：minutes 禁言分钟数（**<=0 解除禁言**；分钟→秒由适配器换算）
**返回值**：\`OpResult\`（成功含禁言时长/解除提示）

### svc.muteGroupMembers(botKey, groupOpenid, memberOpenids, minutes)

**参数**：memberOpenids 目标 openid 列表（批量，一个失败不影响其它）
**返回值**：\`OpResult\`（成功含"已禁言 N 人"；失败含成员级明细"成员(原因)"）

### svc.recallGroupMessage(botKey, groupOpenid, msgId)

**参数**：msgId 要撤回的群消息 ID
**返回值**：\`OpResult\`

### svc.recallRecentMessages(botKey, groupOpenid, memberOpenid)

### svc.recallRecentMessages(botKey, groupOpenid, memberOpenid, count)

**说明**：撤回该成员最近消息（无 count 默认 **1 条**；count 上限 50）。框架负责：权限校验（机器人须群管理）→ 查最近消息 → **2 分钟窗口**判断（超时跳过）→ 逐条撤回并汇总。
**返回值**：\`OpResult\`（成功含"已撤回 N 条，跳过 M 条（超2分钟或平台拒绝）"）

### svc.recallPrivateMessage(botKey, openid, msgId)

**参数**：openid 用户 / msgId 消息 ID
**返回值**：\`OpResult\`

## 四、平台信息查询（类型化返回；null / 空列表 = 平台不支持或失败）

| 方法 | 返回类型 | 说明 |
|---|---|---|
| \`getGroupInfo(botKey, groupOpenid)\` | \`GroupInfo\` | 群信息（远程平台 API，实时） |
| \`getLocalGroupInfo(botKey, groupOpenid)\` | \`GroupInfo\` | 群信息（查本地库，**高频用**，免限频） |
| \`getBotGroupState(botKey, groupOpenid)\` | \`BotGroupState\` | 机器人在群内状态 |
| \`getGroupMuteStatus(botKey, groupOpenid)\` | \`GroupMuteStatus\` | 群禁言状态 |
| \`listGroupJoinRequests(botKey, groupOpenid)\` | \`JoinRequestList\` | 入群申请列表（含 next_cursor） |
| \`listGroupMembers(botKey, groupOpenid)\` | \`List<GroupMember>\` | 群成员列表（本地库） |
| \`listGroups(botKey)\` | \`List<GroupInfo>\` | 机器人所在群列表（本地库） |
| \`getGroupBotRole(botKey, groupOpenid)\` | \`GroupBotRole\` | 机器人在群内角色 |
| \`listUsers(botKey)\` | \`List<UserInfo>\` | 单聊用户列表（本地库） |

**类型化对象字段**：

| 对象 | 字段 / 便捷方法 |
|---|---|
| \`GroupInfo\` | \`groupId\` / \`groupName\` / \`ownerId\` / \`memberCount\` / \`memberMax\` / \`found\`（本地查询是否有档案） |
| \`BotGroupState\` | \`botState\`（1=正常 2=被移出 3=群解散 4=被禁言）/ \`isOnline()\` / \`groupName\` / \`memberCount\` |
| \`GroupMuteStatus\` | \`muteExpireAt\` / \`muteSecondLeft\` / \`isMuted()\` |
| \`GroupMember\` | \`memberId\` / \`nickname\` / \`role\` / \`joinTime\` |
| \`UserInfo\` | \`userId\` / \`nickname\` / \`remark\` / \`unionOpenid\` / \`joinTime\` |
| \`GroupBotRole\` | \`role\` / \`isOwner()\` / \`isAdmin()\` / \`isManager()\` |
| \`JoinRequest\` | 见「事件」Tab 第五节 |
| \`JoinRequestList\` | \`requests()\`（List&lt;JoinRequest&gt;）/ \`nextCursor()\` / \`isEmpty()\` / \`size()\` |

> 所有类型化对象都有 \`raw()\` 返回平台原始 Map，平台字段扩展时仍可读取完整数据。

## 五、底层动作（PlatformActions，适配器实现）

\`PluginServices\` 已封装全部动作，插件一般无需直接使用。\`PlatformActions\` 定义 14 个动作常量（按平台适配器实现，供框架内部路由）：

| 常量 | 值 | 说明 |
|---|---|---|
| \`GROUP_INFO\` | group.info | 群基本信息（远程） |
| \`GROUP_LOCAL_INFO\` | group.local_info | 群本地档案（查库免限频） |
| \`GROUP_BOT_STATE\` | group.bot_state | 机器人群内状态 |
| \`GROUP_MUTE\` | group.mute | 群成员禁言 |
| \`GROUP_MUTE_STATUS\` | group.mute_status | 群禁言状态 |
| \`GROUP_APPROVE\` | group.approve | 入群申请审批 |
| \`GROUP_JOIN_REQUEST_LIST\` | group.join_request_list | 入群申请列表 |
| \`GROUP_RECALL\` | group.recall | 撤回群消息 |
| \`GROUP_RECALL_RECENT\` | group.recall_recent | 撤回成员最近 N 条 |
| \`GROUP_RECALL_PRIVATE\` | group.recall_private | 撤回单聊消息 |
| \`GROUP_MEMBER_LIST\` | group.member_list | 群成员列表（本地库） |
| \`GROUP_LIST\` | group.list | 群列表（本地库） |
| \`GROUP_BOT_ROLE\` | group.bot_role | 机器人群内角色 |
| \`USER_LIST\` | user.list | 单聊用户列表（本地库） |
`

const ANNOTATIONS_DOC = `
# 注解

## 一、插件级：@XuanJiPlugin

标注插件命令/事件方法所在的静态类（插件元信息 + 能力声明）。

**字段**：

| 字段 | 说明 |
|---|---|
| id | 插件唯一 ID（**必填**，推荐 \`group:pluginId\`） |
| name / version / author / description | 插件元信息 |
| permissions | 权限声明：\`NETWORK\`(联网) / \`FILESYSTEM\`(读写文件) / \`PROACTIVE_MESSAGE\`(主动发消息) |
| dependsOn | 依赖的能力 |
| rateLimit | 消息触发频率限制（秒，0=不限制） |
| platforms | 限定平台（空=全部） |

\`\`\`java
@XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",
    author = "XuanJi Team", description = "说明")
public static class Commands { ... }
\`\`\`

## 二、命令：@Command

标注命令方法（合并 \`@GroupMessage\`/\`@PrivateMessage\` + \`@MessageFilter\` 的语法糖，一条消息→一个方法）。

**字段**：

| 字段 | 说明 |
|---|---|
| value / cmd | 触发词（支持正则如 \`"签到|打卡"\`；空串=匹配所有消息） |
| scope | \`Command.Scope.GROUP\`(仅群) / \`PRIVATE\`(仅私聊) / \`BOTH\`(默认，群+私聊) |
| roles | 限定角色，如 \`{"owner","admin"}\`（空=不限制） |
| at | \`AtMode.IGNORE\`(默认不关心) / \`NEED\`(必须@机器人) / \`NOT\`(不能@机器人) |
| order | 优先级 |
| groups / senders | 限定群 / 限定发送者 |
| startWith / endWith | 前缀 / 后缀触发 |
| media / mediaTypes | 富媒体过滤（\`NEED\`=必须含 / \`NOT\`=必须纯文本 / \`IGNORE\`） |
| platforms / invert | 限定平台 / 反转过滤 |

\`\`\`java
@Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String mute(GroupMessageEvent e, PluginServices svc,
                   @Arg(value = "分钟", required = false) Integer minutes) { ... }
\`\`\`

## 三、命令参数：@Arg

标注 @Command 方法的参数，框架自动从消息解析（支持 int/long/String 类型转换；QQ 的 @占位已剥掉）。

**字段**：\`value\`(参数名，显示在帮助) / \`required\`(必填，默认 true) / \`missing\`(缺参提示) / \`rest\`(取剩余全部含空格，仅最后一个 @Arg)

\`\`\`java
@Command("回声")
public String echo(@Arg(value = "内容", required = true, rest = true) String content) { ... }
\`\`\`

## 四、消息监听：@GroupMessage / @PrivateMessage / @OnMessage

| 注解 | 用途 | 字段 |
|---|---|---|
| \`@GroupMessage\` | 收到群聊消息 | \`order\` / \`platforms\` |
| \`@PrivateMessage\` | 收到单聊消息 | \`order\` / \`platforms\` |
| \`@OnMessage\` | 更底层原始消息监听（自动回复/日志/风控） | \`type\`(如 "message/group") / \`priority\` / \`block\`(是否阻断后续链) / \`groupOnly\` / \`privateOnly\` |

\`\`\`java
@GroupMessage(order = 200)
public void onGroupMsg(GroupMessageEvent e, Bot bot) { ... }
\`\`\`

## 五、事件监听：@GroupEvent / @PrivateEvent

| 注解 | 用途 | 字段 |
|---|---|---|
| \`@GroupEvent\` | 群系统事件（进群/退群/入群申请，\`getEventType()\` 区分） | \`order\` / \`platforms\` |
| \`@PrivateEvent\` | 私聊系统事件（好友添加/删除） | \`order\` / \`platforms\` |

\`\`\`java
@GroupEvent(order = 10)
public void onGroupEvent(GroupMessageEvent e, PluginServices svc) {
    if ("GROUP_MEMBER_ADD".equals(e.getEventType())) { ... }
}
\`\`\`

## 六、消息过滤：@MessageFilter

配合 \`@GroupMessage\`/\`@PrivateMessage\` 使用（普通命令用 \`@Command\` 已内置，无需单独使用）：

\`cmd\` / \`startWith\` / \`endWith\` / \`at\` / \`groups\` / \`senders\` / \`roles\` / \`platforms\` / \`media\` / \`mediaTypes\` / \`invert\`

## 七、权限与限流

| 注解 | 用途 | 字段 |
|---|---|---|
| \`@RequireRole\` | 权限要求（裁决：黑名单 → 特权 → 平台角色 → 权限点） | \`value\`(角色 BOT_MASTER/SUPER_ADMIN/OWNER/ADMIN/MEMBER) / \`permissions\`(权限点，OR 关系) |
| \`@RateLimit\` | 限流 | \`count\`(窗口内次数) / \`seconds\`(窗口秒) / \`scope\`(user/group/global) |
| \`@GroupOnly\` | 仅群聊响应 | — |
| \`@PrivateOnly\` | 仅私聊响应（与 @GroupOnly 互斥） | — |

\`\`\`java
@Command(value = "签到", scope = Command.Scope.GROUP)
@RateLimit(count = 1, seconds = 5)   // 同一用户 5 秒内限 1 次
public String sign(GroupMessageEvent e) { ... }
\`\`\`
`

onMounted(() => {
  guideDoc.value = GUIDE_DOC
  eventsDoc.value = EVENTS_DOC
  actionsDoc.value = ACTIONS_DOC
  annotationsDoc.value = ANNOTATIONS_DOC
})
</script>

<template>
  <div>
    <PageHero title="插件开发指南" subtitle="开发指南 / 事件 / 动作 / 注解 — 璇玑插件开发全能力说明" />
    <NCard size="small">
      <NTabs type="line" animated>
        <NTabPane name="guide" tab="开发指南">
          <div class="guide" v-html="md.render(guideDoc)" />
        </NTabPane>
        <NTabPane name="events" tab="事件">
          <div class="guide" v-html="md.render(eventsDoc)" />
        </NTabPane>
        <NTabPane name="actions" tab="动作">
          <div class="guide" v-html="md.render(actionsDoc)" />
        </NTabPane>
        <NTabPane name="annotations" tab="注解">
          <div class="guide" v-html="md.render(annotationsDoc)" />
        </NTabPane>
      </NTabs>
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
