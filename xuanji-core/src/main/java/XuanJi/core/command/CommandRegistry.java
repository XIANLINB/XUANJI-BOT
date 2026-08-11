package XuanJi.core.command;

import XuanJi.api.annotation.*;
import XuanJi.core.plugin.PluginConfigService;
import XuanJi.core.storage.PluginStorageService;
import XuanJi.sdk.bot.Bot;
import XuanJi.sdk.event.GroupMessageEvent;
import XuanJi.sdk.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 指令注册表 — 按事件类型（@GroupMessage / @PrivateMessage 等）注册 handler。
 */
@Slf4j
@Component
public class CommandRegistry {

    private final List<HandlerEntry> groupMsgHandlers = new CopyOnWriteArrayList<>();
    private final List<HandlerEntry> privateMsgHandlers = new CopyOnWriteArrayList<>();
    private final List<HandlerEntry> groupEventHandlers = new CopyOnWriteArrayList<>();
    private final List<HandlerEntry> privateEventHandlers = new CopyOnWriteArrayList<>();
    /** @OnMessage 全量消息监听器（非命令场景，如自动回复/日志/风控）。 */
    private final List<HandlerEntry> messageListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> rateLimitMap = new ConcurrentHashMap<>();
    private final java.util.Set<String> disabledPlugins = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong rateLimitHits = new java.util.concurrent.atomic.AtomicLong();
    /** 命令执行次数（风控中心：@GroupMessage/@PrivateMessage 处理器成功执行数）。 */
    private final java.util.concurrent.atomic.AtomicLong commandExecCount = new java.util.concurrent.atomic.AtomicLong();
    /** 命令执行异常次数（风控中心：处理器抛异常数）。 */
    private final java.util.concurrent.atomic.AtomicLong commandFailCount = new java.util.concurrent.atomic.AtomicLong();
    /** @OnMessage 监听器成功执行数（含非命令消息）。 */
    private final java.util.concurrent.atomic.AtomicLong onMessageExecCount = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 本次事件「命令是否命中」标记（每事件处理前需 reset）。
     *
     * <p>供 LlmChatStage 等「命令未命中才兜底」的场景判断：dispatch 时若有命令 handler
     * 命中并执行，置 true；事件处理链内后续 stage 可读 {@link #isCommandHitInCurrentEvent()}。
     * 线程模型：群/私聊消息事件在各自处理线程内串行，ThreadLocal 安全。
     */
    private final ThreadLocal<Boolean> commandHitInEvent = ThreadLocal.withInitial(() -> false);

    private final XuanJi.core.plugin.PluginBotBindingService bindingService;

    /** 插件持久化服务（方法参数 PluginStorage 自动注入）。 */
    @Autowired(required = false)
    private PluginStorageService pluginStorageService;

    /** 插件配置服务（方法参数 PluginConfig 自动注入）。 */
    @Autowired(required = false)
    private PluginConfigService pluginConfigService;

    /** 多轮会话（方法参数 ConversationSession 自动注入）。 */
    @Autowired(required = false)
    private XuanJi.api.action.ConversationSession conversationSession;

    /** 插件能力门面（方法参数 PluginServices 自动注入，提供 LLM/群管/主动发送）。 */
    @Autowired(required = false)
    private XuanJi.api.plugin.PluginServices pluginServices;

    public CommandRegistry(XuanJi.core.plugin.PluginBotBindingService bindingService) {
        this.bindingService = bindingService;
    }

    public void setPluginEnabled(String pluginId, boolean enabled) {
        if (enabled) disabledPlugins.remove(pluginId);
        else disabledPlugins.add(pluginId);
        log.info("[Handler] 插件{}: {}", enabled ? "启用" : "停用", pluginId);
    }
    public boolean isPluginEnabled(String pluginId) { return !disabledPlugins.contains(pluginId); }

    /** 运行统计（控制台 /console/health 的 plugins 键）。 */
    public Map<String, Object> getStats() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("groupMsgHandlers", groupMsgHandlers.size());
        m.put("privateMsgHandlers", privateMsgHandlers.size());
        m.put("groupEventHandlers", groupEventHandlers.size());
        m.put("privateEventHandlers", privateEventHandlers.size());
        m.put("disabledPlugins", disabledPlugins.size());
        // 命令级限速命中总次数（@Command rateLimit 拦截计数）。历史曾误命名为 pluginTimeoutCount，
        // 前端字段已同步修正为 rateLimitHits；保留旧键兼容旧前端。
        m.put("rateLimitHits", rateLimitHits.get());
        m.put("pluginTimeoutCount", rateLimitHits.get());
        m.put("commandExecCount", commandExecCount.get());
        m.put("commandFailCount", commandFailCount.get());
        return m;
    }

