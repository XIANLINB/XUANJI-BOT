package dev.xuanji.sdk.bot;

/**
 * 璇玑 Bot 消息发送器 — 参考 Shiro 的 {@code Bot} 设计。
 *
 * <p>插件通过在方法参数声明 {@code XjBot} 获取实例，框架自动注入。
 *
 * <h3>使用示例</h3>
 * <pre>
 * @Command("hi")
 * public void hi(XjGroupMessageEvent event, XjBot bot) {
 *     bot.reply("你好 " + event.getSenderName());
 * }
 *
 * @Command("图")
 * public void img(XjBot bot) {
 *     bot.replyImage("https://example.com/pic.jpg");
 * }
 *
 * @Command("投票")
 * public void vote(XjBot bot) {
 *     String md = XjMarkdown.create().h1("投票").text("选项A / 选项B").build();
 *     String kb = XjKeyboard.create().addButton("A", "选A").addButton("B", "选B").build();
 *     bot.replyMarkdown(md, kb);
 * }
 * </pre>
 */
public abstract class XjBot {

    // ===== 文本 =====

    /** 回复当前消息（文本） */
    public abstract void reply(String text);

    // ===== Markdown =====

    /** 回复 Markdown 消息 */
    public abstract void replyMarkdown(String markdownContent);

    /** 回复 Markdown + 按钮键盘 */
    public abstract void replyMarkdown(String markdownContent, String keyboardJson);

    // ===== 富媒体 =====

    /** 回复图片 */
    public abstract void replyImage(String url);

    /** 回复语音 */
    public abstract void replyAudio(String url);

    /** 回复视频 */
    public abstract void replyVideo(String url);

    // ===== Ark =====

    /** 回复 Ark 模板消息 */
    public abstract void replyArk(String templateId, String kvJson);
}
