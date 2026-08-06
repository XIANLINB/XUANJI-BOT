# 璇玑权限体系（Permission System）

> 落地版本：2026-08-05（Batch「PermissionService 黑名单/超管接表」重构）。
> 关键字：等级制权限、持久化主人/黑名单、BOT_MASTER > 群主 > 管理 > 成员 > 黑名单。

---

## 1. 权限等级

权限用单一枚举 `PermissionLevel` 表达，数值越大权限越高，裁决统一用 `rank()` 序数比较：

| 等级 | rank | 含义 | 数据来源 |
|---|---|---|---|
| `BOT_MASTER` | 5 | 机器人主人 | 持久化表 `xuanji_bot_owner`（控制台设置，每 bot 唯一） |
| `GROUP_OWNER` | 4 | 群主 | 平台群角色 `owner`（`group_member.role` / 事件 `senderRole`） |
| `ADMIN` | 3 | 管理 | 平台群角色 `admin` |
| `MEMBER` | 2 | 成员 | 平台群角色 `member`（默认/未知角色也归此处） |
| `BLACKLIST` | 1 | 黑名单 | 持久化表 `xuanji_blacklist`（控制台设置，每群可多名） |
| `NONE` | 0 | 无身份 | 无法识别的用户（无 userId） |

**层级铁律**：`BOT_MASTER > GROUP_OWNER > ADMIN > MEMBER > BLACKLIST`。
「需要角色 X」=「实际等级 rank ≥ X 的 rank」即放行。主人/群主天然满足「管理及以上」的要求。

---

## 2. 数据来源

| 维度 | 来源 | 说明 |
|---|---|---|
| 主人（BOT_MASTER） | `xuanji_bot_owner` 表 | 每 bot 唯一一行；控制台设置；不再读 yml。 |
| 黑名单（BLACKLIST） | `xuanji_blacklist` 表 | 每 `(bot_key, group_id, user_id)` 唯一；控制台设置；一票否决。 |
| 群主/管理/成员 | 平台群角色 | 事件携带的 `GroupMessageEvent.getSenderRole()`（小写 `owner`/`admin`/`member`）；与 `group_member.role` 同源（handler 落库时已同步）。 |

> **为什么不依赖 `group_member` 表做裁决**：每条消息事件已自带 `senderRole`，无需额外 DB 读；`group_member` 仅作持久归档。
> **黑名单按群**：私聊（无群）不在任何群黑名单内，不会被误杀。

---

## 3. 持久化表结构

框架库 `data/xuanji/xuanji.mv.db`，由 `DatabaseInitializer` 建表（已含迁移兼容）：

```sql
-- 机器人主人：每 bot 唯一
CREATE TABLE IF NOT EXISTS xuanji_bot_owner (
    bot_key      VARCHAR(64)  NOT NULL PRIMARY KEY,
    owner_openid VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 黑名单：每群可多名，一票否决
CREATE TABLE IF NOT EXISTS xuanji_blacklist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bot_key     VARCHAR(64)  NOT NULL,
    group_id    VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bot_key, group_id, user_id)
);
```

`bot_key` 统一为 **appId**（与 `xuanji_bot` 注册表、`WhitelistStage` 解析出的 botKey 一致）。

---

## 4. 等级解析算法

`PermissionService.getLevel(botKey, groupId, userId, senderRole)` 是唯一真相源：

```
if userId 为空        → NONE
if 在黑名单(bot,group) → BLACKLIST          // 一票否决，主人也救不回
if 是主人(bot)        → BOT_MASTER
else                  → 按 senderRole 映射（owner/admin/member，未知→MEMBER）
```

---

## 5. 裁决链路

### 5.1 全局闸门（WhitelistStage，pipeline order=20）
每条「有真实发言者」的事件先过 `PermissionService.check(botKey, groupId, userId, null)`：
- 黑名单 → 拒绝（ABORT）
- 主人 → 直接放行
- 其余 → 放行

> 无发言者的系统通知（进退群等）不裁决，直接放行，避免事件到不了 handler。

### 5.2 命令级角色（CommandRegistry）
- `@Command(roles={"owner","admin"})` / `@MessageFilter(roles=...)`：取 `minRequiredLevel(roles)`，实际等级 rank ≥ 之则命中。
- `@RequireRole("ADMIN")`：要求实际等级 rank ≥ ADMIN（主人/群主/admin 均通过）。
- **大小写兼容**：注解值与事件角色均大小写不敏感（`owner`/`OWNER`/`群主` 等价）。
- `permission` 未注入（测试）时退化为仅事件角色比较，不查 DB。

---

## 6. 控制台接口

前缀 `/xuanji/api/console/permission`（`ConsolePermissionController`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/owner?botKey=` | 读取该 bot 当前主人 openid |
| POST | `/owner?botKey=&ownerOpenid=` | 设置主人（覆盖式，每 bot 唯一） |
| DELETE | `/owner?botKey=` | 清除主人 |
| GET | `/blacklist?botKey=&groupId=` | 列出黑名单（groupId 空=该 bot 全部） |
| POST | `/blacklist?botKey=&groupId=&userId=&reason=` | 加黑名单（幂等覆盖 reason） |
| DELETE | `/blacklist?id=` 或 `?botKey=&groupId=&userId=` | 移除黑名单 |

---

## 7. 插件注解用法

```java
// 仅管理员及以上可踢人（主人/群主/admin 都能用）
@Command(value = "踢人|禁言", roles = {"owner", "admin"}, scope = Command.Scope.GROUP)
public String kick(...) { ... }

// 需要管理员角色
@RequireRole("ADMIN")
public String manage(...) { ... }
```

- 角色名大小写不敏感；未知名降级为 MEMBER（宽松，避免拼写错误把命令锁死）。
- `@RequireRole("MEMBER")` = 「成员及以上」= 对所有人开放（黑名单除外）。

---

## 8. 与旧实现的差异 / 迁移

- **旧**：`xuanji.master.bot1` yml 配置 + `PermissionService` 半 no-op（master 因 key 错位近失效，黑名单/超管是桩）。
- **新**：主人/黑名单全部持久化到 DB，经控制台设置；yml `xuanji.master.*` 已删除，`XuanjiRobotProperties.master` 字段已移除。
- `WhitelistStage.resolveBotKey` 改为直接用 `bot.selfId()`（=appId），修复旧「bot1 兜底导致与 appId 错位」的 master 失效 bug。
- `BlacklistController`（游离于 `/xuanji/api/permission`）已删除，功能并入 `ConsolePermissionController`（`/xuanji/api/console` 前缀）。

---

## 9. 已知边界 / 后续可扩展

- **精确角色模式**：当前为「至少某级」语义；若需「仅普通成员（不含 admin）」，可加 `@RequireRole(exact=true)`，暂未做。
- **全局黑名单**：当前黑名单按群；如需全 bot 级黑名单，扩展 `xuanji_blacklist` 允许 `group_id` 为空代表全局。
- **超管（SUPER_ADMIN）**：本体系未单列超管角色，`SUPER_ADMIN` 在注解中映射到 `BOT_MASTER`（最高级）。
- **前端权限管理页**：本次未做（后端+端点+文档先行），后续补 Vue 页（机器人下拉 + 群下拉 + 主人设置 + 黑名单增删）。
