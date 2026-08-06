# 璇玑机器人开源框架 · 总体设计文档
> ⚠️ **历史设计文档（v2.0，2026-07-30）**：总体设计蓝图，多数决策已落地。当前代码以实际实现为准，本档仅作设计溯源参考。


> **版本**：v2.0（全集重写版，取代此前所有版本）
> **技术基线**：开发 JDK 25（LTS）/ 兼容 JDK 21+ / Spring Boot 4.0+ / Apache-2.0
> **日期**：2026-07-30

---

# 第一部分 · 项目概述

## 1.1 定位与愿景

璇玑（Xuanji）是基于 **Java + Spring Boot 4** 的跨平台聊天机器人开源框架，目标是成为 **JVM 生态的 Koishi/AstrBot**：开发者用 Spring 的方式编写机器人插件，同时通过跨语言桥接复用 Python/Node/Go 存量插件生态。

- **同在**：一次接入，多平台多实例运行（QQ 官方首发 → OneBot → 飞书/Discord）；
- **共生**：插件生态共建，JVM 原生插件 + 跨语言插件双轨，统一插件市场；
- **共鸣**：LLM 是可插拔能力而非核心负担——要 AI 装插件，不要 AI 零依赖。

> 注：GitHub 已有的 shibit-net/xuanji（TypeScript 桌面 AI 管家）与本项目仅同名，无关联。

## 1.2 核心决策一览（全部拍板）

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 技术基线 | 开发 JDK 25 LTS，兼容 21+；Spring Boot 4.0+；用足 Java/Spring 特性形成独有机制 |
| 2 | 平台节奏 | QQ 官方 Bot API 首发（抽象层同步就位）→ OneBot 作第二实现验证 → 飞书/Discord |
| 3 | LLM 定位 | 核心零 LLM 依赖；能力 SPI + 官方 LLM 插件 |
| 4 | 开源协议 | Apache-2.0（对 AstrBot AGPL 差异化卡位） |
| 5 | 控制台 v0.1 | 插件管理 + 配置表单 + 日志查看 + 首启向导 |
| 6 | 插件市场 | 控制台+内嵌库为客户端入口；v0.x GitHub 索引仓库 → v1 独立市场服务 |
| 7 | 插件双轨 | JVM（PF4J）原生轨 + XPBP（JSON-RPC sidecar）跨语言轨 |
| 8 | 存储 | 全量内嵌 H2（业务+日志分两个文件），不混 SQLite；两行 YAML 切 MySQL/PostgreSQL；数据按"框架域/平台域/bot 实例域"三级隔离 |
| 9 | 权限 | 五层模型：L0 主人/超管（凌驾平台）+ L1 统一角色枚举 + L2 平台原生透传 + L3 自定义 authority + L4 黑名单（一票否决）；群管动作抽象、身份组不抽象 |
| 10 | 命名规范 | 模块 `xuanji-*` / 框架表 `xuanji_*` / 平台表 `{平台}bot_*` / 插件表 `xplugin_{id}_*` |

---

# 第二部分 · 参考框架调研

## 2.1 概览对比

| 维度 | Koishi | AstrBot | ElainaBot v2（伊蕾娜） |
|---|---|---|---|
| 语言/运行时 | TypeScript / Node.js | Python 3.10+ | Python 3.11+（纯异步） |
| 规模 | 7k+ Star，3000+ 插件 | 35.2k+ Star，1000+ 插件，周活 20 万 | v1 已归档，v2 活跃 |
| 协议 | MIT | AGPL-v3 + EULA（商用受限） | MIT |
| 定位 | 跨平台机器人框架 | LLM Agent 聊天基础设施 | QQ 官方 Bot API 机器人 |
| 精髓 | Context + 副作用自动回收 | 协议归一化 + 洋葱流水线 | 装饰器两级处理链 |

## 2.2 三家精华（结论版）

- **Koishi**：Context 上下文自动收集并回收插件副作用（热重载根基）；监听器→中间件→指令三级处理；Session 全平台归一；Service 服务模型（能力挂载 Context，插件提供/消费）；统一 ORM（minato）+ 内置 user/channel 表；配置 Schema 即控制台表单；社区 currency 插件验证"共享能力插件"模式。
- **AstrBot**：18+ 平台协议归一化为统一事件；洋葱模型 9 阶段流水线（yield 前/后=前置/后置，可中断）；双层 Provider 配置（sources/models 分离）；插件与核心同构（可挂事件/LLM工具/Web路由/定时任务/KV）——生态飞轮之源；配置元数据驱动 UI；短板：AGPL、插件单进程无隔离、Python 性能天花板。
- **伊蕾娜**：装饰器两级处理链（@interceptor priority 阻断 → @handler priority/block/cooldown/owner_only）；富事件对象；插件可注册 HTTP 路由和面板页面（差异化能力）；GitHub 清单插件市场；配置 YAML 缺项自动补齐；短板（勿学）：正则当指令解析器、无角色 ACL、定时任务无抽象、存储碎片化。

## 2.3 概念 → Spring Boot 映射总表

