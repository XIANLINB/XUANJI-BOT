# QQ 机器人 OpenAPI v2 完整官方文档

> 来源：https://bot.q.qq.com/wiki/develop/api-v2/  
> 更新日期：2026-07-26  
> 基础 URL：`https://api.bot.qq.com`（频道相关：`https://api.sgroup.qq.com`）

---

# 一、消息收发

### 1. 发送单聊消息

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

### 2. 流式发送单聊消息

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

### 3. 撤回单聊消息

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

### 4. 发送群聊消息

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

### 5. 撤回群聊消息

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

### 6. 发送子频道消息

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

### 7. 撤回子频道消息

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

### 8. 频道私信

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

### 9. 内嵌格式

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

---

# 二、富媒体上传与机器人管理

### 1. 富媒体消息概述

富媒体消息支持发送图片、视频、语音、文件等类型，需先将文件上传获取 `file_info`，再通过发消息接口（`msg_type=7`）携带 `media.file_info` 发送。

### 支持的消息类型

| 类型 | 说明 |
|------|------|
| 图片 | 支持 jpg/png/gif/webp/bmp 格式，发送后直接展示图片 |
| 语音 | 支持 silk/mp3/wav/ogg 格式，发送后展示语音条 |
| 视频 | 支持 mp4 格式，发送后展示视频封面可播放 |
| 文件 | 支持任意格式，发送后展示文件卡片可下载 |

### 文件类型与限制

| file_type | 类型 | 格式 | 软限制 | 硬限制 |
|-----------|------|------|--------|--------|
| 1 | 图片 | png / jpg | 20 MB | 200 MB |
| 2 | 视频 | mp4 | 30 MB | 200 MB |
| 3 | 语音 | silk | 20 MB | 200 MB |
| 4 | 文件 | - | 200 MB | 200 MB |

> 超过软限制会降级为文件类型上传，超过硬限制会报错。

### 上传方式

#### 整文件上传（URL 上传）
使用 [单聊上传](#2-单聊富媒体上传) / [群聊上传](#5-群聊富媒体上传) 接口，直接传入文件 URL；文件较大时使用分片上传。

#### 分片上传（推荐）

适用于大文件或本地文件，分四步完成：

1. **预上传** — 调用 `upload_prepare`，传入文件信息和校验值 → 获取 `upload_id` + `block_size` + 各分片预签名 URL
2. **分片 PUT** — 按 `block_size` 将文件分片，逐片 HTTP PUT 到对应的预签名 URL
3. **确认分片** — 每片 PUT 成功后调用 `upload_part_finish`，通知服务端该分片完成
4. **完成合并** — 全部分片完成后，携带 `upload_id` 调用上传接口 → 返回 `file_info`

```
upload_prepare          分片 PUT + part_finish          上传接口（合并）
┌──────────────┐      ┌─────────────────────────┐      ┌──────────────────┐
│ 获取         │      │ for each chunk:         │      │ POST .../files   │
│ upload_id    │─────▶│ PUT → presigned_url     │─────▶│ { upload_id }    │
│ block_size   │      │ POST → part_finish      │      │ → file_info      │
│ presigned    │      └─────────────────────────┘      └──────────────────┘
│ URLs         │
└──────────────┘
```

### 使用 file_info 发送

获取 `file_info` 后，在发消息接口中设置 `msg_type=7`，将 `file_info` 填入 `media` 字段：

```json
POST /v2/users/{user_openid}/messages
{
  "msg_type": 7,
  "media": {
    "file_info": "{上一步返回的 file_info}"
  }
}
```

> `srv_send_msg=true` 可在上传的同时直接发送，跳过单独调用发消息接口这一步，但会占用主动消息频次。

### 单聊与群聊隔离

单聊和群聊的文件上传接口相互独立，上传的文件不能跨场景使用：

| 场景 | 上传接口 |
|------|---------|
| 单聊 | `/v2/users/{user_openid}/files` |
| 群聊 | `/v2/groups/{group_openid}/files` |

对应的预上传和分片接口也需使用同场景的端点。

### 注意事项

- `file_info` 有有效期（`ttl`），过期后需重新上传。
- `md5_10m`（文件前 10002432 字节，约 9.54 MB 的 MD5）可用于秒传判断，避免重复上传。
- 分片大小默认 5MB，并发数、重试策略由服务端在 `upload_config` 中下发。
- 上传接口超时建议设为 ≥ 5 秒。

---

### 2. 单聊富媒体上传

上传图片/视频/语音到单聊，返回 `file_info` 用于发送消息接口的 `media` 字段。  
用单聊接口上传的文件仅能发送到单聊。  
支持两种上传方式：URL 上传、分片上传合并。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_openid}/files` |
| HTTP Method | `POST` |
| 接口频率限制 | **50 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_openid | string | 是 | 用户 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_type | integer | 否 | 媒体类型。1=图片, 2=视频, 3=语音, 4=文件。图片支持 png/jpg，视频支持 mp4，语音支持 silk |
| url | string | 否 | 媒体资源的 URL，需以 http 开头，平台会下载并转存。分片上传合并时可为空 |
| srv_send_msg | boolean | 否 | `true`=直接发送消息并占用主动消息频次，返回中包含消息 ID；`false`=仅返回 file_info |
| file_name | string | 否 | 文件名（可选） |
| upload_id | string | 否 | 分片上传任务 ID。来自 UploadPrepare 响应的 upload_id，传入后走分片上传合并路径，url 可为空 |

### 请求示例

**URL 上传图片：**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/files
{
  "file_type": 1,
  "url": "https://example.com/image.png",
  "srv_send_msg": false
}
```

**分片上传合并：**

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/files
{
  "file_type": 2,
  "srv_send_msg": false,
  "file_name": "video.mp4",
  "upload_id": "upload_a1b2c3d4e5f6"
}
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| file_uuid | string | 文件唯一 ID |
| file_info | string | 文件信息，用于发送消息接口的 `media.file_info` 字段。内部为序列化的二进制数据，开发者无需解析，直接透传即可 |
| ttl | integer | file_info 有效期（秒）。到期后需重新上传。0 表示可长期使用 |
| id | string | 发送消息的唯一 ID。仅 `srv_send_msg=true` 时返回 |
| raw_url | string | 文件下载链接（COS 预签名 GET URL），有效期与 ttl 一致。仅分片上传合并（upload_id 路径）且 file_type 为图片/视频/语音时返回；URL 直传和文件类型(file_type=4)不返回此字段 |

### 响应示例

```json
{
  "file_uuid": "uuid_a1b2c3d4e5f6",
  "file_info": "AE86C5D3F0E14B238C656C0F6DD1D0479C",
  "ttl": 300
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 大文件分片上传中 BDH 通道异常，请重试 |
| 40093002 | 超过今天发送文件容量上限 | 请明天再试或减少文件大小 |

---

### 3. 单聊富媒体预上传

单聊大文件分片上传前的准备工作。返回 `upload_id`、分片预签名 URL 和上传配置。  
后续将文件按 `block_size` 分片，逐片 PUT 到预签名 URL，每片完成后调用分片完成接口。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_id}/upload_prepare` |
| HTTP Method | `POST` |
| 接口频率限制 | **10 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_id | string | 是 | 用户 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_type | integer | 否 | 业务类型。1=图片, 2=视频, 3=语音, 4=文件 |
| file_size | string | 否 | 文件大小（字节） |
| file_name | string | 否 | 文件名 |
| md5 | string | 否 | 整个文件的 MD5 |
| sha1 | string | 否 | 整个文件的 SHA1 |
| md5_10m | string | 否 | 文件前 10002432 字节（约 10MB）的 MD5 校验值 |

