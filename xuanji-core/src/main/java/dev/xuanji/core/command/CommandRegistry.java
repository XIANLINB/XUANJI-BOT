package dev.xuanji.core.command;

import dev.xuanji.api.annotation.*;
import dev.xuanji.api.dto.GroupMessageEvent;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令注册表 — 扫描 @Command 方法，建立指令名→处理器的映射。
 *
 * <p>参数注入支持：{@code @Arg String}, {@code int/long/double}, {@code BotEvent}, {@code MessageSender}。
 */
@Slf4j
@Component
public class CommandRegistry {

    private final Map<String, List<HandlerEntry>> map = new ConcurrentHashMap<>();
    /** @GroupMessageHandler 注解方法列表（无前缀限制，每条消息都尝试匹配） */
    private final List<HandlerEntry> groupHandlers = new CopyOnWriteArrayList<>();

    /** 注册一个插件对象中的所有 @Command 和 @GroupMessageHandler 方法。 */
    public void register(Object pluginInstance) {
        for (Method m : pluginInstance.getClass().getDeclaredMethods()) {
            Command cmd = m.getAnnotation(Command.class);
            if (cmd != null) {
                HandlerEntry entry = new HandlerEntry(null, m, pluginInstance, cmd, null);
                if (cmd.match() == Command.Match.EXACT) {
                    map.computeIfAbsent(cmd.value(), k -> new ArrayList<>()).add(entry);
                } else {
                    groupHandlers.add(entry);
                    groupHandlers.sort(Comparator.comparingInt(e -> e.gmhAnnotation != null ? e.gmhAnnotation.order() : 0));
                }
                log.info("[Command] 注册: {} (match={}) → {}.{}", cmd.value(), cmd.match(),
                        pluginInstance.getClass().getSimpleName(), m.getName());
                continue;
            }

            GroupMessageHandler gmh = m.getAnnotation(GroupMessageHandler.class);
            if (gmh != null) {
                HandlerFilter filter = m.getAnnotation(HandlerFilter.class);
                HandlerEntry entry = new HandlerEntry(gmh, m, pluginInstance, null, filter);
                groupHandlers.add(entry);
                groupHandlers.sort(Comparator.comparingInt(e -> e.gmhAnnotation != null ? e.gmhAnnotation.order() : 0));
                log.info("[Handler] 注册群聊处理器: {}.{}", pluginInstance.getClass().getSimpleName(), m.getName());
            }
        }
        map.values().forEach(l -> l.sort(Comparator.comparingInt(e -> -e.cmdAnnotation.priority())));
    }

    // ==================== 上下文（ThreadLocal，GroupMessageHandler 设置） ====================

    private static final ThreadLocal<String> userIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> botKeyTL = new ThreadLocal<>();
    private static final ThreadLocal<String> groupIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> msgIdTL = new ThreadLocal<>();
    private static final ThreadLocal<GroupMessageEvent> eventDtoTL = new ThreadLocal<>();
    private static final ThreadLocal<Bot> botTL = new ThreadLocal<>();

    public static void setContext(String botKey, String groupId, String msgId, String userId,
                                  GroupMessageEvent eventDto, Bot bot) {
        botKeyTL.set(botKey); groupIdTL.set(groupId);
        msgIdTL.set(msgId); userIdTL.set(userId);
        eventDtoTL.set(eventDto); botTL.set(bot);
    }
    public static void clearContext() {
        botKeyTL.remove(); groupIdTL.remove(); msgIdTL.remove();
        userIdTL.remove(); eventDtoTL.remove(); botTL.remove();
    }

    public static String getCurrentUser()   { return userIdTL.get(); }
    public static String getCurrentBotKey() { return botKeyTL.get(); }
    public static String getCurrentGroupId(){ return groupIdTL.get(); }
    public static String getCurrentMsgId()  { return msgIdTL.get(); }

    // ==================== 指令执行 ====================