| 参考框架概念 | 璇玑对应方案 |
|---|---|
| Koishi Context / 副作用回收 | 插件子 ApplicationContext，关闭即整体回收 |
| 装饰器自动注册 | `@XuanjiPlugin` + `@GroupMessage`/`@PrivateMessage`/`@MessageFilter` + BeanPostProcessor 扫描 |
| AstrBot 洋葱流水线 | `BotPipeline` 责任链，Ordered `PipelineStage`（pre/post/abort） |
| 统一 Session / AstrMessageEvent | `BotEvent` 统一事件模型（record） |
| 消息元素 Element | `MessageChain`：sealed `MessageElement` + record 元素 |
| 双层 Provider 配置 | `@ConfigurationProperties` 双层模型 |
| 配置 Schema → UI | configuration-processor 构建期生成元数据 → 控制台自动渲染 |
| 插件注册 Web 路由（伊蕾娜） | 插件子上下文 `@RestController` 动态注册到主 HandlerMapping |
| Service 服务模型（Koishi） | `ServiceRegistry` 能力注册表（provide/require） |
| 定时任务（伊蕾娜缺失） | 插件内直接 `@Scheduled` / `SchedulerService` 抽象 |
| 权限/冷却声明式 | `@RequireRole`/`@RateLimit` AOP 切面 |

---

# 第三部分 · 总体架构

## 3.1 六层架构

```
┌──────────────────────────────────────────────────────────────┐
│ 控制台 Console（插件管理/配置/日志/市场/模拟聊天/监控仪表盘）     │
├──────────────────────────────────────────────────────────────┤
│ 插件层 Plugin（JVM 原生：PF4J+子上下文 ｜ 跨语言：XPBP sidecar） │
├──────────────────────────────────────────────────────────────┤
│ 能力层 Capability（ServiceRegistry：LlmService/EconomyService   │
│              等 SPI + Function Calling + MCP 客户端）          │
├──────────────────────────────────────────────────────────────┤
│ 调度层 Dispatch（EventBus → BotPipeline → 指令/事件分发        │
│              幂等去重 · 限流 · @RequireRole 声明式横切）         │
├──────────────────────────────────────────────────────────────┤
│ 接入层 Adapter（QQ 官方 · OneBot · 飞书… ↔ BotEvent/MessageChain│
│              @HttpExchange 声明式客户端，配置即接入）            │
├──────────────────────────────────────────────────────────────┤
│ 基础设施 Infra（内嵌 H2 · 配置元数据 · 虚拟线程 · 日志/指标）     │
└──────────────────────────────────────────────────────────────┘
```

## 3.2 Maven 模块结构

```
xuanji/
├── xuanji-api/               # 纯接口与模型（零实现零 LLM 依赖）
│     #   BotEvent / MessageChain / BotAdapter / Bot / MessageSender
│     #   PipelineStage / @XuanjiPlugin / @GroupMessage / @PrivateMessage / @MessageFilter / @Arg
│     #   @RateLimit / @RequireRole / 能力 SPI（LlmService/EconomyService…）
├── xuanji-core/              # 运行时：EventBus、Pipeline、指令映射、ServiceRegistry、
│     #   BotManager、PF4J 插件加载、XPBP Runtime Manager、权限/限流/幂等
├── xuanji-adapter-qq/        # QQ 官方 Bot API（首发）
├── xuanji-adapter-onebot/    # OneBot v11（v0.2，抽象第二实现验证）
├── xuanji-console/           # 本地 Web 控制台
├── xuanji-starter/           # Spring Boot Starter，用户工程入口
├── xuanji-plugin-llm/        # 官方 LLM 插件
├── xuanji-plugin-economy/    # 官方经济插件（跨插件数据联动样板）
├── xuanji-bridge/            # XPBP 协议 + sdk-python / sdk-node / sdk-go
└── xuanji-market/            # 中央插件市场服务（v1 独立部署）
```

---

# 第四部分 · 核心抽象设计（P1 一次定齐）

> 铁律：**以下抽象在跑通 QQ 流程时就位，哪怕只有一个实现**。禁止"先硬编码 QQ 再抽象"。

## 4.1 统一事件模型 BotEvent

```java
// 全平台归一的事件模型（record）
public record BotEvent(
    String eventId,          // 全局唯一，幂等键
    EventType type,          // 标准事件类型（层级命名）
    Bot bot,                 // 接收事件的机器人实例
    XuanjiUser sender,       // 统一用户档案
    XuanjiGroup group,       // 群档案（私聊为 null）
    MessageChain message,    // 消息链（非消息事件为 null）
    String replyToMsgId,     // 被动回复引用的 msgId
    JsonNode platformData    // 平台原生数据透传（身份组等精细场景用）
) {}
```

标准事件类型层级（Koishi 命名规范）：`message/private`、`message/group`、`notice/member_join`、`notice/member_leave`、`request/group_invite`、`interaction/button`…… 平台事件由适配器映射，未覆盖的类型落入 `platformData` 透传。

## 4.2 MessageChain 消息链（sealed + record）

