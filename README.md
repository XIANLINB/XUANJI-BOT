# 璇玑 (XuanJi) QQ 机器人开发框架

> 基于 Java + Spring Boot 的 QQ 官方机器人开发框架，处于早期开发阶段。

---

## 项目简介

璇玑是一个面向 QQ 官方 Bot API 的 Java 机器人开发框架，目标是提供简洁的事件处理、消息发送和多机器人管理能力。当前版本聚焦 QQ 官方接口的连接与消息处理，未来会逐步演进为插件化、跨平台的机器人框架。

**注意**：本项目仍在早期开发中，API 和模块结构可能频繁变化，不适合直接用于生产环境。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 4.0.6 |
| 构建 | Maven |
| 协议 | QQ 官方 Bot API（WebSocket / Webhook） |
| 工具 | Lombok、Jackson、java.net.http |

---

## 当前能力

- **双连接模式**：支持 WebSocket 长连接与 Webhook 回调两种方式接入 QQ 官方 Bot API。
- **事件分发**：基于 Spring 依赖注入自动扫描 `@EventMapping` 注解的事件处理器。
- **消息发送**：封装单聊 / 群聊的文本、Markdown、键盘按钮、Ark 模板、图片、语音、视频消息发送。
- **AccessToken 管理**：自动获取、缓存、刷新 QQ 开放平台 AccessToken，支持 401 自动重试。
- **多机器人配置**：通过 `xuanji-robots.yml` 配置多个机器人实例。
- **Ed25519 签名验证**：Webhook 模式下支持回调地址验证与事件签名验证。

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- 一个已注册的 QQ 官方机器人（获取 `app-id` 与 `client-secret`）

### 1. 配置机器人

编辑 `src/main/resources/xuanji-robots.yml`：

```yaml
xuanji:
  webhook-url: "your-domain.com"
  is-new-openbot: true
  ignore-bot-messages: false

  robots:
    bot1:
      app-id: "你的 AppID"
      client-secret: "你的 AppSecret"
      is-sandbox: false
      connection-method: websocket
```

- `connection-method: websocket`：框架主动连接 QQ 网关。
- `connection-method: webhook`：QQ 平台向 `https://{webhook-url}/webhook/{app-id}` 推送事件。

### 2. 编译运行

```bash
./mvnw clean package -DskipTests
java -jar target/xuanji-bot-1.0.0.jar
```

### 3. 测试命令

启动后，在 QQ 群中 @ 机器人或给机器人发私信，发送以下命令测试：

```
文本 | markdown | 按钮 | ark23 | ark24 | ark37 | 图片 | 语音 | 视频
```

---

## 项目结构

```
xuanji/
├── src/main/java/com/qunxing/qq_bot_xuanji/
│   ├── QqBotXuanjiApplication.java      # 启动类
│   ├── common/                          # 公共工具与配置
│   │   ├── config/                      # 配置类
│   │   ├── dto/                         # 事件 DTO
│   │   ├── enums/                       # 枚举
│   │   ├── exception/                   # 异常处理
│   │   └── result/                      # 统一响应
│   ├── core/                            # QQ 平台连接与 API 层
│   │   ├── api/                         # QQ OpenAPI 调用、消息发送
│   │   ├── model/                       # 机器人与环境模型
│   │   ├── websocket/                   # WebSocket 连接管理
│   │   └── webhook/                     # Webhook 回调处理
│   ├── event/                           # 事件系统
│   │   ├── EventDispatcher.java         # 事件分发器
│   │   ├── EventHandler.java            # 处理器接口
│   │   ├── EventMapping.java            # 事件映射注解
│   │   └── handler/                     # 具体事件处理器
│   │       ├── c2c/                     # 单聊消息
│   │       ├── group/                   # 群聊消息
│   │       └── guild/                   # 频道事件
│   ├── registry/                        # 机器人注册表与 Token 管理
│   └── utils/                           # 消息构建器与工具类
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   └── xuanji-robots.yml                # 机器人配置
├── docs/
│   └── xuanji-framework-full-design.md  # 框架总体设计文档（未来架构）
├── pom.xml
└── README.md
```

---

## 核心模块说明

### 事件处理

事件处理器实现 `EventHandler` 接口，并通过 `@EventMapping` 声明处理的事件类型：

```java
@Component
@EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE"})
public class MyGroupHandler implements EventHandler {
    @Override
    public String getEventType() { return "GROUP_MESSAGE_EVENT"; }

    @Override
    public void handle(Long robotId, String envType, JSONObject data) {
        // 处理事件
    }
}
```

### 消息发送

通过注入 `MessageSender` 发送消息，当前事件上下文（robotId / envType）会自动从 `ThreadLocal` 获取：

```java
messageSender.sendGroupText(groupOpenid, "你好！", msgId);
messageSender.sendC2cMarkdown(openid, markdown, keyboard, msgId);
```

### 多机器人管理

`RobotRegistry` 在内存中维护机器人配置，`QqBotWsManager` 负责 WebSocket 连接的启动、停止、健康检查和自动重连。

---

## 配置说明

| 配置项 | 说明 |
|--------|------|
| `xuanji.webhook-url` | Webhook 模式下的回调域名 |
| `xuanji.is-new-openbot` | 是否使用新版 QQ 开放平台（`api.bot.qq.com`） |
| `xuanji.ignore-bot-messages` | 是否忽略机器人自己发送的消息 |
| `xuanji.robots.{id}.app-id` | QQ 开放平台 AppID |
| `xuanji.robots.{id}.client-secret` | QQ 开放平台 AppSecret |
| `xuanji.robots.{id}.is-sandbox` | 是否沙箱环境 |
| `xuanji.robots.{id}.connection-method` | `websocket` 或 `webhook` |

---
---

## 开源协议

本项目采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)。

---

*当前版本：v0.1（早期开发中）*
