# QQ 机器人（qqbot 适配器）· 消息类型构建示例大全

> 对应代码：`xuanji-adapter-qqbot` 模块
> - 构建器：`util/MarkdownBuilder` `util/KeyboardBuilder` `util/ArkBuilder` `util/EmbedBuilder` `util/CardBuilder` `util/MsgBuilder`
> - 发送器：`api/MessageSender`（Spring 注入即可用）
> - 消息体：`dto/SendMessageRequest`
>
> 所有示例基于 `MessageSender` 依赖注入：
> ```java
> @Component
> public class Demo {
>     private final MessageSender sender;   // 自动注入
>     public Demo(MessageSender sender) { this.sender = sender; }
> }
> ```

---

## 1. 文本消息（最基础）

```java
// —— 群聊（主动发送，无需回复某条消息）——
sender.sendGroupText("群 openid", "签到成功！");
// 群聊（被动回复，携带 msg_id 可在 5 分钟窗口内回复）——
sender.sendGroupText("群 openid", "签到成功！", "消息 msg_id");

// —— 单聊（C2C）——
sender.sendC2cText("用户 openid", "你好！");
sender.sendC2cText("用户 openid", "你好！", "消息 msg_id");
```

## 2. Markdown 消息

```java
// MarkdownBuilder 链式构建
String md = MarkdownBuilder.create()
        .h1("每日签到")
        .text("连续签到 **7** 天获得徽章")
        .divider()
        .bullet("今日积分 +10")
        .bullet("连续签到天数：7")
        .bold("积分", "100")
        .quote("积分可在商城兑换道具")
        .build();

// 发送（群聊）
sender.sendGroupMarkdown("群 openid", md, null, "消息 msg_id");   // 被动回复（带键盘时传 null 即可）
sender.sendGroupMarkdown("群 openid", md, null);                  // 主动发送
// 发送（单聊）
sender.sendC2cMarkdown("用户 openid", md, null, null);
```

## 3. Markdown + 键盘按钮（最常用组合）

```java
// 键盘构建：两个按钮一行
String keyboard = KeyboardBuilder.create()
        .addCommandButton("btn_sign", "签到", "sign")     // 指令按钮：点击触发指令 sign
        .addCommandButton("btn_rank", "排行榜", "rank")
        .build(2);                                        // 每行 2 个按钮

String md = MarkdownBuilder.create()
        .h2("签到成功")
        .text("今日积分 +10，连续签到 7 天")
        .build();

// 群聊发送：Markdown + 键盘（被动回复）
sender.sendGroupMarkdown("群 openid", md, keyboard, "消息 msg_id");
// 单聊发送：Markdown + 键盘（主动）
sender.sendC2cMarkdown("用户 openid", md, keyboard);
```

**任意混合布局（row() 显式分行，每行按钮数自由定义）：**

```java
String kb = KeyboardBuilder.create()
        .row(r -> r.button("a", "确定", "ok")
                   .button("b", "取消", "cancel")
                   .link("c", "文档", "https://qq.com"))          // 第一行 3 个
        .row(r -> r.callback("d", "点赞", "like")
                   .commandAuto("e", "签到", "sign"))              // 第二行 2 个（enter 直接发送）
        .row(r -> r.buttonWithPermission("f", "管理面板", "admin", 3,   // 第三行 1 个
                                         0, List.of("openid_xxx"), List.of("role_id")))  // 仅指定用户可用
        .build();
// 注意：一旦使用 row()，addButton 追加的按钮不再参与布局，全部按钮需在 row() 内声明
```

**按钮类型一览（KeyboardBuilder）：**

| 方法 | 按钮类型 | 说明 |
|---|---|---|
| `addButton(id, label, data)` | 指令按钮 | 点击在输入框插入 @bot data，用户手动发送 |
| `addCommandButton(id, label, data, style)` | 指令按钮 | 高级指令按钮（enter/reply 可选） |
| `addCallbackButton(id, label, data)` | 回调按钮 | type=1，点击回调后台，data 传给后台（触发 INTERACTION_CREATE） |
| `addLinkButton(id, label, url)` | 跳转按钮 | type=0，点击跳转 URL |
| `addImageButton(id, label, data)` | 选图按钮 | anchor=1，点击唤起手Q选图器（仅单聊 8983+） |
| `build()` / `build(maxPerRow)` | — | 自动均分分行（每行 maxPerRow 个，1-5） |
| `row(r -> ...)` | — | 显式分行，任意混合布局（RowBuilder: button/callback/link/commandAuto/commandReply/imagePicker/buttonWithPermission） |

