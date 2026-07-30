package com.qunxing.qq_bot_xuanji.utils;

/**
 * JSON 字符串转义工具
 *
 * <p>统一处理 JSON 值中的特殊字符转义，避免各插件重复实现。
 * 转义后的字符串可安全嵌入 JSON 的双引号值中。
 *
 * <h3>处理的特殊字符</h3>
 * <ul>
 *   <li>反斜杠 {@code \} → {@code \\}</li>
 *   <li>双引号 {@code "} → {@code \"}</li>
 *   <li>换行符 {@code \n} → {@code \\n}</li>
 *   <li>回车符 {@code \r} → {@code \\r}</li>
 *   <li>制表符 {@code \t} → {@code \\t}</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link ArkBuilder} — 转义 Ark 消息的 KV 值</li>
 *   <li>{@link CardBuilder} — 转义卡片消息的字段值</li>
 *   <li>{@link EmbedBuilder} — 转义 Embed 消息的字段值</li>
 *   <li>{@link KeyboardBuilder} — 转义按钮的 label 和 data</li>
 *   <li>{@link MarkdownBuilder} — 转义 Markdown 内容</li>
 * </ul>
 */
public final class JsonEscUtil {

    /** 私有构造，防止实例化（纯工具类） */
    private JsonEscUtil() {}

    /**
     * 转义 JSON 字符串值中的特殊字符
     *
     * <p>null 值会被转换为空字符串。
     *
     * @param s 原始字符串（可为 null）
     * @return 转义后的字符串（不会为 null）
     */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
