# 璇玑框架 · 插件体系整合优化方案（借鉴 Koishi / NoneBot2 / AstrBot / ElainaBot_v2）
> ⚠️ **历史设计方案（2026-08-05）**：插件体系整合方案，核心结论（@Command 语法糖/媒体五态/热加载/绑定）均已实施，本档仅作设计过程参考。


> 版本：v1.0 · 2026-08-05
> 范围：xuanji-sdk（插件开发 API）、xuanji-core（插件运行时）、xuanji-adapter-qqbot（QQ 接入）
> 性质：**设计方案**（含待确认项，实施前需逐包确认）

---

## 第一部分 · 前提条件（事实盘点）

| 编号 | 现状事实 | 来源 |
|---|---|---|
| F1 | 注解 8 个：@XuanjiPlugin/@GroupMessage/@PrivateMessage/@GroupEvent/@PrivateEvent/@MessageFilter/@Arg/@RequireRole 已接线；@OnMessage/@RateLimit/@LlmTool/@GroupOnly 定义未接线 | api/annotation |
| F2 | SDK 事件纯文本快照：GroupMessageEvent 无 MessageChain，hasAttachments() 恒 false | sdk/event |
| F3 | MessageChain + 13 sealed MessageElement 已存在（出站用）；QqMessageConverter 仅 toQqPayload，**无入站解析** | api/message、adapter-qqbot/converter |
| F4 | CommandRegistry：反射扫描 + 过滤链（platforms→disabledPlugins→rateLimit→role→虚拟线程 5s 超时） | core/command |
| F5 | XuanjiPluginManager：PF4J + 每插件 Spring 子容器；无 jar 热加载（reload 仅重跑钩子+重注册） | core/plugin |
| F6 | PluginContext.services()：Llm/Economy/Scheduler/Conversation/GroupAdmin 5 接口全无实现 | api/action |
| F7 | BotOutboundExecutor：per-bot 单线程异步出站 + MDC 传递 + 失败不抛 | core/concurrent |
| F8 | 平台路由双维度（platforms 注解 + setContext(platform)），**无 bot 维度** | adapter-qqbot/handler |
| F9 | per-bot 分库，跨平台用户天然隔离（无 bind 映射） | storage |
| F10 | 控制台事件流水已服务化（MessageEventRecorder），前端可扩展 | core/storage |

## 第二部分 · 核心洞察（本质分析）

**璇玑与三框架的差异不是"缺能力"，而是"归一化深度与插件信息量的错配"**：

- Koishi/AstrBot/NoneBot 归一化了"字段模型"（谁发的/哪个群/什么内容），插件拿到 Session/Event 直接可用
- 璇玑归一化了"运行时管线"（BotPipeline + BotEvent + platforms 双维度路由 + 权限/限流/超时），平台差异在管线内已收敛——**但收敛成果没有回灌给插件**，插件拿到的是纯文本快照

因此整合的本质 = **把管线的归一化成果"回灌"给插件**，而不是另起炉灶。

## 第三部分 · 工作包设计（按优先级）

### 工作包 A（P0）· SDK 事件增强：插件能读"完整消息"

**借鉴**：Koishi Session / AstrBot unified_msg_origin / Koishi stripped（借鉴点 4/9/13/17/18）

**设计**：
```java
// sdk/event/GroupMessageEvent 增强
public class GroupMessageEvent {
    // 现有字段保留（messageId/content/plainText/groupId/...）
    private MessageChain chain;          // 懒解析缓存
    private Stripped stripped;           // 解析结果（见下）
    private String unifiedMsgOrigin;     // "qqbot:group:{botKey}:{groupId}"
    private String botKey;               // 归属机器人

    public MessageChain getChain() {     // 懒解析：首次调用才从 rawJson 转换
        if (chain == null) chain = MessageConverterHolder.fromPayload(rawJson);
        return chain;
    }
}

// api/event/Stripped（Koishi session.stripped 同款 record）
public record Stripped(String content,     // 去除 @机器人/昵称前缀后的文本
                       String prefix,      // 匹配到的前缀
                       boolean appel,      // 是否被呼叫（@机器人或昵称）
                       boolean hasAt,      // 是否含 @ 提及
                       boolean atSelf) {}  // 是否 @ 了机器人自身
```

**适配器侧**：
- `GroupMessageHandler`/`C2cMessageHandler` 构造事件时填充 stripped（复用 CommandRegistry 现有的裁前缀/atBot 判定逻辑，提取为公共组件 `MessageStripper`）、botKey、unifiedMsgOrigin
- 新增 `QqMessageConverter.fromQqPayload(ObjectNode) → MessageChain`：解析 QQ 报文 msg_type(0 文本/2 md/7 媒体) + attachments → Text/Image/At/Reply 元素