**按钮字段（RenderData / Action / Permission）已完整支持：** 样式 style(0灰线框/1蓝线框/2白字/3蓝底白字)、permission type(0指定用户/1管理员/2所有人) + specify_user_ids/specify_role_ids、unsupport_tips、click_limit、enter/reply/anchor。默认 permission=所有人、unsupport_tips="暂不支持"。

## 4. Ark 消息（JSON 卡片模板，需在开放平台申请模板）

Ark 的 kv 键名是**固定的模板占位符**，开发者只需要填 value。框架提供模板 23/24/37 的语义化方法：

```java
// —— 模板 23（文本链接列表：#DESC# / #PROMPT# / #LIST#）——
String ark23 = ArkBuilder.create23()
        .desc("需求标题：UI问题解决")
        .prompt("点击下列动作直接扭转状态到：")
        .listItem("已评审")                                   // 列表项（仅 desc）
        .listItemLink("已排期", "https://qun.qq.com")        // 列表项（desc + 跳转链接）
        .listItemLink("开发中", "https://qun.qq.com")
        .build();
sender.sendGroupArk("群 openid", ark23, "msg_id");

// —— 模板 24（文本弹窗：#DESC#/#PROMPT#/#TITLE#/#METADESC#/#IMG#/#LINK#/#SUBTITLE#）——
String ark24 = ArkBuilder.create24()
        .title("系统通知")
        .desc("你的机器人已上线")
        .prompt("点击查看")
        .metaDesc("今日新增 100 个群")
        .img("https://pub.idqqimg.com/xx.png")
        .link("https://qq.com")
        .subtitle("子标题")
        .build();
sender.sendC2cArk("用户 openid", ark24);

// —— 模板 37（大图卡片：#PROMPT#/#METATITLE#/#METASUBTITLE#/#METACOVER#/#METAURL#）——
String ark37 = ArkBuilder.create37()
        .prompt("通知提醒")
        .metaTitle("周报")
        .metaSubtitle("2026 年第 8 期")
        .metaCover("https://vfiles.gtimg.cn/xx.jpg")
        .metaUrl("https://qq.com")
        .build();

// —— 通用模板：任意模板号 + kv / obj 嵌套 ——
String ark = ArkBuilder.create(1)
        .kv("#TITLE#", "签到")
        .kv("#DESC#", "今日积分 +10")
        .obj("#LIST#", o -> o.item("desc", "已完成", "link", "https://qq.com"))
        .build();
sender.sendGroupArk("群 openid", ark, null);
```

**模板占位符速查：**

| 模板 | 占位符 | 语义方法 |
|---|---|---|
| 23 | `#DESC#` `#PROMPT#` `#LIST#` | `desc()` `prompt()` `listItem()` `listItemLink()` |
| 24 | `#DESC#` `#PROMPT#` `#TITLE#` `#METADESC#` `#IMG#` `#LINK#` `#SUBTITLE#` | `desc()` `prompt()` `title()` `metaDesc()` `img()` `link()` `subtitle()` |
| 37 | `#PROMPT#` `#METATITLE#` `#METASUBTITLE#` `#METACOVER#` `#METAURL#` | `prompt()` `metaTitle()` `metaSubtitle()` `metaCover()` `metaUrl()` |

## 5. Embed 消息（频道子频道专用）

```java
String embed = EmbedBuilder.create()
        .title("服务器公告")
        .prompt("点击查看详情")
        .thumbnail("https://example.com/logo.png")
        .field("维护时间", "每周二 03:00", true)
        .field("影响范围", "全部子频道", true)
        .build();

// 发送到子频道（msg_type=2 markdown 或 embed 走 sendGuildMessage 需按模板）
// 框架提供子频道文本/Markdown 发送：
sender.sendGuildText("子频道 id", "服务器公告：今晚 22:00 维护", null);
sender.sendGuildMarkdown("子频道 id", md, keyboard, null);
```

## 6. 图文卡片（msg_type=8，群聊卡片消息）

