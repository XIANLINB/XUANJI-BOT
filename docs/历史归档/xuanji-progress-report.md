# 璇玑框架 · 开发进度与后续计划
> ⚠️ **历史进度报告（2026-07-31）**：当时进度快照，已被『项目总结报告』取代，内容可能过时。


> 日期：2026-07-31 ｜ 基于 v2.0 设计文档 + 代码审计
> 代码：D:\Program\bot\xuanji ｜ 118 个 Java 文件 ｜ 6 个模块 ｜ 7 个 pom

---

## 一、当前架构

```
xuanji-api        ← 框架抽象：注解/接口/DTO/消息模型/异常    (41 类，零平台代码)
    ↑
xuanji-sdk        ← 插件 SDK：Bot 抽象 + 事件封装 + 消息构建   (8 类，平台无关)
    ↑          ↑
xuanji-core  ←  xuanji-adapter-qq                           (31 + 34 类)
(调度/存储/插件)  (QQ API 全量/事件处理/DTO/错误码/Bot 实现)
    ↑          ↑
xuanji-starter     (装配 + Web + Actuator + H2)
xuanji-plugin-demo (PF4J 演示插件)
```

**依赖方向**：adapter-qq → core → sdk → api，core 不再反向依赖 adapter-qq ✅

## 二、按 P 阶段进度

| 阶段 | 状态 | 完成度 | 备注 |
|---|---|---|---|
| P0 工程基线 | ✅ 完成 | 100% | JDK25 + 多模块 + 包名迁移 |
| P1 核心抽象 | ✅ 完成 | 85% | 注解+接口全在；缺少 LlmTool/RequireRole 实现 |
| P2 QQ 适配器 | ✅ 完成 | 90% | WS/Webhook 双模式 + 64 API + 184 错误码 |
| P3 调度指令 | ✅ 完成 | 90% | Pipeline 四阶段 + @Command/@Arg/@RequireRole/@RateLimit 全生效 |
| P4 持久化 | ✅ 完成 | 95% | H2 双库 + 13 表 + 软删除 + 自动同步 + 统计查询 |
| P5 权限体系 | ✅ 完成 | 90% | L4 黑名单 + L0 主人/超管裁决链已接入 GroupMessageHandler |
| P6 控制台 v0.1 | ⚠️ | 40% | 10 API（状态/事件/日志/黑名单/超管），缺前端管理页 |
| P7 JVM 插件化 | ✅ 完成 | 90% | PF4J + Spring 子容器 + 热加载 + Demo 20+ 命令 |
| P8 指标日志 | ✅ 完成 | 95% | MDC + Micrometer Timer + Actuator + logback-spring.xml JSON |
| P9 OneBot | ❌ 未开始 | 0% | v0.2 阶段 |
| P10 插件市场 | ❌ 未开始 | 0% | v0.2 阶段 |

## 三、已完成清单

### 3.1 基础设施
- [x] 多模块 Maven 拆分（7 模块，依赖清晰无循环）
- [x] 包名统一 `dev.xuanji.*`，QQ 代码全部归位 adapter-qq
- [x] 内嵌 H2 双文件（业务 + 日志分离）
- [x] Jackson 统一（无 org.json）
- [x] 优雅停机 + 虚拟线程
- [x] BotContext ScopedValue（JDK21+，非 ThreadLocal）
- [x] logback-spring.xml（文件 JSON 格式 + MDC 字段）

### 3.2 QQ 适配器
- [x] AccessToken 管理（缓存 + 401 自动重试）
- [x] QqApiService 统一 HTTP 层（GET/POST/PUT/PATCH/DELETE + 限流/超时）
- [x] WebSocket 双模式（健康检查 + 自动重连）+ Webhook（Ed25519 签名）
- [x] 消息收发全量（文本/Markdown/Ark/键盘/图片/语音/视频/文件/卡片）
- [x] 消息撤回（群/单聊）
- [x] 群信息查询 + Bot 状态
- [x] 富媒体上传（群/单聊，60s 超时）
- [x] 184 个 QQ 错误码枚举（名称/描述/排查建议/可重试/限流/权限/安全打击）
- [x] QQ DTO 全部移入 adapter-qq/dto