    /** 命令执行统计（风控中心：执行次数 / 异常次数）。 */
    public Map<String, Object> getCommandStats() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("commandExecCount", commandExecCount.get());
        m.put("commandFailCount", commandFailCount.get());
        m.put("onMessageExecCount", onMessageExecCount.get());
        return m;
    }

    /** 命令级限速命中总次数（风控中心概览：@Command rateLimit 拦截计数）。 */
    public long rateLimitHits() {
        return rateLimitHits.get();
    }

    /**
     * 命令清单（控制台「命令管理」页数据源）。
     *
     * <p>群聊/私聊各自注册一条 HandlerEntry（scope=BOTH 时同方法入两个列表），
     * 按 {@code 命令名|插件|方法} 合并去重，作用域用 group/private 两个布尔表达。
     */
    public java.util.List<Map<String, Object>> listCommands() {
        Map<String, Map<String, Object>> byKey = new java.util.LinkedHashMap<>();
        collectCommands(groupMsgHandlers, byKey, true);
        collectCommands(privateMsgHandlers, byKey, false);
        return new java.util.ArrayList<>(byKey.values());
    }

    private void collectCommands(java.util.List<HandlerEntry> entries,
                                 Map<String, Map<String, Object>> byKey, boolean group) {
        for (HandlerEntry e : entries) {
            String cmd = e.filter().cmd();
            if (cmd == null || cmd.isEmpty()) continue;
            String key = cmd + "|" + e.pluginId + "|" + e.method.getName();
            Map<String, Object> m = byKey.computeIfAbsent(key, k -> {
                // 权限优先级：@Command.roles()（CommandFilter 持有）> @RequireRole（requiredRole）> 默认 MEMBER
                String[] cmdRoles = (e.filter() instanceof CommandFilter cf
                        && cf.roles() != null && cf.roles().length > 0)
                        ? cf.roles() : null;
                java.util.List<String> roles = cmdRoles != null
                        ? java.util.List.of(cmdRoles)
                        : (e.requiredRole.isEmpty()
                            ? java.util.List.of("MEMBER")
                            : new java.util.ArrayList<>(e.requiredRole));
                Map<String, Object> x = new java.util.LinkedHashMap<>();
                x.put("cmd", cmd);
                x.put("pluginId", e.pluginId);
                x.put("method", e.method.getName());
                x.put("order", e.order);
                x.put("rateLimitMs", e.rateLimit);
                x.put("roles", roles);
                x.put("platforms", new java.util.ArrayList<>(e.platforms));
                x.put("group", false);
                x.put("private", false);
                return x;
            });
            if (group) m.put("group", true);
            else m.put("private", true);
        }
    }

    public void register(Object pluginInstance, String pluginId) {
        int rateLimit = 0;
        java.util.Set<String> pluginPlatforms = java.util.Set.of();
        XuanJiPlugin plg = pluginInstance.getClass().getAnnotation(XuanJiPlugin.class);
        if (plg != null) {
            rateLimit = plg.rateLimit();
            if (plg.platforms().length > 0) pluginPlatforms = java.util.Set.of(plg.platforms());
        }

        for (Method m : pluginInstance.getClass().getDeclaredMethods()) {
            // P2-F：@Command 语法糖优先——同一方法标了 @Command 则跳过原生注解注册，避免重复
            Command command = m.getAnnotation(Command.class);
            if (command != null) {
                MessageFilter cf = new CommandFilter(command);
                int order = command.order();
                HandlerEntry entry = new HandlerEntry(m, pluginInstance, cf,
                        order, rateLimit, roles(m), pluginId, methodPlatforms(m, pluginPlatforms),
                        scopeOf(m, command.scope()));
                switch (scopeOf(m, command.scope())) {
                    case GROUP -> groupMsgHandlers.add(entry);
                    case PRIVATE -> privateMsgHandlers.add(entry);
                    case BOTH -> { groupMsgHandlers.add(entry); privateMsgHandlers.add(entry); }
                }
                log.info("[Handler] 注册@Command: {}.{} scope={} order={} media={}",
                        pluginInstance.getClass().getSimpleName(), m.getName(),
                        command.scope(), order, cf.media());
                continue;
            }

            MessageFilter filter = m.getAnnotation(MessageFilter.class);
            // 平台白名单：handler 级注解优先，插件级 @XuanJiPlugin(platforms=) 兜底；两者皆空 = 全平台。
            java.util.Set<String> methodPlatforms = resolvePlatforms(m, pluginPlatforms);
            // 场景限制：@GroupOnly 仅群、@PrivateOnly 仅私聊、都不标 = 两者（兼容原生注解 scope）
            boolean gOnly = m.isAnnotationPresent(GroupOnly.class);
            boolean pOnly = m.isAnnotationPresent(PrivateOnly.class);

            if (m.isAnnotationPresent(GroupMessage.class) && !pOnly) {
                groupMsgHandlers.add(new HandlerEntry(m, pluginInstance, filter,
                        m.getAnnotation(GroupMessage.class).order(), rateLimit, roles(m), pluginId, methodPlatforms, Scope.GROUP));
                log.info("[Handler] 注册群聊消息: {}.{} (rateLimit={}s, platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), rateLimit, methodPlatforms);
            }
            if (m.isAnnotationPresent(PrivateMessage.class) && !gOnly) {
                privateMsgHandlers.add(new HandlerEntry(m, pluginInstance, filter, m.getAnnotation(PrivateMessage.class).order(), rateLimit, roles(m), pluginId, methodPlatforms, Scope.PRIVATE));
                log.info("[Handler] 注册私聊消息: {}.{} (rateLimit={}s, platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), rateLimit, methodPlatforms);
            }
            if (m.isAnnotationPresent(GroupEvent.class)) {
                groupEventHandlers.add(new HandlerEntry(m, pluginInstance, null, m.getAnnotation(GroupEvent.class).order(), rateLimit, roles(m), pluginId, methodPlatforms, Scope.GROUP));
                log.info("[Handler] 注册群事件: {}.{} (platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), methodPlatforms);
            }
            if (m.isAnnotationPresent(PrivateEvent.class)) {
                privateEventHandlers.add(new HandlerEntry(m, pluginInstance, null, m.getAnnotation(PrivateEvent.class).order(), rateLimit, roles(m), pluginId, methodPlatforms, Scope.PRIVATE));
                log.info("[Handler] 注册私聊事件: {}.{} (platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), methodPlatforms);
            }
            if (m.isAnnotationPresent(OnMessage.class)) {
                OnMessage om = m.getAnnotation(OnMessage.class);
                Scope sc = om.privateOnly() ? Scope.PRIVATE : om.groupOnly() ? Scope.GROUP : Scope.BOTH;
                messageListeners.add(new HandlerEntry(m, pluginInstance, null, om.priority(), rateLimit, roles(m), pluginId, methodPlatforms, sc));
                log.info("[Handler] 注册@OnMessage: {}.{} scope={} priority={} block={}",
                        pluginInstance.getClass().getSimpleName(), m.getName(), sc, om.priority(), om.block());
            }
        }
        groupMsgHandlers.sort(Comparator.comparingInt(e -> e.order));
        privateMsgHandlers.sort(Comparator.comparingInt(e -> e.order));
        messageListeners.sort(Comparator.comparingInt(e -> e.order));
    }

    /** @Command 方法结合 @GroupOnly/@PrivateOnly 解析最终作用域（注解限制优先于 Command.scope）。 */
    private static Scope scopeOf(Method m, Command.Scope cs) {
        boolean gOnly = m.isAnnotationPresent(GroupOnly.class);
        boolean pOnly = m.isAnnotationPresent(PrivateOnly.class);
        if (pOnly) return Scope.PRIVATE;
        if (gOnly) return Scope.GROUP;
        return cs == Command.Scope.GROUP ? Scope.GROUP : cs == Command.Scope.PRIVATE ? Scope.PRIVATE : Scope.BOTH;
    }

    /** @Command 方法的平台白名单（与原生注解共用解析逻辑）。 */
    private static java.util.Set<String> methodPlatforms(Method m, java.util.Set<String> pluginPlatforms) {
        var c = m.getAnnotation(Command.class);
        java.util.Set<String> ps = new java.util.LinkedHashSet<>();
        if (c != null) for (String p : c.platforms()) ps.add(p);
        return ps.isEmpty() ? pluginPlatforms : ps;
    }

    /** 解析方法的平台白名单：合并各事件注解与 @MessageFilter 的 platforms，handler 级优先于插件级默认。 */
    private java.util.Set<String> resolvePlatforms(Method m, java.util.Set<String> pluginPlatforms) {
        java.util.Set<String> ps = new java.util.LinkedHashSet<>();
        var gm = m.getAnnotation(GroupMessage.class); if (gm != null) for (String p : gm.platforms()) ps.add(p);
        var pm = m.getAnnotation(PrivateMessage.class); if (pm != null) for (String p : pm.platforms()) ps.add(p);
        var ge = m.getAnnotation(GroupEvent.class); if (ge != null) for (String p : ge.platforms()) ps.add(p);
        var pe = m.getAnnotation(PrivateEvent.class); if (pe != null) for (String p : pe.platforms()) ps.add(p);
        var mf = m.getAnnotation(MessageFilter.class); if (mf != null) for (String p : mf.platforms()) ps.add(p);
        // 空 = 未显式限定 → 用插件级默认（插件默认也空 = 全平台）
        return ps.isEmpty() ? pluginPlatforms : ps;
    }

    /** 批量注销指定实例注册的所有 handler */
    public void unregister(Object pluginInstance) {
        groupMsgHandlers.removeIf(h -> h.instance == pluginInstance);
        privateMsgHandlers.removeIf(h -> h.instance == pluginInstance);
        groupEventHandlers.removeIf(h -> h.instance == pluginInstance);
        privateEventHandlers.removeIf(h -> h.instance == pluginInstance);
        messageListeners.removeIf(h -> h.instance == pluginInstance);
        log.info("[Handler] 已注销: {}", pluginInstance.getClass().getSimpleName());
    }

    // ==================== 上下文 ====================

    private static final ThreadLocal<String> userIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> botKeyTL = new ThreadLocal<>();
    private static final ThreadLocal<String> groupIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> msgIdTL = new ThreadLocal<>();
    private static final ThreadLocal<MessageEvent> groupEventDtoTL = new ThreadLocal<>();
    private static final ThreadLocal<Bot> botTL = new ThreadLocal<>();
    private static final ThreadLocal<String> currentPlatformTL = new ThreadLocal<>();

    public static void setContext(String botKey, String groupId, String msgId, String userId,
                                  MessageEvent eventDto, Bot bot, String platform) {
        botKeyTL.set(botKey); groupIdTL.set(groupId);
        msgIdTL.set(msgId); userIdTL.set(userId);
        groupEventDtoTL.set(eventDto); botTL.set(bot);
        currentPlatformTL.set(platform);
    }
    public static void clearContext() {
        botKeyTL.remove(); groupIdTL.remove(); msgIdTL.remove();
        userIdTL.remove(); groupEventDtoTL.remove(); botTL.remove();
        currentPlatformTL.remove();
    }

    public static String getCurrentUser()   { return userIdTL.get(); }
    public static String getCurrentBotKey() { return botKeyTL.get(); }
    public static String getCurrentGroupId(){ return groupIdTL.get(); }
    public static String getCurrentMsgId()  { return msgIdTL.get(); }

    // ==================== 群聊消息分发 ====================

    public String executeGroupMessage(String rawText) {
        return dispatch(groupMsgHandlers, rawText, true);
    }

    public String executePrivateMessage(String rawText) {
        return dispatch(privateMsgHandlers, rawText, false);
    }

    public void dispatchGroupEvent(GroupMessageEvent event) {
        String platform = event.getPlatform();
        // 同 order 内：显式命中当前平台(0) > 默认全平台(1) > 其他平台(2)，使默认 handler 作为兜底而不抢占平台专属 handler
        List<HandlerEntry> ordered = new java.util.ArrayList<>(groupEventHandlers);
        ordered.sort(Comparator.comparingInt((HandlerEntry e) -> e.order)
                .thenComparingInt(e -> platformPriority(e, platform)));
        for (HandlerEntry e : ordered) {
            try {
                // 平台白名单：限定了平台但事件平台不在其中 → 跳过
                if (!e.platforms.isEmpty() && !e.platforms.contains(platform)) continue;
                e.method.invoke(e.instance, resolveArgs(e.method, "", event, e.pluginId));
            } catch (Exception ex) { log.warn("[GroupEvent] {}:", ex.getMessage()); }
        }
    }

    private String dispatch(List<HandlerEntry> list, String rawText, boolean isGroup) {
        String trimmed = rawText != null ? rawText.trim() : "";
        String currentPlatform = currentPlatformTL.get();
        // 同 order 内：显式命中当前平台(0) > 默认全平台(1) > 其他平台(2)
        List<HandlerEntry> ordered = new java.util.ArrayList<>(list);
        ordered.sort(Comparator.comparingInt((HandlerEntry e) -> e.order)
                .thenComparingInt(e -> platformPriority(e, currentPlatform)));
        for (HandlerEntry e : ordered) {
            try {
                // 平台白名单：限定了平台但当前平台不在其中 → 跳过
                if (!e.platforms.isEmpty() && !e.platforms.contains(currentPlatform)) continue;
                if (!matchFilter(e.filter, trimmed, isGroup)) continue;
                if (!isPluginEnabled(e.pluginId)) {
                    log.debug("[Handler] 跳过未启用插件: pluginId={}, cmd={}", e.pluginId,
                            e.filter == null ? "" : e.filter.cmd());
                    continue;
                }
                // 插件-机器人绑定：绑定非空时仅对绑定 bot 生效（空绑定 = 全局）
                if (bindingService != null) {
                    String curBotKey = getCurrentBotKey();
                    if (curBotKey != null && !bindingService.isAllowedForBot(e.pluginId, currentPlatform, curBotKey)) continue;
                }
                if (!checkRateLimit(e)) continue;
                if (!checkRole(e)) continue;  // @RequireRole
                commandHitInEvent.set(true);  // 命令 handler 命中：本事件后续不再触发 LLM 闲聊等兜底
                String argsAfter = argsAfterCommand(trimmed, e.filter);
                Object[] ma = resolveArgs(e.method, argsAfter, groupEventDtoTL.get(), e.pluginId);
                Object result = e.method.invoke(e.instance, ma);
                commandExecCount.incrementAndGet();
                if (result != null) return result.toString();
            } catch (Exception ex) {
                commandFailCount.incrementAndGet();
                log.warn("[Handler] {}: {}", e.method.getName(), ex.getMessage());
            }
        }
        return null;
    }

    /**
     * 执行 @OnMessage 全量监听器（非命令场景）：命令路由未命中时调用。
     * 按优先级排序，block=true 的监听器处理后立即返回（阻断后续监听器）。
     */
    public void dispatchOnMessage(boolean isGroup) {
        String currentPlatform = currentPlatformTL.get();
        List<HandlerEntry> ordered = new java.util.ArrayList<>(messageListeners);
        ordered.sort(Comparator.comparingInt((HandlerEntry e) -> e.order)
                .thenComparingInt(e -> platformPriority(e, currentPlatform)));
        for (HandlerEntry e : ordered) {
            try {
                if (!e.platforms.isEmpty() && !e.platforms.contains(currentPlatform)) continue;
                if (!isPluginEnabled(e.pluginId)) continue;
                // 场景限制：@OnMessage 的 groupOnly/privateOnly
                if (e.scope == Scope.GROUP && !isGroup) continue;
                if (e.scope == Scope.PRIVATE && isGroup) continue;
                // @OnMessage 无命令过滤：所有消息都进
                if (bindingService != null) {
                    String curBotKey = getCurrentBotKey();
                    if (curBotKey != null && !bindingService.isAllowedForBot(e.pluginId, currentPlatform, curBotKey)) continue;
                }
                Object[] ma = resolveArgs(e.method, "", groupEventDtoTL.get(), e.pluginId);
                e.method.invoke(e.instance, ma);
                onMessageExecCount.incrementAndGet();
                // 获取注解 block 标记（通过反射读取，避免记录额外字段）
                OnMessage om = e.method.getAnnotation(OnMessage.class);
                if (om != null && om.block()) return;
            } catch (Exception ex) {
                log.warn("[OnMessage] {}: {}", e.method.getName(), ex.getMessage());
            }
        }
    }

    /** 每事件处理开始前调用，重置「命令命中」标记（保证事件间不串扰）。 */
    public void resetCommandHitFlag() {
        commandHitInEvent.set(false);
    }

    /** 当前事件是否已有命令 handler 命中执行（LLM 闲聊等兜底逻辑读取）。 */
    public boolean isCommandHitInCurrentEvent() {
        return Boolean.TRUE.equals(commandHitInEvent.get());
    }

    /** 平台优先级：显式命中当前平台=0，默认全平台(兜底)=1，其他平台=2（会被白名单拦截） */
    private static int platformPriority(HandlerEntry e, String platform) {
        if (e.platforms.isEmpty()) return 1;
        return e.platforms.contains(platform) ? 0 : 2;
    }

    // ==================== 过滤 ====================

    private boolean matchFilter(MessageFilter f, String text, boolean isGroup) {
        if (f == null) return true;
        boolean match = true;

        if (!f.cmd().isEmpty()) {
            match = text.matches("(?s).*(" + f.cmd() + ").*");
        }
        if (!f.startWith().isEmpty() && !text.startsWith(f.startWith())) match = false;
        if (!f.endWith().isEmpty() && !text.endsWith(f.endWith())) match = false;

        if (isGroup && f.at() != AtMode.IGNORE && groupEventDtoTL.get() instanceof GroupMessageEvent groupEvt) {
            boolean atBot = groupEvt.isAtBot();
            if (f.at() == AtMode.NEED && !atBot) match = false;
            if (f.at() == AtMode.NOT && atBot) match = false;
        }

        if (f.groups().length > 0 && groupEventDtoTL.get() instanceof GroupMessageEvent groupEvt2) {
            if (!Arrays.asList(f.groups()).contains(groupEvt2.getGroupId())) match = false;
        }
        if (f.senders().length > 0) {
            if (!Arrays.asList(f.senders()).contains(userIdTL.get())) match = false;
        }
        if (f.roles().length > 0 && groupEventDtoTL.get() instanceof GroupMessageEvent groupEvt3) {
            String role = groupEvt3.getSenderRole();
            if (role == null || !Arrays.asList(f.roles()).contains(role)) match = false;
        }

        // 富媒体过滤（懒解析纪律：NEED/NOT 只读 hasAttachments 标记；声明 mediaTypes 才解析消息链）
        if (f.media() != MediaMode.IGNORE && groupEventDtoTL.get() instanceof GroupMessageEvent groupEvtM) {
            boolean has = groupEvtM.hasAttachments();
            if (f.media() == MediaMode.NEED && !has) match = false;
            if (f.media() == MediaMode.NOT && has) match = false;
            if (match && f.media() == MediaMode.NEED && has && f.mediaTypes().length > 0) {
                boolean any = false;
                var chain = groupEvtM.getChain();
                if (chain != null) {
                    for (var md : chain.medias()) {
                        if (md.mediaType() != null && Arrays.asList(f.mediaTypes()).contains(md.mediaType())) {
                            any = true;
                            break;
                        }
                    }
                }
                if (!any) match = false;
            }
        }

        return f.invert() != match;
    }

    // ==================== 参数注入 ====================

    /**
     * 剥掉命令词/前缀，返回 @Arg 可用的剩余参数文本。
     * 优先 startWith（前缀整体剥除），其次 cmd（按正则从开头剥离命令词）。
     */
    private static String argsAfterCommand(String text, MessageFilter f) {
        if (f == null || text == null || text.isEmpty()) return text;
        if (!f.startWith().isEmpty() && text.startsWith(f.startWith())) {
            return text.substring(f.startWith().length()).trim();
        }
        if (!f.cmd().isEmpty()) {
            try {
                var p = java.util.regex.Pattern.compile("^\\s*(" + f.cmd() + ")(\\s|$)");
                var m = p.matcher(text);
                if (m.find()) return text.substring(m.end()).trim();
            } catch (Exception ignored) { /* 正则不合法则原样返回 */ }
        }
        return text;
    }

    private Object[] resolveArgs(Method method, String args, XuanJi.sdk.event.MessageEvent event, String pluginId) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        // @Arg 独立游标：从 args（已剥掉命令词）按顺序取 token，不按参数下标错位
        String[] argParts = args != null && !args.isBlank() ? args.trim().split("\\s+") : new String[0];
        int argCursor = 0;

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (XuanJi.sdk.event.GroupMessageEvent.class.isAssignableFrom(type)
                    || XuanJi.sdk.event.MessageEvent.class.isAssignableFrom(type)) {
                values[i] = event; continue;
            }
            if (Bot.class.isAssignableFrom(type)) {
                values[i] = botTL.get(); continue;
            }
            // 插件持久化存储：按当前插件 id 注入隔离视图
            if (XuanJi.api.plugin.PluginStorage.class.isAssignableFrom(type)) {
                values[i] = pluginStorageService != null && pluginId != null
                        ? pluginStorageService.view(pluginId) : null;
                continue;
            }
            // 插件配置读取：DB 值 > schema 默认值 > 兜底
            if (XuanJi.api.plugin.PluginConfig.class.isAssignableFrom(type)) {
                values[i] = pluginConfigService != null && pluginId != null
                        ? pluginConfigService.view(pluginId) : null;
                continue;
            }
            // 多轮会话：注入全局会话管理器（按当前事件上下文定位用户）
            if (XuanJi.api.action.ConversationSession.class.isAssignableFrom(type)) {
                values[i] = conversationSession;
                continue;
            }
            // 插件能力门面：LLM / 群管 / 主动发送（由框架实现注入）
            if (XuanJi.api.plugin.PluginServices.class.isAssignableFrom(type)) {
                values[i] = pluginServices;
                continue;
            }
            Arg arg = params[i].getAnnotation(Arg.class);
            if (arg != null) {
                if (arg.rest()) {
                    // 剩余全部 token 合并（含空格）作为整体参数
                    StringBuilder sb = new StringBuilder();
                    for (int k = argCursor; k < argParts.length; k++) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(argParts[k]);
                    }
                    argCursor = argParts.length;
                    String rest = sb.toString();
                    if (rest.isBlank()) {
                        if (arg.required()) return new Object[]{"缺少参数: " + arg.value()};
                        values[i] = null;
                    } else {
                        values[i] = coerce(rest, type);
                    }
                } else {
                    String raw = argCursor < argParts.length ? argParts[argCursor++] : null;
                    if (raw == null || raw.isEmpty()) {
                        if (arg.required()) return new Object[]{"缺少参数: " + arg.value()};
                        values[i] = null;
                    } else {
                        values[i] = coerce(raw, type);
                    }
                }
            }
        }
        return values;
    }

    private Object coerce(String s, Class<?> type) {
        if (type == String.class) return s;
        if (type == int.class || type == Integer.class) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }
        if (type == long.class || type == Long.class) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } }
        return s;
    }

    private boolean checkRateLimit(HandlerEntry e) {
        if (e.rateLimit <= 0) return true;
        String key = e.method.getName() + ":" + userIdTL.get();
        long now = System.currentTimeMillis();
        Long last = rateLimitMap.get(key);
        if (last != null && (now - last) < e.rateLimit * 1000L) {
            rateLimitHits.incrementAndGet();
            return false;
        }
        rateLimitMap.put(key, now);
        return true;
    }

    /** 检查 @RequireRole 注解 */
    private boolean checkRole(HandlerEntry e) {
        if (e.requiredRole == null || e.requiredRole.isEmpty()) return true;
        var evt = groupEventDtoTL.get();
        if (!(evt instanceof GroupMessageEvent g)) return false;
        String senderRole = g.getSenderRole();
        if (senderRole == null) senderRole = "member";
        return e.requiredRole.contains(senderRole);
    }

    private record HandlerEntry(Method method, Object instance, MessageFilter filter,
                                int order, int rateLimit, java.util.Set<String> requiredRole,
                                String pluginId, java.util.Set<String> platforms, Scope scope) {}

    /** 命令响应场景：GROUP 仅群聊 / PRIVATE 仅私聊 / BOTH 两者都响应。 */
    private enum Scope { GROUP, PRIVATE, BOTH }

    /** 提取 @RequireRole 要求的角色 */
    private static java.util.Set<String> roles(Method m) {
        var rr = m.getAnnotation(RequireRole.class);
        if (rr == null || rr.value().equals("MEMBER")) return java.util.Set.of();
        return java.util.Set.of(rr.value());
    }
}
