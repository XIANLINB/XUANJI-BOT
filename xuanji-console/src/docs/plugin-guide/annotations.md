# 注解

## 一、插件级：@XuanJiPlugin

标注插件命令/事件方法所在的静态类（插件元信息 + 能力声明）。

**字段**：

| 字段 | 说明 |
|---|---|
| id | 插件唯一 ID（**必填**，全局唯一；实际插件多用 `xxx-plugin` 风格，如 `groupadmin-plugin`） |
| name / version / author / description | 插件元信息 |
| permissions | 权限声明：`NETWORK`(联网) / `FILESYSTEM`(读写文件) / `PROACTIVE_MESSAGE`(主动发消息) / `GROUP_ADMIN`(群管/撤回/审批写操作) |
| dependsOn | 依赖的能力（如 EconomyService） |
| rateLimit | 消息触发频率限制（秒，0=不限制） |
| platforms | 限定平台（空=全部） |
| defaultBot | 插件默认机器人 botKey（非事件场景主动发送/定时任务用；事件链路自动携带当前机器人，无需声明） |

```java
@XuanJiPlugin(id = "groupadmin-plugin", name = "群管插件", version = "1.0.0",
    author = "XuanJi Team", description = "说明",
    permissions = { Perm.PROACTIVE_MESSAGE, Perm.GROUP_ADMIN })
public static class Commands { ... }
```

## 二、命令：@Command

标注命令方法（合并 `@GroupMessage`/`@PrivateMessage` + `@MessageFilter` 的语法糖，一条消息→一个方法）。

**字段**：

| 字段 | 说明 |
|---|---|
| value / cmd | 触发词（支持正则如 `"签到|打卡"`；空串=匹配所有消息；两者并存 `cmd` 优先） |
| scope | `Command.Scope.GROUP`(仅群) / `PRIVATE`(仅私聊) / `BOTH`(默认，群+私聊) |
| roles | 限定角色，如 `{"owner","admin"}`（空=不限制） |
| at | `AtMode.IGNORE`(默认不关心) / `NEED`(必须@机器人) / `NOT`(不能@机器人) |
| order | 优先级 |
| groups / senders | 限定群 / 限定发送者 |
| startWith / endWith | 前缀 / 后缀触发 |
| media / mediaTypes | 富媒体过滤（media=MediaMode：`NEED`=必须含 / `NOT`=必须纯文本 / `IGNORE`；mediaTypes=限定具体类型） |
| platforms / invert | 限定平台 / 反转过滤 |

```java
@Command(value = "#禁言", scope = Command.Scope.GROUP, roles = {"owner", "admin"})
public String mute(GroupMessageEvent e, Bot bot,
                   @Arg(value = "分钟", required = false) Integer minutes) { ... }
```

## 三、命令参数：@Arg

标注 @Command 方法的参数，框架自动从消息解析（支持 int/long/String 类型转换；QQ 的 @占位已剥掉）。

**字段**：`value`(参数名，显示在帮助) / `required`(必填，默认 true) / `missing`(缺参提示) / `rest`(取剩余全部含空格，仅最后一个 @Arg)

```java
@Command("回声")
public String echo(@Arg(value = "内容", required = true, rest = true) String content) { ... }
```

## 四、消息监听：@GroupMessage / @PrivateMessage / @OnMessage

| 注解 | 用途 | 字段 |
|---|---|---|
| `@GroupMessage` | 收到群聊消息 | `order` / `platforms` |
| `@PrivateMessage` | 收到单聊消息 | `order` / `platforms` |
| `@OnMessage` | 更底层原始消息监听（自动回复/日志/风控） | `type`(如 "message/group") / `priority` / `block`(是否阻断后续链) / `groupOnly` / `privateOnly` |

```java
@GroupMessage(order = 200)
public void onGroupMsg(GroupMessageEvent e, Bot bot) { ... }
```

## 五、事件监听：@GroupEvent / @PrivateEvent

| 注解 | 用途 | 字段 |
|---|---|---|
| `@GroupEvent` | 群系统事件（进群/退群/入群申请，`getEventType()` 区分） | `order` / `platforms` |
| `@PrivateEvent` | 私聊系统事件（好友添加/删除） | `order` / `platforms` |

```java
@GroupEvent(order = 10)
public void onGroupEvent(GroupMessageEvent e, Bot bot) {
    if ("GROUP_MEMBER_ADD".equals(e.getEventType())) { ... }
}
```

## 六、消息过滤：@MessageFilter

配合 `@GroupMessage`/`@PrivateMessage` 使用（普通命令用 `@Command` 已内置，无需单独使用）：

`cmd` / `startWith` / `endWith` / `at` / `groups` / `senders` / `roles` / `platforms` / `media` / `mediaTypes` / `invert`

## 七、权限与限流

| 注解 | 用途 | 字段 |
|---|---|---|
| `@RequireRole` | 权限要求（裁决：黑名单 → 特权 → 平台角色 → 权限点） | `value`(角色 BOT_MASTER/SUPER_ADMIN/OWNER/ADMIN/MEMBER) / `permissions`(权限点，OR 关系) |
| `@RateLimit` | 限流 | `count`(窗口内次数) / `seconds`(窗口秒) / `scope`(user/group/global) |
| `@GroupOnly` | 仅群聊响应 | — |
| `@PrivateOnly` | 仅私聊响应（与 @GroupOnly 互斥） | — |

```java
@Command(value = "签到", scope = Command.Scope.GROUP)
@RateLimit(count = 1, seconds = 5)   // 同一用户 5 秒内限 1 次
public String sign(GroupMessageEvent e) { ... }
```
