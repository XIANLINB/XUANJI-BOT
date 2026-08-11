package XuanJi.api.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息链 — 有序的消息元素集合，附带便捷建造器。
 */
public record XuanJiMessage(List<XuanJiMessageElement> elements) {

    public XuanJiMessage {
        elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /** 空消息链 */
    public static final XuanJiMessage EMPTY = new XuanJiMessage(List.of());

    /** 单文本元素的快捷消息链 */
    public static XuanJiMessage text(String content) {
        return builder().text(content).build();
    }

    /** 提取链中所有纯文本（拼接） */
    public String plainText() {
        return elements.stream()
                .filter(e -> e instanceof XuanJiMessageElement.Text)
                .map(e -> ((XuanJiMessageElement.Text) e).content())
                .collect(Collectors.joining());
    }

    /** 是否包含 @机器人 */
    public boolean isAtBot() {
        return elements.stream().anyMatch(e -> e instanceof XuanJiMessageElement.At);
    }

    /** 提取链中所有媒体元素（P1-D：Image/Voice/Video/File）。 */
    public List<XuanJiMessageElement.Media> medias() {
        return elements.stream()
                .filter(e -> e instanceof XuanJiMessageElement.Media)
                .map(e -> (XuanJiMessageElement.Media) e)
                .collect(Collectors.toList());
    }

    /** 是否包含媒体元素（懒判定，不触发解析）。 */
    public boolean hasMedia() {
        return elements.stream().anyMatch(e -> e instanceof XuanJiMessageElement.Media);
    }

    /** 是否包含指定类型的元素（懒判定，不触发解析）。 */
    public boolean has(Class<? extends XuanJiMessageElement> type) {
        return elements.stream().anyMatch(type::isInstance);
    }

    /**
     * 元素摘要（日志用）— 如 {@code [图片]}、{@code [图片x2][文件x1]}。
     * 与 {@code MediaSummary} 同口径：按元素类别聚合计数。
     */
    public String summary() {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (XuanJiMessageElement e : elements) {
            String label = switch (e) {
                case XuanJiMessageElement.Image ignored -> "图片";
                case XuanJiMessageElement.Voice ignored -> "语音";
                case XuanJiMessageElement.Video ignored -> "视频";
                case XuanJiMessageElement.File ignored -> "文件";
                case XuanJiMessageElement.Face ignored -> "表情";
                case XuanJiMessageElement.Markdown ignored -> "Markdown";
                case XuanJiMessageElement.Keyboard ignored -> "键盘";
                case XuanJiMessageElement.Ark ignored -> "Ark";
                case XuanJiMessageElement.At ignored -> "@";
                default -> null;
            };
            if (label != null) counts.merge(label, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((label, count) -> sb.append('[').append(label).append(count > 1 ? "x" + count : "").append(']'));
        return sb.toString();
    }

    // ==================== 建造器 ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<XuanJiMessageElement> list = new ArrayList<>();

        public Builder text(String content)      { list.add(new XuanJiMessageElement.Text(content)); return this; }
        public Builder at(String userId)          { list.add(new XuanJiMessageElement.At(userId, "")); return this; }
        public Builder atAll()                    { list.add(XuanJiMessageElement.At.all()); return this; }
        public Builder image(String url)          { list.add(new XuanJiMessageElement.Image(url, "", 0, 0, 0)); return this; }
        public Builder face(int faceId)           { list.add(new XuanJiMessageElement.Face(faceId)); return this; }
        public Builder quote(String msgId, String preview) { list.add(new XuanJiMessageElement.Quote(msgId, "", preview)); return this; }
        public Builder reply(String msgId)        { list.add(new XuanJiMessageElement.Reply(msgId)); return this; }
        public Builder markdown(String md)        { list.add(new XuanJiMessageElement.Markdown(md)); return this; }
        public Builder add(XuanJiMessageElement e)      { list.add(e); return this; }

        public XuanJiMessage build() {
            return new XuanJiMessage(list);
        }
    }
}
