# QQ Bot API v2 - Part 4: 内容管理 (Content Management)

> 完整 API 文档 - 包含频道公告、精华消息、日程、音频控制、论坛等全部接口

---

## 目录

1. [创建频道公告](#1-创建频道公告)
2. [删除频道公告](#2-删除频道公告)
3. [添加精华消息](#3-添加精华消息)
4. [删除精华消息](#4-删除精华消息)
5. [获取精华消息](#5-获取精华消息)
6. [获取频道日程列表](#6-获取频道日程列表)
7. [获取日程详情](#7-获取日程详情)
8. [创建日程](#8-创建日程)
9. [修改日程](#9-修改日程)
10. [删除日程](#10-删除日程)
11. [音频控制](#11-音频控制)
12. [机器人上麦](#12-机器人上麦)
13. [机器人下麦](#13-机器人下麦)
14. [获取帖子列表](#14-获取帖子列表)
15. [获取帖子详情](#15-获取帖子详情)
16. [发表帖子](#16-发表帖子)
17. [删除帖子](#17-删除帖子)
18. [论坛事件对象(ForumEvent)](#18-论坛事件对象forumevent)
19. [开放论坛事件对象(OpenForumEvent)](#19-开放论坛事件对象openforumevent)

---

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

## 1. 创建频道公告

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

## 2. 删除频道公告

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

## 3. 添加精华消息

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

## 4. 删除精华消息

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

## 5. 获取精华消息

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

## 6. 获取频道日程列表

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

## 7. 获取日程详情

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

## 8. 创建日程

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

## 9. 修改日程

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

## 10. 删除日程

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

## 11. 音频控制

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

## 12. 机器人上麦

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

## 13. 机器人下麦

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

## 14. 获取帖子列表

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

## 15. 获取帖子详情

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

## 16. 发表帖子

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

## 17. 删除帖子

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

## 18. 论坛事件对象(ForumEvent)

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

## 19. 开放论坛事件对象(OpenForumEvent)

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
