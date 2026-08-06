package dev.xuanji.adapter.qqbot.util;

import dev.xuanji.adapter.qqbot.dto.SendMessageRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import dev.xuanji.api.json.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建工具
 *
 * <p>提供链式 API 构建 QQ 消息，支持文本、Markdown、Ark、富媒体等所有消息类型。
 * 自动处理被动消息和主动消息的区分。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 构建被动文本消息（回复用户）
 * SendMessageRequest req = MsgBuilder.text("你好！")
 *     .passive("msg_id_xxx")
 *     .build();
 *
 * // 构建主动 Markdown 消息
 * SendMessageRequest req = MsgBuilder.markdown()
 *     .h1("签到成功")
 *     .text("金币 +100")
 *     .text("经验 +20")
 *     .buildActive();
 *
 * // 构建带按钮的 Markdown 消息
 * SendMessageRequest req = MsgBuilder.markdown()
 *     .h2("菜单")
 *     .text("请选择功能：")
 *     .keyboard(kb -> kb
 *         .button("sign_in", "签到")
 *         .button("help", "帮助"))
 *     .buildPassive("msg_id_xxx");
 *
 * // 构建 Ark 消息
 * SendMessageRequest req = MsgBuilder.ark(37)
 *     .kv("desc", "天气预报")
 *     .kv("weather", "晴天 25°C")
 *     .buildActive();
 * }</pre>
 */
public class MsgBuilder {

    private Integer msgType;
    private String content;
    private Object markdown;
    private Object keyboard;
    private Object ark;
    private Object media;
    private String msgId;

    private MsgBuilder() {}

    // ==================== 工厂方法 ====================

    /**
     * 创建文本消息构建器
     *
     * @param content 消息文本
     * @return MsgBuilder
     */
    public static MsgBuilder text(String content) {
        MsgBuilder builder = new MsgBuilder();
        builder.msgType = 0;
        builder.content = content;
        return builder;
    }

    /**
     * 创建 Markdown 消息构建器
     *
     * @return MarkdownMsgBuilder
     */
    public static MarkdownMsgBuilder markdown() {
        return new MarkdownMsgBuilder();
    }

    /**
     * 创建 Ark 消息构建器
     *
     * @param templateId Ark 模板 ID
     * @return ArkMsgBuilder
     */
    public static ArkMsgBuilder ark(int templateId) {
        return new ArkMsgBuilder(templateId);
    }

    /**
     * 创建富媒体消息构建器
     *
     * @param fileInfo 文件信息（由上传接口返回）
     * @return MsgBuilder
     */
    public static MsgBuilder media(String fileInfo) {
        MsgBuilder builder = new MsgBuilder();
        builder.msgType = 7;
        ObjectNode mediaObj = Json.obj();
        mediaObj.put("file_info", fileInfo);
        builder.media = mediaObj;
        return builder;
    }

    // ==================== 被动/主动消息 ====================

    /**
     * 设置为被动消息（回复指定消息）
     *
     * @param msgId 被回复的消息 ID
     * @return MsgBuilder
     */
    public MsgBuilder passive(String msgId) {
        this.msgId = msgId;
        return this;
    }

    /**
     * 构建 SendMessageRequest
     *
     * @return SendMessageRequest
     */
    public SendMessageRequest build() {
        if (msgType == 0) {
            return msgId != null
                    ? SendMessageRequest.passiveText(content, msgId)
                    : SendMessageRequest.activeText(content);
        }

        return SendMessageRequest.builder()
                .msgType(msgType)
                .content(content)
                .markdown(markdown)
                .keyboard(keyboard)
                .ark(ark)
                .media(media)
                .msgId(msgId)
                .build();
    }

    /**
     * 构建主动消息
     *
     * @return SendMessageRequest（无 msg_id）
     */
    public SendMessageRequest buildActive() {
        this.msgId = null;
        return build();
    }

    /**
     * 构建被动消息
     *
     * @param msgId 被回复的消息 ID
     * @return SendMessageRequest（包含 msg_id）
     */
    public SendMessageRequest buildPassive(String msgId) {
        this.msgId = msgId;
        return build();
    }

    // ==================== Markdown 消息构建器 ====================

    /**
     * Markdown 消息构建器
     */
    public static class MarkdownMsgBuilder {
        private final StringBuilder md = new StringBuilder();
        private KeyboardBuilder keyboardBuilder;

