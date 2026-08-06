package dev.xuanji.adapter.qqbot.util;

import java.util.Locale;
import java.util.Set;

/**
 * 富媒体类别判定 — 消息链解析与日志摘要的<b>唯一判定口径</b>。
 *
 * <p><b>为什么不能只看 content_type</b>：QQ 群/单聊的 {@code attachments} 并不保证给出规范的
 * MIME 类型。实测发图片时 {@code content_type} 可能为空串、{@code "file"} 或其它非
 * {@code image/*} 值，仅 {@code filename} 带 {@code .png} 后缀 —— 若只按 MIME 前缀分发，
 * 图片会被误判成普通文件，导致 {@code @MessageFilter(mediaTypes = IMAGE)} 的处理器收不到消息。
 *
 * <p>故判定顺序为：<b>content_type 前缀 → filename 扩展名 → 兜底 FILE</b>。
 */
public enum MediaKind {

    IMAGE("图片"),
    VOICE("语音"),
    VIDEO("视频"),
    FILE("文件");

    private static final Set<String> IMAGE_EXT =
            Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "tiff", "tif", "avif", "ico");
    private static final Set<String> VOICE_EXT =
            Set.of("silk", "amr", "mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "wma");
    private static final Set<String> VIDEO_EXT =
            Set.of("mp4", "mov", "avi", "mkv", "flv", "wmv", "webm", "3gp", "m4v", "mpeg", "mpg");

    private final String label;

    MediaKind(String label) {
        this.label = label;
    }

    /** 中文标签，用于日志摘要。 */
    public String label() {
        return label;
    }

    /** 映射到 api 模块的 MediaType 枚举（用于 MediaFileDownloader 等）。 */
    public dev.xuanji.api.annotation.MediaType toMediaType() {
        return switch (this) {
            case IMAGE -> dev.xuanji.api.annotation.MediaType.IMAGE;
            case VOICE -> dev.xuanji.api.annotation.MediaType.VOICE;
            case VIDEO -> dev.xuanji.api.annotation.MediaType.VIDEO;
            default -> null;
        };
    }

    /**
     * 判定附件类别。
     *
     * @param contentType 平台给出的 MIME（可为 null/空/非标准值）
     * @param filename    附件文件名（可为 null；content_type 不可信时的兜底依据）
     * @return 永不为 null，无法判定时返回 {@link #FILE}
     */
    public static MediaKind resolve(String contentType, String filename) {
        MediaKind byMime = byContentType(contentType);
        if (byMime != null) return byMime;
        MediaKind byExt = byExtension(filename);
        return byExt != null ? byExt : FILE;
    }

    /** 仅按 MIME 前缀判定；无法判定返回 null（交给扩展名兜底）。 */
    private static MediaKind byContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image")) return IMAGE;
        if (ct.startsWith("audio") || ct.startsWith("voice")) return VOICE;
        if (ct.startsWith("video")) return VIDEO;
        return null;
    }

    /** 仅按文件扩展名判定；无法判定返回 null。 */
    private static MediaKind byExtension(String filename) {
        if (filename == null || filename.isBlank()) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (IMAGE_EXT.contains(ext)) return IMAGE;
        if (VOICE_EXT.contains(ext)) return VOICE;
        if (VIDEO_EXT.contains(ext)) return VIDEO;
        return null;
    }
}