```java
public sealed interface MessageElement
    permits Text, At, Image, Face, Quote, Reply, Voice, Video, File, Markdown, Keyboard, Ark {}

// 适配器双向转换：pattern matching switch，编译器强制穷尽
Object toQqPayload(MessageElement e) {
    return switch (e) {
        case Text t     -> ...;
        case Markdown m -> ...;
        case Keyboard k -> ...;
        // 漏一个类型，编译报错
    };
}
```

`Markdown/Keyboard/Ark` 为 QQ 特有元素：其他平台适配器收到时按降级策略处理（转文本/忽略并告警）。

## 4.3 BotAdapter SPI 与 BotManager（多平台多实例）

```java
public interface BotAdapter {
    String platform();                              // "qq" / "onebot" / "feishu"
    Bot connect(BotConfig config);                  // 建立连接（WS/Webhook/轮询）
    void disconnect(Bot bot);
    BotEvent toEvent(JsonNode raw);                 // 平台报文 → 统一事件
    Object toPayload(MessageChain chain);           // 统一消息链 → 平台报文
}
```

- 每个 `xuanji.bots[]` 配置项 = 一个 `Bot` 实例（id = `{platform}:{appId}`），**QQ bot1 + QQ bot2 + OneBot bot3 可同时运行**；
- `BotManager`（现有 RobotRegistry 的升级）：统一管理所有实例的启停/健康检查/自动重连；每连接一个虚拟线程，单机万级长连接；
- 适配器 = 独立自动配置模块，`@ConditionalOnProperty` 激活，**加平台 = 加依赖 + 加 YAML，不改核心代码**。

## 4.4 MessageSender 与收发分离

```java
public interface MessageSender {
    SendReceipt reply(BotEvent event, MessageChain chain);   // 被动回复（自动带 msg_id）
    SendReceipt send(Target target, MessageChain chain);     // 主动发送（定时推送用）
}
```

- 发送走**每 bot 令牌桶 + 发送队列**，防触发平台 429；
- `SendReceipt` 携带平台消息 ID、耗时、失败原因（可重试）。

## 4.5 BotContext（ScopedValue 事件上下文）

```java
// 框架在事件处理入口绑定，插件任意位置取用，不污染方法签名
ScopedValue.where(BotContext.EVENT, event).run(() -> pipeline.proceed(event));

// 插件代码里：
var event = BotContext.current();   // 当前事件、bot、用户、群组
```

取代现有 ThreadLocal 方案：虚拟线程安全、随作用域自动回收、异步链可绑定传播。

## 4.6 能力 SPI（核心无 LLM 的解法）

```java
// xuanji-api 定义接口，核心只有注册表，实现全是插件
public interface LlmService { ChatResponse chat(ChatRequest req); }
public interface EconomyService {
    long earn(XuanjiUser user, long amount, String reason);
    long balance(XuanjiUser user);
    boolean transfer(XuanjiUser from, XuanjiUser to, long amount, String reason);
}

// 提供方（如 xuanji-plugin-llm）：registry.provide(LlmService.class, impl);
// 消费方：registry.require(LlmService.class);
//         未安装时启动期报清晰错误："插件 ai-translate 需要 LlmService，请安装 xuanji-plugin-llm"
```

| 场景 | 行为 |
|---|---|
| 纯群管/娱乐机器人 | 不装 LLM 插件，核心零 LLM 依赖 |
| AI 聊天 | 装 `xuanji-plugin-llm`（OpenAI 兼容主干，sources/models 双层配置，Agent loop + Function Calling，扫描所有插件的 `@LlmTool` 注册） |
| 第三方插件用 AI | 只依赖接口，实现可换（国产模型/本地模型插件） |

## 4.7 平台能力抽象清单（差异最大的三块）

| 抽象 | 解决的平台差异 |
|---|---|
| `GroupAdminAction` 群管动作 | 禁言/踢人/改名片/设管理员：QQ 身份组接口 vs OneBot set_group_ban 系列，统一为动作 API + 能力位声明（bot 不支持时返回明确错误） |
| `MediaService` 媒体 | QQ 的 file_info 两段式（先上传再引用）vs OneBot 文件路径直发，统一 upload/send 语义 |
| `ConversationSession` 会话等待 | ask 一条消息并 await 该用户下一条回复（带超时），多轮交互插件必需（Koishi prompt 同款） |

另有 `PluginContext`（插件可访问能力的门面+权限收敛点）、`SchedulerService`（插件与 sidecar 共用的定时任务抽象）。

# 第五部分 · 调度与指令系统

## 5.1 BotPipeline（洋葱模型责任链）

```
Adapter → BotEvent → 幂等去重 → EventBus → Pipeline → 响应
  ①唤醒检查（@机器人/前缀/私聊） ②黑白名单 ③限流（@RateLimit）
  ④内容安全 ⑤预处理（解析/档案影子同步） ⑥分发（插件事件 + @Command）
  ⑦结果装饰（分段/转图/TTS） ⑧发送（限流队列）
```

