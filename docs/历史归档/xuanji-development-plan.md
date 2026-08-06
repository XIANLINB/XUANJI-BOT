# 璇玑机器人框架 · 分阶段开发文档
> ⚠️ **历史规划文档（2026-07-30）**：P0-P15 分阶段计划。P0-P8 及部分 v0.2 已全部完成，本档仅作规划过程参考。


> 配套文档：《总体设计文档 v2.0》｜ 日期：2026-07-30
> 原则：**抽象先行、每阶段可独立验收、先 QQ 后多平台**
> 对象代码库：`D:\Program\bot\xuanji`（Java 17 + SB 4.0.6 单体 → 目标多模块框架）

---

## 阶段总览

| 阶段 | 内容 | 对应版本 | 对应事项 | 前置 |
|---|---|---|---|---|
| P0 | 工程基线：JDK 25 + 多模块拆分 + 包名迁移 | v0.1 | r8Htgr | — |
| P1 | 核心抽象：xuanji-api 全部接口与注解 | v0.1 | r8Htgr | P0 |
| P2 | QQ 适配器搬家：实现四抽象 | v0.1 | r8Htgr | P1 |
| P3 | 调度与指令：Pipeline + @GroupMessage/@MessageFilter + 可靠投递 | v0.1 | r8Htgr | P2 |
| P4 | 持久化：内嵌 H2 + 三级数据域 | v0.1 | r8Htgr | P3 |
| P5 | 权限体系：五层模型 + 黑名单 | v0.1 | r8Htgr | P4 |
| P6 | 控制台 v0.1 + 首启向导 | v0.1 | rdKIjR | P4 |
| P7 | JVM 插件化：PF4J + 官方打样插件 | v0.1 | r8Htgr | P3 |
| P8 | 指标与日志：MDC 链路 + 四段计时 + 仪表盘 | v0.1 | rdKIjR | P3 |
| P9 | OneBot 适配器（抽象第二实现验证） | v0.2 | — | P3 |
| P10 | 插件规范 + GitHub 索引市场 + 消息模拟器 | v0.2 | rJHJZR | P7 |
| P11 | XPBP 协议 + xuanji-sdk-python | v0.3 | rNBk4w | P7 |
| P12 | economy + 签到插件（跨插件联动打样） | v0.3 | — | P7 |
| P13 | Node SDK + llm 插件 + 监控仪表盘 + 权限完善 | v0.4 | — | P11 |
| P14 | Go SDK + OTel + AOT 原生镜像 | v0.5 | — | P13 |
| P15 | 独立市场服务 + 签名体系 | v1.0 | — | P10 |

---

# 第一阶段 · v0.1 MVP（P0–P8）

> 目标：**QQ 官方机器人全流程跑通**——用户下载 jar，首启向导填凭证，装插件，群里 @指令 有回复。
> 铁律：P1 抽象未定稿前，禁止开始 P2。

## P0 工程基线

| # | 任务 | 产出物 |
|---|---|---|
| 0.1 | 安装 JDK 25，更新 Maven Wrapper / toolchain | `.mvn/`、`maven-toolchains.xml` |
| 0.2 | 单模块拆多模块：xuanji-parent（pom）+ api/core/adapter-qq/starter | 4 个模块骨架 pom |
| 0.3 | 包名迁移 `com.qunxing.qq_bot_xuanji` → `dev.xuanji.*` | 全量 import 替换 |
| 0.4 | JSON 统一 Jackson，移除 org.json 依赖 | pom 清理 + 代码替换 |
| 0.5 | CI 双版本构建（JDK 21 + 25） | GitHub Actions workflow |

**验收**：`mvn clean package` 全绿；现有功能回归（WS 连接 QQ + 9 个测试命令收发正常）不因拆分破坏。

## P1 核心抽象（xuanji-api）

