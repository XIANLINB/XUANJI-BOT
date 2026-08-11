package XuanJi.adapter.qqbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * QQ 机器人消息事件 DTO
 *
 * <p>封装 QQ 开放平台推送的消息事件数据，适用于以下事件类型：
 * <ul>
 *   <li>C2C_MESSAGE_CREATE — 单聊消息</li>
 *   <li>GROUP_AT_MESSAGE_CREATE — 群 @消息</li>
 *   <li>GROUP_MESSAGE_CREATE — 群消息全量模式</li>
 *   <li>AT_MESSAGE_CREATE — 频道 @消息</li>
 *   <li>DIRECT_MESSAGE_CREATE — 频道私信</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <p>不同事件类型和机器人场景下，部分字段可能为空。
 * 例如群消息中 content 字段可能包含 @标签，需要手动去除。
 *
 * @see <a href="https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_at_message_create.html">群@消息事件文档</a>
 * @see <a href="https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_message_create.html">群消息全量模式文档</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMessageEvent {

    /** 消息 ID（全局唯一），用于被动回复时的 msg_id 参数 */
    private String id;

    /** 消息内容（文本消息的文本内容，可能包含 @标签如 <@xxx>） */
    private String content;

    /** 消息类型：0=文本，2=Markdown，7=富媒体 */
    @JsonProperty("message_type")
    private Integer messageType;

    /** 消息时间戳（ISO 8601 格式，如 "2026-07-29T17:32:06+08:00"） */
    private String timestamp;

    /** 原始事件类型（GROUP_MESSAGE_CREATE / GROUP_AT_MESSAGE_CREATE，由 EventDispatcher 注入 _eventType） */
    @JsonProperty("_eventType")
    private String eventType;

    /** 群聊场景：群组的 OpenID（群消息时有值，单聊时为空） */
    @JsonProperty("group_openid")
    private String groupOpenid;

    /** 群聊场景：群组 ID（部分场景下与 group_openid 相同） */
    @JsonProperty("group_id")
    private String groupId;

    /** 频道场景：频道 ID（频道消息时有值） */
    @JsonProperty("channel_id")
    private String channelId;

    /** 频道私信场景：频道 ID（guild_id，私信时有值） */
    @JsonProperty("guild_id")
    private String guildId;

    /** 消息发送者信息 */
    private Author author;

    /** @列表（被 @的用户/机器人列表，仅群聊和频道消息时有值） */
    private List<Mention> mentions;

    /** 附件列表（图片、语音、视频、文件等富媒体消息时有值） */
    private List<Attachment> attachments;

    /** 消息场景信息 */
    @JsonProperty("message_scene")
    private MessageScene messageScene;

    /**
     * 消息发送者信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        /** 用户 ID（不同场景含义不同） */
        private String id;

        /** 单聊场景：用户的 user_openid */
        @JsonProperty("user_openid")
        private String userOpenid;

        /** 群聊场景：成员的 member_openid */
        @JsonProperty("member_openid")
        private String memberOpenid;

        /** 联合 OpenID（同一用户在不同机器人下相同的唯一标识） */
        @JsonProperty("union_openid")
        private String unionOpenid;

        /** 用户名/昵称 */
        private String username;

        /** 群聊场景：成员角色（owner/admin/member） */
        @JsonProperty("member_role")
        private String memberRole;

        /** 是否为机器人 */
        private Boolean bot;
    }

    /**
     * @提及的用户/机器人信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mention {
        /** 用户 ID */
        private String id;

        /** 成员的 member_openid */
        @JsonProperty("member_openid")
        private String memberOpenid;

        /** 用户名/昵称 */
        private String username;

        /** 是否为机器人 */
        private Boolean bot;

        /** 是否为当前机器人（true=@的是自己） */
        @JsonProperty("is_you")
        private Boolean isYou;

        /** 成员角色（owner/admin/member） */
        @JsonProperty("member_role")
        private String memberRole;

        /** @范围：single=仅@单个用户，all=@全体 */
        private String scope;
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

    /**
     * 消息场景信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageScene {
        /** 场景来源（如 "default"） */
        private String source;

        /** 扩展信息列表 */
        private List<String> ext;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取纯文本内容（去除 @标签）
     *
     * <p>QQ 平台的消息内容中，@用户会以 <@xxx> 格式嵌入。
     * 此方法去除所有 @标签，返回纯文本。
     *
     * @return 去除 @标签后的纯文本内容
     */
    public String getPlainTextContent() {
        if (content == null) return "";
        return content.replaceAll("<@[^>]+>", "").trim();
    }

    /**
     * 获取发送者 ID（兼容不同场景）
     *
     * <p>不同场景下发送者 ID 字段不同：
     * <ul>
     *   <li>单聊：author.user_openid</li>
     *   <li>群聊：author.member_openid</li>
     *   <li>频道：author.id</li>
     * </ul>
     *
     * @return 发送者 ID，无法确定时返回空字符串
     */
    public String getSenderId() {
        if (author == null) return "";
        if (author.getUserOpenid() != null && !author.getUserOpenid().isEmpty()) {
            return author.getUserOpenid();
        }
        if (author.getMemberOpenid() != null && !author.getMemberOpenid().isEmpty()) {
            return author.getMemberOpenid();
        }
        return author.getId() != null ? author.getId() : "";
    }

    /**
     * 获取接收者 ID（群 OpenID 或频道 ID）
     *
     * @return 接收者 ID
     */
    public String getReceiverId() {
        if (groupOpenid != null && !groupOpenid.isEmpty()) return groupOpenid;
        if (groupId != null && !groupId.isEmpty()) return groupId;
        if (channelId != null && !channelId.isEmpty()) return channelId;
        if (guildId != null && !guildId.isEmpty()) return guildId;
        return "";
    }

    /**
     * 判断是否为 @当前机器人的消息。
     *
     * <p>两种订阅模式兼容：
     * <ul>
     *   <li><b>AT 消息模式</b>（{@code GROUP_AT_MESSAGE_CREATE}）：官方仅在下发 @机器人 消息，
     *       且事件<b>不携带 mentions</b> → 事件类型本身即判定依据，恒为 true；</li>
     *   <li><b>全量消息模式</b>（{@code GROUP_MESSAGE_CREATE}）：@机器人 时 mentions 含
     *       {@code is_you=true}，未 @ 时无该标记 → 按 mentions 判定。</li>
     * </ul>
     *
     * @return true=@了当前机器人，false=没有@或@了其他人
     */
    public boolean isAtBot() {
        if ("GROUP_AT_MESSAGE_CREATE".equals(eventType)) {
            return true; // AT 订阅模式：事件本身即 @机器人（无 mentions）
        }
        if (mentions == null) return false;
        return mentions.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsYou()));
    }
}