- 每阶段实现 `PipelineStage`（Ordered）：`preHandle` / `postHandle` / `abort`；
- 插件可注册自定义阶段插入任意位置（AstrBot 洋葱模型同款能力）；
- 横切逻辑（如 ignore-bot-messages）只存在于 Pipeline，**禁止写进业务 handler**。

## 5.2 指令与消息注解系统（消灭 switch(content)）

指令与事件用「场景注解 + 过滤注解」组合表达，比单一 `@Command` 更易覆盖群聊/私聊/系统事件多场景：

```java
@XuanjiPlugin(id = "sign", name = "签到", version = "1.0.0", rateLimit = 5)
public class SignPlugin {

    @GroupMessage
    @MessageFilter(cmd = "签到|打卡", roles = {"owner","admin"})
    public String sign(BotEvent event, @Arg(value = "补签日期", required = false) LocalDate date) {
        // 返回值自动转为 Text 元素回复；抛异常走统一错误提示
    }

    @GroupMessage(priority = 100, block = false)
    public void onAnyGroupMessage(BotEvent event) { ... }
}
```

- 场景注解：`@GroupMessage` / `@PrivateMessage` 标记群聊/私聊消息处理方法；`@GroupEvent` / `@PrivateEvent` 标记系统事件（加群/退群等）；
- 触发与过滤：`@MessageFilter(cmd="签到|打卡")` 支持正则触发词、`roles` 限制角色、`startWith`/`endWith` 前缀后缀、`groups`/`senders` 限定范围、`invert` 反转；
- 冷却（限流）：当前为**插件级** `@XuanjiPlugin(rateLimit = 5)`（同一用户 5 秒内仅触发一次），per-command 冷却为后续演进；
- `@Arg` 结构化参数绑定（类型转换/必填/默认值/错误提示），**不学伊蕾娜正则捕获组**；
- 声明式横切：`@RateLimit`、`@GroupOnly`、`@RequireRole` 由 AOP/Stage 统一实现；
- 指令冲突仲裁：`priority` + `block`，与拦截器链分离的两级模型（借鉴伊蕾娜，补其无 ACL 短板）。

> 注：早期设计草案用单一 `@Command` 注解，实现时拆为「场景注解（@GroupMessage/@PrivateMessage/@GroupEvent/@PrivateEvent）+ 过滤注解 @MessageFilter」，更贴合多场景且更易扩展，故以当前实现为准。

## 5.3 可靠投递三件套

1. **事件幂等**：`xuanji_event_dedup`（eventId + platform，24h TTL），webhook 重推/WS 重连不重复处理；
2. **发送限流**：每 bot 令牌桶 + 队列 + 失败重试（指数退避）；
3. **优雅停机**：拒收新事件 → 在途处理完 → WS 按平台规范关闭。

---

# 第六部分 · 插件系统（双轨）

## 6.1 双轨模型

| 轨道 | 技术 | 定位 | 隔离级别 |
|---|---|---|---|
| **JVM 原生轨（首选）** | PF4J + 独立 ClassLoader + 插件子 ApplicationContext | 高频低延迟（消息拦截/实时风控/指令类） | 类加载器隔离 |
| **跨语言轨（生态复用）** | XPBP 桥接 + Python/Node/Go SDK sidecar | 低频重逻辑（AI 总结/定时任务/查询类） | 进程隔离（更强） |

- JVM 轨热重载只发生在安装/升级时（子上下文仅插件自身 Bean，亚秒级），**不在消息热路径，运行期零开销**；插件内 `@RestController` 可动态注册 REST 路由到控制台（伊蕾娜同款差异化能力）；
- 卸载 = 关闭子上下文 / 终止 sidecar → 副作用全回收（Koishi 思路）。

## 6.2 XPBP 跨语言桥接协议

| 维度 | 设计 |
|---|---|
| 传输 | stdio（本地默认，核心拉起子进程）/ WebSocket（远程、容器） |
| 格式 | JSON-RPC 2.0（与 MCP 同构）；预留 MessagePack/Protobuf 编码协商 |
| 生命周期 | `plugin.init/ready/shutdown/heartbeat`；崩溃检测 + 指数退避自动重启 |
| 注册 | `register.command`（指令+参数 schema+权限+cooldown，**匹配规则下推核心**，交互回调免全程往返）/ `register.interceptor` / `register.llmTool` |
| 事件 | `event.message/notice/request`（BotEvent JSON 形态，跨语言一致） |
| 动作 | `action.sendMessage/recall/...`（**语义对齐 OneBot API**，存量作者零陌生感） |
| 能力 | `capability.invoke`（sidecar 同样可 require LlmService 等能力） |

**性能策略**：batch 批量下发；named pipe / Unix domain socket 替代 stdio；单连接约 1k–3k QPS（KB 级 JSON，估算），批量+多 sidecar 可至 1–5 万/s；高频场景引导回 JVM 轨（纯内存 10⁵/s 级）。P0 阶段出实测基准报告。

