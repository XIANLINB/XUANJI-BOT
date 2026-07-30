package dev.xuanji.core.command;

import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.context.BotContext;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.adapter.qq.api.MessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 指令注册表 — 扫描 @Command 方法，建立指令名→处理器的映射。
 *
 * <p>启动时通过 {@link #register(Object)} 注册所有 @XuanjiPlugin 插件 Bean。
 */
@Slf4j
@Component
public class CommandRegistry {

    /** 指令名 → 处理器条目列表（同名按 priority 排序） */
    private final Map<String, List<HandlerEntry>> map = new ConcurrentHashMap<>();

    /**
     * 注册一个插件对象中的所有 @Command 方法。
     */
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
        // 每指令按 priority 降序排列
        map.values().forEach(l -> l.sort(Comparator.comparingInt(e -> -e.annotation.priority())));
    }

    /**
     * 匹配并执行指令。
     *
     * @param rawText 用户消息纯文本
     * @return 执行结果文本，匹配失败返回 null
     */
    public String execute(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String trimmed = rawText.trim();

        // P3 过渡：为旧 MessageSender 设置 ThreadLocal 上下文（由外层 GroupMessageHandler 保证）
        // CommandRegistry 作为纯匹配引擎，不依赖 ScopedValue
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

    private Object[] resolveArgs(Method method, String args) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        String[] argParts = splitArgs(args);

        for (int i = 0; i < params.length; i++) {
            Arg arg = params[i].getAnnotation(Arg.class);
            if (arg != null) {
                String raw = i < argParts.length ? argParts[i] : null;
                if (raw == null || raw.isEmpty()) {
                    if (arg.required()) {
                        return new Object[]{ "缺少参数: " + arg.value() +
                                (arg.missing().isEmpty() ? "" : " - " + arg.missing()) };
                    }
                    values[i] = null;
                } else {
                    values[i] = coerce(raw, params[i].getType());
                }
            }
        }
        return values;
    }

    private String[] splitArgs(String args) {
        if (args == null || args.isBlank()) return new String[0];
        // 简单空格分割（后续支持引号包裹）
        return args.split("\\s+");
    }

    private Object coerce(String s, Class<?> type) {
        if (type == String.class) return s;
        if (type == int.class || type == Integer.class) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }
        if (type == long.class || type == Long.class) { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } }
        if (type == double.class || type == Double.class) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; } }
        if (type == LocalDate.class) { try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; } }
        if (type == LocalDateTime.class) { try { return LocalDateTime.parse(s); } catch (DateTimeParseException e) { return null; } }
        return s;
    }

    private record HandlerEntry(Command annotation, Method method, Object instance) {}
}