| # | 任务 | 产出物 |
|---|---|---|
| 1.1 | 事件模型：`BotEvent`（record）+ `EventType` 层级命名（message/group、notice/*、request/*） | event 包 |
| 1.2 | 消息模型：sealed `MessageElement` + record 元素 + `MessageChain` 建造者 | message 包 |
| 1.3 | 接入抽象：`BotAdapter` / `Bot` / `BotConfig` / `BotManager` 接口 | adapter 包 |
| 1.4 | 发送抽象：`MessageSender`（reply/send 分离）/ `SendReceipt` / `Target` | sender 包 |
| 1.5 | 上下文：`BotContext`（ScopedValue 承载当前事件） | context 包 |
| 1.6 | 注解全套：`@XuanjiPlugin` `@GroupMessage` `@PrivateMessage` `@MessageFilter` `@Arg` `@RateLimit` `@RequireRole` `@GroupOnly` `@LlmTool` | annotation 包 |
| 1.7 | 能力 SPI：`ServiceRegistry` + `LlmService` / `EconomyService` 接口 | capability 包 |
| 1.8 | 平台差异抽象：`GroupAdminAction` / `MediaService` / `ConversationSession` / `SchedulerService` / `PluginContext` | action 包 |
| 1.9 | 配置模型：`BotsProperties`（`xuanji.bots` 标准 application.yml 绑定） | config 包 |

**验收**：api 模块仅依赖 Jackson + JSpecify，零 Spring 零 LLM；全部接口 Javadoc 齐全；**抽象评审通过**（对照：能否表达 QQ 群消息/私聊/按钮回调三类真实事件）。

## P2 QQ 适配器搬家（xuanji-adapter-qq）

| # | 任务 | 产出物 |
|---|---|---|
| 2.1 | 迁入 `QqApiService`/`AccessTokenService`（鉴权/401重试/限流原样保留） | adapter-qq api 包 |
| 2.2 | 迁入 WebSocket（健康检查/重连）+ Webhook（Ed25519）双模式 | adapter-qq connect 包 |
| 2.3 | QQ DTO → `BotEvent` 转换器（群/私聊/按钮回调） | QqEventConverter |
| 2.4 | `MessageChain` → QQ payload 转换器（穷尽 switch） | QqMessageConverter |
| 2.5 | `QqMessageSender` 实现统一接口（文本/MD/键盘/Ark/媒体两段式） | sender 实现 |
| 2.6 | `BotManager` 接管 RobotRegistry：实例 ID=`qq:{appId}`、启停/重连/健康 | core BotManager |

**验收**：YAML 配一个 QQ bot → 群消息转成 BotEvent 打印日志 → 回复文本/Markdown/图片成功；`xuanji.bots` 列表配两个 bot 同时在线。

## P3 调度与指令（xuanji-core）

| # | 任务 | 产出物 |
|---|---|---|
| 3.1 | EventBus + BotPipeline 八阶段（唤醒/黑白名单/限流/安全/预处理/分发/装饰/响应） | pipeline 包 |
| 3.2 | `@GroupMessage`/`@MessageFilter` 扫描注册（BeanPostProcessor）+ `@Arg` 参数绑定（类型转换/必填/默认值） | command 包 |
| 3.3 | 声明式横切 AOP：cooldown / role / @RateLimit / @GroupOnly | aspect 包 |
| 3.4 | 可靠投递：eventId 幂等去重 + 发送令牌桶队列 + 优雅停机 | reliability 包 |
| 3.5 | 9 个测试命令改写为示例插件（@GroupMessage+@MessageFilter 版，switch 彻底消失） | xuanji-plugin-demo |

**验收**：群里发"文本/markdown/图片"指令正常回复；同一 eventId 重推只处理一次（日志可证）；连续高频发送不触发 429。

## P4 持久化（内嵌 H2 三级数据域）

| # | 任务 | 产出物 |
|---|---|---|
| 4.1 | 内嵌 H2 双文件（`xuanji.mv.db` + `xuanji-log.mv.db`）+ 框架自管理 DDL | storage 模块 |
| 4.2 | 框架域建表：user / user_binding / plugin_kv / event_dedup / blacklist | DDL 脚本 |
| 4.3 | bot 实例域：注册时模板自动建表（`xuanji_qqbot_{botKey}_group` 等） | SchemaManager |
| 4.4 | `ProfileService`（forBot(bot).groups()/members()，屏蔽动态表名） | profile 包 |
| 4.5 | 事件影子同步（用户/群/成员 upsert，只写当前 bot 实例表） | 接入 Pipeline 预处理 |
| 4.6 | 插件 KV API + 消息流水（元数据级，写 log 库） | kv / xlog |
| 4.7 | 定时快照备份（每日/留 7 份） | backup 包 |

**验收**：重启后数据完整；配两个 bot 各自生成独立群档案表；`data/backup/` 每日产出快照。

## P5 权限体系（五层模型）

| # | 任务 | 产出物 |
|---|---|---|
| 5.1 | 裁决链：L4 黑名单 → L0 主人/超管 → L1 平台角色 → L3 权限点 | PermissionService |
| 5.2 | L0 角色：framework_role 字段 + 首启向导指定主人 + /超管 任命指令 | master 包 |
| 5.3 | L4 黑名单：三级 scope + 内置 /黑名单 指令 + 到期自动解除 | blacklist 包 |
| 5.4 | @RequireRole AOP 接入 Pipeline ③阶段 | aspect 接入 |

**验收**：主人在群里只是普通成员仍可执行管理指令；黑名单用户事件被丢弃且不计入插件指标；L0 不可被拉黑。

## P6 控制台 v0.1（事项 rdKIjR）

| # | 任务 | 产出物 |
|---|---|---|
| 6.1 | 首启向导：管理员密码 → 平台凭证表单 → 引导装插件 | setup wizard |
| 6.2 | 插件管理页：列表/安装/启停/卸载 | console 前端+API |
| 6.3 | 配置表单：配置元数据自动渲染 + 热生效 | config UI |
| 6.4 | 日志查看：tail + 级别过滤 | log UI |

**验收**：全新环境下载 jar → 5 分钟完成向导并上线；除平台凭证外零配置。

## P7 JVM 插件化（PF4J）

| # | 任务 | 产出物 |
|---|---|---|
| 7.1 | PF4J 接入：plugins/ 目录扫描、独立 ClassLoader、插件子 ApplicationContext | plugin 模块 |
| 7.2 | 热加载/卸载：副作用全回收（指令/拦截器/REST 路由） | lifecycle |
| 7.3 | 插件 REST 路由动态注册（`/x/{pluginId}/**`） | route 注册器 |
| 7.4 | 插件开发脚手架（Maven Archetype） | archetype |

**验收**：插件 jar 放入 plugins/ 即生效，卸载后指令与路由全部消失；脚手架 5 分钟生成可运行插件工程。

## P8 指标与日志

| # | 任务 | 产出物 |
|---|---|---|
| 8.1 | MDC 全链路（traceId/eventId/botId/pluginId）+ JSON 结构化日志 | logging 配置 |
| 8.2 | 消息四段计时（receive/handle/send/e2e）histogram 上报 | metrics 包 |
| 8.3 | Actuator 仪表盘数据：CPU/内存/磁盘/虚拟线程/bot 在线状态 | monitor API |

**验收**：控制台可按 traceId 拉出一条消息全链路日志；P95 耗时、发送成功率可查询。

---

# 第二阶段 · v0.2 多平台验证（P9–P10）

## P9 OneBot 适配器（抽象验证）

| # | 任务 | 产出物 |
|---|---|---|
| 9.1 | xuanji-adapter-onebot：正/反向 WS + HTTP 调用 | adapter-onebot |
| 9.2 | OneBot 报文 ↔ BotEvent/MessageChain 双向转换 | converter |
| 9.3 | **抽象回修**：两个实现暴露的抽象缺陷，回改 xuanji-api（预期内工作） | api 修订记录 |

**验收**：同一示例插件不改代码，QQ + OneBot 双平台同时在线、同时响应；抽象回修清单归档。

## P10 插件规范 + 索引市场 + 模拟器（事项 rJHJZR）

| # | 任务 | 产出物 |
|---|---|---|
| 10.1 | plugin.json schema 定稿 + 打包工具 | 规范文档 + maven 插件 |
| 10.2 | GitHub 索引仓库（plugins.json + PR 收录流程） | market-index repo |
| 10.3 | 控制台市场页：浏览/一键安装（兼容校验+权限确认+哈希校验） | market UI |
| 10.4 | 消息模拟器（与事件录播同源）：控制台发假消息调试插件 | simulator |  

**验收**：签到插件按规范打包，通过索引仓库被控制台安装成功；模拟器里不调真 QQ 可调试指令。

---

# 第三阶段 · v0.3 跨语言与联动（P11–P12）

## P11 XPBP + Python SDK（事项 rNBk4w）

| # | 任务 | 产出物 |
|---|---|---|
| 11.1 | XPBP 协议草案评审（JSON-RPC 2.0，消息族定义） | 协议文档 |
| 11.2 | Runtime Manager：sidecar 拉起/心跳/退避重启/日志收集 | bridge 模块 |
| 11.3 | xuanji-sdk-python：装饰器 API（贴近 NoneBot 习惯） | PyPI 包 |
| 11.4 | 实测基准：stdio 单连接 QPS / 延迟分布（对比 JVM 轨） | benchmark 报告 |

**验收**：Python 回声插件被核心拉起、注册指令、群消息回复成功；基准报告归档（验证 1k–3k QPS 估算）。

## P12 economy + 签到（跨插件联动打样）

| # | 任务 | 产出物 |
|---|---|---|
| 12.1 | xuanji-plugin-economy：EconomyService 实现 + 记账流水表 | economy 插件 |
| 12.2 | xuanji-plugin-sign：调用 economy.earn() 发金币 | sign 插件 |
| 12.3 | 联动演示：签到送金币 → 控制台查 economy 余额与流水 | demo |

**验收**：签到插件零金币存储代码；卸载 economy 时签到插件报清晰依赖错误。

---

# 第四阶段 · v0.4 → v1.0（P13–P15）

## P13 v0.4
- xuanji-sdk-node（koishi-compat 表层）；
- xuanji-plugin-llm（OpenAI 兼容 + Function Calling + Agent loop）；
- 监控仪表盘（P95 耗时/成功率/系统资源可视化）；
- 权限体系完善（L3 权限点管理页）。

## P14 v0.5
- xuanji-sdk-go；
- OpenTelemetry 导出；
- AOT 原生镜像发行版（免 JDK，镜像 <100MB，启动 <1s）。

## P15 v1.0
- 独立市场服务（账号/上传/扫描/评分，Spring Boot + PostgreSQL）；
- 插件签名体系；
- 文档站 + 生态运营（贡献指南、插件开发大赛等）。

---

# 开发规范

1. **分支**：`main`（保护）+ `feature/P{阶段号}-{主题}`；每阶段结束打 tag `v0.x-milestone`；
2. **提交**：Conventional Commits（`feat(adapter-qq): ...`）；
3. **测试**：每阶段验收项 = 自动化测试用例；handler 单测走事件录播，不依赖真实 QQ；
4. **文档**：每阶段更新 README"当前能力"一节；api 模块 100% Javadoc；
5. **评审卡点**：P1 抽象评审、P9 抽象回修评审、P11 协议评审——三个必须过的 gate。

# 风险检查点

| 检查点 | 阶段 | 应对 |
|---|---|---|
| 抽象是否够用 | P9 结束 | 两个实现验证，回修清单必须清零才进 v0.3 |
| XPBP 性能是否达标 | P11.4 | 不达标则强化 batch/二进制编码，仍不达标则跨语言轨只定位低频场景并写入文档 |
| QQ 政策变动 | 全程 | OneBot 兜底能力保持可用（P9 后） |
