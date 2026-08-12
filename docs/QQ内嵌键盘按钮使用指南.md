# QQ 内嵌键盘（keyboard）按钮使用指南

> 适用范围：QQ 官方 Bot API（api.bot.qq.com）· Markdown 消息内嵌键盘
> 本文档基于真实环境（璇玑框架 + 接口测试插件）实测结论整理，字段与官方文档对齐。

---

## 一、概述：键盘必须挂在 Markdown 消息下

QQ 的内嵌键盘（keyboard）**只能作为 Markdown 消息（msg_type=2）的附加字段**发送，不能独立存在、也不能挂在纯文本消息上。

```json
{
  "msg_type": 2,
  "markdown": { "content": "## 标题\n正文" },
  "keyboard": { "content": { "rows": [ ... ] } }
}
```

在璇玑框架里通过 `XuanJiMessage.builder().markdown(...).add(new Keyboard(payload)).build()` 组装，`Keyboard` 接收**官方原生结构**（原样透传）：

```java
XuanJiMessageElement.Keyboard kb = new XuanJiMessageElement.Keyboard(keyboardPayloadMap);
XuanJiMessage msg = XuanJiMessage.builder()
        .markdown("**按钮消息**")
        .add(kb)
        .build();
svc.sendToGroup(botKey, groupOpenid, msg);
```

---

## 二、完整 JSON 结构

```json
{
  "content": {
    "rows": [
      {
        "buttons": [
          {
            "id": "btn_1",
            "render_data": {
              "label": "按钮文字",
              "visited_label": "点击后文字（可选）",
              "style": 1
            },
            "action": {
              "type": 1,
              "permission": { "type": 2 },
              "data": "回调数据",
              "enter": false,
              "reply": false,
              "unsupport_tips": "请升级 QQ 后再试"
            }
          }
        ]
      }
    ]
  }
}
```

> 短形式 `keyboard: {"id": "平台模板ID"}` 使用平台预设模板，本文只讲**自定义布局**（content.rows）。

---

## 三、字段详解

### 3.1 顶层 Keyboard

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 否 | 平台预设键盘模板 ID（用模板时填） |
| `content` | KeyboardContent | 否 | 自定义布局，与 `id` 互斥 |
| `content.rows` | Row[] | 否 | 按钮行列表（行/列超限会报 40034029） |

### 3.2 Row / Button

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rows[].buttons` | Button[] | 否 | 行内按钮，从左到右排列 |
| `buttons[].id` | string | 否 | 按钮 ID，**同一键盘内唯一** |
| `buttons[].render_data` | RenderData | 否 | 按钮渲染 |
| `buttons[].action` | Action | 否 | 按钮点击行为 |

### 3.3 RenderData（渲染）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `label` | string | 否 | 按钮文字，**最多 10 字符** |
| `visited_label` | string | **建议必配** | 点击后文字。**实测：跳转/回调按钮点击后 label 一律被清空为空白**，必须配 visited_label 才能显示点击后文字（如"已点击✓"，实测有效） |
| `style` | integer | 否 | **0=灰线框 1=蓝线框 2=白字 3=蓝底白字** |

> ⚠️ **style 只有这 4 档**，官方未定义红框/红字/蓝字等其他颜色，传其他值无效（实测客户端渲染还有差异：白字按钮电脑 QQ 显示无边框、手机 QQ 显示灰边框——同一 style 不同客户端表现不完全一致，属平台行为）。

### 3.4 Action（点击行为）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | integer | 否 | **0=跳转**（http 或小程序）/ **1=回调**（data 传后台）/ **2=指令**（输入框插入 @bot data） |
| `permission` | Permission | 否 | 操作权限 |
| `data` | string | type=1/2 时必填 | 回调数据 / 指令内容 |
| `enter` | boolean | 否 | 指令按钮专用：点击后**直接自动发送** data（默认 false；仅单聊可用） |
| `reply` | boolean | 否 | 指令按钮专用：指令是否带引用回复本消息（默认 false） |
| `unsupport_tips` | string | 否 | 客户端版本过低时的提示文案 |

### 3.5 Permission（操作权限）⭐ 最容易踩坑

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | integer | 否 | **0=指定用户 1=管理员 2=所有人** |
| `specify_user_ids` | string[] | type=0 时 | 有权限的用户 openid 列表 |
| `specify_role_ids` | string[] | type=0 时 | 有权限的身份组 ID（仅频道可用） |

> ⚠️ **实测结论：permission 必须显式传！** 不传 permission 字段的按钮，点击一律提示「无权限操作」（客户端本地拦截，后端**收不到** INTERACTION_CREATE 事件）。所有按钮都建议显式传 `"permission": {"type": 2}`（所有人可点）。

---

## 四、三种 action.type 实测行为

| type | 名称 | 点击行为 | 实测 |
|---|---|---|---|
| **0** | 跳转按钮 | 跳转 http URL 或小程序 | 配 permission 后正常弹出网页；**不触发 INTERACTION_CREATE（后端无日志）**；点击后按钮文字清空 → 需 visited_label |
| **1** | 回调按钮 | 回调后台接口，`data` 传给后台 | 触发 `INTERACTION_CREATE`（type=11）→ 后端须 `PUT /interactions/{id}` 回应；**无 visited_label 时点击后按钮文字变空白，有 visited_label 时显示点击后文字（实测"已点击✓"有效）** |
| **2** | 指令按钮 | 输入框自动插入 `@bot data` | 正常；配 `enter:true` 时点击直接发送 |

### 回调按钮（type=1）点击后的完整链路

1. 用户点击 → 平台向机器人 WS 推送 `INTERACTION_CREATE`（type=11，含 `id`=interaction_id、`data`=按钮回调数据）
2. 后端须在有效时间内回应 `PUT /interactions/{id}` body `{"code":0}`，否则客户端一直 loading 直到超时
3. **同一 interaction_id 只能回应一次**；code：0=成功 1=操作失败 2=操作频繁 3=重复操作 4=没有权限 5=仅管理员操作

> ⚠️ **实测：跳转/回调按钮点击后按钮 label 都会被清空为空白**（平台通用行为，非配置错误）。
> **解决办法：给按钮配 `render_data.visited_label`**（点击后显示的文字，如"已点击✓"）——实测有效。
> 官方互动响应接口只能回 `code`、不能回传按钮内容，所以 `visited_label` 是按钮点击后的唯一前端反馈手段。

---

## 五、Java 插件构建示例（璇玑框架）

框架 API：`XuanJiMessageElement.Keyboard(Object nativePayload)` —— 传入官方结构的 Map 即原样透传。

```java
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.message.XuanJiMessageElement;

