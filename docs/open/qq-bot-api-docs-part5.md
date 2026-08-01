# QQ Bot API v2 - 完整事件文档 (Part 5)

> 本文档包含 QQ 机器人 API v2 所有事件的完整详细信息，包括事件名、Intent 位、事件体字段表及 JSON 示例。

---

## 目录

- [1. 单聊消息事件 (C2C_MESSAGE_CREATE)](#1-单聊消息事件)
- [2. 群@机器人消息 (GROUP_AT_MESSAGE_CREATE)](#2-群机器人消息)
- [3. 群消息全量模式 (GROUP_MESSAGE_CREATE)](#3-群消息全量模式)
- [4. 用户添加好友 (FRIEND_ADD)](#4-用户添加好友)
- [5. 用户删除好友 (FRIEND_DEL)](#5-用户删除好友)
- [6. 单聊消息接收开启 (C2C_MSG_RECEIVE)](#6-单聊消息接收开启)
- [7. 单聊消息接收关闭 (C2C_MSG_REJECT)](#7-单聊消息接收关闭)
- [8. 机器人加入群聊 (GROUP_ADD_ROBOT)](#8-机器人加入群聊)
- [9. 机器人退出群聊 (GROUP_DEL_ROBOT)](#9-机器人退出群聊)
- [10. 群聊消息接收开启 (GROUP_MSG_RECEIVE)](#10-群聊消息接收开启)
- [11. 群聊消息接收关闭 (GROUP_MSG_REJECT)](#11-群聊消息接收关闭)
- [12. 群成员加入 (GROUP_MEMBER_ADD)](#12-群成员加入)
- [13. 群成员退出 (GROUP_MEMBER_REMOVE)](#13-群成员退出)
- [14. 互动事件 (INTERACTION_CREATE)](#14-互动事件)
- [15. 频道创建 (GUILD_CREATE)](#15-频道创建)
- [16. 频道更新 (GUILD_UPDATE)](#16-频道更新)
- [17. 频道解散 (GUILD_DELETE)](#17-频道解散)
- [18. 子频道创建 (CHANNEL_CREATE)](#18-子频道创建)
- [19. 子频道更新 (CHANNEL_UPDATE)](#19-子频道更新)
- [20. 子频道删除 (CHANNEL_DELETE)](#20-子频道删除)
- [21. 频道消息事件 (AT_MESSAGE_CREATE / MESSAGE_CREATE / DIRECT_MESSAGE_CREATE / MESSAGE_AUDIT)](#21-频道消息事件)
- [22. 频道成员事件 (GUILD_MEMBER_ADD / UPDATE / REMOVE)](#22-频道成员事件)
- [23. 音视频/直播子频道成员进出事件](#23-音视频直播子频道成员进出事件)
- [24. 表情表态事件 (MESSAGE_REACTION_ADD / REMOVE)](#24-表情表态事件)

---

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

## 1. 单聊消息事件

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

## 2. 群@机器人消息

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

## 3. 群消息全量模式

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

## 4. 用户添加好友

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

## 5. 用户删除好友

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

## 6. 单聊消息接收开启

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

## 7. 单聊消息接收关闭

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

## 8. 机器人加入群聊

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

## 9. 机器人退出群聊

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

## 10. 群聊消息接收开启

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

## 11. 群聊消息接收关闭

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

## 12. 群成员加入

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

## 13. 群成员退出

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

## 14. 互动事件

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

## 15. 频道创建

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

## 16. 频道更新

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

## 17. 频道解散

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

## 18. 子频道创建

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

## 19. 子频道更新

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

## 20. 子频道删除

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

## 21. 频道消息事件

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

## 22. 频道成员事件

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

## 23. 音视频/直播子频道成员进出事件

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

## 24. 表情表态事件

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
