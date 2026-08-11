package XuanJi.sdk.msg;

/**
 * 图文卡片消息构建器（msg_type=8）。
 *
 * <pre>
 * String card = Card.create()
 *     .title("QQ开放平台").desc("2分钟完成注册")
 *     .picUrl("https://...").url("https://q.qq.com")
 *     .build();
 * bot.replyCard(card);
 * </pre>
 */
public class Card {
    private String title, desc, picUrl, url;

    public static Card create() { return new Card(); }
    public Card title(String v)  { this.title = v; return this; }
    public Card desc(String v)   { this.desc = v; return this; }
    public Card picUrl(String v) { this.picUrl = v; return this; }
    public Card url(String v)    { this.url = v; return this; }

    public String build() {
        StringBuilder sb = new StringBuilder("{\"type\":\"tuwen\",\"content\":{");
        sb.append("\"title\":\"").append(esc(title)).append("\"");
        if (desc != null)   sb.append(",\"description\":\"").append(esc(desc)).append("\"");
        if (picUrl != null) sb.append(",\"pic_url\":\"").append(esc(picUrl)).append("\"");
        if (url != null)    sb.append(",\"url\":\"").append(esc(url)).append("\"");
        sb.append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
