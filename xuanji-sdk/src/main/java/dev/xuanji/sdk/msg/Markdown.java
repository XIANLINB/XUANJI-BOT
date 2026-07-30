package dev.xuanji.sdk.msg;

/**
 * Markdown 消息构建器 — 链式调用构建 QQ Markdown 消息。
 *
 * <pre>
 * String md = Markdown.create()
 *     .h1("标题")
 *     .text("普通文本 **加粗** *斜体*")
 *     .code("print('hello')")
 *     .divider()
 *     .link("文档", "https://bot.q.qq.com")
 *     .build();
 * bot.replyMarkdown(md);
 * </pre>
 */
public class Markdown {

    private final StringBuilder sb = new StringBuilder();

    public static Markdown create() { return new Markdown(); }

    public Markdown h1(String text) { sb.append("# ").append(text).append("\n\n"); return this; }
    public Markdown h2(String text) { sb.append("## ").append(text).append("\n\n"); return this; }
    public Markdown h3(String text) { sb.append("### ").append(text).append("\n\n"); return this; }
    public Markdown text(String text) { sb.append(text).append("\n\n"); return this; }
    public Markdown bold(String title, String content) {
        sb.append("**").append(title).append("**: ").append(content).append("\n\n"); return this;
    }
    public Markdown quote(String text) { sb.append("> ").append(text).append("\n\n"); return this; }
    public Markdown code(String code) { sb.append("```\n").append(code).append("\n```\n\n"); return this; }
    public Markdown divider() { sb.append("---\n\n"); return this; }
    public Markdown link(String name, String url) {
        sb.append("[").append(name).append("](").append(url).append(")\n\n"); return this;
    }
    public Markdown newline() { sb.append("\n"); return this; }

    public String build() {
        String s = sb.toString();
        // 去掉末尾多余换行
        while (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
