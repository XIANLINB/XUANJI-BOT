# OneBot 适配器 × Napcat 连接指南

本文说明如何用你自己的 Napcat（任意 OneBot v11 实现：Napcat / go-cqhttp / Lagrange 均可）接入璇玑框架，
让框架通过统一的插件系统处理群聊 / 私聊消息。

> 设计原则（参考 Shiro 连 Napcat 范式）：框架只认 **OneBot v11 标准协议**，不写任何 Napcat 专用代码。
> 任何兼容 OneBot v11 的协议端都能接入。

---

## 一、框架侧开启 OneBot 适配器

在 `application.yml`（或环境变量）里打开开关。推荐用**反向 WebSocket**：框架起一个 WS 服务端，Napcat 主动连过来。

```yaml
xuanji:
  onebot:
    enabled: true
    reverse:
      enabled: true
      path: /onebot/ws          # Napcat 的 ws-reverse 要指向这个路径
      access-token: ${ONEBOT_TOKEN:}   # 生产环境务必设置，且通过环境变量注入，不要写进仓库
    forward:
      enabled: false            # 正向 WS（框架主动连 Napcat）默认关闭
```

- 启动后控制台日志会打印 `[OneBot] 适配器已启用` 与 `端点已开放: /onebot/ws`。
- 反向 WS 端点地址：`ws://<框架IP>:<端口>/onebot/ws`
- 若 `access-token` 非空，Napcat 必须带上相同的 token（下面两种填法都支持）：
  - `Authorization: Bearer <token>` 请求头
  - 或查询参数 `?access_token=<token>`

---

## 二、Napcat 侧配置（反向 WS，推荐）

Napcat 的 OneBot 11 配置（JSON）里加一段 `ws-reverse`：

```json
{
  "onebot": {
    "enabled": true,
    "musicSignUrl": "",
    "reportSelfMessage": false,
    "messagePostFormat": "array",
    "wsReverses": [
      {
        "enable": true,
        "url": "ws://127.0.0.1:8668/onebot/ws",
        "reconcile": true,
        "token": "这里填和框架一样的 ONEBOT_TOKEN"
      }
    ]
  }
}
```

要点：
- `url` 必须等于框架的 `反向WS 端点地址`（IP / 端口 / path 三者全对）。
  **不用自己拼**：打开控制台 →「OneBot」板块 →「接入地址」卡片，会直接把当前实例的 `ws://host:port/onebot/ws`
  和一段可复制的 `wsReverses` 片段算好给你（端口默认 `8668`，即 `server.port`）。
- `messagePostFormat` 建议 `"array"`，框架对数组格式和 CQ 码字符串都兼容，但数组格式信息最完整。
- `reportSelfMessage`：若设为 `true`，你自己的发言也会上报，框架默认按 `ignoreSelfMessage=true` 忽略，不会形成回声死循环。
- 框架启动顺序无所谓；Napcat 连上后，控制台「OneBot」板块会显示 `selfId=你的QQ号 · reverse·在线`。

---

## 三、正向 WS（可选，框架主动连 Napcat）

如果你更希望框架去连 Napcat 暴露的 WS 端口：

```yaml
xuanji:
  onebot:
    enabled: true
    reverse:
      enabled: false
    forward:
      enabled: true
      url: ws://127.0.0.1:3001     # Napcat 的 WS 服务地址
      access-token: ${ONEBOT_TOKEN:}
      reconnect-interval-ms: 5000
```

> 反向 / 正向**同时开也可以**：反向接 A 号、正向接 B 号，框架按 `self_id` 区分多 bot。

---

## 四、验证连通

1. 打开控制台 → **OneBot** 板块：应看到「适配器 已启用」「反向WS 开启 · 路径 /onebot/ws」「在线会话 1 个」，
   「已连接会话」里出现 `selfId=<你的号> · reverse·在线`。
