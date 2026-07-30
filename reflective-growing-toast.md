# 架构优化方案：分离事件系统 + core 分层

## 目标

1. 将事件注册、监听、分发从 `webhook/` 中抽离为独立的 `event/` 模块
2. 传输层（websocket/webhook）放入 `core/` 父包，与事件处理层解耦
3. `websocket/` 只负责 WebSocket 连接，`webhook/` 只负责 HTTP 回调

## 新架构

```
src/main/java/com/qunxing/xuanji/
├── core/                           # QQ 平台连接层
│   ├── websocket/                  # WebSocket 连接管理（原 botws/）
│   │   ├── GatewayService.java
│   │   ├── QqBotWsClient.java
│   │   ├── QqBotWsManager.java
│   │   ├── WsPayload.java
│   │   └── WebSocketController.java
│   │
│   ├── webhook/                    # Webhook 连接管理（只负责 HTTP 回调）
│   │   ├── WebhookController.java
│   │   ├── WebhookService.java
│   │   ├── WebhookServiceImpl.java
│   │   ├── WebhookPayload.java
│   │   └── SignatureVerifier.java
│   │
│   ├── api/                        # QQ API 统一调用客户端
│   │   └── QqApiService.java
│   │
│   └── model/                      # 数据模型
│       ├── Robot.java
│       └── RobotEnvironment.java
│
├── event/                          # 【新】事件系统
│   ├── EventDispatcher.java        # 事件分发器（核心路由）
│   ├── EventHandler.java           # 事件处理器接口
│   └── handler/                    # 具体事件处理器
│       ├── C2cMessageHandler.java
│       ├── GroupMessageHandler.java
│       ├── GuildMessageHandler.java
│       ├── DmsMessageHandler.java
│       ├── InteractionEventHandler.java
│       ├── GroupEventHandler.java
│       ├── FriendEventHandler.java
│       ├── SystemEventHandler.java
│       ├── ForumEventHandler.java
│       ├── AudioEventHandler.java
│       └── MessageDeleteHandler.java
│
├── plugin/                         # 插件引擎
│   ├── sdk/                        # 插件 SDK 接口
│   │   ├── BotPlugin.java
│   │   ├── PluginEvent.java
│   │   ├── PluginResult.java
│   │   ├── PluginContext.java
│   │   ├── PluginDataStore.java
│   │   └── CommandInfo.java
│   └── core/                       # 插件运行时
│       ├── PluginManager.java
│       ├── PluginEventDispatcher.java
│       ├── CommandRouter.java
│       ├── CommandRegistry.java
│       ├── CommandCooldownService.java
│       ├── PluginClassLoader.java
│       ├── PluginDependencyResolver.java
│       ├── PluginSandbox.java
│       └── PluginContextImpl.java
│
├── message/                        # 消息构建器
│   ├── MarkdownBuilder.java
│   ├── KeyboardBuilder.java
│   ├── CardBuilder.java
│   ├── EmbedBuilder.java
│   └── ArkBuilder.java
│
├── util/                           # 工具类
│   ├── QqApiErrorCode.java
│   ├── AesUtil.java
│   ├── Ed25519Util.java
│   └── JsonEscUtil.java
│
├── exception/                      # 异常处理
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
│
├── result/                         # 统一响应
│   └── R.java
│
├── config/                         # 基础配置
│   ├── HttpClientConfig.java
│   └── EnvType.java
│
├── registry/                       # 机器人注册表
│   ├── RobotRegistry.java
│   ├── AccessTokenService.java
│   └── AccessTokenServiceImpl.java
│
└── autoconfigure/                  # Spring Boot 自动配置
    ├── XuanjiAutoConfiguration.java
    ├── XuanjiProperties.java
    └── XuanjiBotRunner.java
```

## 依赖关系

```
core/websocket/ ──→ event/ ←── core/webhook/
                       │
                       ↓
                    plugin/
                       │
                       ↓
                    registry/ (RobotRegistry, AccessTokenService)
```

- `core/websocket/QqBotWsClient` 调用 `event/EventDispatcher.dispatch()`
- `core/webhook/WebhookServiceImpl` 调用 `event/EventDispatcher.dispatch()`
- `event/EventDispatcher` 依赖 `event/EventHandler` 接口 + `plugin/PluginManager`
- `event/handler/*` 实现 `event/EventHandler` 接口
- `plugin/PluginManager` 通过 SPI 加载插件

## 实施步骤

### Step 1: 创建新目录结构
- 创建 `core/websocket/`, `core/webhook/`, `core/api/`, `core/model/` 包
- 创建 `event/`, `event/handler/` 包
- 创建 `registry/`, `exception/`, `result/`, `config/` 包

