package XuanJi.adapter.qqbot.util;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 富媒体摘要工具 — 把附件压成日志可读的短标签，如 {@code " [图片x2][语音x1]"}。
 *
 * <p><b>为什么需要</b>：QQ 的纯图片消息 {@code content} 为空串，日志里打出来是
 * {@code content=}，看起来像「什么都没收到」。本工具直接读 DTO 字段，
 * <b>不触发消息链懒解析</b>，零额外开销地补齐观测。
 *
 * <p>类别判定复用 {@link MediaKind}，与消息链解析<b>口径一致</b>：日志显示「图片」时，
 * {@code @MessageFilter(mediaTypes = IMAGE)} 必然能命中，不会出现两边分类打架。
 *
 * <p>群聊与单聊的 Attachment 是两个独立 DTO 内部类，故此处以 {@code List<String>}
 * 解耦，由调用方各自提取字段。
 */
public final class MediaSummary {

    private MediaSummary() {}

    /**
     * @param contentTypes 附件的 content_type 列表（QQ 常给空值，故建议配合 filenames 使用）
     * @param filenames    附件的 filename 列表（与 contentTypes 一一对应，可为 null）
     * @return 形如 {@code " [图片x2]"} 的摘要；无附件返回空串（可直接拼接到日志尾部）
     */
    public static String of(List<String> contentTypes, List<String> filenames) {
        if (contentTypes == null || contentTypes.isEmpty()) return "";
        var counts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < contentTypes.size(); i++) {
            String name = (filenames != null && i < filenames.size()) ? filenames.get(i) : null;
            counts.merge(MediaKind.resolve(contentTypes.get(i), name).label(), 1, Integer::sum);
        }
        var sb = new StringBuilder(" ");
        counts.forEach((k, v) -> sb.append('[').append(k).append('x').append(v).append(']'));
        return sb.toString();
    }

    /** 仅有 content_type 时的重载（判定精度较低，优先用双参版本）。 */
    public static String of(List<String> contentTypes) {
        return of(contentTypes, null);
    }
}