### 3.3 事件与调度
- [x] 事件注解路由（@EventMapping + EventDispatcher）
- [x] 群消息：指令匹配 + @Arg 参数绑定 + @MessageFilter 过滤
- [x] 单聊消息：指令匹配
- [x] 群系统事件：入群/退群/成员进退/通知开关（自动写入 DB）
- [x] 单聊系统事件：好友添加/删除/通知开关（自动写入 DB）
- [x] Pipeline 四阶段（预处理/分发/装饰/响应）
- [x] MDC 全链路（traceId/eventId/botId/pluginId）

### 3.4 数据与存储
- [x] 三级数据域：框架域（全局）→ 平台域（QQ 通用）→ bot 实例域
- [x] 13 张表自动 DDL（CREATE IF NOT EXISTS）
- [x] 软删除（is_deleted）：退群/删好友不真删，计数排除
- [x] 自动同步：收到群消息 → upsert 群+成员（含 role）；收到单聊 → upsert 用户
- [x] 事件日志：全事件写入 xuanji_qqbot_event（含 group_id）
- [x] 统计查询：总群/好友数、今日新增/退群/好友、每群今日进出人数

### 3.5 插件系统
- [x] PF4J 插件加载（plugins/ 目录扫描 + 独立 ClassLoader）
- [x] @XuanjiPlugin + @GroupMessage/@PrivateMessage + @Command + @Arg + @RateLimit
- [x] 参数注入：Bot/GroupMessageEvent/@Arg/基本类型
- [x] Bot SDK：reply/send/撤回/统计/媒体上传
- [x] Demo 插件（20+ 命令覆盖文本/MD/Ark/键盘/媒体/权限/定时/事件）

### 3.6 控制台与监控
- [x] ConsoleApiController（表浏览/数据查询/系统状态/日志文件）
- [x] 消息流水记录（IN/OUT 最近 200 条）
- [x] Actuator 健康检查 + Micrometer 指标
- [x] 消息四段计时（handle/e2e Timer）
- [x] 系统状态（CPU/内存/线程/虚拟线程/DB 大小）

## 四、待完成（按优先级）

### 🔴 v0.1 收尾（阻断验收）

| # | 任务 | 说明 |
|---|---|---|
| 1 | **P5 权限体系** | L4→L0→L1→L3 裁决链 + 黑名单指令 + @RequireRole AOP |
| 2 | **P6 控制台完善** | 插件管理页（列表/启停/卸载）、配置表单、日志查看 |
| 3 | **logback-spring.xml** | JSON 结构化日志 + MDC 字段输出 |
| 4 | **BotContext** | ScopedValue 替代 ThreadLocal（JDK 21+） |
| 5 | **事件录播** | 录播文件用于 handler 单测 |

### 🟡 v0.2 多平台验证

| # | 任务 | 说明 |
|---|---|---|
| 6 | **P9 OneBot 适配器** | 正/反向 WS + HTTP，验证抽象 |
| 7 | **P10 插件规范** | plugin.json + 打包工具 + 控制台市场页 |
| 8 | **消息模拟器** | 控制台发假消息调试插件 |

### 🟢 v0.3+ 后续

| # | 任务 | 说明 |
|---|---|---|
| 9 | **P11 XPBP + Python SDK** | JSON-RPC sidecar 跨语言插件 |
| 10 | **P12 economy + 签到** | 跨插件联动打样 |
| 11 | **P13 监控仪表盘** | P95/成功率/系统资源可视化 |

## 五、风险与遗留

| 风险 | 影响 | 对策 |
|---|---|---|
| adapter-qq → core 单向依赖 | BotDataQuery/BotInfoSync 在 adapter 中引用 core 类 | ✅ 已解决（adapter→core 单向） |
| H2 并发写入 | 单写多读，高并发需切 MySQL | 设计文档预留：两行 YAML 切换 |
| 消息模拟器缺失 | 无法离线调试插件 | v0.2 做，与事件录播同源 |
| 频道管理 API 未接入 | guild/channel/role 全套未实现 | 按需求逐步补，目前群机器人足够 |
