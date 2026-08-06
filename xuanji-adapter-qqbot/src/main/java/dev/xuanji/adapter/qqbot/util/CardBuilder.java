package dev.xuanji.adapter.qqbot.util;

/**
 * QQ 图文卡片消息构建器 (msg_type=8)
 *
 * <p>用于构建群聊专属的图文卡片消息。卡片消息包含标题、描述、图片和跳转链接，
 * 视觉效果优于纯文本消息。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 简单卡片
 * String json = CardBuilder.create()
 *     .title("QQ开放平台")
 *     .description("2分钟完成注册并创建QQBot")
 *     .picUrl("https://example.com/image.png")
 *     .url("https://q.qq.com")
 *     .build();
 *
 * // 构建完整请求体
 * String requestBody = CardBuilder.create()
 *     .title("排行榜")
 *     .description("金币排行榜 TOP10")
 *     .picUrl(rankImageUrl)
 *     .buildRequestBody(msgId); // msgId 为被动回复时的消息 ID
 * }</pre>
 *
 * <h3>限制</h3>
 * <ul>
 *   <li>仅群聊支持 (msg_type=8)</li>
 *   <li>单聊和频道不支持</li>
 *   <li>title 最长 256 字符</li>
 *   <li>description 最长 512 字符</li>
 * </ul>
 *
 * @see ArkBuilder      Ark 消息构建器（模板消息）
 * @see EmbedBuilder    Embed 消息构建器（频道专属）
 */
public class CardBuilder {

    /** 卡片标题（最长 256 字符） */
    private String title;

    /** 卡片描述（最长 512 字符） */
    private String description;

    /** 卡片图片 URL（必须是可访问的 HTTPS 地址） */
    private String picUrl;

    /** 点击卡片后跳转的 URL */
    private String url;

    private CardBuilder() {}

    /**
     * 创建卡片构建器实例
     *
     * @return 新的 CardBuilder 实例
     */
    public static CardBuilder create() {
        return new CardBuilder();
    }

    /**
     * 设置卡片标题
     *
     * @param title 标题文本（最长 256 字符）
     * @return 当前构建器实例（链式调用）
     */
    public CardBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * 设置卡片描述
     *
     * @param description 描述文本（最长 512 字符）
     * @return 当前构建器实例（链式调用）
     */
    public CardBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 设置卡片图片 URL
     *
     * @param picUrl 图片 URL（必须是可访问的 HTTPS 地址）
     * @return 当前构建器实例（链式调用）
     */
    public CardBuilder picUrl(String picUrl) {
        this.picUrl = picUrl;
        return this;
    }

    /**
     * 设置卡片跳转链接
     *
     * @param url 点击卡片后跳转的 URL
     * @return 当前构建器实例（链式调用）
     */
    public CardBuilder url(String url) {
        this.url = url;
        return this;
    }

    /**
     * 构建卡片 JSON 字符串
     *
     * <p>生成的 JSON 结构：
     * <pre>
     * {"type":"tuwen","content":{"title":"...","description":"...","pic_url":"...","url":"..."}}
     * </pre>
     *
     * @return 完整的 card JSON 字符串
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"tuwen\",\"content\":{");

        boolean hasPrev = false;

        if (title != null && !title.isEmpty()) {
            sb.append("\"title\":\"").append(JsonEscUtil.esc(title)).append("\"");
            hasPrev = true;
        }

        if (description != null && !description.isEmpty()) {
            if (hasPrev) sb.append(",");
            sb.append("\"description\":\"").append(JsonEscUtil.esc(description)).append("\"");
            hasPrev = true;
        }

        if (picUrl != null && !picUrl.isEmpty()) {
            if (hasPrev) sb.append(",");
            sb.append("\"pic_url\":\"").append(JsonEscUtil.esc(picUrl)).append("\"");
            hasPrev = true;
        }

        if (url != null && !url.isEmpty()) {
            if (hasPrev) sb.append(",");
            sb.append("\"url\":\"").append(JsonEscUtil.esc(url)).append("\"");
        }

        sb.append("}}");
        return sb.toString();
    }

    /**
     * 构建完整的发送消息请求体（msg_type=8）
     *
     * @param msgId 被动回复的消息 ID（可为 null 表示主动消息）
     * @return 完整的请求体 JSON
     */
    public String buildRequestBody(String msgId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"msg_type\":8,\"card\":").append(build());
        if (msgId != null && !msgId.isEmpty()) {
            sb.append(",\"msg_id\":\"").append(JsonEscUtil.esc(msgId)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