**优缺点**：
- 优点：插件能读图片/At/引用，hasAttachments() 变真；stripped 让插件免做 @ 判断；origin 统一会话标识
- 缺点：入站解析器 ~150 行工作量
- 反对意见"SDK 事件应轻量"→ **懒解析化解**（插件不调 getChain() 零开销）

**风险**：懒解析需在 CommandRegistry.clearContext() 前完成读取；ScopedValue/ThreadLocal 生命周期管理。

### 工作包 B（P0）· 插件方法参数自动注入（NoneBot Depends 式）

**借鉴**：NoneBot Matcher + Depends / Koishi ctx 注入（借鉴点 1/3/8）

**设计**：`CommandRegistry.dispatch` 反射调用前按参数类型注入（白名单 7 种）：
```java
// 插件示例（注入版）
@GroupMessage
public String onMsg(GroupMessageEvent e, MessageChain chain, XjBot bot,
                    PluginContext ctx, MessageSender sender, String plainText) {
    if (chain.hasImage()) return "收到一张图片";
    return bot.reply(plainText);   // 返回 String 自动回复（可选便利）
}
```
| 参数类型 | 注入内容 |
|---|---|
| MessageChain | 当前消息链（懒解析） |
| GroupMessageEvent / PrivateMessageEvent | 当前 SDK 事件 |
| dev.xuanji.api.event.BotEvent | 统一事件 |
| XjBot / C2cXjBot | 出站门面（等价现有 setContext 的 bot） |
| PluginContext | 能力门面 |
| MessageSender | 统一发送器 |
| String | 纯文本内容（裁前缀后） |

**理由**：Java/Spring 里方法签名即声明，比 Koishi 的 ctx 全局句柄更贴原生；比构造注入灵活（不同方法可要不同上下文）。`@Arg` 是"命令参数解析"、类型注入是"上下文注入"，两维度正交。

**优缺点**：
- 优点：插件代码最简、类型安全、无全局状态
- 缺点：反射开销 → 解析结果按 Method 缓存；"隐式魔法" → 白名单 + 文档约束

### 工作包 C（P1）· 插件-机器人动态绑定（ElainaBot _allowed_bots）

**借鉴**：ElainaBot_v2 plugin_bots.yaml + _allowed_bots 过滤（借鉴点 6/15）

**设计**（动态，不写死在注解——按你 15 条要求）：
```sql
-- 新表（框架库）
CREATE TABLE IF NOT EXISTS xuanji_plugin_bot (
    plugin_key VARCHAR(128) NOT NULL,
    bot_key    VARCHAR(64)  NOT NULL,   -- 空 = 全部机器人
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_key, bot_key)
);
```
- 控制台插件页：勾选"此插件作用于哪些机器人"（写入表 → `reloadPlugin` 重注册）
- `CommandRegistry.dispatch` 加过滤：已配置绑定且当前 botKey 不在列表 → 跳过
- **动态性**：改绑定不重启、不改代码；`XuanjiPluginManager.reloadPlugin` 已有重注册机制可复用

**优缺点**：
- 优点：多机器人差异化运营；配置与代码分离
- 缺点：多一张表 + 前端配置 UI 工作量
- 反对意见"注解写死更直观" → 你已明确否决（运营不可调）

### 工作包 D（P1）· 媒体五态归一化（AstrBot convert_to_file_path）

**借鉴**：AstrBot BaseMessageComponent 媒体引用归一化（借鉴点 11/14/16）

**设计**：
```java
// api/message/MessageElement
public sealed interface MessageElement permits Text, At, Image, ... {
    // 仅媒体元素（Image/Voice/Video/File）实现
    interface Media {
        String convertToFilePath();      // 五态 → 本地临时文件
    }
}

// core/media/MediaRefResolver（SPI，core 提供默认实现）
public interface MediaRefResolver {
    String resolve(String ref);          // file:// / http(s):// / base64:// / data:image / 裸 base64 / 本地路径
}
```
- 事件级临时文件管理：事件结束清理（AstrBot `track_temporary_local_file` 同款）
- 适配器可覆盖（OneBot 的 base64 形态与 QQ 不同）

**优缺点**：插件处理媒体一条 API；平台差异全收敛。缺点：临时文件生命周期管理复杂度。

### 工作包 E（P2）· sendQueued 频控友好发送（Koishi delay）

**借鉴**：Koishi session.sendQueued + delay.character/message（借鉴点 7/12）

**设计**：在 BotOutboundExecutor 之上加节奏队列：
```java
public interface MessageSender {
    SendReceipt sendQueued(MessageChain chain);        // 按字数计算延迟（delay.character）
    SendReceipt sendQueued(MessageChain chain, int fixedDelayMs);  // 固定间隔
}
```
- 长回复自动拆多条按节奏发出，降低 40034100 频控触发
- **事实**：QQ 频控错误码 40034100（主动）/40034128（被动回复超限）存在；发送节奏控制是通用缓解手段

