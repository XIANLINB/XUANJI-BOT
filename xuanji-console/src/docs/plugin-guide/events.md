# 事件

插件通过注解监听平台事件，事件对象均为 SDK 类型化类（`getXxx()` 直接取字段，无需解析原始 JSON）。

## 一、事件注解总览

| 注解 | 监听什么 | 事件对象 |
|---|---|---|
| `@GroupMessage` | 群聊消息 | `GroupMessageEvent` |
| `@PrivateMessage` | 单聊消息 | `PrivateMessageEvent` |
| `@OnMessage` | 任意消息（群+私聊，原始监听） | `MessageEvent` |
| `@GroupEvent` | 群系统事件（进群/退群/入群申请…） | `GroupMessageEvent`（`getEventType()` 区分） |
| `@PrivateEvent` | 私聊系统事件（好友添加/删除） | `PrivateMessageEvent`（`getEventType()` 区分） |

## 二、群事件清单（@GroupEvent）

用 `e.getEventType()` 区分：

| 事件类型 | 触发时机 | 关键字段 |
|---|---|---|
| `GROUP_MEMBER_ADD` | 成员进群 | groupId / senderId / senderName |
| `GROUP_MEMBER_REMOVE` | 成员退群 | groupId / senderId |
| `GROUP_JOIN_REQUEST` | 用户申请入群 | groupId / senderId + `getJoinRequestInfo()` 全量字段 |
| `GROUP_ADD_ROBOT` | 机器人被拉入群 | groupId |
| `GROUP_DEL_ROBOT` | 机器人被移出群 | groupId |
| `GROUP_MSG_REJECT` | 群消息接收被**关闭** | groupId |
| `GROUP_MSG_RECEIVE` | 群消息接收被**开启** | groupId |

> 平台事件统一经过框架分发（含多机器人），插件 `@GroupEvent` 都能收到。

## 三、私聊事件清单（@PrivateEvent）

| 事件类型 | 触发时机 |
|---|---|
| `FRIEND_ADD` | 好友添加机器人 |
| `FRIEND_DEL` | 好友删除机器人 |
| （单聊消息） | 用户私聊机器人（@PrivateMessage） |

## 四、消息事件对象字段

### MessageEvent（接口，群聊+单聊通用）

| 方法 | 返回 | 说明 |
|---|---|---|
| `getMessageId()` | String | 消息 ID |
| `getContent()` | String | 原始内容（含 @ 占位） |
| `getPlainText()` | String | 纯文本（已剥掉所有 @占位） |
| `getPlatform()` | String | 平台标识（qq / onebot…） |
| `getChain()` | XuanJiMessage | 解析后的消息链 |
| `getStripped()` | Stripped | 已裁剪命令前缀的文本 |
| `getBotKey()` / `getUnifiedMsgOrigin()` | String | 机器人键 / 消息来源（平台差异时用） |

### GroupMessageEvent（群聊消息 / 群事件）

| 方法 | 返回 | 说明 |
|---|---|---|
| `getGroupId()` | String | 群 openid |
| `getSenderId()` | String | 发送者 member_openid |
| `getSenderName()` | String | 昵称 |
| `getSenderRole()` | String | owner / admin / member |
| `getMentionedUserIds()` | List<String> | **框架已过滤**（不含机器人/自己）的可操作目标，禁言/@ 命令直接用 |
| `getMentionedUsers()` | List<Mention> | 过滤后的目标（含角色） |
| `getAllMentions()` | List<Mention> | 原始 @ 列表（含机器人与自己） |
| `isAtBot()` | boolean | 是否 @ 了机器人 |
| `getEventType()` | String | 群事件类型（普通消息为空串） |
| `getBotId()` | String | 事件所属机器人（多机器人必用） |
| `getJoinRequestInfo()` | JoinRequest | 入群申请完整字段（仅 GROUP_JOIN_REQUEST） |
| `getStripped()` / `getChain()` | — | 裁剪前缀文本 / 消息链 |

`Mention`：`record Mention(String userId, boolean bot, boolean isYou, String role)`
（`userId`=成员 openid，`bot`=是否机器人，`isYou`=是否机器人自己，`role`=成员角色）

### PrivateMessageEvent（单聊消息 / 私聊事件）

`getMessageId()` / `getSenderId()` / `getSenderName()` / `getMessageType()` / `getPlainText()` / `getChain()` / `getStripped()` / `getEventType()`

### Stripped（已裁剪命令前缀）

`record Stripped(String content, String prefix, boolean appel, boolean hasAt, boolean atSelf)`

## 五、入群申请事件（GROUP_JOIN_REQUEST）

`e.getJoinRequestInfo()` 返回 `JoinRequest`（框架已解析 verify_info 两种数据源差异，审批判定由插件实现）：

| 方法 | 返回 | 说明 |
|---|---|---|
| `memberOpenid()` | String | 申请者 openid |
| `username()` | String | 昵称 |
| `applyAt()` / `applySource()` | String | 申请时间 / 来源（self_apply…） |
| `joinRequestId()` | String | 申请 ID（**审批必传**） |
| `isQaMode()` | boolean | 是否设置了入群问题 |
| `getQuestion()` / `getAnswer()` | String | 问题 / 申请者填写的答案 |
| `getVerifyMessage()` | String | 验证消息（无问题时即填写内容） |
| `getMethod()` | String | verify_message / admin_review_qa |
| `verifyInfo()` / `verifyParsed()` | Map | 平台原始 / 框架解析后的验证信息 |