### Step 2: 移动并重命名文件
- `botws/*` → `core/websocket/`（5 个文件，更新包名）
- `webhook/controller/*`, `webhook/service/*`, `webhook/dto/*`, `webhook/verify/*` → `core/webhook/`（5 个文件）
- `webhook/dispatcher/EventDispatcher.java` → `event/EventDispatcher.java`
- `webhook/handler/EventHandler.java` → `event/EventHandler.java`
- `webhook/handler/*.java` → `event/handler/*.java`（11 个处理器）
- `robot/service/QqApiService.java` → `core/api/QqApiService.java`
- `robot/entity/*` → `core/model/*`（2 个实体）
- `robot/service/RobotRegistry.java` → `registry/RobotRegistry.java`
- `robot/service/AccessTokenService.java` → `registry/AccessTokenService.java`
- `robot/service/impl/AccessTokenServiceImpl.java` → `registry/AccessTokenServiceImpl.java`

### Step 3: 更新所有 import
- 更新移动文件的 package 声明
- 更新所有引用移动文件的 import 语句
- 确保模块间依赖关系正确

### Step 4: 清理旧目录
- 删除 `botws/`（已移走）
- 删除 `webhook/`（已移走）
- 删除 `robot/`（已移走）

### Step 5: 验证编译
- `mvn clean compile` 确认无错误

## 文件清单

| 操作 | 源路径 | 目标路径 | 说明 |
|------|--------|----------|------|
| 移动 | `botws/GatewayService.java` | `core/websocket/GatewayService.java` | 网关服务 |
| 移动 | `botws/QqBotWsClient.java` | `core/websocket/QqBotWsClient.java` | WS 客户端 |
| 移动 | `botws/QqBotWsManager.java` | `core/websocket/QqBotWsManager.java` | WS 管理器 |
| 移动 | `botws/WsPayload.java` | `core/websocket/WsPayload.java` | WS 帧 DTO |
| 移动 | `botws/WebSocketController.java` | `core/websocket/WebSocketController.java` | WS 监控 |
| 移动 | `webhook/controller/WebhookController.java` | `core/webhook/WebhookController.java` | HTTP 回调 |
| 移动 | `webhook/service/WebhookService.java` | `core/webhook/WebhookService.java` | 接口 |
| 移动 | `webhook/service/impl/WebhookServiceImpl.java` | `core/webhook/WebhookServiceImpl.java` | 实现 |
| 移动 | `webhook/dto/WebhookPayload.java` | `core/webhook/WebhookPayload.java` | 帧 DTO |
| 移动 | `webhook/verify/SignatureVerifier.java` | `core/webhook/SignatureVerifier.java` | 签名校验 |
| 移动 | `webhook/service/WebhookEventConsumer.java` | `core/webhook/WebhookEventConsumer.java` | 消费者 |
| 移动 | `webhook/dispatcher/EventDispatcher.java` | `event/EventDispatcher.java` | 事件分发器 |
| 移动 | `webhook/handler/EventHandler.java` | `event/EventHandler.java` | 处理器接口 |
| 移动 | `webhook/handler/C2cMessageHandler.java` | `event/handler/C2cMessageHandler.java` | 单聊处理 |
| 移动 | `webhook/handler/GroupMessageHandler.java` | `event/handler/GroupMessageHandler.java` | 群聊处理 |
| 移动 | `webhook/handler/GuildMessageHandler.java` | `event/handler/GuildMessageHandler.java` | 频道处理 |
| 移动 | `webhook/handler/DmsMessageHandler.java` | `event/handler/DmsMessageHandler.java` | 私信处理 |
| 移动 | `webhook/handler/InteractionEventHandler.java` | `event/handler/InteractionEventHandler.java` | 互动处理 |
| 移动 | `webhook/handler/GroupEventHandler.java` | `event/handler/GroupEventHandler.java` | 群事件 |
| 移动 | `webhook/handler/FriendEventHandler.java` | `event/handler/FriendEventHandler.java` | 好友事件 |
| 移动 | `webhook/handler/SystemEventHandler.java` | `event/handler/SystemEventHandler.java` | 系统事件 |
| 移动 | `webhook/handler/ForumEventHandler.java` | `event/handler/ForumEventHandler.java` | 论坛事件 |
| 移动 | `webhook/handler/AudioEventHandler.java` | `event/handler/AudioEventHandler.java` | 音频事件 |
| 移动 | `webhook/handler/MessageDeleteHandler.java` | `event/handler/MessageDeleteHandler.java` | 消息删除 |
| 移动 | `robot/service/QqApiService.java` | `core/api/QqApiService.java` | QQ API |
| 移动 | `robot/entity/Robot.java` | `core/model/Robot.java` | 机器人实体 |
| 移动 | `robot/entity/RobotEnvironment.java` | `core/model/RobotEnvironment.java` | 环境实体 |
| 移动 | `robot/service/RobotRegistry.java` | `registry/RobotRegistry.java` | 注册表 |
| 移动 | `robot/service/AccessTokenService.java` | `registry/AccessTokenService.java` | Token 接口 |
| 移动 | `robot/service/impl/AccessTokenServiceImpl.java` | `registry/AccessTokenServiceImpl.java` | Token 实现 |
