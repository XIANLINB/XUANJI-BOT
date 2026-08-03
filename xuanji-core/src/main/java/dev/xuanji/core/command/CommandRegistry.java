package dev.xuanji.core.command;

import dev.xuanji.api.annotation.*;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
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
    private final Map<String, Long> rateLimitMap = new ConcurrentHashMap<>();
    private final java.util.Set<String> disabledPlugins = ConcurrentHashMap.newKeySet();

    public void setPluginEnabled(String pluginId, boolean enabled) {
        if (enabled) disabledPlugins.remove(pluginId);
        else disabledPlugins.add(pluginId);
    }
    public boolean isPluginEnabled(String pluginId) { return !disabledPlugins.contains(pluginId); }

    public void register(Object pluginInstance, String pluginId) {
        int rateLimit = 0;
        java.util.Set<String> pluginPlatforms = java.util.Set.of();
        XuanjiPlugin plg = pluginInstance.getClass().getAnnotation(XuanjiPlugin.class);
        if (plg != null) {
            rateLimit = plg.rateLimit();
            if (plg.platforms().length > 0) pluginPlatforms = java.util.Set.of(plg.platforms());
        }

        for (Method m : pluginInstance.getClass().getDeclaredMethods()) {
            MessageFilter filter = m.getAnnotation(MessageFilter.class);
            // 平台白名单：handler 级注解优先，插件级 @XuanjiPlugin(platforms=) 兜底；两者皆空 = 全平台。
            java.util.Set<String> methodPlatforms = resolvePlatforms(m, pluginPlatforms);

            if (m.isAnnotationPresent(GroupMessage.class)) {
                groupMsgHandlers.add(new HandlerEntry(m, pluginInstance, filter,
                        m.getAnnotation(GroupMessage.class).order(), rateLimit, roles(m), pluginId, methodPlatforms));
                log.info("[Handler] 注册群聊消息: {}.{} (rateLimit={}s, platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), rateLimit, methodPlatforms);
            }
            if (m.isAnnotationPresent(PrivateMessage.class)) {
                privateMsgHandlers.add(new HandlerEntry(m, pluginInstance, filter, m.getAnnotation(PrivateMessage.class).order(), rateLimit, roles(m), pluginId, methodPlatforms));
                log.info("[Handler] 注册私聊消息: {}.{} (rateLimit={}s, platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), rateLimit, methodPlatforms);
            }
            if (m.isAnnotationPresent(GroupEvent.class)) {
                groupEventHandlers.add(new HandlerEntry(m, pluginInstance, null, m.getAnnotation(GroupEvent.class).order(), rateLimit, roles(m), pluginId, methodPlatforms));
                log.info("[Handler] 注册群事件: {}.{} (platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), methodPlatforms);
            }
            if (m.isAnnotationPresent(PrivateEvent.class)) {
                privateEventHandlers.add(new HandlerEntry(m, pluginInstance, null, m.getAnnotation(PrivateEvent.class).order(), rateLimit, roles(m), pluginId, methodPlatforms));
                log.info("[Handler] 注册私聊事件: {}.{} (platforms={})", pluginInstance.getClass().getSimpleName(), m.getName(), methodPlatforms);
            }
        }
        groupMsgHandlers.sort(Comparator.comparingInt(e -> e.order));
        privateMsgHandlers.sort(Comparator.comparingInt(e -> e.order));
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
        log.info("[Handler] 已注销: {}", pluginInstance.getClass().getSimpleName());
    }

    // ==================== 上下文 ====================

    private static final ThreadLocal<String> userIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> botKeyTL = new ThreadLocal<>();
    private static final ThreadLocal<String> groupIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> msgIdTL = new ThreadLocal<>();
    private static final ThreadLocal<GroupMessageEvent> groupEventDtoTL = new ThreadLocal<>();
    private static final ThreadLocal<Bot> botTL = new ThreadLocal<>();
    private static final ThreadLocal<String> currentPlatformTL = new ThreadLocal<>();

    public static void setContext(String botKey, String groupId, String msgId, String userId,
                                  GroupMessageEvent eventDto, Bot bot, String platform) {
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
                e.method.invoke(e.instance, resolveArgs(e.method, "", event));
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
                if (!isPluginEnabled(e.pluginId)) continue;
                if (!checkRateLimit(e)) continue;
                if (!checkRole(e)) continue;  // @RequireRole
                Object[] ma = resolveArgs(e.method, trimmed, groupEventDtoTL.get());
                Object result = e.method.invoke(e.instance, ma);
                if (result != null) return result.toString();
            } catch (Exception ex) {
                log.warn("[Handler] {}: {}", e.method.getName(), ex.getMessage());
            }
        }
        return null;
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

        if (isGroup && f.at() != AtMode.IGNORE && groupEventDtoTL.get() != null) {
            boolean atBot = groupEventDtoTL.get().isAtBot();
            if (f.at() == AtMode.NEED && !atBot) match = false;
            if (f.at() == AtMode.NOT && atBot) match = false;
        }

        if (f.groups().length > 0 && groupEventDtoTL.get() != null) {
            if (!Arrays.asList(f.groups()).contains(groupEventDtoTL.get().getGroupId())) match = false;
        }
        if (f.senders().length > 0) {
            if (!Arrays.asList(f.senders()).contains(userIdTL.get())) match = false;
        }
        if (f.roles().length > 0 && groupEventDtoTL.get() != null) {
            String role = groupEventDtoTL.get().getSenderRole();
            if (role == null || !Arrays.asList(f.roles()).contains(role)) match = false;
        }

        return f.invert() != match;
    }

    // ==================== 参数注入 ====================

    private Object[] resolveArgs(Method method, String args, dev.xuanji.sdk.event.GroupMessageEvent event) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        String[] argParts = args != null ? args.split("\\s+") : new String[0];

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (dev.xuanji.sdk.event.GroupMessageEvent.class.isAssignableFrom(type)) {
                values[i] = event; continue;
            }
            if (Bot.class.isAssignableFrom(type)) {
                values[i] = botTL.get(); continue;
            }
            Arg arg = params[i].getAnnotation(Arg.class);
            if (arg != null) {
                String raw = i < argParts.length ? argParts[i] : null;
                if (raw == null || raw.isEmpty()) {
                    if (arg.required()) return new Object[]{"缺少参数: " + arg.value()};
                    values[i] = null;
                } else {
                    values[i] = coerce(raw, type);
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
        if (last != null && (now - last) < e.rateLimit * 1000L) return false;
        rateLimitMap.put(key, now);
        return true;
    }

    /** 检查 @RequireRole 注解 */
    private boolean checkRole(HandlerEntry e) {
        if (e.requiredRole == null || e.requiredRole.isEmpty()) return true;
        var evt = groupEventDtoTL.get();
        if (evt == null) return false;
        String senderRole = evt.getSenderRole();
        if (senderRole == null) senderRole = "member";
        return e.requiredRole.contains(senderRole);
    }

    private record HandlerEntry(Method method, Object instance, MessageFilter filter,
                                int order, int rateLimit, java.util.Set<String> requiredRole,
                                String pluginId, java.util.Set<String> platforms) {}

    /** 提取 @RequireRole 要求的角色 */
    private static java.util.Set<String> roles(Method m) {
        var rr = m.getAnnotation(RequireRole.class);
        if (rr == null || rr.value().equals("MEMBER")) return java.util.Set.of();
        return java.util.Set.of(rr.value());
    }
}
