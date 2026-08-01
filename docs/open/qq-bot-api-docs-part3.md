# QQ Bot API v2 完整文档 - Part 3: 频道管理 (Channel Management)

> 本文档包含 QQ 机器人 API v2 频道管理相关接口的完整详细信息。
> 数据来源：https://bot.q.qq.com/wiki/develop/api-v2/

---

## 目录

- [1. 获取频道详情](#1-获取频道详情)
- [2. 获取子频道列表](#2-获取子频道列表)
- [3. 创建子频道](#3-创建子频道)
- [4. 获取子频道详情](#4-获取子频道详情)
- [5. 修改子频道](#5-修改子频道)
- [6. 删除子频道](#6-删除子频道)
- [7. 获取子频道在线成员数](#7-获取子频道在线成员数)
- [8. 获取频道成员列表](#8-获取频道成员列表)
- [9. 获取频道身份组成员列表](#9-获取频道身份组成员列表)
- [10. 获取频道成员详情](#10-获取频道成员详情)
- [11. 删除频道成员](#11-删除频道成员)
- [12. 获取频道身份组列表](#12-获取频道身份组列表)
- [13. 创建频道身份组](#13-创建频道身份组)
- [14. 修改频道身份组](#14-修改频道身份组)
- [15. 删除频道身份组](#15-删除频道身份组)
- [16. 添加身份组成员](#16-添加身份组成员)
- [17. 删除身份组成员](#17-删除身份组成员)
- [18. 获取子频道用户权限](#18-获取子频道用户权限)
- [19. 修改子频道用户权限](#19-修改子频道用户权限)
- [20. 获取子频道身份组权限](#20-获取子频道身份组权限)
- [21. 修改子频道身份组权限](#21-修改子频道身份组权限)
- [22. 获取可用权限列表](#22-获取可用权限列表)
- [23. 发送权限授权链接](#23-发送权限授权链接)
- [24. 获取消息频率设置](#24-获取消息频率设置)
- [25. 频道全员禁言](#25-频道全员禁言)
- [26. 指定成员禁言](#26-指定成员禁言)
- [27. 批量成员禁言](#27-批量成员禁言)
- [附录A: 公共错误码](#附录a-公共错误码)
- [附录B: 数据模型](#附录b-数据模型)

---

## 1. 获取频道详情

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

## 2. 获取子频道列表

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

## 3. 创建子频道

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

## 4. 获取子频道详情

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

## 5. 修改子频道

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

## 6. 删除子频道

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

## 7. 获取子频道在线成员数

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

## 8. 获取频道成员列表

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

## 9. 获取频道身份组成员列表

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

## 10. 获取频道成员详情

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

## 11. 删除频道成员

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

## 12. 获取频道身份组列表

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

## 13. 创建频道身份组

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

## 14. 修改频道身份组

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

## 15. 删除频道身份组

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

## 16. 添加身份组成员

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

## 17. 删除身份组成员

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

## 18. 获取子频道用户权限

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

## 19. 修改子频道用户权限

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

## 20. 获取子频道身份组权限

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

## 21. 修改子频道身份组权限

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

## 22. 获取可用权限列表

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

## 23. 发送权限授权链接

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

## 24. 获取消息频率设置

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

## 25. 频道全员禁言

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

## 26. 指定成员禁言

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

## 27. 批量成员禁言

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
