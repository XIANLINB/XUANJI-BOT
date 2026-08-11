package XuanJi.adapter.qqbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 发送消息请求 DTO
 *
 * <p>封装发送单聊/群聊消息的请求体，支持所有消息类型。
 *
 * <h3>消息类型</h3>
 * <ul>
 *   <li>msg_type=0 — 文本消息</li>
 *   <li>msg_type=2 — Markdown 消息</li>
 *   <li>msg_type=3 — Ark 消息</li>
 *   <li>msg_type=7 — 富媒体消息（图片/视频/文件）</li>
 *   <li>msg_type=10 — 流式消息（AI 场景）</li>
 * </ul>
 *
 * <h3>被动消息 vs 主动消息</h3>
 * <ul>
 *   <li><b>被动消息</b> — 包含 msg_id 字段，回复用户发送的消息，不占用主动消息频次</li>
 *   <li><b>主动消息</b> — 不包含 msg_id 字段，机器人主动发送，占用频次（每天有上限）</li>
 * </ul>
 *
 * @see <a href="https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/overview.html">消息收发概述</a>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendMessageRequest {

    /** 消息类型：0=文本, 2=Markdown, 3=Ark, 7=富媒体, 10=流式 */
    @JsonProperty("msg_type")
    private Integer msgType;

    /** 消息内容（文本消息时为纯文本，Markdown 消息时为 JSON） */
    private String content;

    /** 被动回复的消息 ID（包含此字段为被动消息，不包含为主动消息） */
    @JsonProperty("msg_id")
    private String msgId;

    /** Markdown 消息内容（msg_type=2 时使用） */
    private Object markdown;

    /** 按钮键盘（与 Markdown 搭配使用） */
    private Object keyboard;

    /** Ark 消息内容（msg_type=3 时使用） */
    private Object ark;

    /** 富媒体信息（msg_type=7 时使用） */
    private Object media;

    /** Embed 消息内容（频道消息使用） */
    private Object embed;

    /** 事件 ID（被动回复时使用，替代 msg_id） */
    @JsonProperty("event_id")
    private String eventId;

    // ==================== 工厂方法 ====================

    /**
     * 创建被动文本消息（回复用户消息）
     *
     * @param content 消息文本
     * @param msgId   被回复的消息 ID
     * @return SendMessageRequest
     */
    public static SendMessageRequest passiveText(String content, String msgId) {
        return SendMessageRequest.builder()
                .msgType(0)
                .content(content)
                .msgId(msgId)
                .build();
    }

    /**
     * 创建主动文本消息
     *
     * @param content 消息文本
     * @return SendMessageRequest
     */
    public static SendMessageRequest activeText(String content) {
        return SendMessageRequest.builder()
                .msgType(0)
                .content(content)
                .build();
    }

    /**
     * 创建被动 Markdown 消息
     *
     * @param markdown    Markdown 内容（JSON 对象）
     * @param keyboard    按钮键盘（JSON 对象，可为 null）
     * @param msgId       被回复的消息 ID
     * @return SendMessageRequest
     */
    public static SendMessageRequest passiveMarkdown(Object markdown, Object keyboard, String msgId) {
        return SendMessageRequest.builder()
                .msgType(2)
                .markdown(markdown)
                .keyboard(keyboard)
                .msgId(msgId)
                .build();
    }

    /**
     * 创建主动 Markdown 消息
     *
     * @param markdown Markdown 内容（JSON 对象）
     * @param keyboard 按钮键盘（JSON 对象，可为 null）
     * @return SendMessageRequest
     */
    public static SendMessageRequest activeMarkdown(Object markdown, Object keyboard) {
        return SendMessageRequest.builder()
                .msgType(2)
                .markdown(markdown)
                .keyboard(keyboard)
                .build();
    }

    /**
     * 创建被动 Ark 消息
     *
     * @param ark   Ark 内容（JSON 对象）
     * @param msgId 被回复的消息 ID
     * @return SendMessageRequest
     */
    public static SendMessageRequest passiveArk(Object ark, String msgId) {
        return SendMessageRequest.builder()
                .msgType(3)
                .ark(ark)
                .msgId(msgId)
                .build();
    }

    /**
     * 创建主动 Ark 消息
     *
     * @param ark Ark 内容（JSON 对象）
     * @return SendMessageRequest
     */
    public static SendMessageRequest activeArk(Object ark) {
        return SendMessageRequest.builder()
                .msgType(3)
                .ark(ark)
                .build();
    }

    /**
     * 创建被动富媒体消息
     *
     * @param media 富媒体信息（JSON 对象）
     * @param msgId 被回复的消息 ID
     * @return SendMessageRequest
     */
    public static SendMessageRequest passiveMedia(Object media, String msgId) {
        return SendMessageRequest.builder()
                .msgType(7)
                .media(media)
                .msgId(msgId)
                .build();
    }

    /**
     * 创建主动富媒体消息
     *
     * @param media 富媒体信息（JSON 对象）
     * @return SendMessageRequest
     */
    public static SendMessageRequest activeMedia(Object media) {
        return SendMessageRequest.builder()
                .msgType(7)
                .media(media)
                .build();
    }
}
