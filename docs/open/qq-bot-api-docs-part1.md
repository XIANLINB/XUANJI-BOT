# QQ Bot API v2 完整文档 - Part 1: 消息 APIs

> 数据来源：QQ 机器人官方文档 (https://bot.q.qq.com/wiki/develop/api-v2/)
> 编译时间：2026-07-26

---

## 目录

1. [发送单聊消息](#1-发送单聊消息)
2. [流式发送单聊消息](#2-流式发送单聊消息)
3. [撤回单聊消息](#3-撤回单聊消息)
4. [发送群聊消息](#4-发送群聊消息)
5. [撤回群聊消息](#5-撤回群聊消息)
6. [发送子频道消息](#6-发送子频道消息)
7. [撤回子频道消息](#7-撤回子频道消息)
8. [频道私信](#8-频道私信)
9. [内嵌格式](#9-内嵌格式)

---

## 1. 发送单聊消息

向指定用户发送私聊消息。

**业务规则：**
- 被动消息有效时间 60 分钟，每个消息最多回复 4 次
- 主动消息频控规则：
  - Bot 维度（发送方）：企业认证/个人身份证认证 10/qps；未认证 5/qps 且 30/qpm
  - 单关系维度（接收方）：20/qpm，每个好友 1 天最多接收 1000 条
- 互动召回消息：在用户主动与机器人对话之后，机器人在未来 30 天内可下发互动召回消息给用户（消息类型与当前机器人拥有的消息类型权限一致），每个周期内可下发一条。分别为：当天、1-3 天、3-7 天、7-30 天，合计：4 个周期。在发消息接口中使用 `is_wakeup` 字段声明使用该能力。

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_openid}/messages` |
| HTTP Method | `POST` |
| 接口频率限制 | 100 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_openid | string | 是 | 用户 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| msg_type | integer | 否 | 消息类型。决定哪个内容字段生效: 0=纯文本(content) 2=Markdown(markdown) 6=输入中状态(input_notify) 7=富媒体(media) |
| content | string | 否 | 文本内容。msg_type=0 时为全文。注意: 传了 markdown 后此字段必须为空 |
| markdown | [MessageMarkdown](#messagemarkdown) | 否 | Markdown 消息。msg_type=2 时必填。注意: 填写此字段后 content/ark 必须全为空 |
| keyboard | [Keyboard](#keyboard) | 否 | 内嵌键盘。短形式只传 id，长形式传 content.rows |
| msg_id | string | 否 | 被动回复的消息 ID。从 C2C_MESSAGE_CREATE 等事件的 d.id 获取，5 分钟内有效 |
| event_id | string | 否 | 被动回复的事件 ID。从事件最外层的 id 获取。与 msg_id 二选一，支持事件："INTERACTION_CREATE"、"C2C_MSG_RECEIVE"、"FRIEND_ADD" |
| msg_seq | integer | 否 | 回复消息的序号，与 msg_id 联合使用，避免相同消息 id 回复重复发送，不填默认是 1。相同的 msg_id + msg_seq 重复发送会失败。 |
| media | [MediaInfo](#mediainfo) | 否 | 富媒体消息。msg_type=7 时填写，file_info 来自 /v2/groups/{group_openid}/files |
| message_reference | [MessageReference](#messagereference) | 否 | 引用回复。填写后以引用形式展示，关联上下文 |
| is_wakeup | boolean | 否 | 指明发送消息为互动召回消息，与 msg_id、event_id 互斥使用 |
| input_notify | [InputNotify](#inputnotify) | 否 | 输入中状态，msg_type=6 时使用 |

#### MessageMarkdown

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| template_id | integer | 否 | 【已废弃】平台 Markdown 模板 ID。使用模板时填写，非模板不传 |
| content | string | 否 | Markdown 内容。支持的格式参考文档：Markdown |
| custom_template_id | string | 否 | 【已废弃】自定义模板 ID，与 template_id 二选一 |

#### Keyboard

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| id | string | 否 | 内嵌键盘模板 ID。使用平台预设模板时填写此字段 |
| content | [KeyboardContent](#keyboardcontent) | 否 | 自定义键盘布局。与 id 互斥，用于自定义按钮 |

#### KeyboardContent

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| rows | [][Row](#row) | 否 | 按钮行列表 |

#### Row

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| buttons | [][Button](#button) | 否 | 行内按钮，从左到右排列 |

#### Button

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| id | string | 否 | 按钮 ID。同一键盘内唯一 |
| render_data | [RenderData](#renderdata) | 否 | 按钮渲染 |
| action | [Action](#action) | 否 | 按钮点击行为 |

#### RenderData

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| label | string | 否 | 按钮文字，最多 10 字符 |
| visited_label | string | 否 | 点击后文字，不传则保持不变 |
| style | integer | 否 | 0=灰线框, 1=蓝线框, 2=白字, 3=蓝底白字 |

#### Action

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| type | integer | 否 | 0：跳转按钮：http 或 小程序；1：回调按钮：回调后台接口, data 传给后台；2：指令按钮：自动在输入框插入 @bot data |
| permission | [Permission](#permission) | 否 | 操作权限 |
| data | string | 否 | 回调数据。type=1/2 时必填 |
| click_limit | integer | 否 | 【已废弃】可点击次数限制。0=无限 |
| unsupport_tips | string | 否 | 版本过低时提示文案 |
| enter | boolean | 否 | 指令按钮可用，点击按钮后直接自动发送 data，仅单聊可用，默认 false。支持版本 8983 |
| reply | boolean | 否 | 指令按钮可用，指令是否带引用回复本消息，默认 false。支持版本 8983 |
| anchor | integer | 否 | 本字段仅在指令按钮下有效，设置后会忽略 action.enter 配置。设置为 1 时，点击按钮自动唤起手Q选图器，其他值暂无效果。（仅支持手机端版本 8983+ 的单聊场景，桌面端不支持） |

#### Permission

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| type | integer | 否 | 0=指定用户, 1=管理员, 2=所有人 |
| specify_user_ids | []string | 否 | 有权限的用户 id 的列表 |
| specify_role_ids | []string | 否 | 有权限的身份组 id 的列表（仅频道可用） |

#### MediaInfo

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_info | string | 否 | 文件数据。来自文件上传接口返回值 |

#### MessageReference

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| message_id | string | 否 | 被引用消息 ID |

#### InputNotify

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| input_type | integer | 否 | 填 1 |
| input_second | integer | 否 | 状态持续时间，最长 60s |

### 请求示例

**文本消息 (msg_type=0)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/messages
{
  "content": "你好，欢迎使用机器人助手！",
  "msg_type": 0,
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**Markdown 消息 (msg_type=2)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/messages
{
  "msg_type": 2,
  "markdown": {
    "content": "# 今日推荐\n\n**精选文章**\n> 知识就是力量，学习永无止境\n\n[点击查看详情](https://example.com)"
  },
  "keyboard": {
    "id": "1070001"
  },
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**输入状态通知 (msg_type=6)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/messages
{
  "msg_type": 6,
  "input_notify": {
    "input_type": 1,
    "input_second": 60
  },
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**富媒体消息 (msg_type=7)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/messages
{
  "msg_type": 7,
  "media": {
    "file_info": "AE86C5D3F0E14B238C656C0F6DD1D0479C"
  },
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID，可用于后续撤回 |
| timestamp | string | 发送时间，RFC3339 东八区 |
| ext_info | [MessageExtInfo](#messageextinfo) | 扩展信息 |

#### MessageExtInfo

| 名称 | 类型 | 描述 |
|------|------|------|
| ref_idx | string | 引用消息索引。对应消息时间 ext 里的 msg_idx 与 ref_msg_idx |

### 响应示例

**消息发送成功**

```json
{
  "id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "timestamp": "2026-07-21T10:30:00+08:00"
}
```

**消息发送成功（含扩展信息）**

```json
{
  "id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "timestamp": "2026-07-21T10:30:00+08:00",
  "ext_info": {
    "ref_idx": "REFIDX_xxxxxxxxxxxxxxxxxxxx=="
  }
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 22006 | 消息类型与内容不匹配 | 请检查 msg_type 与 content 是否对应 |
| 50059 | 输入类型错误 | 请检查输入类型 |
| 304004 | 无权限使用该 ARK 模板 | 请先申请 ARK 模板权限 |
| 304061 | 消息内容无效 | 请检查消息格式是否符合要求 |
| 304062 | 订阅按钮数量达到上限 | 请减少按钮数量 |
| 304064 | 订阅消息未授权 | 请先引导用户授权订阅消息 |
| 304080 | 文件信息无效 | 请检查文件信息格式是否正确 |
| 304103 | 消息 ID 已过期，不能回复 | 请在收到消息后尽快回复 |
| 340067 | 获取机器人信息失败 | 请检查机器人状态 |
| 40034004 | 富媒体信息转存失败 | 请重试 |
| 40034005 | 回复消息 msg_id 已过期 | 请在收到消息后尽快回复 |
| 40034006 | 消息内容违规 | 请修改消息内容后重试 |
| 40034008 | markdown 参数有空值 | 请确保所有 Markdown 参数都有值 |
| 40034009 | markdown 参数有换行符 | 请移除 Markdown 参数中的换行符 |
| 40034010 | 模版参数中不能含有 markdown 语法 | 请使用纯文本参数，不要包含 Markdown 语法 |
| 40034011 | 无效的 markdown 内容 | 请检查 Markdown 语法是否正确 |
| 40034024 | 请求参数 msg_id 无效或越权 | 请检查 msg_id 是否正确 |
| 40034025 | 请求参数 event_id 无效 | 请检查 event_id 是否正确 |
| 40034026 | 请求参数 event_id 已过期 | 请在收到事件后尽快回复 |
| 40034027 | 该事件不支持回复消息 | 请确认事件类型是否支持回复 |
| 40034029 | 内联键盘行/列超限 | 请减少键盘按钮数量 |
| 40034100 | 主动消息发送超过频控限制 | 请降低发送频率或等待配额恢复 |
| 40034105 | 主动消息发送失败，无权限 | 请检查机器人权限设置 |
| 40034106 | 消息不支持该指令类型 | 请检查消息指令类型 |
| 40034108 | 指令参数长度超限 | 请缩短指令参数 |
| 40034109 | 指令参数解析失败 | 请检查指令参数格式 |
| 40034122 | 召回消息已达区间上限 | 召回消息已达上限，无法继续召回 |
| 40034123 | 不支持召回消息 | 该消息不支持召回操作 |
| 40034124 | markdown 消息参数错误 | 请检查 Markdown 参数格式 |
| 40034127 | 无 markdown 模板权限 | 请先申请 Markdown 模板权限 |
| 40034128 | 被动回复时间或次数超限 | 请在收到事件后尽快回复 |
| 40054004 | 无好友关系 | 请先添加好友后再发送私信 |
| 40054005 | 消息被去重 | 请确保每次请求使用不同的 msgseq 值 |
| 40054006 | 验证好友关系失败 | 请重试 |
| 40054007 | 消息长度超限 | 请缩短消息内容 |
| 40054013 | 用户拒收消息 | 用户已拒收消息，无法发送 |
| 40054016 | 机器人已下线 | 请检查机器人状态 |
| 40054018 | 消息过长或异常 | 请缩短消息内容 |
| 50055002 | 消息发送异常，请稍后重试 | 请稍后重试 |

---

## 2. 流式发送单聊消息

流式分批发送单聊消息。每个分片使用相同 `stream_msg_id`，index 从 0 递增。支持 markdown 内容格式。

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_openid}/stream_messages` |
| HTTP Method | `POST` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_openid | string | 是 | 用户 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| input_mode | string | 否 | 输入模式。`append`（默认）：ContentRaw 拼接到 Pending。`replace`：ContentRaw 为当前全量正文，须以上游已下发前缀 SentContent 开头；合并后 Pending 仅存未下发后缀。 |
| input_state | integer | 否 | 输入状态。1=生成中，10=生成结束 |
| index | integer | 否 | 分片序号，从 0 递增 |
| content_type | string | 否 | 内容格式类型。`text`：文本消息；`markdown`：Markdown 消息 |
| content_raw | string | 否 | Markdown 格式的文本内容 |
| event_id | string | 否 | 被动回复事件 ID（与 msg_id 二选一） |
| msg_id | string | 否 | 被动回复消息 ID（与 event_id 二选一） |
| stream_msg_id | string | 否 | 流式消息 ID。第一条由服务端生成并返回，后续分片需携带上一分片返回的 id |
| msg_seq | integer | 否 | 消息序号，用于去重 |
| is_wakeup | boolean | 否 | 是否为召回消息。true 时不校验 msg_id/event_id 有效期 |

### 请求示例

**首片消息 (input_state=1, index=0)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/stream_messages
{
  "input_mode": "replace",
  "input_state": 1,
  "index": 0,
  "content_type": "markdown",
  "content_raw": "正在生成回答，请稍候",
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**续片消息 (input_state=1, index=1)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/stream_messages
{
  "input_mode": "replace",
  "input_state": 1,
  "index": 1,
  "content_type": "markdown",
  "content_raw": "正在生成回答，请稍候。目前已完成大部分内容",
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "stream_msg_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "msg_seq": 1
}
```

**结束片消息 (input_state=10)**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/stream_messages
{
  "input_mode": "replace",
  "input_state": 10,
  "index": 2,
  "content_type": "markdown",
  "content_raw": "正在生成回答，请稍候。目前已完成全部内容，以下是最终结果。",
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "stream_msg_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "msg_seq": 1
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID。首条返回 stream_msg_id，用于后续分片 |
| timestamp | string | 消息发送时间，RFC3339 格式 |
| ext_info | [MessageExtInfo](#messageextinfo-1) | 扩展信息 |
| remain_msg_len | integer | 流式消息剩余长度（字符数） |

#### MessageExtInfo

| 名称 | 类型 | 描述 |
|------|------|------|
| ref_idx | string | 引用消息索引。对应消息时间 ext 里的 msg_idx 与 ref_msg_idx |

### 响应示例

**首片响应（返回 stream_msg_id）**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-07-21T10:00:00+08:00",
  "ext_info": {
    "ref_idx": "REFIDX_xxxxxxxxxxxxxxx=="
  }
}
```

**续片/结束片响应**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-07-21T10:00:01+08:00",
  "ext_info": {
    "ref_idx": "REFIDX_xxxxxxxxxxxxxxx=="
  }
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 40007 | 已下发内容前缀不可修改 | 请保持已下发内容前缀一致 |
| 50001 | 服务内部错误 | 请稍后重试 |
| 50002 | 频率限制 | 请降低调用频率 |

---

## 3. 撤回单聊消息

撤回机器人发送给当前用户的消息。发送超过 2 分钟的消息不可撤回。成功返回 HTTP 200，无响应体。

**业务规则：**
- 发送超出 2 分钟的消息不可撤回

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_openid}/messages/{message_id}` |
| HTTP Method | `DELETE` |
| 接口频率限制 | 10 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_openid | string | 是 | 用户 OpenID |
| message_id | string | 是 | 消息 ID |

### 请求示例

```json
DELETE /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/messages/0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
```

### 响应参数

无响应体。

### 响应示例

```json
{}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 306009 | 用户 openid 无效 | 请检查 user_openid 是否正确 |
| 40061001 | 请求参数无效 | 请检查请求参数格式 |
| 40061002 | 请求参数 msgid 无效 | 请检查 msgid 格式是否正确 |
| 40064004 | 已超出消息撤回时限 | 消息发送超过 2 分钟后不可撤回 |

---

## 4. 发送群聊消息

向指定群发送消息。支持文本/Markdown/ARK/富媒体等类型，可附带内嵌键盘。

**业务规则：**
- 群消息不支持流式参数
- 被动消息有效时间 5 分钟，每个消息最多回复 5 次
- 主动消息频控规则：
  - Bot 维度（发送方）：企业认证/个人身份证认证 60/qpm；未认证 30/qpm
  - 单关系维度（接收方）：20/qpm，每个群 1 天最多接收 1000 条

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_openid}/messages` |
| HTTP Method | `POST` |
| 接口频率限制 | 100 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_openid | string | 是 | 群 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| msg_type | integer | 否 | 消息类型。决定哪个内容字段生效: 0=纯文本(content) 2=Markdown(markdown) 7=富媒体(media) |
| content | string | 否 | 文本内容。msg_type=0 时为全文。注意: 传了 markdown 后此字段必须为空 |
| markdown | [MessageMarkdown](#messagemarkdown-1) | 否 | Markdown 消息。msg_type=2 时必填。注意: 填写此字段后 content/ark 必须全为空 |
| keyboard | [Keyboard](#keyboard-1) | 否 | 内嵌键盘。短形式只传 id，长形式传 content.rows |
| msg_id | string | 否 | 被动回复的消息 ID。从 GROUP_AT_MESSAGE_CREATE 等事件的 d.id 获取，5 分钟内有效 |
| event_id | string | 否 | 被动回复的事件 ID。从事件最外层的 id 获取。与 msg_id 二选一，支持事件："INTERACTION_CREATE"、"GROUP_ADD_ROBOT"、"GROUP_MSG_RECEIVE" |
| msg_seq | integer | 否 | 回复消息的序号，与 msg_id 联合使用，避免相同消息 id 回复重复发送，不填默认是 1。相同的 msg_id + msg_seq 重复发送会失败。 |
| media | [MediaInfo](#mediainfo-1) | 否 | 富媒体消息。msg_type=7 时填写，file_info 来自 /v2/groups/{group_openid}/files |
| message_reference | [MessageReference](#messagereference-1) | 否 | 引用回复。填写后以引用形式展示，关联上下文 |
| is_wakeup | boolean | 否 | 指明发送消息为互动召回消息，与 msg_id、event_id 互斥使用 |

#### MessageMarkdown

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| template_id | integer | 否 | 【已废弃】平台 Markdown 模板 ID。使用模板时填写，非模板不传 |
| content | string | 否 | Markdown 内容 |
| custom_template_id | string | 否 | 【已废弃】自定义模板 ID，与 template_id 二选一 |

#### Keyboard

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| id | string | 否 | 内嵌键盘模板 ID。使用平台预设模板时填写此字段 |
| content | [KeyboardContent](#keyboardcontent) | 否 | 自定义键盘布局。与 id 互斥，用于自定义按钮 |

#### KeyboardContent

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| rows | [][Row](#row) | 否 | 按钮行列表 |

#### Row

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| buttons | [][Button](#button) | 否 | 行内按钮，从左到右排列 |

#### Button

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| id | string | 否 | 按钮 ID。同一键盘内唯一 |
| render_data | [RenderData](#renderdata) | 否 | 按钮渲染 |
| action | [Action](#action) | 否 | 按钮点击行为 |

#### RenderData

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| label | string | 否 | 按钮文字，最多 10 字符 |
| visited_label | string | 否 | 点击后文字，不传则保持不变 |
| style | integer | 否 | 0=灰线框, 1=蓝线框, 2=白字, 3=蓝底白字 |

#### Action

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| type | integer | 否 | 0：跳转按钮：http 或 小程序；1：回调按钮：回调后台接口, data 传给后台；2：指令按钮：自动在输入框插入 @bot data |
| permission | [Permission](#permission) | 否 | 操作权限 |
| data | string | 否 | 回调数据。type=1/2 时必填 |
| click_limit | integer | 否 | 【已废弃】可点击次数限制。0=无限 |
| unsupport_tips | string | 否 | 版本过低时提示文案 |
| enter | boolean | 否 | 指令按钮可用，点击按钮后直接自动发送 data，仅单聊可用，默认 false。支持版本 8983 |
| reply | boolean | 否 | 指令按钮可用，指令是否带引用回复本消息，默认 false。支持版本 8983 |
| anchor | integer | 否 | 本字段仅在指令按钮下有效，设置后会忽略 action.enter 配置。设置为 1 时，点击按钮自动唤起手Q选图器，其他值暂无效果。（仅支持手机端版本 8983+ 的单聊场景，桌面端不支持） |

#### Permission

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| type | integer | 否 | 0=指定用户, 1=管理员, 2=所有人 |
| specify_user_ids | []string | 否 | 有权限的用户 id 的列表 |
| specify_role_ids | []string | 否 | 有权限的身份组 id 的列表（仅频道可用） |

#### MediaInfo

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_info | string | 否 | 文件数据。来自文件上传接口返回值 |

#### MessageReference

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| message_id | string | 否 | 被引用消息 ID |

### 请求示例

**文本消息 (msg_type=0)**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/messages
{
  "msg_type": 0,
  "content": "欢迎使用本群助手，有什么可以帮你的吗？",
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**Markdown + 键盘消息 (msg_type=2)**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/messages
{
  "msg_type": 2,
  "markdown": {
    "content": "## 每日签到\n\n今日签到成功！获得 **50** 积分\n连续签到 **7** 天"
  },
  "keyboard": {
    "content": {
      "rows": [
        {
          "buttons": [
            {
              "id": "btn_signin",
              "render_data": {
                "label": "签到",
                "style": 1
              },
              "action": {
                "type": 2,
                "permission": {
                  "type": 2
                },
                "data": "/签到",
                "enter": true
              }
            }
          ]
        }
      ]
    }
  },
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 1
}
```

**富媒体消息 (msg_type=7)**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/messages
{
  "msg_type": 7,
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 2,
  "media": {
    "file_info": "AE86C5D3F0E14B238C656C0F6DD1D0479C"
  },
  "message_reference": {
    "message_id": "ROBOT1.0_yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"
  }
}
```

**卡片消息 (msg_type=8)**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/messages
{
  "msg_type": 8,
  "card": {
    "type": "tuwen",
    "content": {
      "description": "2分钟完成注册并创建QQBot 无缝对接OpenClaw",
      "pic_url": "https://qqminiapp.cdn-go.cn/qq-open-platform/9b9327f1/assets/33-2-GiI9drV8.png",
      "title": "QQ开放平台",
      "url": "https://q.qq.com/#/"
    }
  },
  "msg_id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "msg_seq": 22
}
```

> 当 type 为 `tuwen` 时会发送一个包括标题、描述、图片、跳转链接的消息。title 表示卡片消息的标题，description 表示卡片消息的描述，pic_url 表示卡片消息中出现的图片，url 表示卡片消息中的跳转链接。

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID，可用于后续撤回 |
| timestamp | string | 发送时间，RFC3339 东八区 |
| ext_info | [MessageExtInfo](#messageextinfo-2) | 扩展信息 |

#### MessageExtInfo

| 名称 | 类型 | 描述 |
|------|------|------|
| ref_idx | string | 引用消息索引。对应消息时间 ext 里的 msg_idx 与 ref_msg_idx |

### 响应示例

```json
{
  "id": "ROBOT1.0_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
  "timestamp": "2026-07-21T10:00:00+08:00",
  "ext_info": {
    "ref_idx": "REFIDX_xxxxxxxxxxxxxxx=="
  }
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 22006 | 消息类型与内容不匹配 | 请检查 msg_type 与 content 是否对应 |
| 304004 | 无权限使用该 ARK 模板 | 请先申请 ARK 模板权限 |
| 304036 | 无 Markdown 模板权限 | 请先申请 Markdown 模板权限 |
| 304061 | 消息内容无效 | 请检查消息格式是否符合要求 |
| 304064 | 订阅消息未授权 | 请先引导用户授权订阅消息 |
| 304080 | 文件信息无效 | 请检查文件信息格式是否正确 |
| 304103 | 消息 ID 已过期，不能回复 | 请在收到消息后尽快回复 |
| 305007 | 键盘样式参数错误 | 请检查 keyboard 参数 |
| 340069 | 消息类型无效 | 请检查 msg_type 取值 |
| 40034004 | 富媒体信息转存失败 | 请重试 |
| 40034005 | 回复消息 msg_id 已过期 | 请在收到消息后尽快回复 |
| 40034006 | 消息内容违规 | 请修改消息内容后重试 |
| 40034008 | markdown 参数有空值 | 请确保所有 Markdown 参数都有值 |
| 40034009 | markdown 参数有换行符 | 请移除 Markdown 参数中的换行符 |
| 40034010 | 模版参数中不能含有 markdown 语法 | 请使用纯文本参数，不要包含 Markdown 语法 |
| 40034011 | 无效的 markdown 内容 | 请检查 Markdown 语法是否正确 |
| 40034024 | 请求参数 msg_id 无效或越权 | 请检查 msg_id 是否正确 |
| 40034025 | 请求参数 event_id 无效 | 请检查 event_id 是否正确 |
| 40034026 | 请求参数 event_id 已过期 | 请在收到事件后尽快回复 |
| 40034027 | 该事件不支持回复消息 | 请确认事件类型是否支持回复 |
| 40034029 | 内联键盘行/列超限 | 请减少键盘按钮数量 |
| 40034100 | 主动消息发送超过频控限制 | 请降低发送频率或等待配额恢复 |
| 40034101 | 机器人非群成员 | 请先将机器人加入群聊 |
| 40034105 | 主动消息发送失败，无权限 | 请检查机器人权限设置 |
| 40034106 | 消息不支持该指令类型 | 请检查消息指令类型 |
| 40034108 | 指令参数长度超限 | 请缩短指令参数 |
| 40034109 | 指令参数解析失败 | 请检查指令参数格式 |
| 40034124 | markdown 消息参数错误 | 请检查 Markdown 参数格式 |
| 40034127 | 无 markdown 模板权限 | 请先申请 Markdown 模板权限 |
| 40034128 | 被动回复时间或次数超限 | 请在收到事件后尽快回复 |
| 40054002 | 机器人被禁言 | 请等待解禁后再发送 |
| 40054003 | 机器人不是群成员 | 请先将机器人加入群聊 |
| 40054005 | 消息被去重 | 请确保每次请求使用不同的 msgseq 值 |
| 40054007 | 消息长度超限 | 请缩短消息内容 |
| 40054010 | 不允许发送 URL | 请移除消息中的 URL |
| 40054016 | 机器人已下线 | 请检查机器人状态 |
| 50055001 | 消息发送异常，请稍后重试 | 请稍后重试 |
| 50055006 | ARK 消息发送异常，请稍后重试 | 请稍后重试 |

---

## 5. 撤回群聊消息

撤回机器人发送在当前群的消息。发送超过 2 分钟的消息不可撤回。成功返回 HTTP 200，无响应体。

**业务规则：**
- 发送超出 2 分钟的消息不可撤回

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_openid}/messages/{message_id}` |
| HTTP Method | `DELETE` |
| 接口频率限制 | 10 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_openid | string | 是 | 群 OpenID |
| message_id | string | 是 | 消息 ID |

### 请求示例

```json
DELETE /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/messages/0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
```

### 响应参数

无响应体。

### 响应示例

```json
{}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 40061001 | 请求参数无效 | 请检查请求参数格式 |
| 40062003 | 无操作权限 | 请检查机器人是否有操作权限 |
| 40064004 | 已超出消息撤回时限 | 消息发送超过 2 分钟后不可撤回 |
| 50065001 | 消息撤回失败，请稍后重试 | 请稍后重试 |

---

## 6. 发送子频道消息

用于向 `channel_id` 指定的子频道发送消息。

**业务规则：**
- 要求操作人在该子频道具有发送消息的权限。
- 主动消息在频道主或管理设置了情况下，按设置的数量进行限频。在未设置的情况遵循如下限制：
  - 主动推送消息，默认每天往每个子频道可推送的消息数是 20 条，超过会被限制。
  - 主动推送消息在每个频道中，每天可以往 2 个子频道推送消息。超过后会被限制。
- 不论主动消息还是被动消息，在一个子频道中，每 1s 只能发送 5 条消息。
- 被动回复消息有效期为 5 分钟。超时会报错。
- 发送消息接口要求机器人接口需要连接到 websocket 上保持在线状态。
- 有关主动消息审核，可以通过 Intents 中审核事件 MESSAGE_AUDIT 返回 MessageAudited 对象获取结果。

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/messages` |
| HTTP Method | `POST` |

### Content-Type

请求支持 `application/json` 和 `multipart/form-data` 两种。对于类型为 `multipart/form-data` 的请求，当字段类型为对象或数组时需要将字段序列化为 JSON 字符串后进行调用。回包统一使用 `application/json`。

### 通用参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| content | string | 选填，消息内容，文本内容，支持内嵌格式 |
| embed | [MessageEmbed](#messageembed) | 选填，embed 消息，一种特殊的 ark |
| ark | [MessageArk](#messageark) | 选填，ark 消息 |
| message_reference | [MessageReference](#messagereference-2) | 选填，引用消息 |
| image | string | 选填，图片 url 地址，平台会转存该图片，用于下发图片消息 |
| msg_id | string | 选填，要回复的消息 id（Message.id），在 AT_MESSAGE_CREATE 事件中获取 |
| event_id | string | 选填，要回复的事件 id，在各事件对象中获取 |
| markdown | [MessageMarkdown](#messagemarkdown-2) | 选填，markdown 消息对象 |

### multipart/form-data 专有参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| file_image | file | 图片文件。form-data 支持直接通过文件上传的方式发送图片。 |

> content, embed, ark, image/file_image, markdown 至少需要有一个字段，否则无法下发消息。

#### MessageEmbed

| 名称 | 类型 | 描述 |
|------|------|------|
| title | string | 标题 |
| prompt | string | 消息弹窗内容 |
| thumbnail | string | 图片地址 |
| fields | []EmbedField | 字段信息 |

#### MessageArk

| 名称 | 类型 | 描述 |
|------|------|------|
| template_id | integer | 模板 ID |
| kv | []ArkKV | 键值对 |

#### MessageReference

| 名称 | 类型 | 描述 |
|------|------|------|
| message_id | string | 被引用消息 ID |
| ignore_get_message_error | boolean | 是否忽略获取消息失败错误 |

#### MessageMarkdown

| 名称 | 类型 | 描述 |
|------|------|------|
| template_id | integer | 模板 ID |
| params | []MarkdownParams | 模板参数 |
| content | string | Markdown 内容 |

#### 主动消息与被动消息

- **主动消息**：发送消息时，未填充 msg_id/event_id 字段的消息。
- **被动消息**：发送消息时，填充了 msg_id/event_id 字段的消息。msg_id 和 event_id 两个字段任意填一个即为被动消息。接口使用此 msg_id/event_id 拉取用户的消息或事件，同时判断用户消息或事件的发送时间，如果超过被动消息回复时效，将会不允许发送该消息。

目前支持被动回复的事件类型有：
- GUILD_MEMBER_ADD
- GUILD_MEMBER_UPDATE
- GUILD_MEMBER_REMOVE
- MESSAGE_REACTION_ADD
- MESSAGE_REACTION_REMOVE
- FORUM_THREAD_CREATE
- FORUM_THREAD_UPDATE
- FORUM_THREAD_DELETE
- FORUM_POST_CREATE
- FORUM_POST_DELETE
- FORUM_REPLY_CREATE
- FORUM_REPLY_DELETE

### 返回

返回 Message 对象。

### 请求示例

**JSON 格式**

```json
{
  "content": "<@!1234>hello world",
  "msg_id": "xxxxxx"
}
```

**form-data 格式**

| 字段名 | 值 |
|--------|-----|
| content | `<@!1234>hello world` |
| ark | `{"ark":{"template_id":1,"kv":[{"key":"#DESC#","value":"机器人订阅消息"}]}}` |

### 响应示例

```json
{
  "id": "xxxxxx",
  "channel_id": "xxxxxx",
  "guild_id": "xxxxxx",
  "content": "<@!1234>hello world",
  "timestamp": "2021-05-13T14:45:45+08:00",
  "tts": false,
  "mention_everyone": false,
  "author": {
    "id": "xxxxxx",
    "username": "abc",
    "avatar": "",
    "bot": true
  },
  "embeds": [{}],
  "pinned": false,
  "type": 0,
  "flags": 0
}
```

### 错误码

详见错误码文档。其中推送、回复消息的 code 错误码 304023、304024 会在响应数据包 data 中返回 MessageAudit 审核消息的信息：

```json
{
  "code": 304023,
  "message": "push message is waiting for audit now",
  "data": {
    "message_audit": {
      "audit_id": "ab9bd72f-19e8-4394-b09e-66caca0d64e4"
    }
  }
}
```

---

## 7. 撤回子频道消息

用于撤回子频道 `channel_id` 下的消息 `message_id`。

**业务规则：**
- 管理员可以撤回普通成员的消息。
- 频道主可以撤回所有人的消息。
- 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。
- 注意：开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/messages/{message_id}?hidetip=false` |
| HTTP Method | `DELETE` |
| Content-Type | `application/json` |

### 参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| hidetip | bool | 选填，是否隐藏提示小灰条，true 为隐藏，false 为显示。默认为 false |

### 返回

成功返回 HTTP 状态码 200。

### 请求示例

```
DELETE /channels/123456/messages/112233
```

### 错误码

详见错误码文档。

---

## 8. 频道私信

机器人可与同一频道内的成员建立私信会话，通过私信接口收发消息。

### 8.1 创建私信会话

#### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/users/@me/dms` |
| HTTP Method | `POST` |
| Content-Type | `application/json` |

#### 功能描述

用于机器人和在同一个频道内的成员创建私信会话。
- 机器人和用户存在共同频道才能创建私信会话。
- 创建成功后，返回创建成功的频道 id，子频道 id 和创建时间。

#### 参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| recipient_id | string | 接收者 id |
| source_guild_id | string | 源频道 id |

#### 返回

返回 DMS 对象。

#### DMS 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 私信会话关联的频道 id |
| channel_id | string | 私信会话关联的子频道 id |
| create_time | string | 创建私信会话时间戳 |

#### 请求示例

```json
{
  "recipient_id": "123456",
  "source_guild_id": "112233"
}
```

#### 响应示例

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "xxxxxx",
  "create_time": "1642545606"
}
```

### 8.2 发送私信

#### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/dms/{guild_id}/messages` |
| HTTP Method | `POST` |
| Content-Type | `application/json` |

#### 功能描述

用于发送私信消息，前提是已经创建了私信会话。
- 私信的 guild_id 在创建私信会话时以及私信消息事件中获取。
- 私信场景下，每个机器人每天可以对一个用户发 2 条主动消息。
- 私信场景下，每个机器人每天累计可以发 200 条主动消息。
- 私信场景下，被动消息没有条数限制。

#### 参数

和发送子频道消息参数一致（content, embed, ark, message_reference, image, msg_id, event_id, markdown）。

#### 返回

和发送子频道消息返回一致。

#### 示例

参见发送子频道消息示例。

### 8.3 撤回私信

#### 基础信息

| 字段 | 值 |
|------|-----|
| HTTP URL | `/dms/{guild_id}/messages/{message_id}?hidetip=false` |
| HTTP Method | `DELETE` |
| Content-Type | `application/json` |

#### 功能描述

用于撤回私信频道 guild_id 中 message_id 指定的私信消息。只能用于撤回机器人自己发送的私信。
- 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。
- 注意：开通后需要先将机器人从频道移除，然后重新添加，方可生效。

#### 参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| hidetip | bool | 选填，是否隐藏提示小灰条，true 为隐藏，false 为显示。默认为 false |

#### 返回

成功返回 HTTP 状态码 200。

#### 请求示例

```
DELETE /dms/123456/messages/112233
```

---

## 9. 内嵌格式

利用 content 字段发送内嵌格式的消息。

**规则：**
- 内嵌格式仅在 content 中会生效，在 Ark 和 Embed 中不生效。
- 为了区分是文本还是内嵌格式，消息抄送和发送会对消息内容进行相关的转义。

### 支持的格式

| 类型 | 结构 | 描述 | 示例 |
|------|------|------|------|
| @用户 | `<@user_id>` 或者 `<@!user_id>` | 解析为 @用户 标签 | `<@1234000000001>` |
| @所有人 | `@everyone` | 解析为 @所有人 标签，需要机器人拥有发送 @所有人 消息的权限 | `@everyone` |
| #子频道 | `<#channel_id>` | 解析为 #子频道 标签，点击可以跳转至子频道，仅支持当前频道内的子频道 | `<#12345>` |
| 表情 | `<emoji:id>` | 解析为系统表情，具体表情 id 参考 Emoji 列表，仅支持 type=1 的系统表情，type=2 的 emoji 表情直接按字符串填写即可 | `<emoji:4>` 解析为得意表情 |

### 转义内容

消息抄送会将源字符转为转义后内容然后抄送给机器人；发消息会将转义后字符转为源字符后再发送。

| 源字符 | 转义后 |
|--------|--------|
| `&` | `&amp;` |
| `<` | `&lt;` |
| `>` | `&gt;` |

### 请求示例

```json
{
  "content": "<@!1234>hello world"
}
```

### 响应示例

```json
{
  "id": "xxxxxx",
  "channel_id": "xxxxxx",
  "guild_id": "xxxxxx",
  "content": "<@!1234>hello world",
  "timestamp": "2021-05-13T14:45:45+08:00",
  "tts": false,
  "mention_everyone": false,
  "author": {
    "id": "xxxxxx",
    "username": "abc",
    "avatar": "",
    "bot": true
  },
  "embeds": [{}],
  "pinned": false,
  "type": 0,
  "flags": 0
}
```

---

## 附录：错误码汇总

以下为消息相关 API 的完整错误码速查表：

| 错误码 | 描述 | 排查建议 | 涉及接口 |
|--------|------|----------|----------|
| 40007 | 已下发内容前缀不可修改 | 请保持已下发内容前缀一致 | 流式发送 |
| 22006 | 消息类型与内容不匹配 | 请检查 msg_type 与 content 是否对应 | 单聊/群聊发送 |
| 50001 | 服务内部错误 | 请稍后重试 | 流式发送 |
| 50002 | 频率限制 | 请降低调用频率 | 流式发送 |
| 50059 | 输入类型错误 | 请检查输入类型 | 单聊发送 |
| 304004 | 无权限使用该 ARK 模板 | 请先申请 ARK 模板权限 | 单聊/群聊发送 |
| 304036 | 无 Markdown 模板权限 | 请先申请 Markdown 模板权限 | 群聊发送 |
| 304061 | 消息内容无效 | 请检查消息格式是否符合要求 | 单聊/群聊发送 |
| 304062 | 订阅按钮数量达到上限 | 请减少按钮数量 | 单聊发送 |
| 304064 | 订阅消息未授权 | 请先引导用户授权订阅消息 | 单聊/群聊发送 |
| 304080 | 文件信息无效 | 请检查文件信息格式是否正确 | 单聊/群聊发送 |
| 304103 | 消息 ID 已过期，不能回复 | 请在收到消息后尽快回复 | 单聊/群聊发送 |
| 305007 | 键盘样式参数错误 | 请检查 keyboard 参数 | 群聊发送 |
| 306009 | 用户 openid 无效 | 请检查 user_openid 是否正确 | 撤回单聊 |
| 340067 | 获取机器人信息失败 | 请检查机器人状态 | 单聊发送 |
| 340069 | 消息类型无效 | 请检查 msg_type 取值 | 群聊发送 |
| 40034004 | 富媒体信息转存失败 | 请重试 | 单聊/群聊发送 |
| 40034005 | 回复消息 msg_id 已过期 | 请在收到消息后尽快回复 | 单聊/群聊发送 |
| 40034006 | 消息内容违规 | 请修改消息内容后重试 | 单聊/群聊发送 |
| 40034008 | markdown 参数有空值 | 请确保所有 Markdown 参数都有值 | 单聊/群聊发送 |
| 40034009 | markdown 参数有换行符 | 请移除 Markdown 参数中的换行符 | 单聊/群聊发送 |
| 40034010 | 模版参数中不能含有 markdown 语法 | 请使用纯文本参数，不要包含 Markdown 语法 | 单聊/群聊发送 |
| 40034011 | 无效的 markdown 内容 | 请检查 Markdown 语法是否正确 | 单聊/群聊发送 |
| 40034024 | 请求参数 msg_id 无效或越权 | 请检查 msg_id 是否正确 | 单聊/群聊发送 |
| 40034025 | 请求参数 event_id 无效 | 请检查 event_id 是否正确 | 单聊/群聊发送 |
| 40034026 | 请求参数 event_id 已过期 | 请在收到事件后尽快回复 | 单聊/群聊发送 |
| 40034027 | 该事件不支持回复消息 | 请确认事件类型是否支持回复 | 单聊/群聊发送 |
| 40034029 | 内联键盘行/列超限 | 请减少键盘按钮数量 | 单聊/群聊发送 |
| 40034100 | 主动消息发送超过频控限制 | 请降低发送频率或等待配额恢复 | 单聊/群聊发送 |
| 40034101 | 机器人非群成员 | 请先将机器人加入群聊 | 群聊发送 |
| 40034105 | 主动消息发送失败，无权限 | 请检查机器人权限设置 | 单聊/群聊发送 |
| 40034106 | 消息不支持该指令类型 | 请检查消息指令类型 | 单聊/群聊发送 |
| 40034108 | 指令参数长度超限 | 请缩短指令参数 | 单聊/群聊发送 |
| 40034109 | 指令参数解析失败 | 请检查指令参数格式 | 单聊/群聊发送 |
| 40034122 | 召回消息已达区间上限 | 召回消息已达上限，无法继续召回 | 单聊发送 |
| 40034123 | 不支持召回消息 | 该消息不支持召回操作 | 单聊发送 |
| 40034124 | markdown 消息参数错误 | 请检查 Markdown 参数格式 | 单聊/群聊发送 |
| 40034127 | 无 markdown 模板权限 | 请先申请 Markdown 模板权限 | 单聊/群聊发送 |
| 40034128 | 被动回复时间或次数超限 | 请在收到事件后尽快回复 | 单聊/群聊发送 |
| 40054002 | 机器人被禁言 | 请等待解禁后再发送 | 群聊发送 |
| 40054003 | 机器人不是群成员 | 请先将机器人加入群聊 | 群聊发送 |
| 40054004 | 无好友关系 | 请先添加好友后再发送私信 | 单聊发送 |
| 40054005 | 消息被去重 | 请确保每次请求使用不同的 msgseq 值 | 单聊/群聊发送 |
| 40054006 | 验证好友关系失败 | 请重试 | 单聊发送 |
| 40054007 | 消息长度超限 | 请缩短消息内容 | 单聊/群聊发送 |
| 40054010 | 不允许发送 URL | 请移除消息中的 URL | 群聊发送 |
| 40054013 | 用户拒收消息 | 用户已拒收消息，无法发送 | 单聊发送 |
| 40054016 | 机器人已下线 | 请检查机器人状态 | 单聊/群聊发送 |
| 40054018 | 消息过长或异常 | 请缩短消息内容 | 单聊发送 |
| 40061001 | 请求参数无效 | 请检查请求参数格式 | 撤回单聊/群聊 |
| 40061002 | 请求参数 msgid 无效 | 请检查 msgid 格式是否正确 | 撤回单聊 |
| 40062003 | 无操作权限 | 请检查机器人是否有操作权限 | 撤回群聊 |
| 40064004 | 已超出消息撤回时限 | 消息发送超过 2 分钟后不可撤回 | 撤回单聊/群聊 |
| 50055001 | 消息发送异常，请稍后重试 | 请稍后重试 | 群聊发送 |
| 50055002 | 消息发送异常，请稍后重试 | 请稍后重试 | 单聊发送 |
| 50055006 | ARK 消息发送异常，请稍后重试 | 请稍后重试 | 群聊发送 |
| 50065001 | 消息撤回失败，请稍后重试 | 请稍后重试 | 撤回群聊 |
