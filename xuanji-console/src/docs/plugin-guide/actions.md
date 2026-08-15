# 动作（Bot 门面能力）

插件命令/事件方法注入 `Bot bot` 即可调用框架全部能力；LLM 对话注入 `PluginServices svc`（`svc.chat(...)`）。

**权限闸门**：
- 主动发送（`send*` / `sendToGroup` / `sendToPrivate`）受 `PROACTIVE_MESSAGE` 闸门控制，需 `@XuanJiPlugin(permissions = Perm.PROACTIVE_MESSAGE)`；
- 群管/撤回/审批写操作（`mute*` / `kick*` / `setGroupAdmin` / `approve*` / `recall*`）受 `GROUP_ADMIN` 闸门控制，需 `Perm.GROUP_ADMIN`；
- 被动回复（`reply*`）与只读查询不受限。未声明权限调用会抛 `IllegalStateException`。

## 一、被动回复（reply* 系列，仅事件处理链内可用）

| 方法 | 说明 |
|---|---|
| `reply(String text)` | 回复文本 |
| `replyMarkdown(String md)` / `replyMarkdown(String md, String keyboardJson)` | 回复 Markdown（可附键盘） |
| `replyImage(String url)` / `replyAudio(String url)` / `replyVideo(String url)` | 回复图片/语音/视频 |
| `replyArk(int templateId, String arkJson)` / `replyCard(String cardJson)` | 回复 Ark 卡片 / 图文卡片 |

## 二、主动发送（send* 系列，需 PROACTIVE_MESSAGE 权限）

| 方法 | 说明 |
|---|---|
| `sendGroup(String groupId, String text)` | 群聊文本 |
| `sendGroupMarkdown(String groupId, String md[, String keyboardJson])` | 群聊 Markdown（可附键盘） |
| `sendGroupImage/Audio/Video(String groupId, String url)` | 群聊图片/语音/视频 |
| `sendGroupArk(String groupId, int templateId, String arkJson)` / `sendGroupCard(groupId, json)` | 群聊 Ark / 图文卡片 |
| `sendPrivate(String userId, String text)` / `sendPrivateMarkdown` / `sendPrivateImage` / `sendPrivateAudio` | 私聊文本/Markdown/图片/语音 |
| `sendToGroup(String groupId, XuanJiMessage chain)` | 群消息链，返回 `XuanJiSendReceipt`（`success()`/`platformMsgId()`/`errorMessage()`） |
| `sendToPrivate(String userId, XuanJiMessage chain)` | 私聊消息链，返回回执 |

## 三、媒体上传（返回 URL，供主动发送用）

`uploadImage(String filePath)` / `uploadVideo(...)` / `uploadAudio(...)` / `uploadFile(...)`

## 四、群管动作（返回 OpResult）

> **OpResult**：`ok()` 是否成功；`message()` 成功提示或失败原因（如"禁言被拒：机器人必须为群管理"、"不能禁言群主或管理员"、"QQ平台错误 [10013] …"）。

| 方法 | 说明 |
|---|---|
| `muteGroupMember(String groupId, String memberOpenid, int minutes)` | 禁言（`minutes<=0` 解除） |
| `muteGroupMembers(String groupId, List<String> memberOpenids, int minutes)` | 批量禁言（一个失败不影响其它，汇总结果） |
| `unmuteGroupMembers(String groupId, List<String> memberOpenids)` | 批量解除（= minutes 0 便捷重载） |
| `kickGroupMember(String groupId, String memberOpenid)` | 踢出群成员（平台不支持返回 fail） |
| `setGroupCard(String groupId, String memberOpenid, String card)` | 设置群成员名片 |
| `setGroupAdmin(String groupId, String memberOpenid, boolean setAdmin)` | 设置/取消群管理员 |
| `approveGroupJoinRequest(String groupId, String memberOpenid, String joinRequestId, boolean approve, String reason)` | 入群申请审批（`joinRequestId` 来自 `getJoinRequestInfo().joinRequestId()`） |
| `approveFriendRequest(String openid, boolean approve, String reason)` | 好友申请审批（平台不支持返回 fail） |