### 请求示例

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/upload_prepare
{
  "file_type": 2,
  "file_size": "31457280",
  "file_name": "demo.mp4",
  "md5": "d41d8cd98f00b204e9800998ecf8427e",
  "sha1": "da39a3ee5e6b4b0d3255bfef95601890afd80709",
  "md5_10m": "c4d8c5f3a2b1e0f9a8b7c6d5e4f3a2b1"
}
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| upload_id | string | 上传任务 ID，后续分片上传和完成合并时需携带 |
| block_size | string | 分块大小（字节），默认 5MB。客户端按此大小对文件分片 |
| parts | [][UploadPart](#uploadpart) | 分片列表，每个分片包含一个预签名上传 URL |
| upload_config | [UploadConfig](#uploadconfig) | 上传配置，由后台下发控制客户端上传行为 |

#### UploadPart

| 名称 | 类型 | 描述 |
|------|------|------|
| index | integer | 分片序号，从 0 开始 |
| presigned_url | string | 预签名上传 URL，客户端通过 HTTP PUT 将分片数据上传到此 URL |
| block_size | string | 该分块的大小（字节） |

#### UploadConfig

| 名称 | 类型 | 描述 |
|------|------|------|
| concurrency | integer | 上传并发数，默认 1 |
| retry_timeout | integer | 重试超时时间（秒），默认 300（5分钟） |
| retry_delay | integer | 重试延迟（秒），默认 1 |

### 响应示例

```json
{
  "upload_id": "upload_a1b2c3d4e5f6",
  "block_size": "10485760",
  "parts": [
    {
      "index": 0,
      "presigned_url": "https://cos.example.com/upload?partNumber=1&sign=aaa",
      "block_size": "10485760"
    },
    {
      "index": 1,
      "presigned_url": "https://cos.example.com/upload?partNumber=2&sign=bbb",
      "block_size": "10485760"
    },
    {
      "index": 2,
      "presigned_url": "https://cos.example.com/upload?partNumber=3&sign=ccc",
      "block_size": "10485760"
    }
  ],
  "upload_config": {
    "concurrency": 1,
    "retry_timeout": 300,
    "retry_delay": 1
  }
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 申请上传失败，请重试 |

---

### 4. 单聊分片上传完成

通知服务端某个分片已上传完成。全部分片完成后，用 `upload_id` 作为 MediaUpload 的 `upload_id` 字段调一次上传接口完成合并。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/users/{user_id}/upload_part_finish` |
| HTTP Method | `POST` |
| 接口频率限制 | **10 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| user_id | string | 是 | 用户 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| upload_id | string | 否 | 上传任务 ID |
| part_index | integer | 否 | 分片序号 |
| block_size | string | 否 | 分块大小（字节） |
| md5 | string | 否 | 分片 MD5 |

### 请求示例

```json
POST /v2/users/A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4/upload_part_finish
{
  "upload_id": "upload_a1b2c3d4e5f6",
  "part_index": 0,
  "block_size": "10485760",
  "md5": "c4d8c5f3a2b1e0f9a8b7c6d5e4f3a2b1"
}
```

### 响应

无响应体。

### 响应示例

```json
{}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 分片转存 BDH 通道异常，请重试 |
| 40093002 | 超过今天发送文件容量上限 | 请明天再试或减少文件大小 |

---

### 5. 群聊富媒体上传

上传图片/视频/语音到群聊，返回 `file_info` 用于发送消息接口的 `media` 字段。  
`srv_send_msg=true` 时直接发送消息并占用主动消息频次；`false` 时仅返回 `file_info`。  
用群接口上传的文件仅能发送到群聊。  
支持两种上传方式：URL 上传、分片上传合并。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_openid}/files` |
| HTTP Method | `POST` |
| 接口频率限制 | **50 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_openid | string | 是 | 群 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_type | integer | 否 | 媒体类型。1=图片, 2=视频, 3=语音, 4=文件。图片支持 png/jpg，视频支持 mp4，语音支持 silk |
| url | string | 否 | 媒体资源的 URL，需以 http 开头，平台会下载并转存。分片上传合并时可为空 |
| srv_send_msg | boolean | 否 | `true`=直接发送消息并占用主动消息频次，返回中包含消息 ID；`false`=仅返回 file_info |
| file_name | string | 否 | 文件名（可选） |
| upload_id | string | 否 | 分片上传任务 ID。来自 UploadPrepare 响应的 upload_id，传入后走分片上传合并路径，url 可为空 |

### 请求示例

**URL 上传图片：**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/files
{
  "file_type": 1,
  "url": "https://example.com/image.png",
  "srv_send_msg": false
}
```

**分片上传合并：**

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/files
{
  "file_type": 2,
  "srv_send_msg": false,
  "file_name": "video.mp4",
  "upload_id": "upload_a1b2c3d4e5f6"
}
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| file_uuid | string | 文件唯一 ID |
| file_info | string | 文件信息，用于发送消息接口的 `media.file_info` 字段。内部为序列化的二进制数据，开发者无需解析，直接透传即可 |
| ttl | integer | file_info 有效期（秒）。到期后需重新上传。0 表示可长期使用 |
| id | string | 发送消息的唯一 ID。仅 `srv_send_msg=true` 时返回 |
| raw_url | string | 文件下载链接（COS 预签名 GET URL），有效期与 ttl 一致。仅分片上传合并（upload_id 路径）且 file_type 为图片/视频/语音时返回；URL 直传和文件类型(file_type=4)不返回此字段 |

### 响应示例

```json
{
  "file_uuid": "uuid_a1b2c3d4e5f6",
  "file_info": "AE86C5D3F0E14B238C656C0F6DD1D0479C",
  "ttl": 300
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 大文件分片上传中 BDH 通道异常，请重试 |
| 40093002 | 超过今天发送文件容量上限 | 请明天再试或减少文件大小 |

---

### 6. 群聊富媒体预上传

大文件分片上传前的准备工作。返回 `upload_id`、分片预签名 URL 和上传配置。  
后续将文件按 `block_size` 分片，逐片 PUT 到预签名 URL，每片完成后调用分片完成接口。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_id}/upload_prepare` |
| HTTP Method | `POST` |
| 接口频率限制 | **10 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_id | string | 是 | 群 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file_type | integer | 否 | 业务类型。1=图片, 2=视频, 3=语音, 4=文件。图片软限制 20MB, 视频软限制 30MB, 语音软限制 20MB, 文件软限制 200MB。超过软限制降级为文件类型，超过 200MB 硬限制报错 |
| file_size | string | 否 | 文件大小（字节） |
| file_name | string | 否 | 文件名 |
| md5 | string | 否 | 整个文件的 MD5 校验值 |
| sha1 | string | 否 | 整个文件的 SHA1 校验值 |
| md5_10m | string | 否 | 文件前 10002432 字节（约 10MB）的 MD5 校验值 |

### 请求示例

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/upload_prepare
{
  "file_type": 2,
  "file_size": "31457280",
  "file_name": "demo.mp4",
  "md5": "d41d8cd98f00b204e9800998ecf8427e",
  "sha1": "da39a3ee5e6b4b0d3255bfef95601890afd80709",
  "md5_10m": "c4d8c5f3a2b1e0f9a8b7c6d5e4f3a2b1"
}
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| upload_id | string | 上传任务 ID，后续分片上传和完成合并时需携带 |
| block_size | string | 分块大小（字节），默认 5MB。客户端按此大小对文件分片 |
| parts | [][UploadPart](#uploadpart-1) | 分片列表，每个分片包含一个预签名上传 URL |
| upload_config | [UploadConfig](#uploadconfig-1) | 上传配置，由后台下发控制客户端上传行为 |

#### UploadPart

| 名称 | 类型 | 描述 |
|------|------|------|
| index | integer | 分片序号，从 0 开始 |
| presigned_url | string | 预签名上传 URL，客户端通过 HTTP PUT 将分片数据上传到此 URL |
| block_size | string | 该分块的大小（字节） |

#### UploadConfig

| 名称 | 类型 | 描述 |
|------|------|------|
| concurrency | integer | 上传并发数，默认 1 |
| retry_timeout | integer | 重试超时时间（秒），默认 300（5分钟） |
| retry_delay | integer | 重试延迟（秒），默认 1 |

### 响应示例

```json
{
  "upload_id": "upload_a1b2c3d4e5f6",
  "block_size": "10485760",
  "parts": [
    {
      "index": 0,
      "presigned_url": "https://cos.example.com/upload?partNumber=1&sign=aaa",
      "block_size": "10485760"
    },
    {
      "index": 1,
      "presigned_url": "https://cos.example.com/upload?partNumber=2&sign=bbb",
      "block_size": "10485760"
    },
    {
      "index": 2,
      "presigned_url": "https://cos.example.com/upload?partNumber=3&sign=ccc",
      "block_size": "10485760"
    }
  ],
  "upload_config": {
    "concurrency": 1,
    "retry_timeout": 300,
    "retry_delay": 1
  }
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 申请上传失败，请重试 |

---

### 7. 群聊分片上传完成

通知服务端某个分片已上传完成。需在每片 PUT 成功后调用。全部分片完成后，用 `upload_id` 作为 MediaUpload 的 `upload_id` 字段调一次上传接口完成合并。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_id}/upload_part_finish` |
| HTTP Method | `POST` |
| 接口频率限制 | **10 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_id | string | 是 | 群 OpenID |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| upload_id | string | 否 | 上传任务 ID，来自预上传响应 |
| part_index | integer | 否 | 分片序号，对应 UploadPart.index |
| block_size | string | 否 | 该分块的实际大小（字节） |
| md5 | string | 否 | 该分片的 MD5 校验值 |

### 请求示例

```json
POST /v2/groups/B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5/upload_part_finish
{
  "upload_id": "upload_a1b2c3d4e5f6",
  "part_index": 0,
  "block_size": "10485760",
  "md5": "c4d8c5f3a2b1e0f9a8b7c6d5e4f3a2b1"
}
```

### 响应

无响应体。

### 响应示例

```json
{}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 850018 | 群被禁言或者机器人被禁言 | 请检查机器人是否被禁言 |
| 850019 | 不支持的文件格式 | 请检查 file_type 是否正确 |
| 850026 | 下载原始文件失败 | 请检查 URL 是否可访问或重试 |
| 850031 | 上传文件超过大小限制 | 请减小文件大小 |
| 850027 | 发送数据超时 | 请稍后重试 |
| 10000 | 不支持的操作 | 请检查请求参数 |
| 40093001 | 文件上传失败，请重试 | 分片转存 BDH 通道异常，请重试 |
| 40093002 | 超过今天发送文件容量上限 | 请明天再试或减少文件大小 |

---

### 8. 获取机器人详情

获取当前用户（机器人）的详情信息。

> **注意：** `union_openid` 和 `union_user_account` 需特殊申请并配置后才会返回。这两个字段仅在单独拉取 member 信息时提供。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/users/@me` |
| HTTP Method | `GET` |
| 接口频率限制 | **50 QPS** |

### 请求参数

无。

### 请求示例

```
GET /users/@me
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 用户 ID |
| username | string | 用户名 |
| avatar | string | 头像 URL |
| bot | boolean | 是否为机器人 |
| union_openid | string | 跨应用统一用户 OpenID（需特殊申请） |
| union_user_account | string | 跨应用统一用户账号（需特殊申请） |

### 响应示例

```json
{
  "id": "5777414462219517083",
  "username": "阳光小助手",
  "avatar": "https://thirdqq.qlogo.cn/g?b=oidb&k=AbCdEfGhIjKlMnOpQrStUv&kti=xyzABC&s=0&t=1781676795",
  "bot": true,
  "union_openid": "9F2E872045CCCC5948BEAF5B5FCCDF22",
  "union_user_account": "",
  "share_url": "https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889007780&robot_appid=102083127&biz_type=0",
  "welcome_msg": "欢迎加入我们的群聊"
}
```

### 错误码

无特定错误码列出。

---

### 9. 获取机器人加入的频道列表

获取当前用户（机器人）所加入的频道列表，支持分页。  
Bot Token 获取机器人数据，Bearer Token 获取用户数据。  
`limit` 默认 100，最大 100。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/users/@me/guilds` |
| HTTP Method | `GET` |
| 接口频率限制 | **50 QPS** |

### 查询参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| before | string | 否 | 读取此 guild_id 之前的数据。设置时先反序再分页 |
| after | string | 否 | 读取此 guild_id 之后的数据。与 before 同时设置时 after 无效 |
| limit | integer | 否 | 每次拉取条数，默认 100，最大 100 |

### 请求示例

```
GET /users/@me/guilds?limit=20
```

### 响应

响应体为 `GuildInfo` 数组。

#### GuildInfo

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 频道 ID |
| name | string | 频道名称 |
| icon | string | 频道头像 URL |
| owner_id | string | 频道创建者 ID |
| owner | boolean | 当前用户是否为频道创建者 |
| joined_at | string | 加入时间，ISO8601 格式 |
| member_count | integer | 频道成员数 |
| max_members | integer | 频道成员上限 |
| description | string | 频道简介 |

### 响应示例

```json
[
  {
    "id": "2452178231489345741",
    "name": "读书分享会",
    "icon": "https://groupprohead.gtimg.cn/11259151665662004/40?t=1667468494556",
    "owner_id": "17481532452010052342",
    "owner": false,
    "joined_at": "2025-01-09T15:17:23+08:00",
    "member_count": 6,
    "max_members": 5000000,
    "description": "一起读书，共同成长"
  },
  {
    "id": "16038617105584902418",
    "name": "英语学习角",
    "icon": "https://groupprohead.gtimg.cn/76199361644746202/40?t=1655214551877",
    "owner_id": "12015059872407927338",
    "owner": false,
    "joined_at": "2026-05-12T20:39:33+08:00",
    "member_count": 35,
    "max_members": 5000000,
    "description": "分享英语学习资源，欢迎爱学习的伙伴来交流"
  },
  {
    "id": "9160663460093593400",
    "name": "早起打卡群",
    "icon": "https://groupprohead.gtimg.cn/89330271757059034/40?t=1781084183385",
    "owner_id": "1570904394246748593",
    "owner": true,
    "joined_at": "2026-05-15T10:57:07+08:00",
    "member_count": 13,
    "max_members": 10000,
    "description": "每天早起打卡，养成好习惯"
  }
]
```

### 错误码

无特定错误码列出。

---

### 10. 生成分享链接

生成机器人分享链接，用于邀请用户添加机器人为好友。  
生成带自定义参数的机器人分享链接。用户通过该链接添加机器人时，`callback_data` 参数会透传给开发者。`callback_data` 最长 32 字符。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/generate_url_link` |
| HTTP Method | `POST` |
| 接口频率限制 | **50 QPS** |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| url_link | string | 否 | 需要跳转的 URL |

### 请求示例

```json
POST /v2/generate_url_link
{
  "callback_data": "custom_data_123"
}
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| url_link | string | 生成的分享链接 |

### 响应示例

```json
{
  "url_link": "https://qun.qq.com/qunpro/robot/qunshare?robot_appid=1234567890&robot_uin=12345678&data=xxx"
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 10001 | 请求参数异常 | 请检查请求参数是否正确 |
| 10002 | 请求头异常 | 请检查请求头是否正确 |
| 10003 | 查询机器人信息异常 | 请确认机器人是否存在 |
| 10044 | 从协议头获取uin失败 | 请检查 Authorization Header 是否正确 |
| 11004 | 生成分享ARK失败 | 请稍后重试 |

---

### 11. 获取群基础信息

获取指定群的基本信息。

> **该接口能力正在内邀开放中**

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_openid}/info` |
| HTTP Method | `GET` |
| 接口频率限制 | **60 QPM** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_openid | string | 是 | 群 OpenID |

### 请求示例

```
GET /v2/groups/3E5D8A1F7B2C9E4D6A0F1B3C5D7E9F2A/info
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| group_openid | string | 群 OpenID |
| group_name | string | 群名称 |
| group_finger_memo | string | 群简介 |
| group_class_text | string | 群分类 |
| group_tags | []string | 群标签列表 |
| group_member_num | integer | 群成员人数 |

### 响应示例

```json
{
  "group_openid": "3E5D8A1F7B2C9E4D6A0F1B3C5D7E9F2A",
  "group_name": "读书分享会",
  "group_finger_memo": "每周共读一本好书",
  "group_class_text": "文化",
  "group_tags": [
    "阅读",
    "文学",
    "成长"
  ],
  "group_member_num": 256
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 11253 | 应用无接口访问权限 | 该接口仅白名单机器人可用，请联系平台运营申请权限 |

---

### 12. 获取机器人群内状态

获取机器人在指定群的状态信息。

> **该接口能力正在内邀开放中**

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/v2/groups/{group_openid}/bot_state` |
| HTTP Method | `GET` |
| 接口频率限制 | **60 QPM** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| group_openid | string | 是 | 群 OpenID |

### 请求示例

```
GET /v2/groups/3E5D8A1F7B2C9E4D6A0F1B3C5D7E9F2A/bot_state
```

### 响应

| 名称 | 类型 | 描述 |
|------|------|------|
| member_openid | string | 机器人的 OpenID |
| joined_at | string | 入群时间，RFC3339 格式 |
| allow_proactive_msg | boolean | 是否接收主动推送 |
| recv_msg_setting | string | 接收消息类型: `all`=全部, `only_mention`=仅@, `mention_and_context`=@和上下文 |
| member_role | string | 群成员角色: `member`=普通成员, `owner`=群主, `admin`=管理员 |

### 响应示例

```json
{
  "member_openid": "7A3B9C1D5E2F4A6B8C0D1E3F5A7B9C2D",
  "joined_at": "2025-06-15T14:30:00+08:00",
  "allow_proactive_msg": false,
  "recv_msg_setting": "only_mention",
  "member_role": "member"
}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 11253 | 应用无接口访问权限 | 该接口仅白名单机器人可用，请联系平台运营申请权限 |

---

### 13. 互动事件响应

收到 `INTERACTION_CREATE` 事件后需调用此接口回应，告知 QQ 后台事件已收到。否则客户端会一直处于 loading 状态直到超时。

> **说明：** 仅 `type=11`（消息按钮）和 `type=12`（快捷菜单）的互动事件需要调用此接口回应，其他类型无需回应（调用也不会报错）。需在事件触发的有效时间内回应，超时后 `interaction_id` 失效。同一 `interaction_id` 只能回应一次。`code=0` 时，对于 `type=14`（清空会话），后台会下发会话已清空小灰条提示用户。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/interactions/{interaction_id}` |
| HTTP Method | `PUT` |
| 接口频率限制 | **50 QPS** |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| interaction_id | string | 是 | 互动事件 ID，从 INTERACTION_CREATE 事件的 id 字段获取 |

### 请求体

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| code | integer | 否 | 回调结果。0=成功, 1=操作失败, 2=操作频繁, 3=重复操作, 4=没有权限, 5=仅管理员操作 |

### 请求示例

```json
PUT /interactions/a1b2c3d4-e5f6-7890-abcd-ef1234567890
{
  "code": 0
}
```

### 响应

无响应体。

### 响应示例

```json
{}
```

### 错误码

| 错误码 | 描述 | 排查建议 |
|--------|------|----------|
| 630001 | param invalid | 请检查请求参数是否正确 |
| 630002 | get appid failed | 请检查 Authorization Header 是否正确 |
| 630003 | appid invalid | AppID 与 interaction_id 不匹配，请确认使用正确的 Bot Token |
| 630004 | set interaction data failed | 请稍后重试 |
| 630005 | get interaction data failed | 请稍后重试 |
| 630006 | get header appid failed | 请检查请求 Header |
| 630007 | data too large | 请减小请求体大小 |
| 630008 | interaction preprocess failed | 请检查请求参数 |

---

## 附录：快速参考

### API 端点汇总

| # | API 名称 | 方法 | URL | 频率限制 |
|---|---------|------|-----|----------|
| 1 | 单聊富媒体上传 | POST | `/v2/users/{user_openid}/files` | 50 QPS |
| 2 | 单聊预上传 | POST | `/v2/users/{user_id}/upload_prepare` | 10 QPS |
| 3 | 单聊分片完成 | POST | `/v2/users/{user_id}/upload_part_finish` | 10 QPS |
| 4 | 群聊富媒体上传 | POST | `/v2/groups/{group_openid}/files` | 50 QPS |
| 5 | 群聊预上传 | POST | `/v2/groups/{group_id}/upload_prepare` | 10 QPS |
| 6 | 群聊分片完成 | POST | `/v2/groups/{group_id}/upload_part_finish` | 10 QPS |
| 7 | 获取机器人详情 | GET | `/users/@me` | 50 QPS |
| 8 | 获取频道列表 | GET | `/users/@me/guilds` | 50 QPS |
| 9 | 生成分享链接 | POST | `/v2/generate_url_link` | 50 QPS |
| 10 | 获取群基础信息 | GET | `/v2/groups/{group_openid}/info` | 60 QPM |
| 11 | 获取机器人群内状态 | GET | `/v2/groups/{group_openid}/bot_state` | 60 QPM |
| 12 | 互动事件响应 | PUT | `/interactions/{interaction_id}` | 50 QPS |

### 文件类型与限制速查

| file_type | 类型 | 格式 | 软限制 | 硬限制 |
|-----------|------|------|--------|--------|
| 1 | 图片 | png/jpg | 20MB | 200MB |
| 2 | 视频 | mp4 | 30MB | 200MB |
| 3 | 语音 | silk | 20MB | 200MB |
| 4 | 文件 | - | 200MB | 200MB |

### 分片上传流程速查

```
1. POST /upload_prepare  →  获取 upload_id + presigned URLs
2. PUT 每个 presigned_url  →  上传分片数据
3. POST /upload_part_finish  →  确认每片完成（重复 2+3 直到所有分片）
4. POST /files { upload_id }  →  合并并获取 file_info
```

### 互动事件响应 code 值

| code | 含义 |
|------|------|
| 0 | 成功 |
| 1 | 操作失败 |
| 2 | 操作频繁 |
| 3 | 重复操作 |
| 4 | 没有权限 |
| 5 | 仅管理员操作 |

---

# 三、频道管理

### 1. 获取频道详情

获取指定频道的基本信息。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}` |
| HTTP Method | `GET` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求示例

```
GET /guilds/123456789012345678
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 频道 ID |
| name | string | 频道名称 |
| icon | string | 频道头像 URL |
| owner_id | string | 创建者用户 ID |
| owner | boolean | 当前机器人是否为创建者 |
| member_count | integer | 成员数 |
| max_members | integer | 最大成员数 |
| description | string | 频道描述 |
| joined_at | string | 加入时间，ISO8601 格式 |

### 响应示例

```json
{
  "id": "123456789012345678",
  "name": "技术交流频道",
  "icon": "https://thirdqq.qlogo.cn/0",
  "owner_id": "123456789012345678",
  "owner": false,
  "member_count": 100,
  "max_members": 1000,
  "description": "专注于技术分享与交流的频道",
  "joined_at": "2026-01-01T00:00:00+08:00"
}
```

---

### 2. 获取子频道列表

获取指定频道下的子频道列表。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/channels` |
| HTTP Method | `GET` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求示例

```
GET /guilds/123456789012345678/channels
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| channels | [][Channel](#channel对象) | 子频道对象数组 |

#### Channel 对象字段

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名 |
| type | integer | 子频道类型: 0=文字, 2=语音, 4=分组, 10005=直播, 10006=应用, 10007=论坛 |
| sub_type | integer | 子频道子类型（文字子频道）: 0=闲聊, 1=公告, 2=攻略, 3=开黑 |
| position | integer | 排序值，从 1 开始 |
| parent_id | string | 所属分组 ID（仅子频道有效） |
| owner_id | string | 创建人 ID |
| private_type | integer | 子频道私密类型: 0=公开, 1=群主管理员可见, 2=群主管理员+指定成员 |
| speak_permission | integer | 子频道发言权限: 0=无效, 1=所有人, 2=群主管理员+指定成员 |
| application_id | string | 应用子频道标识 |
| permissions | string | 用户拥有的子频道权限 |

### 响应示例

```json
[
  {
    "id": "123456",
    "guild_id": "123456789012345678",
    "name": "文字交流区",
    "type": 0,
    "sub_type": 0,
    "position": 1,
    "parent_id": "0",
    "owner_id": "123456789012345678",
    "private_type": 0,
    "speak_permission": 1
  },
  {
    "id": "123457",
    "guild_id": "123456789012345678",
    "name": "语音聊天室",
    "type": 2,
    "sub_type": 0,
    "position": 2,
    "parent_id": "0",
    "owner_id": "123456789012345678",
    "private_type": 0,
    "speak_permission": 1
  }
]
```

---

### 3. 创建子频道

在指定频道下创建子频道。需要管理员权限。私域接口，创建成功后会触发 `CHANNEL_CREATE` 事件。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/channels` |
| HTTP Method | `POST` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| name | string | 否 | 子频道名称 |
| type | integer | 否 | 子频道类型 |
| sub_type | integer | 否 | 子频道子类型 |
| position | integer | 否 | 排序值（分组类型必须 >= 2） |
| parent_id | string | 否 | 所属分组 ID |
| private_type | integer | 否 | 私密类型 |
| private_user_ids | []string | 否 | 私密成员 ID 列表 |
| speak_permission | integer | 否 | 发言权限 |
| application_id | string | 否 | 应用子频道 AppID |

### 请求示例

**创建文字子频道：**
```
POST /guilds/123456789012345678/channels
```
```json
{
  "name": "公告区",
  "type": 0,
  "sub_type": 1,
  "position": 3,
  "parent_id": "0",
  "private_type": 0,
  "speak_permission": 1
}
```

**创建语音子频道：**
```
POST /guilds/123456789012345678/channels
```
```json
{
  "name": "开黑房",
  "type": 2,
  "sub_type": 3,
  "position": 4,
  "parent_id": "0"
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名 |
| type | integer | 子频道类型: 0=文字, 2=语音, 4=分组, 10005=直播, 10006=应用, 10007=论坛 |
| sub_type | integer | 子频道子类型（文字子频道）: 0=闲聊, 1=公告, 2=攻略, 3=开黑 |
| position | integer | 排序值，从 1 开始 |
| parent_id | string | 所属分组 ID（仅子频道有效） |
| owner_id | string | 创建人 ID |
| private_type | integer | 子频道私密类型: 0=公开, 1=群主管理员可见, 2=群主管理员+指定成员 |
| speak_permission | integer | 子频道发言权限: 0=无效, 1=所有人, 2=群主管理员+指定成员 |
| application_id | string | 应用子频道标识 |
| permissions | string | 用户拥有的子频道权限 |

### 响应示例

```json
{
  "id": "123458",
  "guild_id": "123456789012345678",
  "name": "公告区",
  "type": 0,
  "sub_type": 1,
  "position": 3,
  "parent_id": "0",
  "owner_id": "123456789012345678",
  "private_type": 0,
  "speak_permission": 1
}
```

---

### 4. 获取子频道详情

获取指定子频道的基本信息。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}` |
| HTTP Method | `GET` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |

### 请求示例

```
GET /channels/123456
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名 |
| type | integer | 子频道类型: 0=文字, 2=语音, 4=分组, 10005=直播, 10006=应用, 10007=论坛 |
| sub_type | integer | 子频道子类型（文字子频道）: 0=闲聊, 1=公告, 2=攻略, 3=开黑 |
| position | integer | 排序值，从 1 开始 |
| parent_id | string | 所属分组 ID（仅子频道有效） |
| owner_id | string | 创建人 ID |
| private_type | integer | 子频道私密类型: 0=公开, 1=群主管理员可见, 2=群主管理员+指定成员 |
| speak_permission | integer | 子频道发言权限: 0=无效, 1=所有人, 2=群主管理员+指定成员 |
| application_id | string | 应用子频道标识 |
| permissions | string | 用户拥有的子频道权限 |

### 响应示例

```json
{
  "id": "123456",
  "guild_id": "123456789012345678",
  "name": "文字交流区",
  "type": 0,
  "sub_type": 0,
  "position": 1,
  "parent_id": "0",
  "owner_id": "123456789012345678",
  "private_type": 0,
  "speak_permission": 1,
  "permissions": "0"
}
```

---

### 5. 修改子频道

修改子频道信息。需要管理员权限。私域接口，只需传入要修改的字段，修改成功后会触发 `CHANNEL_UPDATE` 事件。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}` |
| HTTP Method | `PATCH` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| name | string | 否 | 子频道名 |
| position | integer | 否 | 排序 |
| parent_id | string | 否 | 分组 ID |
| private_type | integer | 否 | 私密类型 |
| speak_permission | integer | 否 | 发言权限 |

### 请求示例

```
PATCH /channels/123456
```
```json
{
  "name": "公告区（已改名）",
  "position": 5,
  "parent_id": "0"
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名 |
| type | integer | 子频道类型: 0=文字, 2=语音, 4=分组, 10005=直播, 10006=应用, 10007=论坛 |
| sub_type | integer | 子频道子类型（文字子频道）: 0=闲聊, 1=公告, 2=攻略, 3=开黑 |
| position | integer | 排序值，从 1 开始 |
| parent_id | string | 所属分组 ID（仅子频道有效） |
| owner_id | string | 创建人 ID |
| private_type | integer | 子频道私密类型: 0=公开, 1=群主管理员可见, 2=群主管理员+指定成员 |
| speak_permission | integer | 子频道发言权限: 0=无效, 1=所有人, 2=群主管理员+指定成员 |
| application_id | string | 应用子频道标识 |
| permissions | string | 用户拥有的子频道权限 |

### 响应示例

```json
{
  "id": "123456",
  "guild_id": "123456789012345678",
  "name": "公告区（已改名）",
  "type": 0,
  "sub_type": 0,
  "position": 5,
  "parent_id": "0",
  "owner_id": "123456789012345678",
  "private_type": 0,
  "speak_permission": 1
}
```

---

### 6. 删除子频道

删除子频道。需要管理员权限。私域接口，删除成功后会触发 `CHANNEL_DELETE` 事件。子频道删除后无法恢复。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}` |
| HTTP Method | `DELETE` |
| 接口频率限制 | 50 QPS |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |

### 请求示例

```
DELETE /channels/123456
```

### 响应

成功返回 HTTP 状态码 204，无响应体。

### 响应示例

```json
{}
```

---

### 7. 获取子频道在线成员数

用于查询音视频/直播子频道的在线成员数。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/online_nums` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |

### 请求示例

```
GET /channels/123456/online_nums
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| online_nums | integer | 在线成员数 |

### 响应示例

```json
{
  "online_nums": 1
}
```

---

### 8. 获取频道成员列表

用于获取频道中所有成员的详情列表，支持分页。

> **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 查询参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| after | string | 否 | 上一次回包中最后一个 member 的 user id，第一次请求填 `0`，默认为 `0` |
| limit | uint32 | 否 | 分页大小，1-400，默认是 1。成员较多的频道尽量使用较大的 limit 值，以减少请求数 |

### 请求示例

```
GET /guilds/123456/members?limit=2
```

### 响应参数

返回 [Member](#member对象) 对象数组。

| 名称 | 类型 | 描述 |
|------|------|------|
| user | [User](#user对象) | 用户的频道基础信息 |
| nick | string | 用户的昵称 |
| roles | string[] | 用户在频道内的身份组 ID |
| joined_at | ISO8601 timestamp | 用户加入频道的时间 |
| deaf | boolean | 是否被禁音 |
| mute | boolean | 是否被禁言 |
| pending | boolean | 是否待审核 |

### 分页说明

- 在每次翻页的过程中，可能会返回上一次请求已经返回过的 member 信息，需要调用方自己根据 user id 来进行去重。
- 每次返回的 member 数量与 limit 不一定完全相等。翻页请使用最后一个 member 的 user id 作为下一次请求的 after 参数，直到回包为空，拉取结束。

### 响应示例

```json
[
  {
    "user": {
      "id": "xxxxxx",
      "username": "xxxx",
      "avatar": "xxxxxx",
      "bot": false,
      "public_flags": 0,
      "system": false,
      "union_openid": "xxxxxx",
      "union_user_account": ""
    },
    "nick": "",
    "roles": ["1"],
    "joined_at": "2021-12-09T15:53:41+08:00",
    "deaf": false,
    "mute": false,
    "pending": false
  },
  {
    "user": {
      "id": "xxxxxx",
      "username": "秦时明月",
      "avatar": "xxxxxx",
      "bot": false,
      "public_flags": 0,
      "system": false,
      "union_openid": "xxxxxx",
      "union_user_account": ""
    },
    "nick": "",
    "roles": ["4"],
    "joined_at": "2021-12-02T15:19:00+08:00",
    "deaf": false,
    "mute": false,
    "pending": false
  }
]
```

---

### 9. 获取频道身份组成员列表

用于获取频道中指定身份组下所有成员的详情列表，支持分页。

> **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/roles/{role_id}/members` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| role_id | string | 是 | 身份组 ID |

### 查询参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| start_index | string | 否 | 将上一次回包中 next 填入，第一次请求填 `0`，默认为 `0` |
| limit | uint32 | 否 | 分页大小，1-400，默认是 1。成员较多的频道尽量使用较大的 limit 值，以减少请求数 |

### 请求示例

```
GET /guilds/123456/roles/4/members?limit=2
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| data | [Member](#member对象)[] | 一组用户信息对象 |
| next | string | 下一次请求的分页标识 |

### 分页说明

每次返回的 member 数量与 limit 不一定完全相等。特定管理身份组下的成员可能存在一次性返回全部的情况。

### 响应示例

```json
{
  "data": [
    {
      "user": {
        "id": "xxx",
        "username": "xxx",
        "avatar": "xxx",
        "bot": false
      },
      "nick": "xxx",
      "joined_at": "2021-11-03T20:41:36+08:00"
    }
  ],
  "next": "0"
}
```

---

### 10. 获取频道成员详情

用于获取频道中指定成员的详细信息。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members/{user_id}` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| user_id | string | 是 | 用户 ID |

### 请求示例

```
GET /guilds/123456/members/112233
```

### 响应参数

返回 [Member](#member对象) 成员对象。

| 名称 | 类型 | 描述 |
|------|------|------|
| user | [User](#user对象) | 用户的频道基础信息 |
| nick | string | 用户的昵称 |
| roles | string[] | 用户在频道内的身份组 ID |
| joined_at | ISO8601 timestamp | 用户加入频道的时间 |

### 响应示例

```json
{
  "user": {
    "id": "2823701233424295228",
    "username": "xxx",
    "avatar": "https://qqchannel-profile-1251316161.file.myqcloud.com/xxxxxxx",
    "bot": false,
    "union_openid": "",
    "union_user_account": ""
  },
  "nick": "",
  "roles": [
    "1"
  ],
  "joined_at": "2021-12-05T14:08:29+08:00"
}
```

---

### 11. 删除频道成员

用于删除频道下的指定成员。

- 需要使用的 token 对应的用户具备踢人权限。如果是机器人，要求被添加为管理员。
- 操作成功后，会触发频道成员删除事件。
- 无法移除身份为管理员的成员。

> **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members/{user_id}` |
| HTTP Method | `DELETE` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| user_id | string | 是 | 用户 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| add_blacklist | bool | 否 | 删除成员的同时，将该用户添加到频道黑名单中 |
| delete_history_msg_days | int | 否 | 删除成员的同时，撤回该成员的消息，可以指定撤回消息的时间范围。支持固定的天数：3, 7, 15, 30。特殊值 `-1`：撤回全部消息。默认值 `0` 不撤回任何消息。 |

### 请求示例

```
DELETE /guilds/123456/members/112233
```
```json
{
  "add_blacklist": true,
  "delete_history_msg_days": -1
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 12. 获取频道身份组列表

用于获取频道下的身份组列表。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/roles` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求示例

```
GET /guilds/123456/roles
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| guild_id | string | 频道 ID |
| roles | [Role](#role对象)[] | 一组频道身份组对象 |
| role_num_limit | string | 默认分组上限 |

### Role 对象字段

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 身份组 ID |
| name | string | 身份组名称 |
| color | uint32 | ARGB 颜色值（十进制） |
| hoist | int32 | 在成员列表中单独展示: 0-否, 1-是 |
| number | int32 | 当前拥有此身份组的人数 |
| member_limit | int32 | 身份组成员上限 |

### 响应示例

```json
{
  "guild_id": "123456",
  "roles": [
    {
      "id": "4",
      "name": "创建者",
      "color": 4294927682,
      "hoist": 1,
      "number": 1,
      "member_limit": 1
    },
    {
      "id": "2",
      "name": "管理员",
      "color": 4280276644,
      "hoist": 1,
      "number": 5,
      "member_limit": 50
    }
  ],
  "role_num_limit": "30"
}
```

---

### 13. 创建频道身份组

用于在频道下创建一个身份组。

- 需要使用的 token 对应的用户具备创建身份组权限。如果是机器人，要求被添加为管理员。
- 参数为非必填，但至少需要传其中之一，默认为空或 0。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/roles` |
| HTTP Method | `POST` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| name | string | 否 | 名称 |
| color | uint32 | 否 | ARGB 的 HEX 十六进制颜色值转换后的十进制数值 |
| hoist | int32 | 否 | 在成员列表中单独展示: 0-否, 1-是 |

### 请求示例

```json
{
  "name": "test",
  "color": 99999,
  "hoist": "1"
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| role_id | string | 身份组 ID |
| role | [Role](#role对象) | 所创建的频道身份组对象 |

### 响应示例

```json
{
  "role_id": "10177739",
  "role": {
    "id": "10177739",
    "name": "test",
    "color": 99999,
    "hoist": 1,
    "number": 0,
    "member_limit": 2000
  }
}
```

---

### 14. 修改频道身份组

用于修改频道下指定的身份组。

- 需要使用的 token 对应的用户具备修改身份组权限。如果是机器人，要求被添加为管理员。
- 接口会修改传入的字段，不传入的默认不会修改，至少要传入一个参数。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/roles/{role_id}` |
| HTTP Method | `PATCH` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| role_id | string | 是 | 身份组 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| name | string | 否 | 名称 |
| color | uint32 | 否 | ARGB 的 HEX 十六进制颜色值转换后的十进制数值 |
| hoist | int32 | 否 | 在成员列表中单独展示: 0-否, 1-是 |

### 请求示例

```json
{
  "name": "test",
  "color": 99999,
  "hoist": "1"
}
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| guild_id | string | 频道 ID |
| role_id | string | 身份组 ID |
| role | [Role](#role对象) | 修改后的频道身份组对象 |

### 响应示例

```json
{
  "guild_id": "3489223429684602178",
  "role_id": "10177739",
  "role": {
    "id": "10177739",
    "name": "test",
    "color": 99999,
    "hoist": 1,
    "number": 1,
    "member_limit": 2000
  }
}
```

---

### 15. 删除频道身份组

用于删除频道下指定的身份组。

- 需要使用的 token 对应的用户具备删除身份组权限。如果是机器人，要求被添加为管理员。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/roles/{role_id}` |
| HTTP Method | `DELETE` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| role_id | string | 是 | 身份组 ID |

### 请求示例

```
DELETE /guilds/123456/roles/112233
```

### 响应

成功返回 HTTP 状态码 204。

---

### 16. 添加身份组成员

用于将频道下的用户添加到指定身份组。

- 需要使用的 token 对应的用户具备增加身份组成员权限。如果是机器人，要求被添加为管理员。
- 如果要增加的身份组 ID 是 5-子频道管理员，需要增加 channel 对象来指定具体是哪个子频道。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members/{user_id}/roles/{role_id}` |
| HTTP Method | `PUT` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| user_id | string | 是 | 用户 ID |
| role_id | string | 是 | 身份组 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel | [Channel](#channel对象) | 否 | 接收一个只填充了子频道 id 字段的对象（身份组 ID 为 5 时必填） |

### 请求示例

```json
{
  "channel": {
    "id": "1744939"
  }
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 17. 删除身份组成员

用于将用户从频道的指定身份组中移除。

- 需要使用的 token 对应的用户具备删除身份组成员权限。如果是机器人，要求被添加为管理员。
- 如果要删除的身份组 ID 是 5-子频道管理员，需要增加 channel 对象来指定具体是哪个子频道。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members/{user_id}/roles/{role_id}` |
| HTTP Method | `DELETE` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| user_id | string | 是 | 用户 ID |
| role_id | string | 是 | 身份组 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel | [Channel](#channel对象) | 否 | 接收一个只填充了子频道 id 字段的对象（身份组 ID 为 5 时必填） |

### 请求示例

```
DELETE /guilds/123456/members/112233/roles/445566
```

### 响应

成功返回 HTTP 状态码 204。

---

### 18. 获取子频道用户权限

用于获取子频道下用户的权限。

- 要求操作人具有管理子频道的权限，如果是机器人，则需要将机器人设置为管理员。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/members/{user_id}/permissions` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |
| user_id | string | 是 | 用户 ID |

### 请求示例

```
GET /channels/123456/members/112233/permissions
```

### 响应参数

返回 [ChannelPermissions](#channelpermissions对象) 对象。

| 名称 | 类型 | 描述 |
|------|------|------|
| channel_id | string | 子频道 ID |
| user_id | string | 用户 ID |
| permissions | string | 用户拥有的子频道权限（位图，十进制字符串） |

### 权限值说明

| 权限 | 值 | 描述 |
|------|-----|------|
| 可查看子频道 | `0x0000000001` (1 << 0) | 支持指定成员可见类型，支持身份组可见类型 |
| 可管理子频道 | `0x0000000002` (1 << 1) | 创建者、管理员、子频道管理员都具有此权限 |
| 可发言子频道 | `0x0000000004` (1 << 2) | 支持指定成员发言类型，支持身份组发言类型 |

### 响应示例

```json
{
  "channel_id": "123456",
  "user_id": "112233",
  "permissions": "4"
}
```

---

### 19. 修改子频道用户权限

用于修改子频道下用户的权限。

- 要求操作人具有管理子频道的权限，如果是机器人，则需要将机器人设置为管理员。
- 参数包括 `add` 和 `remove` 两个字段，分别表示授予的权限以及删除的权限。要授予用户权限即把 `add` 对应位置 1，删除用户权限即把 `remove` 对应位置 1。当两个字段同一位都为 1，表现为删除权限。
- 本接口不支持修改可管理子频道权限。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/members/{user_id}/permissions` |
| HTTP Method | `PUT` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |
| user_id | string | 是 | 用户 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| add | string | 否 | 字符串形式的位图表示赋予用户的权限 |
| remove | string | 否 | 字符串形式的位图表示删除用户的权限 |

### 请求示例

```json
{
  "add": "1",
  "remove": "4"
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 20. 获取子频道身份组权限

用于获取子频道下身份组的权限。

- 要求操作人具有管理子频道的权限，如果是机器人，则需要将机器人设置为管理员。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/roles/{role_id}/permissions` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |
| role_id | string | 是 | 身份组 ID |

### 请求示例

```
GET /channels/123456/roles/112233/permissions
```

### 响应参数

返回 [ChannelPermissions](#channelpermissions对象) 对象。

| 名称 | 类型 | 描述 |
|------|------|------|
| channel_id | string | 子频道 ID |
| role_id | string | 身份组 ID |
| permissions | string | 身份组拥有的子频道权限（位图，十进制字符串） |

### 响应示例

```json
{
  "channel_id": "123456",
  "role_id": "112233",
  "permissions": "5"
}
```

---

### 21. 修改子频道身份组权限

用于修改子频道下身份组的权限。

- 要求操作人具有管理子频道的权限，如果是机器人，则需要将机器人设置为管理员。
- 参数包括 `add` 和 `remove` 两个字段，分别表示授予的权限以及删除的权限。要授予身份组权限即把 `add` 对应位置 1，删除身份组权限即把 `remove` 对应位置 1。当两个字段同一位都为 1，表现为删除权限。
- 本接口不支持修改可管理子频道权限。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/channels/{channel_id}/roles/{role_id}/permissions` |
| HTTP Method | `PUT` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 子频道 ID |
| role_id | string | 是 | 身份组 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| add | string | 否 | 字符串形式的位图表示赋予身份组的权限 |
| remove | string | 否 | 字符串形式的位图表示删除身份组的权限 |

### 请求示例

```json
{
  "add": "1",
  "remove": "4"
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 22. 获取可用权限列表

用于获取机器人在频道内可以使用的权限列表。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/api_permission` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求示例

```
GET /guilds/123456/api_permission
```

### 响应参数

| 名称 | 类型 | 描述 |
|------|------|------|
| apis | [APIPermission](#apipermission对象)[] | 机器人可用权限列表 |

#### APIPermission 对象字段

| 名称 | 类型 | 描述 |
|------|------|------|
| path | string | API 接口名，例如 `/guilds/{guild_id}/members/{user_id}` |
| method | string | 请求方法，例如 `GET` |
| desc | string | API 接口名称，例如 "获取当前频道成员信息" |
| auth_status | int | 授权状态，`1` 为已授权，`0` 为未授权 |

### 响应示例

```json
{
  "apis": [
    {
      "path": "/guilds/{guild_id}/members/{user_id}",
      "method": "GET",
      "desc": "获取当前频道成员信息",
      "auth_status": 0
    },
    {
      "path": "/channels/{channel_id}/messages",
      "method": "POST",
      "desc": "创建消息",
      "auth_status": 1
    }
  ]
}
```

---

### 23. 发送权限授权链接

用于创建 API 接口权限授权链接，该链接指向指定频道。

- 每天只能在一个频道内发 3 条（默认值）频道权限授权链接。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/api_permission/demand` |
| HTTP Method | `POST` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| channel_id | string | 是 | 授权链接发送的子频道 ID |
| api_identify | [APIPermissionDemandIdentify](#apipermissiondemandidentify对象) | 是 | API 权限需求标识对象 |
| desc | string | 是 | 机器人申请对应的 API 接口权限后可以使用功能的描述 |

#### APIPermissionDemandIdentify 对象字段

| 名称 | 类型 | 描述 |
|------|------|------|
| path | string | API 接口名，例如 `/guilds/{guild_id}` |
| method | string | 请求方法，例如 `GET` |

### 请求示例

```json
{
  "channel_id": "123456",
  "api_identify": {
    "path": "/guilds/{guild_id}",
    "method": "GET"
  },
  "desc": "显示频道信息"
}
```

### 响应参数

返回 [APIPermissionDemand](#apipermissiondemand对象) 对象。

| 名称 | 类型 | 描述 |
|------|------|------|
| guild_id | string | 申请接口权限的频道 ID |
| channel_id | string | 接口权限需求授权链接发送的子频道 ID |
| api_identify | [APIPermissionDemandIdentify](#apipermissiondemandidentify对象) | 权限接口唯一标识 |
| title | string | 接口权限链接中的接口权限描述信息 |
| desc | string | 接口权限链接中的机器人可使用功能的描述信息 |

### 响应示例

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "123456",
  "api_identify": {
    "path": "/guilds/{guild_id}",
    "method": "GET"
  },
  "title": "王者机器人申请授权频道信息接口权限",
  "desc": "申请权限后才能正常使用机器人显示频道信息功能"
}
```

---

### 24. 获取消息频率设置

用于获取机器人在频道内的消息频率设置。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/message/setting` |
| HTTP Method | `GET` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求示例

```
GET /guilds/123456/message/setting
```

### 响应参数

返回 [MessageSetting](#messagesetting对象) 对象。

| 名称 | 类型 | 描述 |
|------|------|------|
| disable_create_dm | string | 是否允许创建私信 |
| disable_push_msg | string | 是否允许发主动消息 |
| channel_ids | string[] | 子频道 ID 数组 |
| channel_push_max_num | uint32 | 每个子频道允许主动推送消息最大消息条数 |

### 响应示例

```json
{
  "disable_create_dm": true,
  "disable_push_msg": false,
  "channel_ids": [
    "1146313",
    "2651849",
    "2651149"
  ],
  "channel_push_max_num": 12
}
```

---

### 25. 频道全员禁言

用于将频道的全体成员（非管理员）禁言。

- 需要使用的 token 对应的用户具备管理员权限。如果是机器人，要求被添加为管理员。
- 该接口同样可用于解除禁言。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/mute` |
| HTTP Method | `PATCH` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| mute_end_timestamp | string | 否 | 禁言到期时间戳，绝对时间戳，单位：秒（与 mute_seconds 字段同时赋值的话，以该字段为准） |
| mute_seconds | string | 否 | 禁言多少秒（两个字段二选一，默认以 mute_end_timestamp 为准） |

### 解除禁言

将 `mute_end_timestamp` 或 `mute_seconds` 传值为字符串 `'0'` 即可解除全员禁言。

### 请求示例

```json
{
  "mute_end_timestamp": "1641916800",
  "mute_seconds": "120"
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 26. 指定成员禁言

用于禁言频道下的指定成员。

- 需要使用的 token 对应的用户具备管理员权限。如果是机器人，要求被添加为管理员。
- 该接口同样可用于解除指定成员禁言。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/members/{user_id}/mute` |
| HTTP Method | `PATCH` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |
| user_id | string | 是 | 用户 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| mute_end_timestamp | string | 否 | 禁言到期时间戳，绝对时间戳，单位：秒（与 mute_seconds 字段同时赋值的话，以该字段为准） |
| mute_seconds | string | 否 | 禁言多少秒（两个字段二选一，默认以 mute_end_timestamp 为准） |

### 解除禁言

将 `mute_end_timestamp` 或 `mute_seconds` 传值为字符串 `'0'` 即可解除指定成员禁言。

### 请求示例

```json
{
  "mute_end_timestamp": "1641916800",
  "mute_seconds": "120"
}
```

### 响应

成功返回 HTTP 状态码 204。

---

### 27. 批量成员禁言

用于将频道的指定批量成员（非管理员）禁言。

- 需要使用的 token 对应的用户具备管理员权限。如果是机器人，要求被添加为管理员。
- 该接口同样可用于批量解除禁言。

### 请求

| 字段 | 值 |
|------|-----|
| HTTP URL | `/guilds/{guild_id}/mute` |
| HTTP Method | `PATCH` |
| Content-Type | `application/json` |

### 路径参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| guild_id | string | 是 | 频道 ID |

### 请求体参数

| 名称 | 类型 | 必填 | 描述 |
|------|------|------|------|
| mute_end_timestamp | string | 否 | 禁言到期时间戳，绝对时间戳，单位：秒（与 mute_seconds 字段同时赋值的话，以该字段为准） |
| mute_seconds | string | 否 | 禁言多少秒（两个字段二选一，默认以 mute_end_timestamp 为准） |
| user_ids | string[] | 是 | 禁言成员的 user_id 列表 |

### 批量解除禁言

将 `mute_end_timestamp` 或 `mute_seconds` 传值为字符串 `'0'`，以及需要批量解除禁言的成员的 `user_ids` 列表。

### 请求示例

```json
{
  "mute_end_timestamp": "1641916800",
  "mute_seconds": "120",
  "user_ids": ["1201318637970874066", "1201318637970874067"]
}
```

### 响应参数

成功返回 HTTP 状态码 200，并返回设置成功的成员 user_ids。

| 名称 | 类型 | 描述 |
|------|------|------|
| user_ids | string[] | 成功设置的成员 user_id 列表 |

### 响应示例

```json
{
  "user_ids": ["1201318637970874066"]
}
```

---

## 附录A: 公共错误码

以下为所有接口通用的公共错误码。各接口特有的错误码请查阅具体接口文档。

### HTTP 状态码

| 值 | 含义 |
|-----|------|
| 200 | 成功 |
| 204 | 成功，但是无包体，一般用于删除操作 |
| 201, 202 | 异步操作成功，虽然说成功，但是会返回一个 error body，需要特殊处理 |
| 401 | 认证失败 |
| 404 | 未找到 API |
| 405 | HTTP method 不允许 |
| 429 | 频率限制 |
| 500 | 处理失败 |
| 504 | 处理失败 |

### 公共错误码

| 值 | 含义 |
|-----|------|
| 10001 | UnknownAccount 账号异常 |
| 10003 | UnknownChannel 子频道异常 |
| 10004 | UnknownGuild 频道异常 |
| 11281 | ErrorCheckAdminFailed 检查是否是管理员失败，系统错误，一般重试一次会好，最多只能重试一次 |
| 11282 | ErrorCheckAdminNotPass 检查是否是管理员未通过，该接口需要管理员权限，但是用户在添加机器人的时候并未授予该权限，属于逻辑错误，可以提示用户进行授权 |
| 11251 | ErrorWrongAppid 参数中的 appid 错误，开发者填的 token 错误，appid 无法识别 |
| 11252 | ErrorCheckAppPrivilegeFailed 检查应用权限失败，系统错误，一般重试一次会好，最多只能重试一次 |
| 11253 | ErrorCheckAppPrivilegeNotPass 检查应用权限不通过，该机器人应用未获得调用该接口的权限，需要向平台申请 |
| 11254 | ErrorInterfaceForbidden 应用接口被封禁，该机器人虽然获得了该接口权限，但是被封禁了 |
| 11261 | ErrorWrongAppid 参数中缺少 appid，同 11251 |
| 11262 | ErrorCheckRobot 当前接口不支持使用机器人 Bot Token 调用 |
| 11263 | ErrorCheckGuildAuth 检查频道权限失败，系统错误，一般重试一次会好，最多只能重试一次 |
| 11264 | ErrorGuildAuthNotPass 检查小站权限未通过，管理员添加机器人的时候未授予该接口权限，属于逻辑错误，可提示用户进行授权 |
| 11265 | ErrorRobotHasBaned 机器人已经被封禁 |
| 11241 | ErrorWrongToken 参数中缺少 token |
| 11242 | ErrorCheckTokenFailed 校验 token 失败，系统错误，一般重试一次会好，最多只能重试一次 |
| 11243 | ErrorCheckTokenNotPass 校验 token 未通过，用户填充的 token 错误，需要开发者进行检查 |
| 11273 | ErrorCheckUserAuth 检查用户权限失败，当前接口不支持使用 Bearer Token 调用 |
| 11274 | ErrorUserAuthNotPass 检查用户权限未通过，用户 OAuth 授权时未给与该接口权限，可提示用户重新进行授权 |
| 11275 | ErrorWrongAppid 无 appid，同 11251 |
| 11301 | ErrorGetHTTPHeader HTTP Header 无效 |
| 11302 | ErrorGetHeaderUIN HTTP Header 无效 |
| 11303 | ErrorGetNick 获取昵称失败 |
| 11304 | ErrorGetAvatar 获取头像失败 |
| 11305 | ErrorGetGuildID 获取频道 ID 失败 |
| 11306 | ErrorGetGuildInfo 获取频道信息失败 |
| 12001 | ReplaceIDFailed 替换 id 失败 |
| 12002 | RequestInvalid 请求体错误 |
| 12003 | ResponseInvalid 回包错误 |
| 20028 | ChannelHitWriteRateLimit 子频道消息触发限频 |
| 50006 | CannotSendEmptyMessage 消息为空 |
| 50035 | InvalidFormBody form-data 内容异常 |
| 50037 | 带有 markdown 消息只支持 markdown 或者 keyboard 组合 |
| 50038 | 非同频道同子频道 |
| 50039 | 获取消息失败 |
| 50040 | 消息模版类型错误 |
| 50041 | markdown 有空值 |
| 50042 | markdown 列表长达最大值 |
| 50043 | guild_id 转换失败 |
| 50045 | 不能回复机器人自己产生的消息 |
| 50046 | 非 at 机器人消息 |
| 50047 | 非机器人产生的消息或者 at 机器人消息 |
| 50048 | message id 不能为空 |
| 50049 | 只能修改含有 keyboard 元素的消息 |
| 50050 | 修改消息时，keyboard 元素不能为空 |
| 50051 | 只能修改机器人自己发送的消息 |
| 50053 | 修改消息错误 |
| 50054 | markdown 模版参数错误 |
| 50055 | 无效的 markdown content |
| 50056 | 不允许发送 markdown content |
| 50057 | markdown 参数只支持原生语法或者模版二选一 |

### 子频道权限错误 (301000~301099)

| 值 | 含义 |
|-----|------|
| 301000 | 参数错误 |
| 301001 | 查询频道信息错误 |
| 301002 | 查询子频道权限错误 |
| 301003 | 修改子频道权限错误 |
| 301004 | 私密子频道关联的人数到达上限 |
| 301005 | 调用 Rpc 服务失败 |
| 301006 | 非群成员没有查询权限 |
| 301007 | 参数超过数量限制 |

### 日程相关错误 (302000~302024)

| 值 | 含义 |
|-----|------|
| 302000 | 参数错误 |
| 302001 | 查询频道信息错误 |
| 302002 | 查询日程列表失败 |
| 302003 | 查询日程失败 |
| 302004 | 修改日程失败 |
| 302005 | 删除日程失败 |
| 302006 | 创建日程失败 |
| 302007 | 获取创建者信息失败 |
| 302008 | 子频道 ID 不能为空 |
| 302009 | 频道系统错误，请联系客服 |
| 302010 | 暂无修改日程权限 |
| 302011 | 日程活动已被删除 |
| 302012 | 每天只能创建 10 个日程，明天再来吧！ |
| 302013 | 创建日程触发安全打击 |
| 302014 | 日程持续时间超过 7 天，请重新选择 |
| 302015 | 开始时间不能早于当前时间 |
| 302016 | 结束时间不能早于开始时间 |
| 302017 | Schedule 对象为空 |
| 302018 | 参数类型转换失败 |
| 302019 | 调用下游失败，请联系客服 |
| 302020 | 日程内容违规、账号违规 |
| 302021 | 频道内当日新增活动达上限 |
| 302022 | 不能绑定非当前频道的子频道 |
| 302023 | 开始时跳转不可绑定日程子频道 |
| 302024 | 绑定的子频道不存在 |

### 消息相关错误 (304003~304052)

| 值 | 含义 |
|-----|------|
| 304003 | URL_NOT_ALLOWED url 未报备 |
| 304004 | ARK_NOT_ALLOWED 没有发 ark 消息权限 |
| 304005 | EMBED_LIMIT embed 长度超限 |
| 304006 | SERVER_CONFIG 后台配置错误 |
| 304007 | GET_GUILD 查询频道异常 |
| 304008 | GET_BOT 查询机器人异常 |
| 304009 | GET_CHENNAL 查询子频道异常 |
| 304010 | CHANGE_IMAGE_URL 图片转存错误 |
| 304011 | NO_TEMPLATE 模板不存在 |
| 304012 | GET_TEMPLATE 取模板错误 |
| 304014 | TEMPLATE_PRIVILEGE 没有模板权限 |
| 304016 | SEND_ERROR 发消息错误 |
| 304017 | UPLOAD_IMAGE 图片上传错误 |
| 304018 | SESSION_NOT_EXIST 机器人没连上 gateway |
| 304019 | AT_EVERYONE_TIMES @全体成员 次数超限 |
| 304020 | FILE_SIZE 文件大小超限 |
| 304021 | GET_FILE 下载文件错误 |
| 304022 | PUSH_TIME 推送消息时间限制 |
| 304023 | PUSH_MSG_ASYNC_OK 推送消息异步调用成功，等待人工审核 |
| 304024 | REPLY_MSG_ASYNC_OK 回复消息异步调用成功，等待人工审核 |
| 304025 | BEAT 消息被打击 |
| 304026 | MSG_ID 回复的消息 id 错误 |
| 304027 | MSG_EXPIRE 回复的消息过期 |
| 304028 | MSG_PROTECT 非 At 当前用户的消息不允许回复 |
| 304029 | CORPUS_ERROR 调语料服务错误 |
| 304030 | CORPUS_NOT_MATCH 语料不匹配 |
| 304031 | 私信已关闭 |
| 304032 | 私信不存在 |
| 304033 | 拉私信错误 |
| 304034 | 不是私信成员 |
| 304035 | 推送消息超过子频道数量限制 |
| 304036 | 没有 markdown 模板的权限 |
| 304037 | 没有发消息按钮组件的权限 |
| 304038 | 消息按钮组件不存在 |
| 304039 | 消息按钮组件解析错误 |
| 304040 | 消息按钮组件消息内容错误 |
| 304044 | 取消息设置错误 |
| 304045 | 子频道主动消息数限频 |
| 304046 | 不允许在此子频道发主动消息 |
| 304047 | 主动消息推送超过限制的子频道数 |
| 304048 | 不允许在此频道发主动消息 |
| 304049 | 私信主动消息数限频 |
| 304050 | 私信主动消息总量限频 |
| 304051 | 消息设置引导请求构造错误 |
| 304052 | 发消息设置引导超频 |

### 消息撤回错误 (306001~306006)

| 值 | 含义 |
|-----|------|
| 306001 | param invalid 撤回消息参数错误 |
| 306002 | msgid error 消息 id 错误 |
| 306003 | fail to get message 获取消息错误(可重试) |
| 306004 | no permission to delete message 没有撤回此消息的权限 |
| 306005 | retract message error 消息撤回失败(可重试) |
| 306006 | fail to get channel 获取子频道失败(可重试) |

### 公告错误 (501000~501020)

| 值 | 含义 |
|-----|------|
| 501001 | 参数校验失败 |
| 501002 | 创建子频道公告失败(可重试) |
| 501003 | 删除子频道公告失败(可重试) |
| 501004 | 获取频道信息失败(可重试) |
| 501005 | MessageID 错误 |
| 501006 | 创建频道全局公告失败(可重试) |
| 501007 | 删除频道全局公告失败(可重试) |
| 501008 | MessageID 不存在 |
| 501009 | MessageID 解析失败 |
| 501010 | 此条消息非子频道内消息 |
| 501011 | 创建精华消息失败(可重试) |
| 501012 | 删除精华消息失败(可重试) |
| 501013 | 精华消息超过最大数量 |
| 501014 | 安全打击 |
| 501015 | 此消息不允许设置 |
| 501016 | 频道公告子频道推荐超过最大数量 |
| 501017 | 非频道主或管理员 |
| 501018 | 推荐子频道 ID 无效 |
| 501019 | 公告类型错误 |
| 501020 | 创建推荐子频道类型频道公告失败 |

### 禁言相关错误 (502000~502010)

| 值 | 含义 |
|-----|------|
| 502001 | 频道 id 无效 |
| 502002 | 频道 id 为空 |
| 502003 | 用户 id 无效 |
| 502004 | 用户 id 为空 |
| 502005 | timestamp 不合法 |
| 502006 | timestamp 无效 |
| 502007 | 参数转换错误 |
| 502008 | rpc 调用失败 |
| 502009 | 安全打击 |
| 502010 | 请求头错误 |

### 论坛相关错误 (503001~503020)

| 值 | 含义 |
|-----|------|
| 503001 | 频道 id 无效 |
| 503002 | 频道 id 为空 |
| 503003 | 获取子频道信息失败 |
| 503004 | 超出发布帖子的频次限制 |
| 503005 | 帖子标题为空 |
| 503006 | 帖子内容为空 |
| 503007 | 帖子 ID 为空 |
| 503008 | 获取 X-Uin 失败 |
| 503009 | 帖子 ID 无效或不合法 |
| 503010 | 通过 Uin 获取 TinyID 失败 |
| 503011 | 帖子 ID 里面的时间戳无效或不合法 |
| 503012 | 帖子不存在或已删除 |
| 503013 | 服务器内部错误 |
| 503014 | 帖子 JSON 内容解析失败 |
| 503015 | 帖子内容转换失败 |
| 503016 | 链接数量超过限制 |
| 503017 | 字数超过限制 |
| 503018 | 图片数量超过限制 |
| 503019 | 视频数量超过限制 |
| 503020 | 标题长度超过限制 |

### 消息频率相关错误 (504000~504004)

| 值 | 含义 |
|-----|------|
| 504001 | 请求参数无效错误 |
| 504002 | 获取 HTTP 头失败 |
| 504003 | 获取 BOT UIN 错误 |
| 504004 | 获取消息频率设置信息错误 |

### 频道权限错误 (610000~610014)

| 值 | 含义 |
|-----|------|
| 610001 | 获取频道 ID 失败 |
| 610002 | 获取 HTTP 头失败 |
| 610003 | 获取机器人号码失败 |
| 610004 | 获取机器人角色失败 |
| 610005 | 获取机器人角色内部错误 |
| 610006 | 拉取机器人权限列表失败 |
| 610007 | 机器人不在频道内 |
| 610008 | 无效参数 |
| 610009 | 获取 API 接口详情失败 |
| 610010 | API 接口已授权 |
| 610011 | 获取机器人信息失败 |
| 610012 | 限频失败 |
| 610013 | 已限频 |
| 610014 | api 授权链接发送失败 |

### 表情表态错误 (620001~620007)

| 值 | 含义 |
|-----|------|
| 620001 | 表情表态无效参数 |
| 620002 | 已经达到表情反应的类型数量上限 |
| 620003 | 已经设置过该表情表态 |
| 620004 | 没有设置过该表情表态 |
| 620005 | 没有权限设置表情表态 |
| 620006 | 操作限频 |
| 620007 | 表情表态操作失败，请重试 |

### 互动回调数据更新错误 (630001~630007)

| 值 | 含义 |
|-----|------|
| 630001 | 互动回调数据更新无效参数 |
| 630002 | 互动回调数据更新获取 AppID 失败 |
| 630003 | 互动回调数据 AppID 不匹配 |
| 630004 | 互动回调数据更新内部存储错误 |
| 630005 | 互动回调数据更新内部存储读取错误 |
| 630006 | 互动回调数据更新读取请求 AppID 失败 |
| 630007 | 互动回调数据太大 |

### 发消息错误 (1000000~2999999)

| 值 | 含义 |
|-----|------|
| 1100100 | 安全打击：消息被限频 |
| 1100101 | 安全打击：内容涉及敏感，请返回修改 |
| 1100102 | 安全打击：抱歉，暂未获得新功能体验资格 |
| 1100103 | 安全打击 |
| 1100104 | 安全打击：该群已失效或当前群已不存在 |
| 1100300 | 系统内部错误 |
| 1100301 | 调用方不是群成员 |
| 1100302 | 获取指定频道名称失败 |
| 1100303 | 主页频道非管理员不允许发消息 |
| 1100304 | @次数鉴权失败 |
| 1100305 | TinyId 转换 Uin 失败 |
| 1100306 | 非私有频道成员 |
| 1100307 | 非白名单应用子频道 |
| 1100308 | 触发频道内限频 |
| 1100499 | 其他错误 |

### 编辑消息错误 (3000000~3999999)

| 值 | 含义 |
|-----|------|
| 3300006 | 安全打击 |

### 错误响应格式

```json
{
  "err_code": 40034005,
  "message": "回复消息msg_id已过期",
  "trace_id": "4a8a61565b909f199b1ec169fdd6f49e"
}
```

### 全链路追踪

平台的链路追踪 TraceID 可通过两种方式获取：
- HTTP 响应头：`X-Tps-trace-ID` 字段
- 响应 Body：返回体中的 `trace_id` 字段

如果开发者有无法自行定位的问题，需要找平台协助时，可提取该 ID 提交给平台方，方便查询相关日志。

---

## 附录B: 数据模型

### Member 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| user | [User](#user对象) | 用户的频道基础信息，只有成员相关接口中会填充此信息 |
| nick | string | 用户的昵称 |
| roles | string[] | 用户在频道内的身份组 ID |
| joined_at | ISO8601 timestamp | 用户加入频道的时间 |

### MemberWithGuildID 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道 ID |
| user | [User](#user对象) | 用户的频道基础信息 |
| nick | string | 用户的昵称 |
| roles | string[] | 用户在频道内的身份组 ID |
| joined_at | ISO8601 timestamp | 用户加入频道的时间 |

### User 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | string | 用户 ID |
| username | string | 用户名 |
| avatar | string | 用户头像 URL |
| bot | boolean | 是否为机器人 |
| public_flags | int | 公共标识 |
| system | boolean | 是否为系统用户 |
| union_openid | string | 联合 OpenID |
| union_user_account | string | 联合用户账号 |

### Role 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | string | 身份组 ID |
| name | string | 身份组名称 |
| color | uint32 | ARGB 颜色值（十进制） |
| hoist | int32 | 在成员列表中单独展示: 0-否, 1-是 |
| number | int32 | 当前拥有此身份组的人数 |
| member_limit | int32 | 身份组成员上限 |

### ChannelPermissions 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| channel_id | string | 子频道 ID |
| user_id/role_id | string | 用户 ID 或身份组 ID，只会返回其中之一 |
| permissions | string | 用户拥有的子频道权限 |

#### Permissions 权限位图

权限使用位图表示，传递时序列化为十进制数值字符串。如权限值为 `0x6FFF`，会被序列化为十进制 `"28671"`。

| 权限 | 值 | 描述 |
|------|-----|------|
| 可查看子频道 | `0x0000000001` (1 << 0) | 支持指定成员可见类型，支持身份组可见类型 |
| 可管理子频道 | `0x0000000002` (1 << 1) | 创建者、管理员、子频道管理员都具有此权限 |
| 可发言子频道 | `0x0000000004` (1 << 2) | 支持指定成员发言类型，支持身份组发言类型 |

### APIPermission 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| path | string | API 接口名，例如 `/guilds/{guild_id}/members/{user_id}` |
| method | string | 请求方法，例如 `GET` |
| desc | string | API 接口名称，例如 "获取频道信息" |
| auth_status | int | 授权状态，`1` 为已授权 |

### APIPermissionDemand 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 申请接口权限的频道 ID |
| channel_id | string | 接口权限需求授权链接发送的子频道 ID |
| api_identify | [APIPermissionDemandIdentify](#apipermissiondemandidentify对象) | 权限接口唯一标识 |
| title | string | 接口权限链接中的接口权限描述信息 |
| desc | string | 接口权限链接中的机器人可使用功能的描述信息 |

### APIPermissionDemandIdentify 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| path | string | API 接口名，例如 `/guilds/{guild_id}` |
| method | string | 请求方法，例如 `GET` |

### MessageSetting 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| disable_create_dm | string | 是否允许创建私信 |
| disable_push_msg | string | 是否允许发主动消息 |
| channel_ids | string[] | 子频道 ID 数组 |
| channel_push_max_num | uint32 | 每个子频道允许主动推送消息最大消息条数 |

---

> 文档编写时间：2026-07-26
> 数据来源：QQ 机器人官方文档 https://bot.q.qq.com/wiki/develop/api-v2/

---

# 四、频道内容管理

## 数据对象参考

### Announces 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道 id |
| channel_id | string | 子频道 id |
| message_id | string | 消息 id |
| announces_type | uint32 | 公告类别 0:成员公告 1:欢迎公告，默认成员公告 |
| recommend_channels | RecommendChannel[] | 推荐子频道详情列表 |

### RecommendChannel 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| channel_id | string | 子频道 id |
| introduce | string | 推荐语 |

### PinsMessage 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道 id |
| channel_id | string | 子频道 id |
| message_ids | string[] | 子频道内精华消息 id 数组 |

### Schedule 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | string | 日程 id |
| name | string | 日程名称 |
| description | string | 日程描述 |
| start_timestamp | string | 日程开始时间戳(ms) |
| end_timestamp | string | 日程结束时间戳(ms) |
| creator | Member | 创建者 |
| jump_channel_id | string | 日程开始时跳转到的子频道 id |
| remind_type | string | 日程提醒类型，取值参考 RemindType |

### RemindType 枚举

| 值 | 描述 |
|----|------|
| 0 | 不提醒 |
| 1 | 开始时提醒 |
| 2 | 开始前 5 分钟提醒 |
| 3 | 开始前 15 分钟提醒 |
| 4 | 开始前 30 分钟提醒 |
| 5 | 开始前 60 分钟提醒 |

### AudioControl 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| audio_url | string | 音频数据的url，status为0时传 |
| text | string | 状态文本（比如：简单爱-周杰伦），可选，status为0时传，其他操作不传 |
| status | STATUS | 播放状态，参考 STATUS 枚举 |

### STATUS 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| START | 0 | 开始播放操作 |
| PAUSE | 1 | 暂停播放操作 |
| RESUME | 2 | 继续播放操作 |
| STOP | 3 | 停止播放操作 |

### AudioAction 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道id |
| channel_id | string | 子频道id |
| audio_url | string | 音频数据的url，status为0时传 |
| text | string | 状态文本，可选，status为0时传，其他操作不传 |

### Thread 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道ID |
| channel_id | string | 子频道ID |
| author_id | string | 作者ID |
| thread_info | ThreadInfo | 主帖内容 |

### ThreadInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| thread_id | string | 主帖ID |
| title | string | 帖子标题 |
| content | string | 帖子内容 |
| date_time | ISO8601 timestamp | 发表时间 |

### Post 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道ID |
| channel_id | string | 子频道ID |
| author_id | string | 作者ID |
| post_info | PostInfo | 帖子内容 |

### PostInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| thread_id | string | 主题ID |
| post_id | string | 帖子ID |
| content | string | 帖子内容 |
| date_time | string | 评论时间 |

### Reply 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道ID |
| channel_id | string | 子频道ID |
| author_id | string | 作者ID |
| reply_info | ReplyInfo | 回复内容 |

### ReplyInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| thread_id | string | 主题ID |
| post_id | string | 帖子ID |
| reply_id | string | 回复ID |
| content | string | 回复内容 |
| date_time | string | 回复时间 |

### AuditResult 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道ID |
| channel_id | string | 子频道ID |
| author_id | string | 作者ID |
| thread_id | string | 主题ID |
| post_id | string | 帖子ID |
| reply_id | string | 回复ID |
| type | uint32 | AuditType 审核的类型 |
| result | uint32 | 审核结果. 0:成功 1:失败 |
| err_msg | string | result不为0时错误信息 |

### AuditType 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| PUBLISH_THREAD | 1 | 帖子 |
| PUBLISH_POST | 2 | 评论 |
| PUBLISH_REPLY | 3 | 回复 |

### RichText 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| paragraphs | Paragraph[] | 段落，一段落一行，段落内无元素的为空行 |

### Paragraph 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| elems | Elem[] | 元素列表 |
| props | ParagraphProps | 段落属性 |

### Elem 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| text | TextElem | 文本元素 |
| image | ImageElem | 图片元素 |
| video | VideoElem | 视频元素 |
| url | URLElem | URL元素 |
| type | ElemType | 元素类型 |

### ElemType 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| ELEM_TYPE_TEXT | 1 | 文本 |
| ELEM_TYPE_IMAGE | 2 | 图片 |
| ELEM_TYPE_VIDEO | 3 | 视频 |
| ELEM_TYPE_URL | 4 | URL |

### RichObject 对象（旧版富文本格式）

| 字段名 | 类型 | 描述 |
|--------|------|------|
| type | int | RichType 富文本类型 |
| text_info | TextInfo | 文本 |
| at_info | AtInfo | @ 内容 |
| url_info | URLInfo | 链接 |
| emoji_info | EmojiInfo | 表情 |
| channel_info | ChannelInfo | 提到的子频道 |

### RichType 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| TEXT | 1 | 普通文本 |
| AT | 2 | at信息 |
| URL | 3 | url信息 |
| EMOJI | 4 | 表情 |
| CHANNEL | 5 | #子频道 |
| VIDEO | 10 | 视频 |
| IMAGE | 11 | 图片 |

### TextInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| text | string | 普通文本 |

### AtInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| type | AtType | at类型 |
| user_info | AtUserInfo | 用户 |
| role_info | AtRoleInfo | 角色组信息 |
| guild_info | AtGuildInfo | 频道信息 |

### AtType 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| AT_EXPLICIT_USER | 1 | at特定人 |
| AT_ROLE_GROUP | 2 | at角色组所有人 |
| AT_GUILD | 3 | at频道所有人 |

### AtUserInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | string | 身份组ID |
| nick | string | 用户昵称 |

### AtRoleInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| role_id | uint64 | 身份组ID |
| name | string | 身份组名称 |
| color | uint32 | 颜色值 |

### AtGuildInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| guild_id | string | 频道ID |
| guild_name | string | 频道名称 |

### URLInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| url | string | 链接地址 |
| display_text | string | 链接显示文本 |

### EmojiInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | string | 表情id |
| type | string | 表情类型 |
| name | string | 名称 |
| url | string | 链接 |

### ChannelInfo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| channel_id | uint64 | 子频道id |
| channel_name | string | 子频道名称 |

### TextElem 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| text | string | 正文 |
| props | TextProps | 文本属性 |

### TextProps 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| font_bold | bool | 加粗 |
| italic | bool | 斜体 |
| underline | bool | 下划线 |

### ImageElem 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| third_url | string | 第三方图片链接 |
| width_percent | double | 宽度比例（缩放比，在屏幕里显示的比例） |

### PlatImage 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| url | string | 架平图片链接 |
| width | uint32 | 图片宽度 |
| height | uint32 | 图片高度 |
| image_id | string | 图片ID |

### VideoElem 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| third_url | string | 第三方视频文件链接 |

### PlatVideo 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| url | string | 架平图片链接 |
| width | uint32 | 图片宽度 |
| height | uint32 | 图片高度 |
| video_id | string | 视频ID |
| duration | uint32 | 视频时长 |
| cover | PlatImage | 视频封面图属性 |

### URLElem 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| url | string | URL链接 |
| desc | string | URL描述 |

### ParagraphProps 对象

| 字段名 | 类型 | 描述 |
|--------|------|------|
| alignment | int32 | 段落对齐方向属性，数值可以参考 Alignment |

### Alignment 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| ALIGNMENT_LEFT | 0 | 左对齐 |
| ALIGNMENT_MIDDLE | 1 | 居中 |
| ALIGNMENT_RIGHT | 2 | 右对齐 |

---

### 1. 创建频道公告

### 接口

```
POST /guilds/{guild_id}/announces
```

### 功能描述

用于创建频道全局公告，公告类型分为 **消息类型的频道公告** 和 **推荐子频道类型的频道公告**。

- 当请求参数 `message_id` 有值时，优先创建消息类型的频道公告，消息类型的频道公告只能创建成员公告类型的频道公告。
- 创建推荐子频道类型的频道全局公告请将 `message_id` 设置为空，并设置对应的 `announces_type` 和 `recommend_channels` 请求参数，会一次全部替换推荐子频道列表。
- 推荐子频道和消息类型全局公告不能同时存在，会互相顶替设置。
- 同频道内推荐子频道最多只能创建 3 条。
- 只有子频道权限为全体成员可见才可设置为推荐子频道。
- 删除推荐子频道类型的频道公告请使用删除频道公告接口，并将 `message_id` 设置为 `all`。

### Content-Type

```
application/json
```

### 请求参数

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message_id | string | 选填 | 消息 id，message_id 有值则优选将某条消息设置为成员公告 |
| channel_id | string | 选填 | 子频道 id，message_id 有值则为必填 |
| announces_type | uint32 | 选填 | 公告类别 0:成员公告，1:欢迎公告，默认为成员公告 |
| recommend_channels | RecommendChannel[] | 选填 | 推荐子频道列表，会一次全部替换推荐子频道列表 |

### 返回

返回 [Announces](#announces-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例 - 创建消息类型频道公告

```json
{
  "channel_id": "123456",
  "message_id": "xxxxxx"
}
```

### 响应示例 - 创建消息类型频道公告

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "123456",
  "message_id": "xxxxxx",
  "announces_type": 0,
  "recommend_channels": []
}
```

### 请求示例 - 创建推荐子频道类型的频道公告

```json
{
  "announces_type": 1,
  "recommend_channels": [
    {
      "channel_id": "xxxx",
      "introduce": "推荐语"
    },
    {
      "channel_id": "xxxx",
      "introduce": "推荐语"
    }
  ]
}
```

### 响应示例 - 创建推荐子频道类型的频道公告

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "xxxxx",
  "message_id": "",
  "announces_type": 1,
  "recommend_channels": [
    {
      "channel_id": "xxxx",
      "introduce": "推荐语"
    },
    {
      "channel_id": "xxxx",
      "introduce": "推荐语"
    }
  ]
}
```

---

### 2. 删除频道公告

### 接口

```
DELETE /guilds/{guild_id}/announces/{message_id}
```

### 功能描述

用于删除频道 `guild_id` 下指定 `message_id` 的全局公告。

- `message_id` 有值时，会校验 `message_id` 合法性，若不校验 `message_id`，请将 `message_id` 设置为 `all`。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| guild_id | string | 是 | 频道ID |
| message_id | string | 是 | 消息ID，设置为 `all` 可删除所有推荐子频道类型的频道公告 |

### 返回

成功返回 HTTP 状态码 `204`，无响应体。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
DELETE /guilds/123456/announces/112233
```

---

### 3. 添加精华消息

### 接口

```
PUT /channels/{channel_id}/pins/{message_id}
```

### 功能描述

用于添加子频道 `channel_id` 内的精华消息。

- 精华消息在一个子频道内最多只能创建 **20 条**。
- 只有可见的消息才能被设置为精华消息。
- 接口返回对象中 `message_ids` 为当前请求后子频道内所有精华消息 message_id 数组。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| message_id | string | 是 | 消息ID |

### 返回

返回 [PinsMessage](#pinsmessage-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
PUT /channels/123456/pins/112233
```

### 响应示例

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "xxxxxx",
  "message_ids": ["xxxxx"]
}
```

---

### 4. 删除精华消息

### 接口

```
DELETE /channels/{channel_id}/pins/{message_id}
```

### 功能描述

用于删除子频道 `channel_id` 下指定 `message_id` 的精华消息。

- 删除子频道内全部精华消息，请将 `message_id` 设置为 `all`。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| message_id | string | 是 | 消息ID，设置为 `all` 可删除全部精华消息 |

### 返回

成功返回 HTTP 状态码 `204`，无响应体。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
DELETE /channels/123456/pins/112233
```

---

### 5. 获取精华消息

### 接口

```
GET /channels/{channel_id}/pins
```

### 功能描述

用于获取子频道 `channel_id` 内的精华消息。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 返回

返回 [PinsMessage](#pinsmessage-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 响应示例

```json
{
  "guild_id": "xxxxxx",
  "channel_id": "xxxxxx",
  "message_ids": ["xxxxx"]
}
```

---

### 6. 获取频道日程列表

### 接口

```
GET /channels/{channel_id}/schedules
```

### 功能描述

用于获取 `channel_id` 指定的子频道中当天的日程列表。

- 若带了参数 `since`，则返回结束时间在 `since` 之后的日程列表；若未带参数 `since`，则默认返回当天的日程列表。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 查询参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| since | uint64 | 否 | 起始时间戳(ms) |

### 返回

返回 [Schedule](#schedule-对象) 对象数组。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```json
{
  "since": 1642076400000
}
```

### 响应示例

```json
[
  {
    "id": "xxxxxx",
    "name": "上王者",
    "start_timestamp": "1642076400000",
    "end_timestamp": "1642083600000",
    "creator": {
      "user": {
        "id": "xxxxxx",
        "username": "xxxxxx",
        "bot": true
      },
      "nick": "",
      "joined_at": "2022-01-11T10:24:13+08:00"
    },
    "jump_channel_id": "0",
    "remind_type": "0"
  }
]
```

---

### 7. 获取日程详情

### 接口

```
GET /channels/{channel_id}/schedules/{schedule_id}
```

### 功能描述

获取日程子频道 `channel_id` 下 `schedule_id` 指定的日程的详情。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| schedule_id | string | 是 | 日程ID |

### 返回

返回 [Schedule](#schedule-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
GET /channels/123455/schedules/112233
```

### 响应示例

```json
{
  "id": "112233",
  "name": "上王者",
  "start_timestamp": "1642076400000",
  "end_timestamp": "1642083600000",
  "creator": {
    "user": {
      "id": "xxxxxx",
      "username": "xxxxxx",
      "bot": true
    },
    "nick": "",
    "joined_at": "2022-01-11T10:24:13+08:00"
  },
  "jump_channel_id": "0",
  "remind_type": "0"
}
```

---

### 8. 创建日程

### 接口

```
POST /channels/{channel_id}/schedules
```

### 功能描述

用于在 `channel_id` 指定的日程子频道下创建一个日程。

- 要求操作人具有管理频道的权限，如果是机器人，则需要将机器人设置为管理员。
- 创建成功后，返回创建成功的日程对象。
- **创建操作频次限制：**
  - 单个管理员每天限 10 次。
  - 单个频道每天 100 次。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 请求参数

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| schedule | Schedule | 是 | 日程对象，不需要带 id |

Schedule 请求参数：

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| name | string | 是 | 日程名称 |
| start_timestamp | string | 是 | 日程开始时间戳(ms) |
| end_timestamp | string | 是 | 日程结束时间戳(ms) |
| jump_channel_id | string | 否 | 日程开始时跳转到的子频道 id |
| remind_type | string | 否 | 日程提醒类型，参考 RemindType 枚举 |

### 返回

返回 [Schedule](#schedule-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```json
{
  "schedule": {
    "name": "上王者",
    "start_timestamp": "1642076453000",
    "end_timestamp": "1642083653000",
    "jump_channel_id": "0",
    "remind_type": "0"
  }
}
```

### 响应示例

```json
{
  "id": "xxxxxx",
  "name": "上王者",
  "start_timestamp": "1642076400000",
  "end_timestamp": "1642083600000",
  "creator": {
    "user": {
      "id": "xxxxxx",
      "username": "xxxxxx",
      "bot": true
    },
    "nick": "",
    "joined_at": "2022-01-11T10:24:13+08:00"
  },
  "jump_channel_id": "0",
  "remind_type": "0"
}
```

---

### 9. 修改日程

### 接口

```
PATCH /channels/{channel_id}/schedules/{schedule_id}
```

### 功能描述

用于修改日程子频道 `channel_id` 下 `schedule_id` 指定的日程的详情。

- 要求操作人具有管理频道的权限，如果是机器人，则需要将机器人设置为管理员。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| schedule_id | string | 是 | 日程ID |

### 请求参数

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| schedule | Schedule | 是 | 日程对象，不需要带 id |

Schedule 请求参数（同创建日程）：

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| name | string | 否 | 日程名称 |
| start_timestamp | string | 否 | 日程开始时间戳(ms) |
| end_timestamp | string | 否 | 日程结束时间戳(ms) |
| jump_channel_id | string | 否 | 日程开始时跳转到的子频道 id |
| remind_type | string | 否 | 日程提醒类型 |

### 返回

返回 [Schedule](#schedule-对象) 对象。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```json
{
  "schedule": {
    "name": "今晚八点上王者",
    "start_timestamp": "1642076453000",
    "end_timestamp": "1642083653000",
    "jump_channel_id": "0",
    "remind_type": "0"
  }
}
```

### 响应示例

```json
{
  "id": "xxxxxx",
  "name": "今晚八点上王者",
  "start_timestamp": "1642076453000",
  "end_timestamp": "1642083653000",
  "creator": {
    "user": {
      "id": "xxxxxx",
      "username": "xxxxxx",
      "bot": true
    },
    "nick": "",
    "joined_at": "2022-01-13T11:02:21+08:00"
  },
  "jump_channel_id": "0",
  "remind_type": "0"
}
```

---

### 10. 删除日程

### 接口

```
DELETE /channels/{channel_id}/schedules/{schedule_id}
```

### 功能描述

用于删除日程子频道 `channel_id` 下 `schedule_id` 指定的日程。

- 要求操作人具有管理频道的权限，如果是机器人，则需要将机器人设置为管理员。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| schedule_id | string | 是 | 日程ID |

### 返回

成功返回 HTTP 状态码 `204`，无响应体。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
DELETE /channels/123456/schedules/112233
```

---

### 11. 音频控制

### 接口

```
POST /channels/{channel_id}/audio
```

### 功能描述

用于控制子频道 `channel_id` 下的音频。

- **音频接口：仅限音频类机器人才能使用**，后续会根据机器人类型自动开通接口权限，现如需调用，需联系平台申请权限。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 请求参数

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| audio_url | string | 条件必填 | 音频数据的url，status为0时必填 |
| text | string | 选填 | 状态文本（比如：简单爱-周杰伦），status为0时传，其他操作不传 |
| status | STATUS | 是 | 播放状态：0=开始播放，1=暂停，2=继续，3=停止 |

### 返回

成功返回空对象。

```json
{}
```

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```json
{
  "audio_url": "http://xxxxx.mp3",
  "text": "xxx",
  "status": 0
}
```

### 响应示例

```json
{}
```

---

### 12. 机器人上麦

### 接口

```
PUT /channels/{channel_id}/mic
```

### 功能描述

机器人在 `channel_id` 对应的语音子频道上麦。

- **音频接口：仅限音频类机器人才能使用**，后续会根据机器人类型自动开通接口权限，现如需调用，需联系平台申请权限。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 请求参数

无请求体参数（仅路径参数 `channel_id`）。

### 返回

成功返回空对象。

```json
{}
```

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
PUT /channels/123456/mic
```

请求体：

```json
{}
```

### 响应示例

```json
{}
```

---

### 13. 机器人下麦

### 接口

```
DELETE /channels/{channel_id}/mic
```

### 功能描述

机器人在 `channel_id` 对应的语音子频道下麦。

- **音频接口：仅限音频类机器人才能使用**，后续会根据机器人类型自动开通接口权限，现如需调用，需联系平台申请权限。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 请求参数

无请求体参数（仅路径参数 `channel_id`）。

### 返回

成功返回空对象。

```json
{}
```

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
DELETE /channels/123456/mic
```

请求体：

```json
{}
```

### 响应示例

```json
{}
```

---

### 14. 获取帖子列表

### 接口

```
GET /channels/{channel_id}/threads
```

### 功能描述

用于获取子频道下的帖子列表。

> ⚠️ **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 返回参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| threads | Thread[] | 帖子列表对象（返回值里面的content字段，可参照 RichText 结构） |
| is_finish | uint32 | 是否拉取完毕(0:否；1:是) |

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 响应示例

```json
{
  "threads": [
    {
      "guild_id": "75827011639035987",
      "channel_id": "2324603",
      "author_id": "144115218680332809",
      "thread_info": {
        "thread_id": "B_59101362700301001441152186803328090X60-1645416537",
        "title": "帖子标题1",
        "content": "{\"paragraphs\":[{\"elems\":[{\"text\":{\"text\":\"发送消息 | QQ机器人文档\"},\"type\":1}],\"props\":{}},{\"elems\":[{\"text\":{\"text\":\"• 主动消息：发送消息时，未填充msg_id 字段的消息。\"},\"type\":1}],\"props\":{}}]}",
        "date_time": "2022-02-21T12:08:57+08:00"
      }
    },
    {
      "guild_id": "75827011639035987",
      "channel_id": "2324603",
      "author_id": "144115218680332809",
      "thread_info": {
        "thread_id": "B_79051362477c03001441152186803328090X60-1645413753",
        "title": "帖子标题2",
        "content": "{\"paragraphs\":[{\"elems\":[{\"text\":{\"text\":\"发送消息 | QQ机器人文档\"},\"type\":1}],\"props\":{}},{\"elems\":[{\"text\":{\"text\":\"• 主动消息：发送消息时，未填充msg_id 字段的消息。\"},\"type\":1}],\"props\":{}}]}",
        "date_time": "2022-02-21T11:22:33+08:00"
      }
    }
  ],
  "is_finish": 1
}
```

---

### 15. 获取帖子详情

### 接口

```
GET /channels/{channel_id}/threads/{thread_id}
```

### 功能描述

用于获取子频道下的帖子详情。

> ⚠️ **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| thread_id | string | 是 | 帖子ID |

### 返回参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| thread | ThreadInfo | 帖子详情对象（返回值里面的content字段，可参照 RichText 结构） |

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 响应示例

```json
{
  "thread": {
    "guild_id": "75827011639035987",
    "channel_id": "2324603",
    "author_id": "144115218680332809",
    "thread_info": {
      "thread_id": "B_79051362477c03001441152186803328090X60-1645413753",
      "title": "帖子标题",
      "content": "{\"paragraphs\":[{\"elems\":[{\"text\":{\"text\":\"发送消息 | QQ机器人文档\"},\"type\":1}],\"props\":{}},{\"elems\":[{\"text\":{\"text\":\"• 主动消息：发送消息时，未填充msg_id 字段的消息。\"},\"type\":1}],\"props\":{}}]}",
      "date_time": "2022-02-21T11:22:33+08:00"
    }
  }
}
```

---

### 16. 发表帖子

### 接口

```
PUT /channels/{channel_id}/threads
```

### 功能描述

用于在子频道下发表帖子。

- 创建成功后，返回创建成功的任务ID。

> ⚠️ **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |

### 请求参数

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| title | string | 是 | 帖子标题 |
| content | string | 是 | 帖子内容 |
| format | uint32 | 是 | 帖子文本格式，参考 Format 枚举 |

### Format 枚举

| 字段名 | 值 | 描述 |
|--------|------|------|
| FORMAT_TEXT | 1 | 普通文本 |
| FORMAT_HTML | 2 | HTML |
| FORMAT_MARKDOWN | 3 | Markdown |
| FORMAT_JSON | 4 | JSON（content参数可参照 RichText 结构） |

### 返回参数

| 字段名 | 类型 | 描述 |
|--------|------|------|
| task_id | string | 帖子任务ID |
| create_time | string | 发帖时间戳，单位：秒 |

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```json
{
  "title": "title",
  "content": "<html lang=\"en-US\"><body><a href=\"https://bot.q.qq.com/wiki\" title=\"QQ机器人文档Title\">QQ机器人文档</a>\n<ul><li>主动消息：发送消息时，未填msg_id字段的消息。</li><li>被动消息：发送消息时，填充了msg_id字段的消息。</li></ul></body></html>",
  "format": 2
}
```

### 响应示例

```json
{
  "task_id": "1645413752912602306",
  "create_time": "1645503180"
}
```

---

### 17. 删除帖子

### 接口

```
DELETE /channels/{channel_id}/threads/{thread_id}
```

### 功能描述

用于删除指定子频道下的某个帖子。

> ⚠️ **注意：** 公域机器人暂不支持申请，仅私域机器人可用，选择私域机器人后默认开通。开通后需要先将机器人从频道移除，然后重新添加，方可生效。

### Content-Type

```
application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| channel_id | string | 是 | 子频道ID |
| thread_id | string | 是 | 帖子ID |

### 返回

成功返回 HTTP 状态码 `204`，无响应体。

### 错误码

详见 [错误码](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)。

### 请求示例

```
DELETE /channels/123456/threads/B_79051362477c03001441152186803328090X60-1645413753
```

---

### 18. 论坛事件对象(ForumEvent)

### Intent

```
FORUM_EVENT
```

### 发送时机

用户在话题子频道内发帖、评论、回复评论时产生该事件。

### 主题事件

事件类型：
- `FORUM_THREAD_CREATE` - 创建主题
- `FORUM_THREAD_UPDATE` - 更新主题
- `FORUM_THREAD_DELETE` - 删除主题

事件内容为 [Thread](#thread-对象) 对象。

#### 主题事件示例

```json
{
  "guild_id": 47129941624960822,
  "channel_id": 1661124,
  "author_id": 144115218182563108,
  "thread_info": {
    "thread_id": "B_7c02cb615f8904001441152181825631080X60",
    "title": [
      {
        "type": 1,
        "text_info": {
          "text": "Test"
        }
      }
    ],
    "content": [
      {
        "type": 1,
        "text_info": {
          "text": "tencent "
        }
      },
      {
        "type": 5,
        "channel_info": {
          "channel_id": 1505272,
          "channel_name": "#隐私子频道"
        }
      },
      {
        "type": 1,
        "text_info": {
          "text": " "
        }
      },
      {
        "type": 3,
        "url_info": {
          "url": "https://apple.com",
          "display_text": "Apple"
        }
      },
      {
        "type": 1,
        "text_info": {
          "text": ""
        }
      }
    ],
    "date_time": "2021-12-30T15:17:34+08:00"
  }
}
```

### 帖子事件

事件类型：
- `FORUM_POST_CREATE` - 创建帖子（评论）
- `FORUM_POST_DELETE` - 删除帖子（评论）

事件内容为 [Post](#post-对象) 对象。

#### 帖子事件示例

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "author_id": "144115218182563108",
  "post_info": {
    "thread_id": "B_6d02bb61e45b0d001441152181867088220X60",
    "post_id": "c_1500cb611f950a001441152181825631080X60",
    "content": [
      {
        "type": 1,
        "text_info": {
          "text": "test"
        }
      },
      {
        "type": 4,
        "emoji_info": {
          "id": 109,
          "type": "1"
        }
      },
      {
        "type": 1,
        "text_info": {
          "text": "111"
        }
      },
      {
        "type": 4,
        "emoji_info": {
          "id": 13,
          "type": "1"
        }
      }
    ],
    "date_time": "2021-12-30T15:17:34+08:00"
  }
}
```

### 回复事件

事件类型：
- `FORUM_REPLY_CREATE` - 创建回复
- `FORUM_REPLY_DELETE` - 删除回复

事件内容为 [Reply](#reply-对象) 对象。

#### 回复事件示例

```json
{
  "guild_id": 47129941624960822,
  "channel_id": 1661124,
  "author_id": 144115218182563108,
  "reply_info": {
    "thread_id": "B_8914b26116bb03001441152181867088220X60",
    "post_id": "c_39bab261d2b907001441152181867088220X60",
    "reply_id": "r_e701cb6128dc0b001441152181825631080X60",
    "content": [
      {
        "type": 1,
        "text_info": {
          "text": "Apple"
        }
      }
    ],
    "date_time": "2021-12-30T15:17:34+08:00"
  }
}
```

### 帖子审核事件

事件类型：
- `FORUM_PUBLISH_AUDIT_RESULT` - 帖子审核结果

事件内容为 [AuditResult](#auditresult-对象) 对象。

#### 审核事件示例

```json
{
  "guild_id": 47129941624960822,
  "channel_id": 1661124,
  "author_id": 144115218182563108,
  "type": 1,
  "result": 0,
  "err_msg": "",
  "thread_id": "B_8914b26116bb03001441152181867088220X60",
  "post_id": "c_39bab261d2b907001441152181867088220X60",
  "reply_id": "r_e701cb6128dc0b001441152181825631080X60"
}
```

---

### 19. 开放论坛事件对象(OpenForumEvent)

### Intent

```
OPEN_FORUM_EVENT
```

### 发送时机

用户在话题子频道内发帖、评论、回复评论时产生该事件。

> **说明：** 开放论坛事件与论坛事件的区别在于，开放论坛事件只传递 `guild_id`、`channel_id`、`author_id` 三个基本字段，不包含帖子/回复的具体内容。需要获取具体内容请使用对应的获取帖子/回复详情接口。

### 主题事件

事件类型：
- `OPEN_FORUM_THREAD_CREATE` - 创建主题
- `OPEN_FORUM_THREAD_UPDATE` - 更新主题
- `OPEN_FORUM_THREAD_DELETE` - 删除主题

#### 主题事件示例

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "author_id": "144115218182563108"
}
```

### 帖子事件

事件类型：
- `OPEN_FORUM_POST_CREATE` - 创建帖子（评论）
- `OPEN_FORUM_POST_DELETE` - 删除帖子（评论）

#### 帖子事件示例

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "author_id": "144115218182563108"
}
```

### 回复事件

事件类型：
- `OPEN_FORUM_REPLY_CREATE` - 创建回复
- `OPEN_FORUM_REPLY_DELETE` - 删除回复

#### 回复事件示例

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "author_id": "144115218182563108"
}
```

---

## 通用错误码

所有接口通用的错误码参见：[https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html](https://bot.q.qq.com/wiki/develop/api-v2/openapi/error/error.html)

常见的 HTTP 状态码：

| 状态码 | 描述 |
|--------|------|
| 200 | 请求成功 |
| 204 | 请求成功，无返回内容（DELETE 操作） |
| 400 | 请求参数错误 |
| 401 | 未授权（Token 无效或过期） |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

> 📅 文档生成时间: 2026-07-26
> 📖 来源: QQ 机器人官方文档 (https://bot.q.qq.com/wiki/develop/api-v2/)

---

# 五、事件订阅

## 公共数据结构

以下嵌套对象在多个事件中复用，统一定义于此。

### User

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 用户唯一标识（OpenID 格式） |
| username | string | 用户昵称 |
| bot | boolean | 是否为机器人 |
| union_openid | string | 跨应用统一用户 OpenID（可能为空） |
| union_user_account | string | 跨应用统一用户账号（可能为空） |
| user_openid | string | 用户 OpenID（单聊场景使用） |
| member_openid | string | 群成员 OpenID（群聊场景使用） |
| member_role | string | 群内角色。`member`=普通成员, `admin`=管理员, `owner`=群主 |

### MessageScene

| 名称 | 类型 | 描述 |
|------|------|------|
| source | string | 场景来源。`default`=默认聊天窗口 |
| ext | []string | 扩展数据列表，key=value 格式：<br>- `msg_idx`=消息索引，用于去重<br>- `ref_msg_idx`=引用的消息索引（引用场景）<br>- `auth_token`=鉴权令牌 |

### MessageAttachment

| 名称 | 类型 | 描述 |
|------|------|------|
| url | string | 附件下载 URL |
| filename | string | 文件名 |
| width | integer | 图片宽度（像素），非图片附件无此字段 |
| height | integer | 图片高度（像素），非图片附件无此字段 |
| size | integer | 文件大小（字节） |
| content_type | string | 附件内容类型（MIME 类型）：<br>`voice`=语音消息, `image/jpeg`=JPEG 图片, `image/png`=PNG 图片, `image/gif`=GIF 图片, `video/mp4`=MP4 视频, `file`=群文件 |
| voice_wav_url | string | 语音消息 SILK 等转换后的 WAV 文件 URL |
| asr_refer_text | string | 语音消息 ASR 参考结果 |

### ARKData

| 名称 | 类型 | 描述 |
|------|------|------|
| prompt | string | 卡片消息中的用户操作提示文本 |
| ark_type | string | 卡片消息类型标识：<br>`tuwen`=图文 H5（如快手分享链接）, `feed`=图文卡片（群相册、频道帖子、分享卡片）, `miniapp`=小程序（微信小程序、QQ 小程序、哔哩哔哩等）, `map`=位置卡片, `contact_card`=好友名片, `video_share`=视频分享, `music_together`=一起听歌 |
| ark_name | string | 卡片消息类型的中文名称，如"图文 H5"、"小程序"、"图文卡片" |
| fields | object | 卡片消息字段，常见键名：<br>`tag`/`tags`=来源标签, `title`=标题, `desc`=描述, `jump_url`=跳转链接, `preview`=预览图, `source`=来源名称, `source_logo`=来源图标, `tag_icon`=标签图标, `nickname`=昵称, `avatar`=头像, `address`=地址 |

### MsgElement

| 名称 | 类型 | 描述 |
|------|------|------|
| msg_idx | string | 消息元素在列表中的引用消息索引 |
| author | [User](#user) | 该元素对应的消息发送者 |
| message_type | integer | 消息内容类型：`0`=普通文本, `3`=结构化卡片, `101`=并行消息, `102`=聊天记录, `103`=引用消息 |
| content | string | 消息正文内容 |
| attachments | [][MessageAttachment](#messageattachment) | 该元素携带的附件 |
| ark_data | [ARKData](#arkdata) | 结构化卡片消息数据（message_type=3 时有值） |
| msg_elements | [][MsgElement](#msgelement) | 嵌套消息元素列表（递归结构） |

### FriendAuthor

| 名称 | 类型 | 描述 |
|------|------|------|
| union_openid | string | 用户统一 OpenID（跨应用标识） |

### InteractionData

| 名称 | 类型 | 描述 |
|------|------|------|
| type | integer | 互动数据类型，与外层 type 含义一致。`11`=消息按钮点击, `12`=快捷菜单点击, `13`=消息反馈点击, `14`=清空会话点击, `15`=故事集点击, `16`=切换模型点击 |
| resolved | [InteractionResolved](#interactionresolved) | 解析后的互动数据 |

### InteractionResolved

| 名称 | 类型 | 描述 |
|------|------|------|
| button_data | string | 按钮的 data 字段值（发送消息按钮时设置）；消息反馈场景下为回调数据 |
| button_id | string | 按钮的 id 字段值（发送消息按钮时设置） |
| user_id | string | 操作用户 ID（仅频道场景有值） |
| feature_id | string | 功能 ID（仅快捷菜单有值，管理端设置） |
| message_id | string | 操作的消息 ID（频道场景为消息 OpenID；消息反馈场景为机器人消息 ID） |
| feedback_opt | string | 反馈选项（仅 type=13 消息反馈）。`LIKE`=点赞, `UNLIKE`=点踩 |
| checked | integer | 反馈选项是否选中（仅 type=13 消息反馈） |
| action | string | 操作类型（type=15 故事集：`ENTER_STORY`=进入, `QUIT_STORY`=退出；type=16 切换模型：对应操作动作） |
| message_scene | [InteractionMessageScene](#interactionmessagescene) | 消息场景信息（仅 type=13 消息反馈） |
| authorize_data | [AuthorizeData](#authorizedata) | 授权数据（仅 type=18/19 用户/群授权事件） |

### InteractionMessageScene

| 名称 | 类型 | 描述 |
|------|------|------|
| ext | []string | 扩展信息键值对列表，如 `"disable_net_search=1"` 表示关闭联网搜索 |

### AuthorizeData

| 名称 | 类型 | 描述 |
|------|------|------|
| opt_scene | string | 授权操作场景。`setting`=资料页设置, `dialog`=弹窗授权 |
| scope | string | 授权范围。`c2c_push`=C2C 主动消息推送, `group_push`=群主动消息推送 |

### MessageReaction（频道表情表态）

| 名称 | 类型 | 描述 |
|------|------|------|
| user_id | string | 操作用户 ID |
| emoji | object | 表情对象，包含 `id`（表情 ID）和 `type`（表情类型） |
| channel_id | string | 子频道 ID |
| guild_id | string | 频道 ID |
| target | object | 表态目标，包含 `id`（目标 ID）和 `type`（目标类型，`0`=消息） |

---

### 1. 单聊消息事件

> 用户给机器人发送单聊消息时触发。为确保消息可达，相同 msg_id 可能重复推送，开发者需结合 msg_seq 做去重。message_type 决定消息结构：`0`=纯文本，`3`=ARK卡片（ark_data 有值），`103`=引用消息（msg_elements 有值，message_scene.ext 含 ref_msg_idx）。

| 字段 | 值 |
|------|------|
| **事件名** | `C2C_MESSAGE_CREATE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID，可用于被动回复和撤回 |
| author | [User](#user) | 发送者（user_openid 有值） |
| content | string | 消息文本内容 |
| timestamp | string | 消息发送时间，RFC3339 格式 |
| message_type | integer | 消息内容类型：`0`=普通文本, `3`=结构化卡片, `101`=并行消息, `102`=聊天记录, `103`=引用消息 |
| message_scene | [MessageScene](#messagescene) | 消息场景上下文（含消息索引、鉴权令牌等） |
| attachments | [][MessageAttachment](#messageattachment) | 消息附件（图片、文件、语音等） |
| ark_data | [ARKData](#arkdata) | 结构化卡片消息数据（message_type=3 时有值） |
| msg_elements | [][MsgElement](#msgelement) | 消息元素列表（message_type=103 引用消息时包含被引用内容） |

### 事件示例

**示例1：纯文本消息**

```json
{
  "id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "author": {
    "id": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "user_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "union_openid": "",
    "username": "",
    "bot": false
  },
  "content": "你好，今天有什么推荐的活动吗？",
  "message_type": 0,
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_xxxxxxxxxxxxxxx=="
    ]
  },
  "timestamp": "2026-07-21T10:00:00+08:00"
}
```

**示例2：结构化卡片消息（小程序）**

```json
{
  "id": "ROBOT1.0_yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy",
  "author": {
    "id": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
    "user_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
    "union_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
    "username": "",
    "bot": false
  },
  "content": "[卡片消息] 小程序\n摘要: [每日打卡]快来完成今日学习打卡",
  "message_type": 3,
  "ark_data": {
    "ark_type": "miniapp",
    "ark_name": "小程序",
    "prompt": "[每日打卡]快来完成今日学习打卡",
    "fields": {
      "title": "快来完成今日学习打卡",
      "source": "学习助手",
      "tag": "微信小程序",
      "preview": "https://pubminishare-30161.picsz.qpic.cn/preview_a1b2c3d4",
      "source_logo": "https://miniapp.gtimg.cn/generated-icon/app_a1b2c3d4.png",
      "tag_icon": "https://miniapp.gtimg.cn/public/miniwx.png"
    }
  },
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_yyyyyyyyyyyyyyy=="
    ]
  },
  "timestamp": "2026-07-21T10:01:00+08:00"
}
```

**示例3：引用消息**

```json
{
  "id": "ROBOT1.0_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
  "author": {
    "id": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "user_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "union_openid": "",
    "username": "",
    "bot": false
  },
  "content": "这个建议很有帮助，谢谢你！",
  "message_type": 103,
  "msg_elements": [
    {
      "msg_idx": "REFIDX_aaaaaaaaaaaaaaa==",
      "message_type": 103,
      "content": "每天坚持阅读半小时，一个月后你会发现自己的变化"
    }
  ],
  "message_scene": {
    "source": "default",
    "ext": [
      "ref_msg_idx=REFIDX_aaaaaaaaaaaaaaa==",
      "msg_idx=REFIDX_zzzzzzzzzzzzzzz=="
    ]
  },
  "timestamp": "2026-07-21T10:02:00+08:00"
}
```

---

### 2. 群@机器人消息

> 用户在群里@机器人发送消息时触发。这是机器人最常接收的事件。content 字段已自动去除@机器人的前缀。为确保消息可达，相同 msg_id 可能重复推送，开发者需结合 msg_seq 做去重。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_AT_MESSAGE_CREATE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID，可用于被动回复和撤回 |
| author | [User](#user) | 发送者（member_openid 有值） |
| content | string | 消息文本内容（已去除@机器人的前缀） |
| group_openid | string | 群 OpenID |
| timestamp | string | 消息发送时间，RFC3339 格式 |
| message_type | integer | 消息内容类型（同 C2C_MESSAGE_CREATE） |
| message_scene | [MessageScene](#messagescene) | 消息场景上下文 |
| attachments | [][MessageAttachment](#messageattachment) | 消息附件 |
| mentions | [][User](#user) | 消息中@的用户列表（不含@机器人自身） |
| ark_data | [ARKData](#arkdata) | 结构化卡片消息数据 |
| msg_elements | [][MsgElement](#msgelement) | 消息元素列表 |

### 事件示例

**示例1：纯文本@消息**

```json
{
  "id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "author": {
    "id": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "member_role": "member",
    "username": "小明",
    "bot": false
  },
  "content": " /今日天气 ",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 0,
  "timestamp": "2026-07-21T10:00:00+08:00",
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_xxxxxxxxxxxxxxx==",
      "auth_token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    ]
  }
}
```

**示例2：带图片附件的@消息**

```json
{
  "id": "ROBOT1.0_yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy",
  "author": {
    "id": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "member_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "member_role": "member",
    "username": "小红",
    "bot": false
  },
  "content": " 看看这张风景照 ",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 0,
  "timestamp": "2026-07-21T10:05:00+08:00",
  "attachments": [
    {
      "content_type": "image/jpeg",
      "filename": "photo.jpg",
      "url": "https://multimedia.nt.qq.com.cn/download?appid=xxx&fileid=xxx&rkey=xxx&spec=0",
      "width": 1920,
      "height": 1080,
      "size": 256000
    }
  ],
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_yyyyyyyyyyyyyyy==",
      "auth_token=yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"
    ]
  }
}
```

**示例3：引用消息**

```json
{
  "id": "ROBOT1.0_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
  "author": {
    "id": "D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1",
    "member_openid": "D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1",
    "member_role": "owner",
    "username": "小华",
    "bot": false
  },
  "content": " ",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 103,
  "timestamp": "2026-07-21T10:10:00+08:00",
  "msg_elements": [
    {
      "content": "=== 消息 1 ===\n[消息内容] 今天的学习计划已完成\n\n=== 消息 2 ===\n[消息内容] 很棒！继续保持，明天继续加油\n\n=== 消息 3 ===\n[消息内容] 好的，一起进步！"
    }
  ],
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_zzzzzzzzzzzzzzz==",
      "auth_token=zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
      "ref_msg_idx=TMP_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]
  }
}
```

---

### 3. 群消息全量模式

> 当机器人开启了"接收所有消息"功能后，群里的每一条消息（不限于@机器人）都会推送此事件。各字段含义与 GROUP_AT_MESSAGE_CREATE 完全一致。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_MESSAGE_CREATE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID，可用于被动回复和撤回 |
| author | [User](#user) | 发送者 |
| content | string | 消息文本内容（已去除@机器人的前缀） |
| group_openid | string | 群 OpenID |
| timestamp | string | 消息发送时间，RFC3339 格式 |
| message_type | integer | 消息内容类型 |
| message_scene | [MessageScene](#messagescene) | 消息场景上下文 |
| attachments | [][MessageAttachment](#messageattachment) | 消息附件 |
| mentions | [][User](#user) | 消息中@的用户列表 |
| ark_data | [ARKData](#arkdata) | 结构化卡片消息数据 |
| msg_elements | [][MsgElement](#msgelement) | 消息元素列表 |

### 事件示例

**示例1：普通群消息**

```json
{
  "id": "ROBOT1.0_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "author": {
    "id": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
    "member_role": "member",
    "username": "小明",
    "bot": false
  },
  "content": "大家早上好呀",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 0,
  "timestamp": "2026-07-21T08:00:00+08:00",
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_xxxxxxxxxxxxxxx==",
      "auth_token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    ]
  }
}
```

**示例2：带图片附件**

```json
{
  "id": "ROBOT1.0_yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy",
  "author": {
    "id": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "member_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
    "member_role": "owner",
    "username": "小红",
    "bot": false
  },
  "content": "分享一张今天的风景照",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 0,
  "timestamp": "2026-07-21T09:30:00+08:00",
  "attachments": [
    {
      "content_type": "image/jpeg",
      "filename": "photo.jpg",
      "url": "https://multimedia.nt.qq.com.cn/download?appid=xxx&fileid=xxx&rkey=xxx&spec=0",
      "width": 1920,
      "height": 1080,
      "size": 256000
    }
  ],
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_yyyyyyyyyyyyyyy==",
      "auth_token=yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"
    ]
  }
}
```

**示例3：引用消息**

```json
{
  "id": "ROBOT1.0_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
  "author": {
    "id": "D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1",
    "member_openid": "D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1",
    "member_role": "admin",
    "username": "小华",
    "bot": false
  },
  "content": " ",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "message_type": 103,
  "timestamp": "2026-07-21T10:10:00+08:00",
  "msg_elements": [
    {
      "content": "=== 消息 1 ===\n[消息内容] 今天的学习计划已完成\n\n=== 消息 2 ===\n[消息内容] 很棒！继续保持，明天继续加油\n\n=== 消息 3 ===\n[消息内容] 好的，一起进步！"
    }
  ],
  "message_scene": {
    "source": "default",
    "ext": [
      "msg_idx=REFIDX_zzzzzzzzzzzzzzz==",
      "auth_token=zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
      "ref_msg_idx=TMP_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]
  }
}
```

---

### 4. 用户添加好友

> 通过传 scene_param 中的 callback_data 可区分不同来源的添加好友场景。

| 字段 | 值 |
|------|------|
| **事件名** | `FRIEND_ADD` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 添加时间戳（Unix 秒） |
| openid | string | 用户 OpenID |
| scene | integer | 加好友场景值：<br>`1000`=缺省默认<br>`1001`=网络搜索（全部tab）<br>`1002`=网络搜索（机器人tab）<br>`1003`=群场景<br>`1004`=空间场景<br>`2001`=站内分享资料页<br>`2002`=站外分享资料页<br>`2003`=开发者生成的分享链接（站内）<br>`2004`=开发者生成的分享链接（站外） |
| scene_param | string | 开发者自定义的回调数据（callback_data），用于区分不同来源 |
| author | [FriendAuthor](#friendauthor) | 用户信息 |

### 事件示例

**网络搜索场景**

```json
{
  "openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570523,
  "scene": 1001,
  "scene_param": "",
  "author": {
    "union_openid": "DB85A74E07BA08B5B44CD9ED332FCBD2"
  }
}
```

**开发者分享链接**

```json
{
  "openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570600,
  "scene": 2003,
  "scene_param": "callback_abc123",
  "author": {
    "union_openid": "DB85A74E07BA08B5B44CD9ED332FCBD2"
  }
}
```

---

### 5. 用户删除好友

> 用户删除机器人好友时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `FRIEND_DEL` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 删除时间戳（Unix 秒） |
| openid | string | 用户 OpenID |
| author | [FriendAuthor](#friendauthor) | 用户信息 |

### 事件示例

```json
{
  "openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570524
}
```

---

### 6. 单聊消息接收开启

> 用户在机器人资料卡手动开启主动消息推送开关时触发。开启后机器人可向该用户发送主动消息。

| 字段 | 值 |
|------|------|
| **事件名** | `C2C_MSG_RECEIVE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 操作时间戳（Unix 秒） |
| openid | string | 用户 OpenID |

### 事件示例

```json
{
  "openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570617
}
```

---

### 7. 单聊消息接收关闭

> 用户在机器人资料卡手动关闭主动消息推送时触发。关闭后机器人无法向该用户发送主动消息。

| 字段 | 值 |
|------|------|
| **事件名** | `C2C_MSG_REJECT` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 操作时间戳（Unix 秒） |
| openid | string | 用户 OpenID |

### 事件示例

```json
{
  "openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570599
}
```

---

### 8. 机器人加入群聊

> 机器人被添加到群聊时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_ADD_ROBOT` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 加入时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| op_member_openid | string | 操作添加机器人进群的群成员 OpenID |

### 事件示例

```json
{
  "group_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "op_member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570534
}
```

---

### 9. 机器人退出群聊

> 机器人被移出群聊时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_DEL_ROBOT` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 移除时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| op_member_openid | string | 操作移除机器人退群的群成员 OpenID |

### 事件示例

```json
{
  "group_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "op_member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "timestamp": 1784570535
}
```

---

### 10. 群聊消息接收开启

> 群管理员在机器人资料页操作开启通知时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_MSG_RECEIVE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 操作时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| op_member_openid | string | 操作群成员 OpenID |

### 事件示例

```json
{
  "timestamp": 1784276800,
  "group_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "op_member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4"
}
```

---

### 11. 群聊消息接收关闭

> 群管理员在机器人资料页操作关闭通知时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_MSG_REJECT` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 操作时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| op_member_openid | string | 操作群成员 OpenID |

### 事件示例

```json
{
  "timestamp": 1784276810,
  "group_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "op_member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4"
}
```

---

### 12. 群成员加入

> 有新成员加入群聊时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_MEMBER_ADD` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 事件时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| member_openid | string | 新加入成员的 OpenID |
| user_openid | string | 新成员的用户 OpenID（跨应用统一标识，可能为空） |

### 事件示例

```json
{
  "timestamp": 1784276757,
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "member_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
  "user_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6"
}
```

---

### 13. 群成员退出

> 群成员退出或被移出群聊时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GROUP_MEMBER_REMOVE` |
| **Intent** | `GROUP_AND_C2C_EVENT` (1<<25) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| timestamp | integer | 事件时间戳（Unix 秒） |
| group_openid | string | 群 OpenID |
| member_openid | string | 退出成员的 OpenID |
| user_openid | string | 退出成员的用户 OpenID（可能为空） |

### 事件示例

```json
{
  "timestamp": 1784276759,
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "member_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6",
  "user_openid": "C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6"
}
```

---

### 14. 互动事件

> 用户与机器人的互动操作触发此事件，包括消息按钮点击、快捷菜单回调、消息反馈、清空会话、进出故事集、切换模型、用户/群授权等。
>
> **重要**：仅 `type=11`（消息按钮）和 `type=12`（快捷菜单）需要调用 `PUT /interactions/{interaction_id}` 回应；其他类型无需回应。同一 interaction_id 只能回应一次，超时后失效。

| 字段 | 值 |
|------|------|
| **事件名** | `INTERACTION_CREATE` |
| **Intent** | `INTERACTION` (1<<26) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 事件 ID，用于被动消息发送和互动回调 |
| type | integer | 互动类型（见下表） |
| scene | string | 事件发生场景。`c2c`=单聊, `group`=群聊, `guild`=频道 |
| chat_type | integer | 聊天场景。`0`=频道, `1`=群聊, `2`=单聊 |
| timestamp | string | 触发时间，RFC3339 格式 |
| guild_id | string | 频道 OpenID（仅频道场景有值） |
| channel_id | string | 子频道 OpenID（仅频道场景有值） |
| user_openid | string | 用户 OpenID（仅单聊场景有值） |
| group_openid | string | 群 OpenID（仅群聊场景有值） |
| group_member_openid | string | 群成员 OpenID（仅群聊场景有值） |
| data | [InteractionData](#interactiondata) | 互动数据 |
| version | integer | 版本号，默认 1 |
| application_id | string | 机器人 AppID |

**type 互动类型说明：**

| type 值 | 名称 | 说明 |
|---------|------|------|
| 11 | INLINE_KEYBOARD | 消息按钮回调：用户点击消息中的内联键盘按钮 |
| 12 | CALLBACK_COMMAND | 单聊快捷菜单回调：用户点击单聊场景下的自定义菜单 |
| 13 | MESSAGE_FEEDBACK | 消息反馈：用户对智能体消息进行点赞/点踩反馈 |
| 14 | CLEAR_SESSION | 清空会话：用户清空智能体会话历史 |
| 15 | IN_OUT_STORY | 进出故事集：用户进入或退出故事集 |
| 16 | SWITCH_MODEL | 切换模型：用户切换智能体模型 |
| 18 | USER_AUTHORIZE | 用户授权事件 |
| 19 | GROUP_AUTHORIZE | 群授权事件 |
| 20 | GROUP_AUTHORIZE_STATUS | 群授权状态变更 |

### 事件示例

**示例1：单聊消息按钮点击**

```json
{
  "application_id": "1904842048",
  "chat_type": 2,
  "data": {
    "resolved": {
      "button_data": "confirm:once",
      "button_id": "allow-once"
    },
    "type": 11
  },
  "id": "1b13d569-4610-4ab9-bc51-feecc5def6d4",
  "scene": "c2c",
  "timestamp": "2026-07-20T21:53:54+08:00",
  "type": 11,
  "user_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "version": 1
}
```

**示例2：群聊消息按钮点击**

```json
{
  "application_id": "101984245",
  "chat_type": 1,
  "data": {
    "resolved": {
      "button_data": "eyJjb21tYW5kIjogInNhbXBsZSJ9"
    },
    "type": 11
  },
  "group_member_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "group_openid": "B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5",
  "id": "06915133-7aef-46ed-94f7-c50939e285ae",
  "scene": "group",
  "timestamp": "2026-07-20T21:53:54+08:00",
  "type": 11,
  "version": 1
}
```

**示例3：用户授权事件**

```json
{
  "application_id": "102057050",
  "data": {
    "resolved": {
      "authorize_data": {
        "opt_scene": "setting",
        "scope": "c2c_push"
      }
    }
  },
  "id": "c30c003e-9454-4450-8e5e-665267c088c4",
  "scene": "c2c",
  "timestamp": "2026-07-20T21:54:38+08:00",
  "type": 18,
  "user_openid": "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4",
  "version": 1
}
```

---

### 15. 频道创建

> 机器人被加入到某个频道时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_CREATE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 频道 ID |
| name | string | 频道名称 |
| icon | string | 频道头像 URL |
| owner_id | string | 频道创建者 ID |
| member_count | integer | 频道成员数 |
| max_members | integer | 频道成员上限 |
| description | string | 频道简介 |
| joined_at | string | 加入时间，ISO8601 格式 |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456789012345678",
  "name": "技术交流频道",
  "icon": "https://thirdqq.qlogo.cn/0",
  "owner_id": "123456789012345678",
  "member_count": 100,
  "max_members": 1000,
  "description": "专注于技术分享与交流的频道",
  "joined_at": "2026-01-01T00:00:00+08:00",
  "op_user_id": "123456789012345678"
}
```

---

### 16. 频道更新

> 频道信息变更时触发。事件内容为变更后的数据。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_UPDATE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 频道 ID |
| name | string | 频道名称 |
| icon | string | 频道头像 URL |
| owner_id | string | 频道创建者 ID |
| member_count | integer | 频道成员数 |
| max_members | integer | 频道成员上限 |
| description | string | 频道简介 |
| joined_at | string | 加入时间，ISO8601 格式 |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456789012345678",
  "name": "更新后的频道",
  "owner_id": "123456789012345678",
  "icon": "https://thirdqq.qlogo.cn/0",
  "member_count": 12,
  "max_members": 1000,
  "description": "更新后的描述"
}
```

---

### 17. 频道解散

> 频道被解散或机器人被移除时触发。事件内容为变更前的数据。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_DELETE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 频道 ID |
| name | string | 频道名称 |
| icon | string | 频道头像 URL |
| owner_id | string | 频道创建者 ID |
| member_count | integer | 频道成员数 |
| max_members | integer | 频道成员上限 |
| description | string | 频道简介 |
| joined_at | string | 加入时间，ISO8601 格式 |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456789012345678",
  "name": "测试频道",
  "owner_id": "123456789012345678",
  "icon": "https://thirdqq.qlogo.cn/0",
  "member_count": 10,
  "max_members": 1000,
  "description": "频道描述"
}
```

---

### 18. 子频道创建

> 子频道被创建时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `CHANNEL_CREATE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名称 |
| type | integer | 子频道类型：`0`=文字, `2`=语音, `4`=分组, `10005`=直播, `10006`=应用, `10007`=论坛 |
| sub_type | integer | 子频道子类型 |
| owner_id | string | 创建者 ID |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456",
  "guild_id": "123456789012345678",
  "name": "新子频道",
  "type": 0,
  "sub_type": 0,
  "position": 1,
  "owner_id": "123456789012345678"
}
```

---

### 19. 子频道更新

> 子频道信息变更时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `CHANNEL_UPDATE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名称 |
| type | integer | 子频道类型：`0`=文字, `2`=语音, `4`=分组, `10005`=直播, `10006`=应用, `10007`=论坛 |
| sub_type | integer | 子频道子类型 |
| owner_id | string | 创建者 ID |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456",
  "guild_id": "123456789012345678",
  "name": "更新后的子频道",
  "type": 0,
  "sub_type": 0,
  "position": 1,
  "owner_id": "123456789012345678"
}
```

---

### 20. 子频道删除

> 子频道被删除时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `CHANNEL_DELETE` |
| **Intent** | `GUILDS` (1<<0) |

### 事件体

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 子频道 ID |
| guild_id | string | 所属频道 ID |
| name | string | 子频道名称 |
| type | integer | 子频道类型：`0`=文字, `2`=语音, `4`=分组, `10005`=直播, `10006`=应用, `10007`=论坛 |
| sub_type | integer | 子频道子类型 |
| owner_id | string | 创建者 ID |
| op_user_id | string | 操作人 ID |

### 事件示例

```json
{
  "id": "123456",
  "guild_id": "123456789012345678",
  "name": "被删除的子频道",
  "type": 0,
  "sub_type": 0,
  "position": 1,
  "owner_id": "123456789012345678"
}
```

---

### 21. 频道消息事件

### 21.1 AT_MESSAGE_CREATE

> 用户发送消息@当前机器人或回复机器人消息时触发。消息顺序不保证严格有序，可基于 Message.seq 自行排序。

| 字段 | 值 |
|------|------|
| **事件名** | `AT_MESSAGE_CREATE` |
| **Intent** | `PUBLIC_GUILD_MESSAGES` |

**事件体**：内容为 Message 对象

| 名称 | 类型 | 描述 |
|------|------|------|
| id | string | 消息 ID |
| channel_id | string | 子频道 ID |
| guild_id | string | 频道 ID |
| content | string | 消息内容 |
| timestamp | string | 消息发送时间 |
| seq | integer | 消息序列号 |
| author | object | 发送者，包含 `id`, `username`, `avatar`, `bot` |
| member | object | 成员信息，包含 `joined_at`, `roles` |

**示例：**

```json
{
  "author": {
    "avatar": "http://thirdqq.qlogo.cn/0",
    "bot": false,
    "id": "1234",
    "username": "abc"
  },
  "channel_id": "100010",
  "content": "ndnnd",
  "guild_id": "18700000000001",
  "id": "0812345677890abcdef",
  "member": {
    "joined_at": "2021-04-12T16:34:42+08:00",
    "roles": ["1"]
  },
  "timestamp": "2021-05-20T15:14:58+08:00",
  "seq": 101
}
```

### 21.2 MESSAGE_CREATE（私域）

> 用户在文字子频道内发送的所有聊天消息（私域）。消息顺序不保证严格有序。

| 字段 | 值 |
|------|------|
| **事件名** | `MESSAGE_CREATE` |
| **Intent** | `PUBLIC_GUILD_MESSAGES`（私域） |

**事件体**：内容为 Message 对象（同 AT_MESSAGE_CREATE）

**示例：**

```json
{
  "author": {
    "avatar": "http://thirdqq.qlogo.cn/0",
    "bot": false,
    "id": "1234",
    "username": "abc"
  },
  "channel_id": "100010",
  "content": "ndnnd",
  "guild_id": "18700000000001",
  "id": "0812345677890abcdef",
  "member": {
    "joined_at": "2021-04-12T16:34:42+08:00",
    "roles": ["1"]
  },
  "timestamp": "2021-05-20T15:14:58+08:00",
  "seq": 101
}
```

### 21.3 DIRECT_MESSAGE_CREATE

> 用户通过私信发消息给机器人时触发。私信场景不支持沙箱环境，可通过用户 ID 白名单调试。

| 字段 | 值 |
|------|------|
| **事件名** | `DIRECT_MESSAGE_CREATE` |
| **Intent** | `DIRECT_MESSAGE` |

**事件体**：内容为 Message 对象（同上，无 seq 字段）

**示例：**

```json
{
  "author": {
    "avatar": "http://thirdqq.qlogo.cn/0",
    "bot": false,
    "id": "1234",
    "username": "abc"
  },
  "channel_id": "100010",
  "content": "ndnnd",
  "guild_id": "18700000000001",
  "id": "0812345677890abcdef",
  "member": {
    "joined_at": "2021-04-12T16:34:42+08:00",
    "roles": ["1"]
  },
  "timestamp": "2021-05-20T15:14:58+08:00"
}
```

### 21.4 消息审核事件

#### MESSAGE_AUDIT_PASS

> 消息审核通过时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `MESSAGE_AUDIT_PASS` |
| **Intent** | `MESSAGE_AUDIT` |

**事件体**：内容为 MessageAudited 对象

| 名称 | 类型 | 描述 |
|------|------|------|
| audit_id | string | 审核 ID |
| audit_time | string | 审核时间 |
| channel_id | string | 子频道 ID |
| create_time | string | 消息创建时间 |
| guild_id | string | 频道 ID |
| message_id | string | 消息 ID |

#### MESSAGE_AUDIT_REJECT

> 消息审核不通过时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `MESSAGE_AUDIT_REJECT` |
| **Intent** | `MESSAGE_AUDIT` |

**事件体**：内容为 MessageAudited 对象（同上）

**示例：**

```json
{
  "audit_id": "5f60b782-d134-4628-93b8-9baa4b182f48",
  "audit_time": "2022-01-04T18:05:42+08:00",
  "channel_id": "1699792",
  "create_time": "2022-01-04T18:05:42+08:00",
  "guild_id": "46646271634786417",
  "message_id": "10d0df671a1231343431313532313831383136323933383420801e280030a0cbc4013848404148f6b7d08e0650b1acf8fa05"
}
```

---

### 22. 频道成员事件

> 基于 MemberWithGuildID 对象，增加 op_user_id 代表操作人。

### 22.1 GUILD_MEMBER_ADD

> 新用户加入频道时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_MEMBER_ADD` |
| **Intent** | `GUILDS` (1<<0) |

**事件体：**

| 名称 | 类型 | 描述 |
|------|------|------|
| guild_id | string | 频道 ID |
| joined_at | string | 加入时间 |
| nick | string | 频道昵称 |
| op_user_id | string | 操作人 ID |
| roles | []string | 身份组 ID 列表 |
| user | object | 用户对象，包含 `id`, `username`, `avatar`, `bot` |

**示例：**

```json
{
  "guild_id": "200000000",
  "joined_at": "2021-10-21T11:20:18+08:00",
  "nick": "",
  "op_user_id": "100000000",
  "roles": ["1"],
  "user": {
    "avatar": "http://thirdqq.qlogo.cn/g?b=oidb&k=IU4JJatZtNXCVrf44eshNg&s=0&t=1638261405",
    "bot": true,
    "id": "8834102668809967837",
    "username": "b站机器人"
  }
}
```

### 22.2 GUILD_MEMBER_UPDATE

> 用户的频道属性发生变化时触发（如频道昵称、身份组变更）。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_MEMBER_UPDATE` |
| **Intent** | `GUILDS` (1<<0) |

**事件体**：同 GUILD_MEMBER_ADD

**示例：**

```json
{
  "guild_id": "200000000",
  "joined_at": "2021-10-21T11:20:18+08:00",
  "nick": "",
  "op_user_id": "8834102668809967837",
  "roles": ["2"],
  "user": {
    "avatar": "http://thirdqq.qlogo.cn/g?b=oidb&k=IU4JJatZtNXCVrf44eshNg&s=0&t=1638261405",
    "bot": true,
    "id": "8834102668809967837",
    "username": "b站机器人"
  }
}
```

### 22.3 GUILD_MEMBER_REMOVE

> 用户离开频道时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `GUILD_MEMBER_REMOVE` |
| **Intent** | `GUILDS` (1<<0) |

**事件体**：同 GUILD_MEMBER_ADD

**示例：**

```json
{
  "guild_id": "200000000",
  "joined_at": "2021-10-21T11:20:18+08:00",
  "nick": "",
  "op_user_id": "100000000",
  "roles": ["1"],
  "user": {
    "avatar": "http://thirdqq.qlogo.cn/g?b=oidb&k=IU4JJatZtNXCVrf44eshNg&s=0&t=1638261405",
    "bot": true,
    "id": "8834102668809967837",
    "username": "b站机器人"
  }
}
```

---

### 23. 音视频/直播子频道成员进出事件

### 23.1 AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER

> 用户进入音视频/直播子频道时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER` |
| **Intent** | `GUILDS` (1<<0) |

**事件体：**

| 名称 | 类型 | 描述 |
|------|------|------|
| guild_id | string | 频道 ID |
| channel_id | string | 子频道 ID |
| channel_type | integer | 子频道类型。`2`=音视频子频道, `5`=直播子频道 |
| user_id | string | 用户 ID |

**示例：**

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "channel_type": 2,
  "user_id": "144115218182563108"
}
```

### 23.2 AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT

> 用户离开音视频/直播子频道时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT` |
| **Intent** | `GUILDS` (1<<0) |

**事件体：** 同 AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER

**示例：**

```json
{
  "guild_id": "47129941624960822",
  "channel_id": "1661124",
  "channel_type": 5,
  "user_id": "144115218182563108"
}
```

---

### 24. 表情表态事件

> 目前表情表态仅支持在频道内使用。

### 24.1 MESSAGE_REACTION_ADD

> 用户对消息进行表情表态时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `MESSAGE_REACTION_ADD` |
| **Intent** | `GUILD_MESSAGE_REACTIONS` |

**事件体**：内容为 MessageReaction 对象

| 名称 | 类型 | 描述 |
|------|------|------|
| user_id | string | 操作用户 ID |
| emoji | object | 表情对象，包含 `id`（表情 ID）和 `type`（表情类型） |
| channel_id | string | 子频道 ID |
| guild_id | string | 频道 ID |
| target | object | 表态目标，包含 `id`（目标 ID）和 `type`（目标类型，`0`=消息） |

### 24.2 MESSAGE_REACTION_REMOVE

> 用户取消消息表情表态时触发。

| 字段 | 值 |
|------|------|
| **事件名** | `MESSAGE_REACTION_REMOVE` |
| **Intent** | `GUILD_MESSAGE_REACTIONS` |

**事件体**：内容为 MessageReaction 对象（同上）

**示例：**

```json
{
  "user_id": "1111222233333",
  "emoji": {
    "id": "277",
    "type": 1
  },
  "channel_id": "12345",
  "guild_id": "11110011112222",
  "target": {
    "id": "2",
    "type": 0
  }
}
```

---

## 附录：Intent 位汇总

| Intent 名称 | 位运算 | 包含的事件 |
|-------------|--------|-----------|
| `GUILDS` | 1<<0 | GUILD_CREATE, GUILD_UPDATE, GUILD_DELETE, CHANNEL_CREATE, CHANNEL_UPDATE, CHANNEL_DELETE, GUILD_MEMBER_ADD, GUILD_MEMBER_UPDATE, GUILD_MEMBER_REMOVE, AUDIO_OR_LIVE_CHANNEL_MEMBER_ENTER, AUDIO_OR_LIVE_CHANNEL_MEMBER_EXIT |
| `GUILD_MESSAGE_REACTIONS` | 1<<10 | MESSAGE_REACTION_ADD, MESSAGE_REACTION_REMOVE |
| `PUBLIC_GUILD_MESSAGES` | 1<<25 (部分) | AT_MESSAGE_CREATE, MESSAGE_CREATE |
| `DIRECT_MESSAGE` | 1<<12 | DIRECT_MESSAGE_CREATE |
| `MESSAGE_AUDIT` | 1<<27 | MESSAGE_AUDIT_PASS, MESSAGE_AUDIT_REJECT |
| `GROUP_AND_C2C_EVENT` | 1<<25 | C2C_MESSAGE_CREATE, GROUP_AT_MESSAGE_CREATE, GROUP_MESSAGE_CREATE, FRIEND_ADD, FRIEND_DEL, C2C_MSG_RECEIVE, C2C_MSG_REJECT, GROUP_ADD_ROBOT, GROUP_DEL_ROBOT, GROUP_MSG_RECEIVE, GROUP_MSG_REJECT, GROUP_MEMBER_ADD, GROUP_MEMBER_REMOVE |
| `INTERACTION` | 1<<26 | INTERACTION_CREATE |

---

> 文档来源：QQ 机器人官方文档 (bot.q.qq.com/wiki/develop/api-v2/)
> 整理时间：2026-07-26
> 包含全部 24 个事件页面的完整内容