**三个官方 SDK**（API 风格贴近各生态习惯）：
- `xuanji-sdk-python`（v0.3，装饰器风格贴近 NoneBot/AstrBot，存量最大优先）；
- `xuanji-sdk-node`（v0.4，Context 风格贴近 Koishi，内置 koishi-compat 表层）；
- `xuanji-sdk-go`（v0.5）。

**存量插件迁移三层承诺（不吹 100% 兼容）**：L1 纯逻辑插件改 import 即跑；L2 常见 API 表层兼容（koishi-compat/nonebot-compat）；L3 深度依赖原框架内部机制的需人工改造（提供迁移指南）。

## 6.3 插件包规范与权限声明

```
sign-plugin.zip
├── plugin.json          # id(group:pluginId 全局唯一) / name / version / author
│                        # xuanjiVersion: ">=0.3 <1.0"（安装时校验）
│                        # runtime: jvm | python | node | go
│                        # permissions: [network, filesystem, proactive-message]
│                        # dependencies: 依赖的插件与能力（如 EconomyService）
├── sign-plugin.jar      # 或 sidecar 包（含运行时入口）
└── config-schema.json   # 配置元数据（构建期自动生成 → 控制台表单）
```

---

# 第七部分 · 数据持久化

## 7.1 存储选型（定稿）

- **全量内嵌 H2**，不混用 SQLite（两引擎=两套文件锁/备份/方言，SQLite 还带 native 库）；
- **两个 H2 文件分离膨胀**：`data/xuanji.mv.db`（业务）+ `data/xuanji-log.mv.db`（消息流水/运行日志数据）；
- 运行日志本体走 Logback 滚动文件（`logs/`）；
- **扩展路径**：`spring.datasource` 两行 YAML 切 MySQL/PostgreSQL（Spring 标准，Java 用户零学习成本）；控制台监测超阈值主动引导迁移并一键搬数据；
- 插件如有独立外部 DB 需求，可声明自己的 DataSource（Spring 多数据源标准做法）。

## 7.2 数据层级划分（三级数据域，物理隔离）

**场景**：A 机器人同时在 A 群、B 群；B 机器人也在 A 群。同一个物理群，在两个 bot 视角下是**两份独立档案**——每个 bot 在群里的身份、权限、群名片、消息流水都不同。因此数据按"框架 → 平台 → bot 实例"三级命名空间划分，**不塞进一张大表**：

```
框架域（全局唯一一份，所有 bot 共享）
  xuanji_user                       统一用户档案（内部 ID/昵称/framework_role/authority）
  xuanji_user_binding               跨平台账号绑定（platform+平台账号 → 内部 userId）
  xuanji_plugin_kv                  插件 KV（plugin_id+key+json；bot 相关数据 key 带 {botKey}: 前缀）
  xuanji_event_dedup                eventId 幂等去重（24h TTL）
  xuanji_blacklist                  黑名单（三级 scope，见第八部分）

平台域（每平台一份，该平台所有 bot 共享）
  qqbot_token                       QQ access_token 缓存
  onebot_meta                       OneBot 端信息

bot 实例域（每 bot 实例一组表，注册时自动建表，实例间完全隔离）
  xuanji_qqbot_a_group              A 机器人的群档案
  xuanji_qqbot_a_group_member       A 机器人的群成员（含平台角色/群名片/入群时间）
  xuanji_qqbot_b_group              B 机器人的群档案（与 A 隔离，同一物理群各存一份）
  xlog_qqbot_a_message（log 库）     A 机器人的消息流水（默认元数据，全文可选）
```

**关键机制**：
- `botKey`：bot 在 YAML 中的配置别名（如 `qqbot_a`），缺省取 appId；实例 ID 仍是 `{platform}:{appId}`；
- bot 实例表**不走 JPA 静态实体**：由框架"档案服务"在 BotManager 注册新 bot 时**按模板自动 DDL 建表**；插件通过 `ProfileService.forBot(bot).groups() / .members()` API 访问，屏蔽动态表名，禁止插件手拼表名；
- 事件流入时影子同步**只写当前 bot 的实例表**（A bot 在 A 群收到的消息只进 `xuanji_qqbot_a_*`，B bot 收到时才写它自己的）；
- 删除 bot 实例 → 控制台提示是否 drop 该实例整组表（与插件卸载清表同体验）；
- 跨 bot 全局统计（控制台总览页）走 UNION 查询，不在消息热路径；
- 插件自有 JPA 实体表仍是静态 `xplugin_{id}_*`，不受动态分表影响；插件若需按 bot 隔离数据，用 KV key 前缀或实体加 botKey 列。

## 7.3 插件数据三种姿势

```java
store.put(userId, "lastSignDate", date);        // L0 KV：80% 场景，零建表
@Entity class Account { ... }                    // L1 JPA：表名自动 xplugin_bank_*，卸载提示清表
economy.earn(user, 50, "每日签到");               // L2 能力插件：跨插件共享数据
```

## 7.4 跨插件数据联动（签到 × 银行）

