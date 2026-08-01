# QQ Bot API v2 完整文档 - Part 2: 富媒体 + 机器人 + 群聊管理 + 交互

> 数据来源：QQ 机器人官方文档 (bot.q.qq.com/wiki)  
> 抓取日期：2026-07-26

---

## 目录

- [1. 富媒体消息概述](#1-富媒体消息概述)
- [2. 单聊富媒体上传](#2-单聊富媒体上传)
- [3. 单聊富媒体预上传](#3-单聊富媒体预上传)
- [4. 单聊分片上传完成](#4-单聊分片上传完成)
- [5. 群聊富媒体上传](#5-群聊富媒体上传)
- [6. 群聊富媒体预上传](#6-群聊富媒体预上传)
- [7. 群聊分片上传完成](#7-群聊分片上传完成)
- [8. 获取机器人详情](#8-获取机器人详情)
- [9. 获取机器人加入的频道列表](#9-获取机器人加入的频道列表)
- [10. 生成分享链接](#10-生成分享链接)
- [11. 获取群基础信息](#11-获取群基础信息)
- [12. 获取机器人群内状态](#12-获取机器人群内状态)
- [13. 互动事件响应](#13-互动事件响应)

---

## 1. 富媒体消息概述

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

## 2. 单聊富媒体上传

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

## 3. 单聊富媒体预上传

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

## 4. 单聊分片上传完成

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

## 5. 群聊富媒体上传

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

## 6. 群聊富媒体预上传

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

## 7. 群聊分片上传完成

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

## 8. 获取机器人详情

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

## 9. 获取机器人加入的频道列表

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

## 10. 生成分享链接

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

## 11. 获取群基础信息

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

## 12. 获取机器人群内状态

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

## 13. 互动事件响应

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
