package dev.xuanji.api.message;

import dev.xuanji.api.annotation.MediaType;

/**
 * 媒体引用归一化结果 — 描述一份媒体引用的真实形态与原始值。
 *
 * <p>作为五态归一化的核心值对象，跨平台 / 跨插件共享。
 * 替代原 {@code api.action.MediaService.MediaRef}（已废弃的空壳）。
 *
 * @param form 引用形态（URL / FILE_PATH / BASE64 / DATA_URI / PLATFORM_ID）
 * @param raw  原始引用字符串（归一化不修改其值，仅标注形态）
 * @param type 富媒体类型（IMAGE / VOICE / VIDEO / FILE）
 */
public record MediaRef(MediaForm form, String raw, MediaType type) {
    public static MediaRef of(MediaForm form, String raw, MediaType type) {
        return new MediaRef(form, raw, type);
    }
}