// ── 工具方法 ──
/** 构造单个按钮（render_data + action）。 */
static Map<String, Object> btn(String id, String label, int style, Map<String, Object> action) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("id", id);
    b.put("render_data", Map.of("label", label, "style", style));
    if (action != null) b.put("action", action);
    return b;
}

/** 把多行按钮包装成官方 keyboard 结构（content.rows[].buttons[]）。 */
static XuanJiMessageElement.Keyboard keyboard(List<List<Map<String, Object>>> rows) {
    List<Map<String, Object>> rowList = new ArrayList<>();
    for (List<Map<String, Object>> row : rows) {
        rowList.add(Map.of("buttons", row));
    }
    return new XuanJiMessageElement.Keyboard(Map.of("content", Map.of("rows", rowList)));
}

// ── 命令中使用 ──
@Command(value = "#按钮", scope = Command.Scope.GROUP)
public String button(GroupMessageEvent e, PluginServices svc) {
    // 回调按钮（所有人可点）—— 注意显式 permission.type=2
    Map<String, Object> cb = btn("cb_1", "回调", 1,
            Map.of("type", 1, "data", "my_callback_data", "permission", Map.of("type", 2)));
    // 指令按钮（点击后直接发送 /签到）
    Map<String, Object> cmd = btn("cmd_1", "签到", 3,
            Map.of("type", 2, "data", "/签到", "permission", Map.of("type", 2), "enter", true));
    // 跳转按钮
    Map<String, Object> jump = btn("jump_1", "官网", 1,
            Map.of("type", 0, "data", "https://bot.q.qq.com", "permission", Map.of("type", 2)));
    // 指定用户按钮（仅发送者本人可点）
    Map<String, Object> me = btn("me_1", "仅我", 1,
            Map.of("type", 1, "data", "only_me",
                    "permission", Map.of("type", 0, "specify_user_ids", List.of(e.getSenderId()))));

    XuanJiMessageElement.Keyboard kb = keyboard(List.of(List.of(cb, cmd), List.of(jump, me)));
    XuanJiMessage msg = XuanJiMessage.builder().markdown("**测试**").add(kb).build();
    svc.sendToGroup(e.getBotId(), e.getGroupId(), msg);
    return "已发送";
}
```

---

## 六、实测结论汇总（#按钮 系列）

| 配置 | 结果 |
|---|---|
| 按钮**不传 permission** | 点击一律「无权限操作」（群主也一样），后端无日志 —— **必须显式传 permission** |
| `permission.type=2`（所有人） | 群主、普通成员均可点 ✓（#按钮3 全员配 type=2 后全部可点验证通过） |
| `permission.type=1`（管理员） | 群主可点、普通成员「无权限操作」 ✓ |
| `permission.type=0` + `specify_user_ids` | 仅指定用户可点，其他人「无权限操作」 ✓ |
| `action.type=2` + `enter:true` | 点击自动在输入框插入 `@bot data`，可直接发送 ✓ |
| `action.type=1`（回调）无 visited_label | 触发 INTERACTION_CREATE；点击后按钮**变空白** |
| `action.type=1`（回调）+ `visited_label` | 触发 INTERACTION_CREATE；点击后显示 visited_label（如"已点击✓"）✓ |
| `action.type=0`（跳转） | 配 permission 后正常弹出网页；**不触发交互事件（后端无日志）**；点击后按钮文字清空，需 visited_label |
| `render_data.style` 0-3 | 正常渲染；不同客户端表现有差异（白字电脑无边框/手机灰边框） |
| style 其他值（如想红框红字） | **不支持**，官方仅 0-3 |
| 指令按钮电脑 vs 手机 | 电脑灰线框黑字、手机灰线框红字（客户端差异，非配置问题） |

---

## 七、常见错误码

| 错误码 | 含义 | 建议 |
|---|---|---|
| 305007 | 键盘样式参数错误 | 检查 keyboard 结构/字段 |
| 40034029 | 内联键盘行/列超限 | 减少按钮数量 |
| 630003 | AppID 与 interaction_id 不匹配 | 回应互动事件用对机器人 |
| 630007 | data too large | 减小请求体 |

---

## 八、注意事项速查

1. **permission 必传**（建议统一 `{"type": 2}`）
2. **键盘只能挂 Markdown 消息**（msg_type=2）
3. 回调按钮点击后要回应 `PUT /interactions/{id}`（`{"code":0}`），同一 id 只能回应一次；跳转按钮不触发交互事件
4. `label` 最多 10 字符
5. `style` 只有 0-3，无其他颜色
6. **按钮（跳转/回调）点击后 label 一律清空** → 必须配 `visited_label` 显示点击后文字（实测"已点击✓"有效）
7. `enter:true` 仅单聊指令按钮可用；群聊指令按钮点击是插入输入框不自动发
8. 同一键盘按钮 `id` 必须唯一
