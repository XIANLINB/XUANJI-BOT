package dev.xuanji.sdk.msg;

/**
 * 按钮键盘构建器 — 配合 Markdown 消息使用。
 *
 * <pre>
 * String kb = XjKeyboard.create()
 *     .addButton("sign_in", "签到", "签到")
 *     .addButton("help", "帮助", "帮助")
 *     .build();
 * bot.replyMarkdown("请选择功能:", kb);
 * </pre>
 */
public class XjKeyboard {

    private final StringBuilder actions = new StringBuilder();
    private int count;

    public static XjKeyboard create() { return new XjKeyboard(); }

    /**
     * 添加按钮。
     * @param id   回调标识（插件收到后根据此 ID 判断）
     * @param label 按钮文字
     * @param data  携带数据
     */
    public XjKeyboard addButton(String id, String label, String data) {
        if (count > 0) actions.append(",");
        actions.append("{\"id\":\"").append(escape(id))
               .append("\",\"render_data\":{\"label\":\"").append(escape(label))
               .append("\",\"visited_label\":\"").append(escape(label))
               .append("\"},\"action\":{\"type\":0,\"permission\":{\"type\":2}")
               .append(",\"data\":\"").append(escape(data)).append("\"}}");
        count++;
        return this;
    }

    /** 返回 QQ 键盘 JSON */
    public String build() {
        return "{\"content\":{\"rows\":[{\"buttons\":[" + actions + "]}]}}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
