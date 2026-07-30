package dev.xuanji.sdk.bot;

/**
 * 璇玑 Bot 消息发送器 — 被动回复 + 主动发送。
 *
 * <h3>被动回复（带 msg_id，仅事件��理链内可用）</h3>
 * <pre>bot.reply("文本");
 * bot.replyMarkdown(md);
 * bot.replyImage(url);</pre>
 *
 * <h3>主动发送（不依赖当前事件，定时任务/推送等场景）</h3>
 * <pre>bot.sendGroup("群ID", "文本");
 * bot.sendGroupImage("群ID", url);
 * bot.sendPrivate("用户ID", "文本");</pre>
 *
 * <h3>媒体上传</h3>
 * <pre>String fileId = bot.uploadImage("C:/pic.png");</pre>
 */
public abstract class Bot {

    // ==================== 被动回复（reply* 系列） ====================

    public abstract void reply(String text);
    public abstract void replyMarkdown(String markdownContent);
    public abstract void replyMarkdown(String markdownContent, String keyboardJson);
    public abstract void replyImage(String url);
    public abstract void replyAudio(String url);
    public abstract void replyVideo(String url);
    public abstract void replyArk(int templateId, String arkJson);
    public abstract void replyCard(String cardJson);

    // ==================== 主动发送（send* 系列，需指定目标） ====================

    /** 主动发送群聊文本 */
    public abstract void sendGroup(String groupId, String text);
    /** 主动发送群聊 Markdown */
    public abstract void sendGroupMarkdown(String groupId, String markdownContent);
    /** 主动发送群聊 Markdown + 键盘 */
    public abstract void sendGroupMarkdown(String groupId, String markdownContent, String keyboardJson);
    /** 主动发送群聊图片 */
    public abstract void sendGroupImage(String groupId, String url);
    /** 主动发送群聊语音 */
    public abstract void sendGroupAudio(String groupId, String url);
    /** 主动发送群聊视频 */
    public abstract void sendGroupVideo(String groupId, String url);
    /** 主动发送群聊 Ark */
    public abstract void sendGroupArk(String groupId, int templateId, String arkJson);
    /** 主动发送群聊图文卡片 */
    public abstract void sendGroupCard(String groupId, String cardJson);

    /** 主动发送私聊文本 */
    public abstract void sendPrivate(String userId, String text);
    /** 主动发送私聊 Markdown */
    public abstract void sendPrivateMarkdown(String userId, String markdownContent);
    /** 主动发送私聊图片 */
    public abstract void sendPrivateImage(String userId, String url);
    /** 主动发送私聊语音 */
    public abstract void sendPrivateAudio(String userId, String url);

    // ==================== 媒体上传 ====================

    public abstract String uploadImage(String filePath);
    public abstract String uploadVideo(String filePath);
    public abstract String uploadAudio(String filePath);
    public abstract String uploadFile(String filePath);
}
