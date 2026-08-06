package dev.xuanji.api.annotation;

/**
 * 富媒体过滤模式 — 配合 {@link MessageFilter#media()} 使用。
 *
 * <p>解决「纯图片消息 content 为空、命令式过滤器全部 miss」的订阅盲区：
 * 标注 {@code media = MediaMode.NEED} 的 handler 不依赖文本即可命中。
 */
public enum MediaMode {
    /** 不限制（默认） */
    IGNORE,
    /** 必须含富媒体（图片 / 语音 / 视频 / 文件） */
    NEED,
    /** 必须不含富媒体（纯文本消息） */
    NOT
}