## 五、撤回（返回 OpResult）

| 方法 | 说明 |
|---|---|
| `recallGroupMessage(String groupId, String messageId)` | 撤回群消息 |
| `recallRecentMessages(String groupId, String memberOpenid, int count)` | 撤回该成员最近 N 条（count 上限 50；框架负责权限校验→查最近消息→2 分钟窗口判断→逐条撤回汇总） |
| `recallRecentMessages(String groupId, String memberOpenid)` | 撤回最近 1 条 |
| `recallPrivateMessage(String openid, String messageId)` | 撤回单聊消息 |

## 六、平台信息查询（类型化返回；null / 空列表 = 平台不支持或失败）

| 方法 | 返回类型 | 说明 |
|---|---|---|
| `getGroupInfo(String groupId)` | `GroupInfo` | 群信息（远程平台 API，实时） |
| `getLocalGroupInfo(String groupId)` | `GroupInfo` | 群信息（查本地库，**高频用**，免限频） |
| `getBotGroupState(String groupId)` | `BotGroupState` | 机器人在群内状态 |
| `getGroupMuteStatus(String groupId)` | `GroupMuteStatus` | 群禁言状态 |
| `listGroupJoinRequests(String groupId)` | `JoinRequestList` | 入群申请列表（含 next_cursor） |
| `listGroupMembers(String groupId)` | `List<GroupMember>` | 群成员列表（本地库） |
| `listGroups()` | `List<GroupInfo>` | 机器人所在群列表（本地库） |
| `getGroupBotRole(String groupId)` | `GroupBotRole` | 机器人在群内角色 |
| `listUsers()` | `List<UserInfo>` | 单聊用户列表（本地库） |
| `getUserInfo(String openid)` | `UserInfo` | 单用户资料（远程平台接口） |

**类型化对象字段**：

| 对象 | 字段 / 便捷方法 |
|---|---|
| `GroupInfo` | `groupId` / `groupName` / `ownerId` / `memberCount` / `memberMax` / `found`（本地查询是否有档案） |
| `BotGroupState` | `botState`（1=正常 2=被移出 3=群解散 4=被禁言）/ `isOnline()` / `groupName` / `memberCount` |
| `GroupMuteStatus` | `muteExpireAt` / `muteSecondLeft` / `isMuted()` |
| `GroupMember` | `memberId` / `nickname` / `role` / `joinTime` |
| `UserInfo` | `userId` / `nickname` / `remark` / `unionOpenid` / `joinTime` |
| `GroupBotRole` | `role` / `isOwner()` / `isAdmin()` / `isManager()` |
| `JoinRequest` | 见「事件」Tab 第五节 |
| `JoinRequestList` | `requests()`（List<JoinRequest>）/ `nextCursor()` / `isEmpty()` / `size()` |

> 所有类型化对象都有 `raw()` 返回平台原始 Map，平台字段扩展时仍可读取完整数据。

## 七、LLM 对话（PluginServices）

注入 `PluginServices svc`：

| 方法 | 说明 |
|---|---|
| `svc.chat(String user)` | 单轮对话（用户消息），全局默认模型 |
| `svc.chat(String system, String user)` | 带系统指令的单轮对话 |

> 使用全局配置的默认供应商/模型；429/5xx 等瞬态错误框架自动重试。

## 八、Bot 信息查询

`getGroupCount()` / `getUserCount()` / `getBotInfo()`（返回 Map）/ `getTodayFriendAdd()` / `getTodayFriendDel()` / `getTodayGroupAdd()` / `getTodayGroupDel()` / `getTodayMemberAdd(groupId)` / `getTodayMemberDel(groupId)`
