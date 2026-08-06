# 璇玑框架 v3.3 全套验收测试流程

> 版本：2026-08-06（#209 console-server 拆分 + core 全量重建后首版）
> 适用范围：`D:\Program\bot\xuanji` 9 模块工程
> 验证铁律：**全程使用隔离回环 WS 测试实例，绝不接入真实机器人**；构建在 PowerShell 下执行。

---

## 〇、验收前置

| 项 | 要求 |
|---|---|
| JDK | IDEA SDK 名为 `25`（指向 `D:\Program\Java\jdk-25`） |
| 运行配置 | Main class=`dev.xuanji.starter.XuanjiApplication`，classpath= xuanji-starter |
| 数据目录 | 首验建议**全新空 data/**（验证首启建表）；再补一轮带旧 data 的迁移验证 |
| 冒烟目录 | 隔离目录如 `smoke_cli/`（含 `application.yml` + 打好的 starter jar） |
| 控制台 | 前端已构建到 `xuanji-console/dist`，访问 `http://localhost:<port>/xuanji/console/` |

---

## 一、静态验收（代码结构，约 5 分钟）

| 编号 | 检查项 | 预期 | 实测 |
|---|---|---|---|
| S1 | 模块数量 | 9 个：api/sdk/core/adapter-qqbot/adapter-onebot/console-server/console/plugin-demo/starter | |
| S2 | 依赖单向 | `api ← sdk ← core ← {adapter-qqbot, adapter-onebot, console-server} ← starter`，无反向依赖 | |
| S3 | core 纯内核化 | `xuanji-core/pom.xml` 无 spring-web/webmvc/jakarta.servlet | |
| S4 | 重建核心类 | core 下 16 个关键类在位（ConfigService / PlatformDataProvider / MessageEventRecorder / BotDataSourceRegistry / FrameworkBotRepository / BotSchemaProvider / HealthMetricProvider / ConnectionStatusProvider / PermissionLevel / TimeUtils / Sleeper / BotOutboundExecutor / DefaultBotContextManager / PluginBotBinding 三件套） | |
| S5 | 控制台迁移 | `xuanji-console-server` 11 个类在 `dev.xuanji.console.*`；旧 core 包内无 Console*Controller | |
| S6 | 待提交清单 | `git status`：55 RM + 37 M + 61 ?? + 7 D（7 个 D 为迁出的 web 类 + 过时测试，属预期） | |

---

## 二、构建验收（自动化，约 2 分钟/轮）

```powershell
Set-Location 'D:\Program\bot\xuanji'
$env:JAVA_HOME='D:\Program\Java\jdk-25'
mvn -o clean package -DskipTests   # B1：全量打包
mvn -o test                        # B2：全量测试
```

| 编号 | 命令 | 预期 |
|---|---|---|
| B1 | `mvn -o clean package -DskipTests` | BUILD SUCCESS，9 模块，starter 产出可执行 jar |
| B2 | `mvn -o test` | BUILD SUCCESS，**58 测试全绿**（core 2 / qqbot 9 / onebot 47） |
| B3 | 重复执行 B1 | 幂等成功（无残留状态依赖） |

---

## 三、启动冒烟（IDEA 手动，隔离回环）

### 3.1 首启建表
1. 删除/清空 data 目录（全新验证），IDEA 启动 XuanjiApplication。
2. 预期日志：`[DB] 首次框架初始化建表完成`，无 ERROR。

| 编号 | 检查项 | 预期 |
|---|---|---|
| C1 | 框架库 | `data/xuanji/xuanji.mv.db` 出现 **10 张表**：xuanji_bot / xuanji_config / xuanji_bot_setting / xuanji_dedup / xuanji_plugin / xuanji_plugin_kv / xuanji_bot_owner / xuanji_blacklist / xuanji_plugin_binding / xuanji_setup |
| C2 | 旧表迁移 | 无旧表残留（xuanji_setting / xuanji_bot_config / xuanji_super_admin / xuanji_qqbot_group* 等已删除） |
| C3 | 控制台 API | `GET /xuanji/api/console/bots` → `[]`（无机器人） |
| C4 | 前端可达 | `GET /xuanji/console/` 返回 SPA HTML；`/setup/status` 返回未完成向导状态 |

### 3.2 带旧 data 的迁移验证（第二轮）
1. 保留旧版 data 目录启动，验证 `migrateLegacyTables`：
   - 旧 `xuanji_bot.bot_identifier` → 新 `instance_id` 列数据搬迁；
   - 旧 `xuanji_setting` → `xuanji_config` 迁移；
   - 旧固定列配置表删除。
2. 预期：启动成功、日志打印迁移条目数、数据可见。

---

## 四、控制台功能验收（前端 11 页逐页）

> 先添加 1 个 QQ 回环机器人（见第五节）作为数据源，再逐页验证。

| 编号 | 页面/操作 | 操作 | 预期 |
|---|---|---|---|
| K1 | 设置向导 | 走完 setup 流程 | 完成后不再进向导 |
| K2 | 机器人管理·添加 | 添加 QQ 回环机器人（WS） | 注册表双写：`xuanji_bot.status=ONLINE` + `qqbot_bot.status=ONLINE`；WS 已连接 |
| K3 | 机器人管理·停止 | 停止机器人 | `xuanji_bot.status` + `qqbot_bot.status` 均停用；重启项目后该机器人不再自动启动 |
| K4 | 机器人管理·启用 | 再次启用 | 双写回 ONLINE；reload 后自动重连 |
| K5 | 机器人管理·删除 | 删除机器人 | 全流程：停 WS→反注册→删密钥→closeInstance（释放文件锁）→删注册行→清平台库行→删 `data/qqbot/{appId}/` 目录；文件锁可再次创建同名机器人 |
| K6 | 联系人 | 群列表/成员列表 | 跨 bot 聚合，每行带 `BOT_APPID` 章；按 BOT_APPID 过滤正确 |
| K7 | 事件流 | 群内发消息 | 消息流水 IN/OUT 实时出现；时间戳为 epoch 秒 |
| K8 | 监控 | 指标页 | handle/e2e 计时器有数据；慢阶段（>100ms）有告警日志 |
| K9 | 插件 | 加载/卸载/启停插件 | 列表正确；卸载后命令失效且无 ClassLoader 泄漏（日志无警告） |
| K10 | 权限 | 主人/黑名单管理 | 增删改查生效（见第六节矩阵联动） |
| K11 | 数据库/日志 | 浏览框架库表、日志 | 表数据可查；日志库有内容 |

---

## 五、平台回环验收（核心！全部用隔离 WS 实例）

### 5.1 QQ 官方协议回环（mock 网关）

1. 用独立脚本起一个回环 WS server（模拟 QQ 网关 `/api/gateway`），连接框架的 WS client。
2. 依次推送 `docs/qqbot-message-examples.md` 中的真实报文结构：
   - 群聊文本（含 @机器人）
   - 私聊文本
   - 图片消息（**content 为空**）
   - 加群请求（`join_request_id` 字段）
   - 未知/新增事件类型

| 编号 | 场景 | 预期 |
|---|---|---|
| Q1 | 群文本命令 | 命令链命中（如 `/help`）；出站经 BotOutboundExecutor；事件流 OUT 记录 |
| Q2 | 私聊文本 | 私聊命令链命中 |
| Q3 | 图片消息 | 命令链 miss；`@MessageFilter(media=NEED, mediaTypes=IMAGE)` 订阅命中；`MediaKind` 判定为 IMAGE |
| Q4 | 加群请求 | 解析 `join_request_id`；请求信息入库；可同意/拒绝 |
| Q5 | 未知事件 | 先完整 dump 原始报文（`data.toPrettyString()`），不崩、跳过 |
| Q6 | 落库 | per-bot 库 `data/qqbot/{appId}/data/{appId}.mv.db` 的 message/event/group_member 表有数据 |

### 5.2 OneBot 回环（Napcat 反向 WS 模拟）

1. `xuanji.onebot.enabled=true`，框架起反向 WS 服务。
2. 用 websocat / 自写 client 连接反向 WS 端口，模拟 Napcat 上报：
   - 群消息 CQ 段：`[{"type":"text","data":{"text":"hello"}}]`
   - 图片段：`[{"type":"image","data":{"file":"https://..."}}]`
   - 纯图片消息（无文本）

| 编号 | 场景 | 预期 |
|---|---|---|
| N1 | 文本消息 | 命令链命中；`getChain()` 非空（**消息链直塞生效**） |
| N2 | 图片消息 | 媒体订阅命中；`chain.medias()` 非空；`resolve("onebot")` 返回 URL 形态 |
| N3 | 日志摘要 | 日志显示「图片」而非「文件」（MediaKind 判定正确） |
| N4 | 私聊 | 私聊链命中；私聊事件正确构建（PrivateMessageEvent） |
| N5 | 持久化 | onebot per-bot 实例库（`data/onebot/{appId}/...`）落库正常 |

### 5.3 Webhook 回环

| 编号 | 场景 | 操作 | 预期 |
|---|---|---|---|
| W1 | 验签通过 | 注册机器人后，用注册的密钥对 body 做 HMAC 签名 POST /webhook | 正常分发处理 |
| W2 | 验签失败 | 错误签名 POST | 拒绝 401/403 |
| W3 | 停止后拒收 | 控制台停止 webhook 机器人 | 拒收（RobotRegistry.setRobotStatus(0)，WebhookServiceImpl 读内存 status 拒绝），日志说明已停止 |

---

## 六、框架特性验收

### 6.1 权限等级矩阵

> 等级：NONE(0) < BLACKLIST(1) < MEMBER(2) < ADMIN(3) < GROUP_OWNER(4) < BOT_MASTER(5)

1. 通过控制台权限页设 `xuanji_bot_owner`（主人）+ `xuanji_blacklist`（黑名单）各一条。

| 编号 | 用户状态 | 要求等级 | 预期 |
|---|---|---|---|
| P1 | 普通成员 senderRole=member | 无要求 | 放行（≥MEMBER） |
| P2 | 普通成员 | 需 ADMIN | 拒绝 |
| P3 | 管理员 senderRole=admin | 需 ADMIN | 放行 |
| P4 | 群主 senderRole=owner | 需 GROUP_OWNER | 放行 |
| P5 | 黑名单成员 | 任意 | 全局闸门拒绝（check 黑名单否决，最高优先级） |
| P6 | 主人（xuanji_bot_owner） | 需 BOT_MASTER | 放行 |
| P7 | 未登记群成员 | 无要求 | 拒绝（NONE < MEMBER） |

### 6.2 出站节奏

| 编号 | 场景 | 操作 | 预期 |
|---|---|---|---|
| T1 | 节流生效 | 设置 `outbound_pace_ms=2000`（bot 级 EAV 或全局），连发 3 条回复 | 相邻出站间隔 ≥2s，串行发送（per-bot 单线程） |
| T2 | 默认不节流 | 不设 pace | 立即发送（pace=0 向后兼容） |
| T3 | 并发隔离 | 两个 bot 同时出站 | 各 bot 时间线独立，互不干扰 |

### 6.3 适配器可插拔

| 编号 | 配置 | 预期 |
|---|---|---|
| A1 | 默认（qqbot enabled） | 正常启动，QQ Bean ~36 个在位 |
| A2 | `xuanji.qqbot.enabled=false` | 正常启动，QQ Bean 全缺席不崩（EventDispatcher ObjectProvider 兜底） |
| A3 | `xuanji.onebot.enabled=true` | 正常启动，OneBot 反向 WS 服务在监听 |
| A4 | 双关 | `qqbot.enabled=false` + `onebot.enabled=false` 仍能启动（纯内核 + 控制台） |

### 6.4 其他机制

| 编号 | 机制 | 操作 | 预期 |
|---|---|---|---|
| M1 | 时间字段 | 发一条消息查 message 表 | `create_time` 为 BIGINT epoch 秒（非 TIMESTAMP） |
| M2 | 幂等去重 | 推送相同 event_id 两次 | 仅处理一次 |
| M3 | 熔断 | mock QQ 网关连续返回 5 次 5xx | 第 6 次起熔断（30s 冷却），`getCircuitBreakerSnapshot()` 有状态；冷却后自动恢复 |
| M4 | 插件 KV | 插件写入 kv | xuanji_plugin_kv 落库 |

---

## 七、插件机制验收

| 编号 | 场景 | 预期 |
|---|---|---|
| G1 | `@Command(scope=GROUP)` 群命令 | 群聊命中；私聊不命中 |
| G2 | `@Command(scope=BOTH)` 纯文本命令 | 群/私聊均命中 |
| G3 | 依赖 `GroupMessageEvent` 参数的 `scope=BOTH` 命令 | **私聊必须不注入该参数（无 NPE）**——scope 铁律验证 |
| G4 | `@MessageFilter(media=NEED, mediaTypes=[IMAGE,VOICE])` | 图片/语音命中，文本不命中 |
| G5 | `@Arg` 注入 | 参数正确剥离 startWith+cmd；必填缺失抛 MissingArgException 回给用户 |
| G6 | Stripped 消息 | 仅文本参与匹配 |
| G7 | 媒体五态 | 插件内 `e.getChain().medias()` + `img.resolve(platform)` 返回正确 MediaRef（URL/BASE64/PLATFORM_ID 等） |

---

## 八、回归与异常

| 编号 | 场景 | 预期 |
|---|---|---|
| R1 | WS 断线 | 自动重连（指数退避），恢复后正常收发 |
| R2 | 删机器人后重加同名 | 成功（文件锁已释放、密钥已删、目录已清） |
| R3 | 插件卸载 | 命令注册移除；`ArgumentResolver.evict(class)` 无泄漏 |
| R4 | 慢阶段 | 某阶段 >100ms 打印 `[Pipeline] 慢阶段` 告警 |
| R5 | 未知字段报文 | 不崩，dump 原始报文后跳过 |

---

## 九、验收记录汇总表

| 用例号 | 名称 | 结果（✅/❌/⚠️） | 备注 |
|---|---|---|---|
| S1–S6 静态 | 代码结构 | | |
| B1–B3 构建 | 编译+测试 | | |
| C1–C4 冒烟 | 建表+启动 | | |
| K1–K11 控制台 | 11 页功能 | | |
| Q1–Q6 QQ 回环 | 官方协议 | | |
| N1–N5 OneBot 回环 | Napcat 模拟 | | |
| W1–W3 Webhook | 验签/拒收 | | |
| P1–P7 权限 | 等级矩阵 | | |
| T1–T3 节奏 | 出站节流 | | |
| A1–A4 可插拔 | 适配器开关 | | |
| M1–M4 机制 | 时间/幂等/熔断/KV | | |
| G1–G7 插件 | 命令语法糖 | | |
| R1–R5 回归 | 异常路径 | | |

**判定标准**：S/B/C 全绿 + 平台回环 Q/N/W 全绿 + 权限 P 全绿 → 验收通过；K 页面允许 ⚠️（样式级）但功能不可缺。

---

## 十、验收后遗留动作

1. **git 提交**：55 RM + 37 M + 61 ?? + 7 D 全部未提交，验收通过后由用户 review 并提交（**勿在沙箱跑 `git rm`**）。
2. **IDEA Local History 校对**：重建的 core 类（重点 PermissionService / ConfigService / MessageEventRecorder / BotOutboundExecutor / PluginBotBinding*）有历史快照则恢复比对。
3. 清理 `_core_web_removed/`（已迁出，可归档）、`_tools/`（CFR）、`_compile_209.log` / `_test_209.log`。

---

## 十一、已完成功能补录（2026-08-06 更新）

> 以下两项为验收流程后新增/补全的待办，均已实现并通过 60 个测试：

| 编号 | 功能 | 验收方式 | 状态 |
|---|---|---|---|
| **P1-D 补尾（③）** | 媒体下载落盘：`MessageElement.Media.resolveFile(platform[, botKey])` → URL 下载到 `data/xuanji/media/`（**内容 SHA-256 去重**，同图不同 URL 只存 1 份；按需下载 + bot 级/全局开关 + TTL/配额清理） | 群发图片 → 回复带「↓ 已落盘: 文件名 (xxKB)」；重复图片显示「内容去重命中」；日志 `[媒体下载]` | ✅ |
| **P3-G（④）** | 插件 jar 热加载：控制台插件页「热加载」/ `POST /console/plugins/{id}/reload` → 卸载旧实例（Spring 子容器 + 指令 + pf4j）→ 重新加载新 jar（不重启框架，持久态保留）。`CopyingJarPluginLoader` 复制 jar 到 `plugins/.work/` 加载副本，**Windows 下原 jar 不被锁，可随时覆盖** | 改插件 → 打包覆盖 `plugins/` 原 jar → 点热加载 → `[Plugin] 热加载完成` → 新命令生效 | ✅ |
| **性能（⑤）** | BotDataSourceRegistry 改 HikariCP 连接池（原 DriverManagerDataSource 每次写新建 H2 连接 → dispatch 700-900ms）；H2 URL 加 `AUTO_SERVER=TRUE` 支持池内多连接 | 重启后日志 `[DataSource] 打开 H2(池化)`；dispatch 慢阶段明显下降 | ✅ |
| **收尾** | 启动清理 `plugins/.work` 旧副本（loadAndStartAll 时删除 copy-* 文件） | 启动日志「[Plugin] 启动清理 .work 旧副本 N 个」 | ✅ |

**补充说明**：
- 媒体下载配置：全局 `media.download.enabled` / `media.download.max_file_bytes`（默认 200MB）/ `media.storage.ttl_days`（默认 7）/ `media.storage.max_bytes`（默认 4GB）；bot 级 `media_download_enabled`（覆盖全局，30s 内生效）。
- 插件多 jar 互不影响，可各自热重载（按 pluginId 独立）。