1. **共享能力插件（最佳实践）**：经济数据归 `xuanji-plugin-economy` 持有，签到 `earn()`、银行 `balance()`，零耦合天然一致，自带 reason 记账流水可对账回滚（Koishi currency 插件验证的模式）；
2. **领域事件（松耦合）**：签到发 `UserSignedInEvent`，银行监听后按自有规则处理（插件事件命名空间 `pluginId/eventName`）；
3. **直接读库（不推荐）**：技术可行，文档反对，仅逃生舱。

## 7.5 可靠性与备份

- H2：ACID + MVStore 事务日志 + 崩溃恢复，重启不丢数据；单进程文件锁（多实例必须外置库）；单机文件（GB 级/外部直连需求时迁移）；
- 三道保险：定时自动快照（默认每日/留 7 份）、控制台一键导出/导入 zip、超阈值迁移引导；
- 同类验证：AstrBot SQLite（20 万+ 周活）、Metabase 默认 H2。

---

# 第八部分 · 权限体系

## 8.1 各平台权限差异

| 平台 | 权限模型 |
|---|---|
| QQ 官方（群） | 群主 / 管理员 / 普通成员（频道另有身份组 roles 体系） |
| OneBot | owner / admin / member |
| 飞书 | 群主 / 管理员 / 成员 |
| Discord | 细粒度 roles + 权限位 |

## 8.2 五层权限模型（抽象什么、不抽象什么）

```
L0 框架特权角色（凌驾所有平台角色之上）
   BOT_MASTER  机器人主人（部署者本人，全权限，唯一/极少数）
   SUPER_ADMIN 机器人超管（主人任命，管理类权限）
   存于 xuanji_user.framework_role；跨平台、跨 bot、跨群生效——
   即使在某群里只是普通成员，主人/超管依然拥有最高权限

L1 统一角色枚举（平台角色映射，80% 插件只用这层）
   BotRole { OWNER, ADMIN, MEMBER } ← 适配器负责平台角色映射
   用法：@MessageFilter(cmd = "...", roles = {"owner","admin"})（角色过滤在 @MessageFilter.roles）

L2 平台原生透传（精细场景）
   身份组/权限位不抽象（平台语义差异过大），保留在
   BotEvent.platformData 中，插件按需读取

L3 框架自定义 authority（插件权限点，跨平台生效）
   xuanji_user.authority 数值权限（Koishi 同款）：
   bot 主自定义"签到管理员"等插件级角色，与平台角色正交
   PermissionService.check(user, "sign.manage") 综合裁决

L4 黑名单（负权限，一票否决，最高优先级）
   三级作用域：框架级（全 bot 生效）/ bot 实例级 / 群级
   两类对象：用户黑名单 + 群黑名单（整群禁用）
   在 Pipeline ②黑白名单阶段拦截，早于一切权限判定
```

**裁决顺序**：L4 黑名单（命中即拒，直接丢弃事件）→ L0 特权（主人/超管的管理操作直接放行）→ L1 平台角色 → L3 权限点。

**结论：群管动作抽象（`GroupAdminAction`），身份组不抽象；主人/超管/黑名单是框架级概念，不属于任何平台。**

## 8.3 群管动作 + 能力位

```java
public interface GroupAdminAction {
    void mute(XuanjiGroup group, XuanjiUser target, Duration duration);
    void kick(XuanjiGroup group, XuanjiUser target);
    void setCard(XuanjiGroup group, XuanjiUser target, String card);
    void setAdmin(XuanjiGroup group, XuanjiUser target, boolean enable);
}
// 适配器声明能力位：qq 支持 setAdmin，onebot 全支持，某平台不支持时
// 调用返回 UnsupportedActionException（明确错误，不静默失败）
```

## 8.4 黑名单（xuanji_blacklist）

| 字段 | 说明 |
|---|---|
| `scope` | `framework`（全 bot）/ `bot:{platform}:{appId}` / `group:{botKey}:{groupId}` |
| `target_type` | `user`（内部 userId）/ `group`（群号，整群禁用） |
| `target_id` | 目标 ID |
| `reason` / `expires_at` | 原因 / 过期时间（空=永久） |
| `created_by` | 操作人（主人/超管的 userId） |

- 内置管理指令：`/黑名单 添加 @用户 [期限] [原因]`、`/黑名单 移除`、`/黑名单 列表`（仅 L0 角色可用）；
- 控制台提供黑名单管理页（三级作用域筛选、到期自动解除）；
- 命中黑名单的事件在 Pipeline ②阶段直接丢弃并记 `xlog`（不计入插件处理指标）；
- 主人/超管免黑名单（L0 不可被拉黑，防止误锁死）。

# 第九部分 · 插件市场

## 9.1 "共用一个市场"的三要素

1. **插件坐标全局唯一**：`group:pluginId` 命名空间，官方市场审核占用，防重名；
2. **索引协议开放**：索引 JSON Schema 公开，可自建镜像市场，控制台支持多市场源（默认官方）；
3. **兼容性与信任**：`xuanjiVersion` 安装校验；权限声明确认制；官方市场 GitHub 实名上传 + 自动静态扫描 + 签名。