```java
String card = CardBuilder.create()
        .title("商城上新")
        .description("限时 7 折，全场包邮")
        .picUrl("https://example.com/product.png")
        .url("https://example.com/mall")
        .build();

sender.sendGroupCard("群 openid", card, "消息 msg_id");   // 被动
sender.sendGroupCard("群 openid", card);                  // 主动
```

## 7. 图片 / 语音 / 视频媒体消息

```java
// 简化 API（自动上传 + 发送）
sender.sendGroupImage("群 openid", "https://example.com/a.png", "msg_id");  // 图片
sender.sendGroupAudio("群 openid", "https://example.com/a.mp3", "msg_id");  // 语音
sender.sendGroupVideo("群 openid", "https://example.com/a.mp4", "msg_id");  // 视频
sender.sendC2cImage("用户 openid", "https://example.com/a.png", null);      // 单聊

// 完整 API（先上传拿 file_info，再发送——可复用 file_info）
String fileInfo = sender.uploadMedia("群 openid", 1, "https://example.com/a.png");
ObjectNode result = sender.sendGroupMedia("群 openid", fileInfo, "msg_id");
// file_type: 1=图片 2=视频 3=语音 4=文件
```

## 8. MsgBuilder 统一构建（一条链搞定主动/被动）

```java
// —— 文本（主动）——
SendMessageRequest req = MsgBuilder.text("你好").buildActive();
sender.sendGroupMessage("群 openid", req);

// —— 文本（被动回复）——
sender.sendGroupMessage("群 openid", MsgBuilder.text("回复你").passive("msg_id").build());

// —— Markdown + 键盘（被动）——
sender.sendGroupMessage("群 openid",
        MsgBuilder.markdown()
                .h2("欢迎新人")
                .text("新人请先看群规")
                .keyboard(kb -> kb.addCommandButton("rules", "群规", "rules"))
                .buildPassive("msg_id"));

// —— Ark（主动）——
sender.sendC2cMessage("用户 openid",
        MsgBuilder.ark(23).kv("desc", "提示").buildActive());

// —— 媒体（file_info）——
sender.sendGroupMessage("群 openid",
        MsgBuilder.media("file_info 字符串").passive("msg_id").build());
```

## 9. 流式消息（单聊，逐步输出长内容）

```java
// 首帧：streamMsgId 传 null
ObjectNode first = sender.sendC2cStream("用户 openid", 0, "第一段", null, null);
String streamId = first.path("stream_msg_id").asText();
// 续帧：携带首帧返回的 stream_msg_id
sender.sendC2cStream("用户 openid", 0, "第二段", streamId, null);
```

## 10. 私信（DMS，频道私信场景）

```java
// 第一步：创建私信会话（recipientId=用户 openid，sourceGuildId=发起频道 id）
ObjectNode dms = sender.createDms("频道 id", "用户 openid");
String dmsGuildId = dms.path("guild_id").asText();
// 第二步：发送私信
sender.sendDmsText(dmsGuildId, "私信内容", null);
// 撤回私信
sender.retractDmsMessage(dmsGuildId, "消息 id");
```

## 11. 子频道消息

```java
sender.sendGuildText("子频道 id", "公告内容", null);
sender.sendGuildMarkdown("子频道 id", md, keyboard, null);
sender.retractGuildMessage("子频道 id", "消息 id");
```

## 12. 撤回消息

```java
sender.retractGroupMessage("群 openid", "消息 id");   // 群聊（5 分钟窗口内）
sender.retractC2cMessage("用户 openid", "消息 id");   // 单聊（60 分钟窗口内）
sender.retractGuildMessage("子频道 id", "消息 id");   // 子频道
sender.retractDmsMessage("私信会话 id", "消息 id");    // 私信
```

## 13. 互动事件响应（按钮回调 ACK）

**官方机制**（已核实文档）：
- 收到 `INTERACTION_CREATE` 事件后，**仅 `type=11`（消息按钮）与 `type=12`（快捷菜单）必须调用** `PUT /interactions/{id}` 回应，否则客户端会一直 loading 直到超时
- 其余类型（13 消息反馈 / 14 清空会话 / 15 故事集 / 16 切换模型 / 18/19/20 授权）**无需回应**
- 同一 `interaction_id` 只能回应一次，超时后失效
- 事件体的 `id` 字段即 `interaction_id`；`data.resolved.button_data` 是按钮的 data（按钮点击时携带）

