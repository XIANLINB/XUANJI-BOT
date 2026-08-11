package XuanJi.api.annotation;

/**
 * 富媒体类型 — 配合 {@link MessageFilter#mediaTypes()} 精确限定。
 *
 * <p>仅在显式声明时才会触发消息链懒解析，未声明则只按
 * {@code hasAttachments} 标记判定，零解析开销。
 */
public enum MediaType {
    IMAGE,
    VOICE,
    VIDEO,
    FILE
}
