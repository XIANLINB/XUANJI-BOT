package dev.xuanji.adapter.qq.util;

/**
 * QQ Ark 消息构建器
 *
 * <p>Ark 消息是 QQ 平台的模板消息格式，需要先在开放平台创建模板，
 * 然后通过模板 ID + KV 参数填充内容。适用于需要精美排版的消息场景。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String json = ArkBuilder.create(1)
 *     .kv("#DESC#", "签到成功")
 *     .kv("#PROMPT#", "每日签到")
 *     .kv("#TITLE#", "签到通知")
 *     .kv("#METAURL#", "https://example.com")
 *     .build();
 * }</pre>
 *
 * <h3>常用模板 ID</h3>
 * <ul>
 *   <li>1 — 通用模板</li>
 *   <li>23 — 文本链接模板</li>
 *   <li>24 — 文本弹窗模板</li>
 *   <li>37 — 大图模板</li>
 *   <li>38 — 小图模板</li>
 * </ul>
 *
 * @see dev.xuanji.starter.common.util.CardBuilder 图文卡片消息构建器（群聊专属）
 * @see dev.xuanji.starter.common.util.EmbedBuilder Embed 消息构建器（频道专属）
 */
public class ArkBuilder {

    /** 模板 ID（在 QQ 开放平台创建模板时获得） */
    private final int templateId;

    /** 键值对列表，每项为 [key, value] 二元组 */
    private final java.util.List<String[]> kvPairs = new java.util.ArrayList<>();

    private ArkBuilder(int templateId) {
        this.templateId = templateId;
    }

    /**
     * 创建 Ark 构建器
     *
     * @param templateId 模板 ID（如 1=通用模板，23=文本链接模板）
     * @return 新的 ArkBuilder 实例
     */
    public static ArkBuilder create(int templateId) {
        return new ArkBuilder(templateId);
    }

    /**
     * 添加键值对参数
     *
     * <p>键名通常是模板中定义的占位符，如 #TITLE#、#DESC#、#PROMPT# 等。
     *
     * @param key   键名（如 #TITLE#, #DESC#）
     * @param value 值（会自动进行 JSON 转义）
     * @return 当前构建器实例（链式调用）
     */
    public ArkBuilder kv(String key, String value) {
        kvPairs.add(new String[]{key, value});
        return this;
    }

    /**
     * 构建 Ark JSON 字符串
     *
     * <p>生成的 JSON 结构：
     * <pre>
     * {
     *   "template_id": 1,
     *   "kv": [
     *     {"key": "#TITLE#", "value": "..."},
     *     {"key": "#DESC#", "value": "..."}
     *   ]
     * }
     * </pre>
     *
     * @return 完整的 Ark JSON 字符串
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"template_id\":").append(templateId);
        sb.append(",\"kv\":[");

        for (int i = 0; i < kvPairs.size(); i++) {
            if (i > 0) sb.append(",");
            String[] kv = kvPairs.get(i);
            sb.append("{\"key\":\"").append(JsonEscUtil.esc(kv[0]))
              .append("\",\"value\":\"").append(JsonEscUtil.esc(kv[1])).append("\"}");
        }

        sb.append("]}");
        return sb.toString();
    }
}
