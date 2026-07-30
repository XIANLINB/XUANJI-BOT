package dev.xuanji.core.command;

import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.dto.GroupMessageEvent;
import dev.xuanji.core.bot.QqXjBot;
import dev.xuanji.sdk.bot.XjBot;
import dev.xuanji.sdk.event.XjGroupMessageEvent;
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

    /** 注册一个插件对象中的所有 @Command 方法。 */
    public void register(Object pluginInstance) {
        for (Method m : pluginInstance.getClass().getDeclaredMethods()) {
            Command cmd = m.getAnnotation(Command.class);
            if (cmd == null) continue;

            HandlerEntry entry = new HandlerEntry(cmd, m, pluginInstance);
            map.computeIfAbsent(cmd.value(), k -> new ArrayList<>()).add(entry);
            for (String alias : cmd.alias()) {
                map.computeIfAbsent(alias, k -> new ArrayList<>()).add(entry);
            }
            log.info("[Command] 注册: {} → {}.{}", cmd.value(),
                    pluginInstance.getClass().getSimpleName(), m.getName());
        }
        map.values().forEach(l -> l.sort(Comparator.comparingInt(e -> -e.annotation.priority())));
    }

    // ==================== 上下文（ThreadLocal，GroupMessageHandler 设置） ====================

    private static final ThreadLocal<String> userIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> botKeyTL = new ThreadLocal<>();
    private static final ThreadLocal<String> groupIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> msgIdTL = new ThreadLocal<>();
    private static final ThreadLocal<GroupMessageEvent> eventDtoTL = new ThreadLocal<>();
    private static final ThreadLocal<XjBot> botTL = new ThreadLocal<>();

    public static void setContext(String botKey, String groupId, String msgId, String userId,
                                  GroupMessageEvent eventDto, XjBot bot) {
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
    public static BotEvent getCurrentEvent(){ return eventTL.get(); }
    public static MessageSender getCurrentSender() { return senderTL.get(); }

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
        return null;
    }

    /** 解析方法参数：@Arg String → 文本分段；BotEvent → 当前事件；MessageSender → 发送器 */
    private Object[] resolveArgs(Method method, String args) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        String[] argParts = splitArgs(args);

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();

            // 注入 XjGroupMessageEvent（SDK 事件封装）
            if (XjGroupMessageEvent.class.isAssignableFrom(type)) {
                values[i] = eventDtoTL.get() != null
                        ? new XjGroupMessageEvent(eventDtoTL.get()) : null;
                continue;
            }

            // 注入 XjBot（SDK 消息发送器）
            if (XjBot.class.isAssignableFrom(type)) {
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

    private record HandlerEntry(Command annotation, Method method, Object instance) {}
}