**框架已自动处理**：`GuildInteractionHandler` 收到 type=11/12 自动回执 code=0，无需插件干预：

```java
// 手动响应（如需返回业务失败状态）：
// code: 0=成功 1=操作失败 2=操作频繁 3=重复操作 4=没有权限 5=仅管理员操作
sender.replyInteraction("interaction_id（来自事件体 id）", 0, null);
```

## 14. 引用消息（MessageReference）

```java
// 引用某条消息发送（以"引用原消息"样式展示，关联上下文）
sender.sendGroupMessage("群 openid",
        MsgBuilder.text("同意该方案")
                .reference("被引用的消息 id")     // 主动引用
                .build());

// 被动回复 + 引用 可同时使用
sender.sendGroupMessage("群 openid",
        MsgBuilder.text("收到，已处理")
                .passive("被回复的消息 id")
                .reference("被引用的消息 id")
                .build());

// Markdown + 引用
sender.sendGroupMessage("群 openid",
        MsgBuilder.markdown().text("处理结果如下").reference("msg_id_xxx").buildPassive("msg_id_yyy"));
```

## 15. 插件侧快捷回复（QqXjBot / C2cXjBot）

```java
// 插件 @GroupMessage 方法中，通过 CommandRegistry 上下文拿到 bot：
@GroupMessage
public void onMsg(GroupMessageEvent e, QqXjBot bot) {
    bot.reply("收到：你好");                 // 文本回复
    bot.replyMarkdown("**加粗**回复");       // Markdown 回复
    bot.replyMarkdown(md, keyboard);         // Markdown + 键盘（kbJson 字符串）
    bot.replyImage("https://example.com/a.png");
    bot.replyAudio("https://example.com/a.mp3");
    bot.replyArk(23, arkJson);               // Ark 模板消息
    bot.replyCard(cardJson);                 // 图文卡片（msg_type=8）
    bot.uploadImage("本地路径");             // 上传媒体，返回 file_info
}
```

## 附：主动 vs 被动消息

| 类型 | 语义 | 窗口 | 用法 |
|---|---|---|---|
| 主动消息 | 机器人主动发起（定时/事件触发） | 无限制但受频控（40034100） | 不传 `msg_id` |
| 被动回复 | 回复用户消息 | 单聊 60 分钟 / 群聊·子频道 5 分钟 | 传 `msg_id`（或 `event_id`） |

> 群聊/子频道被动回复建议同时配 `msg_seq` 做去重（框架 `addMsgSeq()` 已内置自增序号）。

## 附：邀请分享链接（用户邀请统计）

**官方机制**（已核实文档）：`POST /v2/generate_url_link` + `callback_data`（≤32 字符）。
为每个分享者生成带唯一 `callback_data` 的专属链接；**用户通过该链接把机器人加为好友时，
FRIEND_ADD 事件体的 `scene_param` 会原样带回 `callback_data`**——这就是"谁邀请、谁转化"的统计闭环：

```java
// 1. 生成专属链接（callback_data = 分享者标识，可放 userId / 群号 / 渠道码）
String link = sender.generateInviteLink("inviter_" + userId);
// → https://qun.qq.com/qunpro/robot/qunshare?robot_appid=...&data=...

// 2. 在 FRIEND_ADD 事件处理中还原邀请者（C2cSystemEventHandler / 事件监听处）：
//    ObjectNode data = (ObjectNode) botEvent.platformData();
//    String inviter = data.path("scene_param").asText("");   // == "inviter_xxxxx"
//    String scene   = data.path("scene").asText("");         // 1000~2004 表示来源场景
```

限制：`callback_data` 最长 32 字符；仅好友添加（FRIEND_ADD）透传，邀请进群不适用（群添加无该字段）。

## 附：富媒体上传限制与超时

| file_type | 格式 | 软限制（超过降级为文件） | 硬限制（超过报错） |
|---|---|---|---|
| 1 图片 | png/jpg/gif/webp/bmp | 20 MB | 200 MB |
| 2 视频 | mp4 | 30 MB | 200 MB |
| 3 语音 | silk/mp3/wav/ogg | 20 MB | 200 MB |
| 4 文件 | 任意 | — | 200 MB |