        /**
         * 添加一级标题
         */
        public MarkdownMsgBuilder h1(String text) {
            md.append("# ").append(text).append("\n");
            return this;
        }

        /**
         * 添加二级标题
         */
        public MarkdownMsgBuilder h2(String text) {
            md.append("## ").append(text).append("\n");
            return this;
        }

        /**
         * 添加三级标题
         */
        public MarkdownMsgBuilder h3(String text) {
            md.append("### ").append(text).append("\n");
            return this;
        }

        /**
         * 添加文本
         */
        public MarkdownMsgBuilder text(String text) {
            md.append(text).append("\n");
            return this;
        }

        /**
         * 添加粗体文本
         */
        public MarkdownMsgBuilder bold(String label, String value) {
            md.append("**").append(label).append("**：").append(value).append("\n");
            return this;
        }

        /**
         * 添加引用
         */
        public MarkdownMsgBuilder quote(String text) {
            md.append("> ").append(text).append("\n");
            return this;
        }

        /**
         * 添加分割线
         */
        public MarkdownMsgBuilder divider() {
            md.append("---\n");
            return this;
        }

        /**
         * 添加链接
         */
        public MarkdownMsgBuilder link(String text, String url) {
            md.append("[").append(text).append("](").append(url).append(")");
            return this;
        }

        /**
         * 添加图片
         */
        public MarkdownMsgBuilder image(String url) {
            md.append("![image](").append(url).append(")");
            return this;
        }

        /**
         * 添加按钮键盘
         *
         * @param builder 键盘构建器配置函数
         * @return MarkdownMsgBuilder
         */
        public MarkdownMsgBuilder keyboard(java.util.function.Function<KeyboardBuilder, KeyboardBuilder> builder) {
            this.keyboardBuilder = builder.apply(KeyboardBuilder.create());
            return this;
        }

        /**
         * 构建 Markdown 消息内容（JSON 对象）
         */
        public ObjectNode buildMarkdownJson() {
            ObjectNode mdObj = Json.obj();
            mdObj.put("custom_template_id", NullNode.instance);
            mdObj.put("params", NullNode.instance);
            mdObj.put("content", md.toString());
            return mdObj;
        }

        /**
         * 构建主动消息
         */
        public SendMessageRequest buildActive() {
            ObjectNode mdJson = buildMarkdownJson();
            ObjectNode kbJson = keyboardBuilder != null ? Json.parseObj(keyboardBuilder.build()) : null;
            return SendMessageRequest.activeMarkdown(mdJson, kbJson);
        }

        /**
         * 构建被动消息
         */
        public SendMessageRequest buildPassive(String msgId) {
            ObjectNode mdJson = buildMarkdownJson();
            ObjectNode kbJson = keyboardBuilder != null ? Json.parseObj(keyboardBuilder.build()) : null;
            return SendMessageRequest.passiveMarkdown(mdJson, kbJson, msgId);
        }

        /**
         * 构建消息（默认为主动消息）
         */
        public SendMessageRequest build() {
            return buildActive();
        }
    }

    // ==================== Ark 消息构建器 ====================

    /**
     * Ark 消息构建器
     */
    public static class ArkMsgBuilder {
        private final int templateId;
        private final List<ObjectNode> kvList = new ArrayList<>();

        public ArkMsgBuilder(int templateId) {
            this.templateId = templateId;
        }

        /**
         * 添加键值对
         */
        public ArkMsgBuilder kv(String key, String value) {
            ObjectNode kv = Json.obj();
            kv.put("key", key);
            kv.put("value", value);
            kvList.add(kv);
            return this;
        }

        /**
         * 构建 Ark 消息内容（JSON 对象）
         */
        public ObjectNode buildArkJson() {
            ObjectNode ark = Json.obj();
            ark.put("template_id", templateId);
            ArrayNode kvArray = Json.arr();
            for (ObjectNode kv : kvList) {
                kvArray.add(kv);
            }
            ark.put("kv", kvArray);
            return ark;
        }

        /**
         * 构建主动消息
         */
        public SendMessageRequest buildActive() {
            return SendMessageRequest.activeArk(buildArkJson());
        }

        /**
         * 构建被动消息
         */
        public SendMessageRequest buildPassive(String msgId) {
            return SendMessageRequest.passiveArk(buildArkJson(), msgId);
        }

        /**
         * 构建消息（默认为主动消息）
         */
        public SendMessageRequest build() {
            return buildActive();
        }
    }
}
