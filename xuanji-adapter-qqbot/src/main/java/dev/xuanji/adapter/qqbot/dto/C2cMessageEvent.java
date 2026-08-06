package dev.xuanji.adapter.qqbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 单聊（C2C）消息事件 DTO
 *
 * <p>封装 QQ 开放平台推送的单聊消息事件数据。
 *
 * <h3>事件信息</h3>
 * <ul>
 *   <li><b>事件类型</b>：C2C_MESSAGE_CREATE</li>
 *   <li><b>所需 Intent</b>：{@code 1 << 25}（GROUP_AND_C2C_EVENT）</li>
 *   <li><b>触发条件</b>：用户在 QQ 客户端单聊发送消息给机器人</li>
 * </ul>
 *
 * <h3>与群聊消息的区别</h3>
 * <ul>
 *   <li>单聊消息没有 group_openid 字段</li>
 *   <li>发送者使用 user_openid 而非 member_openid</li>
 *   <li>没有 mentions 数组（不存在 @机器人的概念）</li>
 * </ul>
 *
 * @see <a href="https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/c2c_message_create.html">单聊消息事件文档</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class C2cMessageEvent {

    /** 消息 ID（全局唯一），用于被动回复时的 msg_id 参数 */
    private String id;

    /** 消息内容（纯文本，不包含 @标签） */
    private String content;

    /** 消息类型：0=文本，2=Markdown，7=富媒体 */
    @JsonProperty("message_type")
    private Integer messageType;

    /** 消息时间戳（ISO 8601 格式，如 "2026-07-29T17:32:06+08:00"） */
    private String timestamp;

    /** 消息发送者信息 */
    private Author author;

    /** 附件列表（图片、语音、视频、文件等富媒体消息时有值） */
    private List<Attachment> attachments;

    /**
     * 消息发送者信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        /** 用户 ID */
        private String id;

        /** 用户的 user_openid（单聊场景的主要标识） */
        @JsonProperty("user_openid")
        private String userOpenid;

        /** 联合 OpenID（同一用户在不同机器人下相同的唯一标识） */
        @JsonProperty("union_openid")
        private String unionOpenid;

        /** 用户名/昵称 */
        private String username;

        /** 是否为机器人 */
        private Boolean bot;
    }

    /**
     * 消息附件信息（图片、语音、视频、文件等）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attachment {
        /** 附件内容类型（如 image/jpeg, audio/pcm 等） */
        @JsonProperty("content_type")
        private String contentType;

        /** 附件文件名 */
        private String filename;

        /** 附件大小（字节） */
        private Long size;

        /** 附件 URL（临时链接，有效期有限） */
        private String url;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取发送者 ID
     *
     * @return 用户的 user_openid
     */
    public String getSenderId() {
        if (author == null) return "";
        if (author.getUserOpenid() != null && !author.getUserOpenid().isEmpty()) {
            return author.getUserOpenid();
        }
        return author.getId() != null ? author.getId() : "";
    }

    /**
     * 获取发送者用户名
     *
     * @return 用户名，未知时返回 "未知"
     */
    public String getSenderName() {
        if (author == null || author.getUsername() == null) return "未知";
        return author.getUsername();
    }
}
