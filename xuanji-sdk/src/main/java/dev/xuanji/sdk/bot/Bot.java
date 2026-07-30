package dev.xuanji.sdk.bot;

/**
 * 璇玑 Bot 消息发送器。
 *
 * <h3>文本</h3>
 * <pre>bot.reply("文本");</pre>
 *
 * <h3>Markdown</h3>
 * <pre>bot.replyMarkdown(md); bot.replyMarkdown(md, keyboardJson);</pre>
 *
 * <h3>富媒体</h3>
 * <pre>bot.replyImage(url); bot.replyAudio(url); bot.replyVideo(url);</pre>
 *
 * <h3>Ark 模板</h3>
 * <pre>bot.replyArk(templateId, arkJson);</pre>
 *
 * <h3>图文卡片 (msg_type=8)</h3>
 * <pre>bot.replyCard(cardJson);</pre>
 *
 * <h3>媒体上传</h3>
 * <pre>String fileId = bot.uploadImage(filePath);  // 返回 file_info 用于后续发送</pre>
 */
public abstract class Bot {

    // ===== 文本 =====
    public abstract void reply(String text);

    // ===== Markdown =====
    public abstract void replyMarkdown(String markdownContent);
    public abstract void replyMarkdown(String markdownContent, String keyboardJson);

    // ===== 富媒体 =====
    public abstract void replyImage(String url);
    public abstract void replyAudio(String url);
    public abstract void replyVideo(String url);

    // ===== Ark =====
    /** 回复 Ark 模板消息（传入 Ark.Ark24/Ark23/Ark37.build() 的结果） */
    public abstract void replyArk(int templateId, String arkJson);

    // ===== 图文卡片 =====
    /** 回复图文卡片消息（msg_type=8） */
    public abstract void replyCard(String cardJson);

    // ===== 媒体上传（返回 file_info 字符串，可用于后续发送） =====
    /** 上传图片，返回 file_info */
    public abstract String uploadImage(String filePath);
    /** 上传视频 */
    public abstract String uploadVideo(String filePath);
    /** 上传语音 */
    public abstract String uploadAudio(String filePath);
    /** 上传文件（最大 200MB） */
    public abstract String uploadFile(String filePath);
}
