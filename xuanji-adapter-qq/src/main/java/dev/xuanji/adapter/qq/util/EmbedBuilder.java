package dev.xuanji.adapter.qq.util;

/**
 * QQ Embed 消息构建器（频道专属）
 *
 * <p>Embed 消息是 QQ 频道中使用的一种结构化消息格式，
 * 类似于 Discord 的 Embed，支持标题、弹窗提示、缩略图和字段列表。
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>服务器状态展示（CPU/内存/在线人数等）</li>
 *   <li>数据统计卡片（排行榜、签到统计等）</li>
 *   <li>信息公告（带结构化字段的通知）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String json = EmbedBuilder.create()
 *     .title("服务器状态")
 *     .prompt("服务器运行正常")
 *     .thumbnail("https://example.com/icon.png")
 *     .field("CPU", "15%", true)
 *     .field("内存", "2.1GB / 8GB", true)
 *     .field("在线人数", "128", true)
 *     .build();
 * }</pre>
 *
 * <h3>与 ArkBuilder 的区别</h3>
 * <ul>
 *   <li>Embed — 频道专属，结构化字段展示，视觉效果好</li>
 *   <li>Ark — 通用模板消息，需要预先在平台创建模板</li>
 * </ul>
 *
 * @see ArkBuilder   Ark 消息构建器
 * @see CardBuilder  图文卡片消息构建器（群聊专属）
 */
public class EmbedBuilder {

    /** 标题 — 显示在 Embed 顶部 */
    private String title;

    /** 弹窗内容 — 消息气泡中的提示文本 */
    private String prompt;

    /** 缩略图 URL — 显示在 Embed 右侧的小图 */
    private String thumbnail;

    /** 字段列表 — 每个字段为 [name, value, inline] 三元组 */
    private final java.util.List<String[]> fields = new java.util.ArrayList<>();

    /** 私有构造，通过 {@link #create()} 创建实例 */
    private EmbedBuilder() {}

    /**
     * 创建 Embed 构建器实例
     *
     * @return 新的 EmbedBuilder 实例
     */
    public static EmbedBuilder create() {
        return new EmbedBuilder();
    }

    /**
     * 设置标题
     *
     * @param title 标题文本（显示在 Embed 顶部，支持加粗）
     * @return 当前构建器实例（链式调用）
     */
    public EmbedBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * 设置弹窗内容
     *
     * <p>当消息发送后，弹窗内容会显示在消息气泡中。
     * 如果不设置，则不显示弹窗。
     *
     * @param prompt 弹窗提示文本
     * @return 当前构建器实例（链式调用）
     */
    public EmbedBuilder prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * 设置缩略图 URL
     *
     * <p>缩略图会显示在 Embed 右侧，建议使用正方形图片。
     *
     * @param url 图片 URL（必须是可访问的 HTTPS 地址）
     * @return 当前构建器实例（链式调用）
     */
    public EmbedBuilder thumbnail(String url) {
        this.thumbnail = url;
        return this;
    }

    /**
     * 添加字段（完整参数）
     *
     * <p>字段是 Embed 中的核心展示单元，每个字段包含名称和值。
     * inline=true 时字段会水平排列，inline=false 时独占一行。
     *
     * @param name   字段名称（显示为标签）
     * @param value  字段值（显示为内容）
     * @param inline 是否行内显示：true=水平排列，false=独占一行
     * @return 当前构建器实例（链式调用）
     */
    public EmbedBuilder field(String name, String value, boolean inline) {
        fields.add(new String[]{name, value, String.valueOf(inline)});
        return this;
    }

    /**
     * 添加字段（默认行内显示）
     *
     * @param name  字段名称
     * @param value 字段值
     * @return 当前构建器实例（链式调用）
     */
    public EmbedBuilder field(String name, String value) {
        return field(name, value, true);
    }

    /**
     * 构建 Embed JSON 字符串
     *
     * <p>生成的 JSON 结构：
     * <pre>
     * {
     *   "title": "标题",
     *   "prompt": "弹窗内容",
     *   "thumbnail": "图片URL",
     *   "fields": [
     *     {"name": "字段名", "value": "字段值", "inline": true}
     *   ]
     * }
     * </pre>
     *
     * @return 完整的 Embed JSON 字符串
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // 标题
        if (title != null) {
            sb.append("\"title\":\"").append(JsonEscUtil.esc(title)).append("\"");
        }

        // 弹窗内容
        if (prompt != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"prompt\":\"").append(JsonEscUtil.esc(prompt)).append("\"");
        }

        // 缩略图
        if (thumbnail != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"thumbnail\":\"").append(JsonEscUtil.esc(thumbnail)).append("\"");
        }

        // 字段列表
        if (!fields.isEmpty()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(",");
                String[] f = fields.get(i);
                sb.append("{\"name\":\"").append(JsonEscUtil.esc(f[0]))
                  .append("\",\"value\":\"").append(JsonEscUtil.esc(f[1]))
                  .append("\",\"inline\":").append(f[2]).append("}");
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }
}