**优缺点**：延迟到达（体验权衡）；替代方案：先做固定 500ms 间隔简化版。

### 工作包 F（P2）· @Command 便捷注解

**借鉴**：Koishi ctx.command / AstrBot @star.register（借鉴点 8）

**设计**：
```java
@Command(name = "签到", aliases = {"sign"}, help = "每日签到获取积分", platforms = {"qqbot"})
@GroupMessage
public String sign(@Arg("cmd") String cmd, GroupMessageEvent e) { ... }
```
- 自动生成 /帮助 目录（控制台展示插件指令清单）
- 与 @MessageFilter 正交：@Command 定义"指令语义"（名/别名/帮助），@MessageFilter 定义"过滤规则"

**反对意见**：现有 @GroupMessage+@MessageFilter 已能实现 → 优点：帮助系统/别名自动化，值得做。

### 工作包 G（P3）· jar 热加载（诚实评估）

**借鉴**：ElainaBot _watcher / 热重载（借鉴点 2）

**事实**：PF4J 支持卸载类加载器；但我们用 Spring 子容器——**卸载 Spring 上下文是难点**（bean 销毁、线程池、连接泄漏）。Python 热重载（ElainaBot）与 Java 不可类比。

**建议**：P3 低优先级。先保"配置级热重载"（启停/指令重注册/绑定变更已支持）；jar 热加载作为长期课题（参考 Spring DevTools 类加载器隔离）。

**诚实弱点**：Java 生态通病。不做不丢人，做了要投入大量测试。

### 工作包 H（P3）· 能力服务落地 + Spring 特性开放

**借鉴**：Koishi Service / NoneBot Depends / Spring Boot（借鉴点 3/4）

**设计**：
- 落地前两个高频服务：`SchedulerService`（@Scheduled 封装）、`GroupAdminAction`
- **诚实指出**：QQ 开放平台对 **QQ 群**没有禁言/踢人 API（mute 仅频道场景）——GroupAdminAction 对 QQ 群可能空转，需先确认平台能力再实现
- 插件子容器开放 Spring 能力：插件类可 @Autowired 子容器 bean、可声明 @Scheduled 方法

## 第四部分 · 实施路线图（优先级排序）

| 优先级 | 工作包 | 工作量 | 依赖 |
|---|---|---|---|
| P0 | A SDK 事件增强（chain/stripped/origin + 入站解析器） | 中（~150 行解析器 + 事件改造） | 无 |
| P0 | B 方法参数自动注入 | 中（反射缓存 + dispatch 改造） | A（chain 注入） |
| P1 | C 插件-机器人动态绑定 | 小-中（表 + 过滤 + 控制台 UI） | 无 |
| P1 | D 媒体五态归一化 | 中（SPI + 临时文件管理） | A（chain 里有 Image） |
| P2 | E sendQueued | 小（outbound 之上加队列） | F7 |
| P2 | F @Command 注解 | 小（注解 + 帮助注册表） | B |
| P3 | G jar 热加载 | 大（Spring 子容器卸载） | F5 |
| P3 | H 能力服务 | 中（先 Scheduler；GroupAdmin 需确认平台能力） | 无 |

## 第五部分 · 待确认问题（实施前）

1. **B 包注入白名单**：7 种类型全要，还是只 MessageChain + XjBot？
2. **F 包 @Command**：优先级如何？帮助系统自动化值得做吗？
3. **C 包绑定粒度**：插件级够吗？需要方法级吗？
4. **D 包 MediaRefResolver**：适配器可覆盖，还是 core 统一实现？
5. **G 包热加载**：接受 P3 低优先级？
6. **A 包懒解析**：接受"首次 getChain() 才解析"？

## 第六部分 · 自检与风险

- **遗漏检查**：unified_msg_origin 与 BOT_APPID（控制台聚合过滤）不冲突——origin 是会话标识、BOT_APPID 是聚合章；@RequireRole 与 stripped.atSelf 协作已在包 A 覆盖；热重载时子容器线程池关闭（F5 关联）已在包 G 风险标注
- **初学者易忽略**：懒解析与 clearContext 生命周期；子容器销毁关线程池；新增出站队列沿用 MDC 传递
- **替代方案**：B 包备选=构造注入 PluginContext + context.getChain()（等价但啰嗦）；E 包备选=固定延迟简化版
- **最诚实的一句话**：这份方案真正的价值在包 A+B（把管线归一化回灌给插件），C/D/E 是运营增强，G/H 是长期课题——建议按 P0 先行，其余逐包确认后实施。
