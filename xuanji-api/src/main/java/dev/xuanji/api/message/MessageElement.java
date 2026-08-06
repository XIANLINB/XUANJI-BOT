package dev.xuanji.api.message;

import dev.xuanji.api.annotation.MediaType;

/**
 * 消息元素 — 组成消息的基本单位，平台无关的抽象。
 *
 * <p>采用 sealed + record：适配器转换时用 pattern matching switch，
 * 编译器保证穷尽处理所有元素类型。
 */
public sealed interface MessageElement
        permits MessageElement.Text, MessageElement.At,
        MessageElement.Image, MessageElement.Face,
        MessageElement.Quote, MessageElement.Reply,
        MessageElement.Voice, MessageElement.Video, MessageElement.File,
        MessageElement.Markdown, MessageElement.Keyboard, MessageElement.Ark,
        MessageElement.Passthrough, MessageElement.Media {

    /**
     * 媒体元素统一抽象（P1-D）— Image / Voice / Video / File 均实现。
     *
     * <p>提供五态归一化：{@link #resolve(String)} 按平台分派 {@link MediaRefResolverHolder}
     * 得到 {@link MediaRef}；{@link #form()} 直接判读当前形态。
     */
    non-sealed interface Media extends MessageElement {
        /** 原始媒体引用（url / file_id / base64 等）。 */
        String rawRef();

        /** 媒体类型（IMAGE / VOICE / VIDEO / FILE）。 */
        MediaType mediaType();

        /** 按平台归一化为五态 MediaRef。 */
        default MediaRef resolve(String platform) {
            return MediaRefResolverHolder.resolve(platform, rawRef(), mediaType());
        }

        /** 当前形态（纯函数判定，不下载）。 */
        default MediaForm form() {
            return MediaRefResolverHolder.resolve(rawRef(), mediaType()).form();
        }
    }

    /** 纯文本 */
    record Text(String content) implements MessageElement {}

    /** @某人 */
    record At(String userId, String display) implements MessageElement {
        public static At all() { return new At("all", "全体成员"); }
    }

    /** 图片 */
    record Image(String url, String fileMd5, int width, int height, long size) implements MessageElement, Media {
        @Override public String rawRef() { return url; }
        @Override public MediaType mediaType() { return MediaType.IMAGE; }
    }

    /** QQ 表情 */
    record Face(int faceId) implements MessageElement {}

    /** 引用一段消息 */
    record Quote(String msgId, String senderId, String preview) implements MessageElement {}

    /** 被动回复（携带消息 ID 自动关联） */
    record Reply(String targetMsgId) implements MessageElement {}

    /** 语音 */
    record Voice(String url, int duration) implements MessageElement, Media {
        @Override public String rawRef() { return url; }
        @Override public MediaType mediaType() { return MediaType.VOICE; }
    }

    /** 视频 */
    record Video(String url, int duration, int width, int height) implements MessageElement, Media {
        public Video(String url) { this(url, 0, 0, 0); }
        @Override public String rawRef() { return url; }
        @Override public MediaType mediaType() { return MediaType.VIDEO; }
    }

    /** 文件 */
    record File(String url, String name, long size) implements MessageElement, Media {
        @Override public String rawRef() { return url; }
        @Override public MediaType mediaType() { return MediaType.FILE; }
    }

    /** Markdown 富文本 */
    record Markdown(String content, Object nativePayload) implements MessageElement {
        public Markdown(String content) { this(content, null); }
    }

    /** 键盘按钮 */
    record Keyboard(Object nativePayload) implements MessageElement {}

    /** Ark 模板 */
    record Ark(int templateId, Object nativePayload) implements MessageElement {}

    /**
     * 透传 — 承载适配器无法归类为通用元素的平台专属内容。
     * 其他平台适配器收到时可降级为文本或忽略。
     */
    record Passthrough(String platform, String description, Object nativePayload) implements MessageElement {}
}