## 9.2 两期建设

| 期 | 形态 | 理由 |
|---|---|---|
| v0.x | GitHub 索引仓库（plugins.json + PR 收录 + Releases 分发） | 零运维，三家验证过的路径 |
| v1 | 独立市场服务（Spring Boot + PostgreSQL）：OAuth、上传 API/CI、扫描、版本、统计、评分 | 插件量 >100 后索引仓库瓶颈 |

## 9.3 安装链路（控制台闭环）

```
浏览 → 安装 → 校验 xuanjiVersion → 权限声明确认 → 下载 → 哈希/签名校验
→ 按 runtime 分流：jvm → PF4J+子上下文热生效；python → 拉起 sidecar → init → register
卸载：关闭子上下文 / 终止 sidecar → 副作用全回收
```

---

# 第十部分 · 控制台与可观测性

## 10.1 控制台

- **v0.1（拍板范围）**：插件管理（JVM+跨语言统一视图，显示 runtime 与运行状态）、配置表单（元数据自动渲染、热生效）、日志查看（tail+级别过滤）、**首启向导**（管理员密码 → 表单填平台凭证 → 引导装插件，5 分钟上线）；
- **v0.2+**：市场浏览、消息模拟器（与事件录播设施同源）、监控仪表盘；
- **本地存储**：内嵌 H2 开箱即用，控制台数据（消息流水、插件状态、定时任务记录）直接查库。

## 10.2 日志系统

- **双输出**：人读（console，彩色分级）+ 机读（`logs/xuanji.log` JSON 结构化，logstash 编码）；
- **MDC 全链路串联**：每条日志带 `traceId / eventId / botId / pluginId`，控制台可按 traceId 一次拉出"某条消息从接收到回复"的全部日志；
- **分级文件**：`xuanji.log`（全量）/ `xuanji-error.log`（仅错误）/ 插件独立日志（含 sidecar stdout 收集）；
- 滚动策略：按天 + 单文件 100MB 上限，保留 14 天（控制台可改）。

## 10.3 指标体系（Micrometer，第一天埋点）

**消息全链路耗时（用户要求的核心指标）**：

```
适配器收到事件 t0 → 进入 Pipeline t1 → 开始处理 t2 → 处理完成 t3 → 发送返回 t4
xuanji.message.receive_ms   = t1-t0   入队/解析耗时
xuanji.message.handle_ms    = t3-t2   业务处理耗时（按 pluginId/command 分标签）
xuanji.message.send_ms      = t4-t3   平台发送耗时
xuanji.message.e2e_ms       = t4-t0   端到端总耗时
```

- 全部以 histogram 上报，控制台展示 **P50 / P95 / P99**（tags：platform / adapter / plugin / command）；
- 业务指标：事件速率、指令调用量 TopN、发送成功率、429/失败计数、插件异常数（按 pluginId）；
- bot 指标：在线状态、WS 重连次数、消息队列积压。

## 10.4 系统性能监控（控制台仪表盘）

基于 Spring Actuator + Micrometer 内置指标，开箱展示：

| 类别 | 指标 |
|---|---|
| CPU | 核数、系统负载、进程 CPU 使用率 |
| 内存 | JVM 堆/非堆/直接内存、GC 次数与耗时、虚拟线程数 |
| 磁盘 | 磁盘总量/剩余、`data/` 与 `logs/` 目录占用（超阈值告警并引导清理/迁移） |
| 运行时 |  uptime、线程状态、H2 连接池、慢事件 TopN |

- OpenTelemetry 导出可选（`xuanji.otel.enabled`），接入企业现有观测体系。

---

# 第十一部分 · 工程规范

## 11.1 命名规范（前缀体系）

| 级别 | 前缀/约定 | 示例 |
|---|---|---|
| Maven 模块 / Java 包 | `xuanji-*` / `dev.xuanji.*` | `xuanji-api`、`dev.xuanji.adapter.qq` |
| 框架核心表 | `xuanji_*` | `xuanji_user`、`xuanji_event_log` |
| 平台适配器表 | `{平台}bot_*` | `qqbot_token`、`onebot_friend` |
| bot 实例表（按实例隔离） | `xuanji_{平台}bot_{botKey}_*` | `xuanji_qqbot_a_group`、`xuanji_qqbot_a_group_member` |
| 消息流水表（log 库） | `xlog_{平台}bot_{botKey}_*` | `xlog_qqbot_a_message` |
| 插件表 | `xplugin_{pluginId}_*` | `xplugin_bank_account` |
| 配置前缀 | `xuanji.*` / `xuanji.adapter.qq.*` | — |
| 事件命名空间 | 平台名 / `xuanji/*` | `qq/message`、`xuanji/plugin_loaded` |
| Bot 实例 ID | `{platform}:{appId}` | `qq:102000xxx` |
| 数据/日志文件 | `data/xuanji*.mv.db`、`logs/xuanji*.log` | — |
| 指标名 | `xuanji.*` | `xuanji.message.e2e_ms` |
| traceId 格式 | `xj-{platform}-{毫秒ts}-{seq}` | `xj-qq-1753872000123-0042` |
| 插件 REST 路由 | `/x/{pluginId}/**`（保留 `/xuanji/api/**` 给框架） | — |

