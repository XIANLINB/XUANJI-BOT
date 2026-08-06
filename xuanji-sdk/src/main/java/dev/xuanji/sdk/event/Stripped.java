package dev.xuanji.sdk.event;

/**
 * 剥离后的消息文本 — 借鉴 Koishi {@code Session.stripped}。
 *
 * <p>将消息中的「呼叫信号」（@机器人 / 命令前缀）与「正文」分离，
 * 插件无需自己处理 @ 判断与前缀裁剪：
 * <pre>{@code
 *   if (event.getStripped().appel()) { ... }   // 是否被 @
 *   String cmd = event.getStripped().content(); // 已裁掉前缀的纯命令文本
 * }</pre>
 */
public record Stripped(
        /** 去除命令前缀后的纯文本（@标签已由 DTO 层剥离） */
        String content,
        /** 命中的命令前缀（如 "/"，未命中则为空串） */
        String prefix,
        /** 是否被呼叫（@了机器人或被昵称提及） */
        boolean appel,
        /** 是否包含 @ 提及 */
        boolean hasAt,
        /** 是否 @ 了机器人自身 */
        boolean atSelf
) {}