- **超时**：官方建议上传接口超时 ≥ 5 秒；框架 `uploadAndSend*Media` 已用 60 秒超时（官方建议 30–60s 区间）
- **分片上传**（大文件/本地文件）：`uploadPrepare` → 逐片 PUT 预签名 URL → `uploadPartFinish` → 携 `upload_id` 调上传接口合并 → 返回 `file_info`（分片大小/并发由服务端 `upload_config` 下发，默认 5MB）
- `srv_send_msg=true` 可在上传的同时直接发送（占用主动消息频次）；`md5_10m`（前 9.54MB 的 MD5）可做秒传
- 单聊与群聊的上传接口相互隔离，`file_info` 不能跨场景使用；`file_info` 有有效期（ttl），过期需重新上传

## 附：Markdown 官方渲染语法（已核实 + 社区实测）

**支持（官方文档列出）**：`#/##/###` 标题、`**加粗**`、`__下划线加粗__`、`_斜体_`、`*星号斜体*`、`***加粗斜体***`、`~~删除线~~`、`[链接](url)`、`![图片 #208px #320px](url)`（可带尺寸）、无序/有序列表（嵌套二级列表前空 4 空格）、`> 块引用`、`*** 分割线`、多行（空行 + `\u200B`）。

**支持（官方文档未列出，社区多框架实测确认）**：
- **代码块**：` ``` ` 包裹（可带语言标识，如 ```java）——OpenClaw 分块策略、qwen QQ 频道实现均确认渲染引擎支持
- **表格**：`| 列1 | 列2 |` + `| --- | --- |` 分隔行 + 数据行——OpenClaw 明确写"保持表格结构完整性"

**不支持**：HTML 标签。

`MarkdownBuilder` 已覆盖全部支持语法：`h1–h6` / `text` / `bold` / `underlineBold` / `italic` / `asteriskItalic` / `boldItalic` / `strikethrough` / `code` / **`codeBlock`（代码块）** / **`table`（表格）** / `quote` / `bullet` / `numbered` / `link` / `image` / `imageWithSize` / `divider` / `blankLine` / `br`。

```java
String md = MarkdownBuilder.create()
        .h2("示例：代码块 + 表格")
        .codeBlock("java", "System.out.println(\"Hello\");")   // 代码块（带语言）
        .table(new String[]{"命令", "说明"},
               new String[][]{{"help", "查看帮助"}, {"rank", "排行榜"}})
        .build();
```

## 附：Markdown 模板注册中心（借鉴 ElainaBot）

把开放平台审核过的模板集中注册（模板名 → 模板 ID + 参数顺序），插件按名引用、按序填值；`render` 内置**反渲染拆分**（参数值里的 `**bold**`/`` `code` ``/`[text](url)` 等语法符号自动拆散，防止 QQ 把参数值当格式指令渲染——ElainaBot _split_markdown_to_values 思路）：

```java
@Autowired QqMarkdownTemplates markdownTemplates;

// 1. 启动时注册（@PostConstruct / 构造后）
markdownTemplates.register("notice", "101993071_1658748972", "title", "content", "link");

// 2. 插件中使用
ObjectNode payload = markdownTemplates.render("notice", "系统公告", "今晚 22:00 维护", "https://qq.com");
sender.sendGroupMessage("群 openid", SendMessageRequest.activeMarkdown(payload, null));
// 想保留参数值的格式渲染时用 renderRaw
ObjectNode raw = markdownTemplates.renderRaw("notice", "**重要**公告", "...", null);
```

## 附：主动消息 Markdown 投递分层（借鉴 OpenClaw）

主动消息消耗配额、且 Markdown 渲染有被拒风险（降级纯文本）。建议按场景分层：
- **被动回复**：放心用 Markdown（最稳定，不占主动配额）
- **主动消息**：仅在消息含**表格/代码块**时用 Markdown（渲染收益大），纯文本内容直接用文本消息（省配额、免被拒）

```java
// 主动发送时：无表格/代码块 → 文本；有 → Markdown
if (content.contains("|") && content.contains("---")) {
    sender.sendC2cMarkdown("用户 openid", md, null);
} else {
    sender.sendC2cText("用户 openid", content);
}
```