## 11.2 工程约定（八条）

1. JSON 库统一 Jackson，禁用 org.json 混用；
2. 事件幂等去重（见 5.3）；
3. 发送限流队列（见 4.4）；
4. 凭证支持 `${ENV}` 占位，控制台脱敏显示，不明文落库；
5. 优雅停机（见 5.3）；
6. 事件录播/模拟器：handler 单测与控制台"模拟聊天"共用事件回放设施；
7. 指标第一天埋点（见 10.3）；
8. CI 双版本构建（JDK 21 + 25），插件脚手架 Maven Archetype 一键生成。

---

# 第十二部分 · 版本路线图

| 版本 | 内容 |
|---|---|
| **v0.1（MVP）** | api+core（四抽象就位：BotEvent/MessageChain/BotAdapter/MessageSender）+ **QQ 官方适配器** + JVM 插件（PF4J）+ 指令系统 + 内嵌 H2 + 控制台精简版 + 首启向导 |
| **v0.2** | **OneBot 适配器**（抽象第二实现验证）+ 消息模拟器 + 事件录播 + GitHub 索引市场 + 控制台市场页 |
| **v0.3** | **XPBP + xuanji-sdk-python** + xuanji-plugin-economy（签到/银行打样联动） |
| **v0.4** | xuanji-sdk-node（koishi-compat）+ xuanji-plugin-llm 官方插件 + 权限体系完善 + 监控仪表盘 |
| **v0.5** | xuanji-sdk-go + OTel 导出 + AOT 原生镜像 |
| **v1.0** | 独立市场服务 + 插件签名体系 + 文档/生态运营完善 |

---

# 第十三部分 · 风险与待办

## 13.1 风险清单

| 风险 | 等级 | 缓解 |
|---|---|---|
| 跨语言兼容表层做不满 | 中 | 只承诺 L1/L2；SDK 文档写清边界 |
| QQ 官方政策变动 | 中 | OneBot 兜底；接入层隔离影响 |
| 市场冷启动难 | 高 | 官方打样 4 插件（llm/群管/签到/economy）+ 跨语言桥接存量 + Apache-2.0 企业位 |
| JDK21+ 用户环境滞后 | 低 | Docker 一键；v0.5 AOT 原生镜像免 JDK |

## 13.2 待办

- [x] 拆事项（已创建，指派 owner）：核心骨架 POC `r8Htgr` ／ 控制台精简版 `rdKIjR` ／ 插件规范+索引市场 `rJHJZR` ／ XPBP+Python SDK `rNBk4w`
- [ ] 将本文档上传项目资料库
- [ ] 邀请研发同学后进 PRD 评审，事项转派

---

# 附录 A · 现有代码改造映射（v0.1 单体 → 多模块）

> 对象：`D:\Program\bot\xuanji`（Java 17 + SB 4.0.6，54 类，包名 `com.qunxing.qq_bot_xuanji`）
> 原则：**搬家 + 抽象，不是重写**。现有 QQ 接入层是未来 `xuanji-adapter-qq` 的核心资产。

| 现有类 | 新位置 | 改造动作 |
|---|---|---|
| `QqApiService` + `AccessTokenService` | xuanji-adapter-qq | 原样保留（鉴权/401重试/限流/trace-ID 已是生产级）；可选重构 @HttpExchange |
| `MessageSender` | 拆两半 | 发送实现 → adapter-qq 实现统一接口；ThreadLocal → ScopedValue `BotContext` |
| `EventDispatcher` | xuanji-core Dispatch | 字符串路由 → BotEvent 类型路由；接入 Pipeline |
| `@EventMapping`/`EventHandler` | xuanji-api | 升级 `@GroupMessage`/`@PrivateMessage`/`@MessageFilter`；`handle(JSONObject)` → `handle(BotEvent)` |
| 9 个测试命令 handler | 示例插件 | `switch(content)` → 每命令一个 `@GroupMessage`+`@MessageFilter` 方法 |
| `RobotRegistry` | xuanji-core BotManager | key 从 robotId → `{platform}:{appId}`；加生命周期管理 |
| `XuanjiRobotProperties` | xuanji-api BotsProperties | 迁标准 `application.yml` 的 `xuanji.bots` 列表绑定 |
| `MarkdownBuilder` 等 | adapter-qq 构建器 | 保留，长期包装为 MessageChain 的 QQ 扩展元素 |
| `GroupMessageEvent` 等 DTO | adapter-qq 解析器 | handler 手动解析 → 适配器统一解析为 BotEvent |

**落地顺序**：P0 基线（JDK 25 + 多模块拆分 + 包名迁 `dev.xuanji.*`）→ P1 抽象先行（四抽象接口）→ P2 搬家（core/registry/utils 入 adapter-qq）→ P3 调度升级（Pipeline+@GroupMessage/@MessageFilter 接管）→ P4 持久化。