2. 在 QQ 群里 @机器人 发一句 `帮助`，到控制台 **消息监控 →「全平台事件流」**，会看到一条 `IN text` 记录；
   若装了带 `@GroupMessage` 的插件命中，紧接着会有一条 `OUT` 回复记录。
   > 注意区分：「事件日志」板块读的是**持久化数据表**（按平台切换），而「全平台事件流」读的是框架内存环形缓冲，
   > QQ 与 OneBot 的收发都会实时落在这里 —— 联调时看这个最快。
3. **仪表盘**底部的「最近事件」也会同步显示，且顶部多出「OneBot 会话」指标、Bot 状态列表里会并列出 OneBot 连接。
4. 也可在 OneBot 板块的「主动发送测试」里填 群号 + 文本，点发送，验证下行通道。

---

## 五、写插件响应 OneBot 消息

OneBot 的群聊 / 私聊消息已被桥接到统一的 `CommandRegistry`，
**和 QQ 官方消息走同一套插件系统**。你只需写一次 `@GroupMessage` / `@PrivateMessage` 方法，两类平台都能触发：

```java
@XuanjiPlugin(id = "my-echo", name = "回声插件")
public class EchoPlugin {

    @GroupMessage(startWith = "!echo")
    public String onGroup(dev.xuanji.sdk.event.GroupMessageEvent event) {
        // event.getGroupId() / getSenderId() / getPlainText() 平台无关
        return "你说了：" + event.getPlainText();
    }

    @PrivateMessage
    public String onPrivate(dev.xuanji.sdk.event.PrivateMessageEvent event) {
        return "私聊收到：" + event.getContent();
    }
}
```

把插件打成 PF4J 插件 jar 丢进 `plugins/` 目录，控制台「插件管理」里启用即可。
处理好友请求 / 加群请求 / 群管动作（踢人、禁言、设管理员…）可用事件 `platformData` 里的 `flag`，
调用 `Bot` 门面上的 `approveFriendRequest / approveGroupAddRequest / kickGroupMember / banGroupMember …`。

---

## 六、安全与注意事项

- **token 必须设**：本机回环可临时留空，但凡暴露在网络的端口务必配置 `access-token` 且用环境变量注入。
- **不要对线上 bot 起第二连接**：本框架的 OneBot 适配器与「QQ 官方机器人」是两套独立接入，互不冲突；
  但同一 QQ 号若已在别处登录协议端，请确保只连一个，避免互踢。
- **隔离目录**：联调时用自己的测试号 + 隔离的 `data/` 目录，绝不复用生产凭据。
- 框架接收到消息后若 `self_id` 与 `ignoreSelfMessage` 配置一致会忽略自身回声，避免循环。

---

## 七、回归测试脚本（不连真机也能验证）

仓库内置两个纯标准库 Python 脚本，可在**隔离目录**起一个临时框架实例 + 模拟 OneBot 客户端，端到端验证「消息→插件→回复」全链路，绝不触碰你的线上 bot：

```
docs/onebot-verify/wsclient_verify.py   # 模拟 Napcat：连反向 WS，推 lifecycle + 群消息，回 echo 响应
docs/onebot-verify/hold_conn.py         # 保持连接 20s，验证「在线会话」计数与面板显示
```

用法（在隔离目录，临时端口，假 selfId）：

```bash
cp xuanji-starter/target/xuanji-starter.jar /tmp/obtest/
cd /tmp/obtest
nohup java -jar xuanji-starter.jar --server.port=18081 \
  --xuanji.onebot.enabled=true --xuanji.onebot.reverse.enabled=true \
  --xuanji.onebot.reverse.path=/onebot/ws --xuanji.onebot.reverse.access-token=verify-token-x &
python3 docs/onebot-verify/wsclient_verify.py   # 期望看到插件回「你好, hello!」
python3 docs/onebot-verify/hold_conn.py         # 期间观察控制台「OneBot」板块在线会话
```

脚本里 `X-Self-ID: 10001`、`Authorization: Bearer verify-token-x` 均为测试值，可随意改动。