    /**
     * 匹配并执行指令。
     *
     * <p>返回值由 GroupMessageHandler 处理：String → 发文本；null → 未匹配。
     */
    public String execute(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String trimmed = rawText.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String cmdName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        List<HandlerEntry> entries = map.get(cmdName);
        if (entries == null) return null;

        for (HandlerEntry entry : entries) {
            try {
                Object[] methodArgs = resolveArgs(entry.method, args);
                Object result = entry.method.invoke(entry.instance, methodArgs);
                return result != null ? result.toString() : null;
            } catch (Exception e) {
                log.warn("[Command] {} 执行失败: {}", cmdName, e.getMessage());
            }
        }
        // 2. 尝试非精确模式 @Command + @GroupMessageHandler
        for (HandlerEntry entry : groupHandlers) {
            try {
                // @Command 非精确匹配模式
                if (entry.cmdAnnotation != null) {
                    if (!matchCommand(entry.cmdAnnotation, trimmed)) continue;
                }
                // @GroupMessageHandler 过滤
                if (entry.filterAnnotation != null) {
                    if (!matchFilter(entry.filterAnnotation)) continue;
                }
                Object[] methodArgs = resolveArgs(entry.method, trimmed);
                entry.method.invoke(entry.instance, methodArgs);
                return null; // void 方法已自行发送，不返回文本
            } catch (Exception e) {
                log.debug("[Handler] 执行失败: {}", e.getMessage());
            }
        }

        return null;
    }

    private boolean matchCommand(Command cmd, String text) {
        return switch (cmd.match()) {
            case EXACT -> text.startsWith(cmd.value() + " ") || text.equals(cmd.value());
            case PREFIX -> text.startsWith(cmd.value());
            case SUFFIX -> text.endsWith(cmd.value());
            case CONTAINS -> text.contains(cmd.value());
            case REGEX -> text.matches(cmd.value());
        };
    }

    /** 解析方法参数：@Arg String → 文本分段；BotEvent → 当前事件；MessageSender → 发送器 */
    private Object[] resolveArgs(Method method, String args) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        String[] argParts = splitArgs(args);

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();

            // 注入 XjGroupMessageEvent（SDK 事件封装）
            if (GroupMessageEvent.class.isAssignableFrom(type)) {
                values[i] = eventDtoTL.get() != null
                        ? new GroupMessageEvent(eventDtoTL.get()) : null;
                continue;
            }

            // 注入 Bot（SDK 消息发送器）
            if (Bot.class.isAssignableFrom(type)) {
                values[i] = botTL.get();
                continue;
            }

            // @Arg 参数绑定
            Arg arg = params[i].getAnnotation(Arg.class);
            if (arg != null) {
                String raw = i < argParts.length ? argParts[i] : null;
                if (raw == null || raw.isEmpty()) {
                    if (arg.required()) return new Object[]{
                        "缺少参数: " + arg.value() + (arg.missing().isEmpty() ? "" : " - " + arg.missing())
                    };
                    values[i] = null;
                } else {
                    values[i] = coerce(raw, type);
                }
            }
        }
        return values;
    }

    private String[] splitArgs(String args) {
        if (args == null || args.isBlank()) return new String[0];
        return args.split("\\s+");
    }

    private Object coerce(String s, Class<?> type) {
        if (type == String.class) return s;
        if (type == int.class || type == Integer.class) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }
        if (type == long.class || type == Long.class) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } }
        if (type == double.class || type == Double.class) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; } }
        return s;
    }

    /** 检查 @HandlerFilter 条件是否满足 */
    private boolean matchFilter(HandlerFilter f) {
        if (f == null) return true;
        GroupMessageEvent evt = eventDtoTL.get();
        String text = evt != null ? evt.getPlainTextContent().trim() : "";

        // cmd 正则匹配
        if (!f.cmd().isEmpty()) {
            if (!text.matches(".*(" + f.cmd() + ").*")) return false;
        }

        // @模式
        if (f.at() == AtMode.NEED && (evt == null || !evt.isAtBot())) return false;
        if (f.at() == AtMode.NOT && evt != null && evt.isAtBot()) return false;

        // 限定群
        if (f.groups().length > 0 && evt != null) {
            if (!Arrays.asList(f.groups()).contains(evt.getGroupOpenid())) return false;
        }

        // 限定发送者
        if (f.senders().length > 0) {
            String uid = userIdTL.get();
            if (uid == null || !Arrays.asList(f.senders()).contains(uid)) return false;
        }

        // 前缀
        if (!f.startWith().isEmpty() && !text.startsWith(f.startWith())) return false;

        // 后缀
        if (!f.endWith().isEmpty() && !text.endsWith(f.endWith())) return false;

        return true;
    }

    private record HandlerEntry(
            Command cmdAnnotation,
            Method method, Object instance,
            GroupMessageHandler gmhAnnotation,
            HandlerFilter filterAnnotation) {}

    // 兼容旧的构造（只有 @Command）
    private record CmdOnly(Command annotation, Method method, Object instance) {}
}
