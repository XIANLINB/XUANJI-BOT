package dev.xuanji.api.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息链 — 有序的消息元素集合，附带便捷建造器。
 */
public record MessageChain(List<MessageElement> elements) {

    public MessageChain {
        elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /** 空消息链 */
    public static final MessageChain EMPTY = new MessageChain(List.of());

    /** 单文本元素的快捷消息链 */
    public static MessageChain text(String content) {
        return builder().text(content).build();
    }

    /** 提取链中所有纯文本（拼接） */
    public String plainText() {
        return elements.stream()
                .filter(e -> e instanceof MessageElement.Text)
                .map(e -> ((MessageElement.Text) e).content())
                .collect(Collectors.joining());
    }

    /** 是否包含 @机器人 */
    public boolean isAtBot() {
        return elements.stream().anyMatch(e -> e instanceof MessageElement.At);
    }

    /** 提取链中所有媒体元素（P1-D：Image/Voice/Video/File）。 */
    public List<MessageElement.Media> medias() {
        return elements.stream()
                .filter(e -> e instanceof MessageElement.Media)
                .map(e -> (MessageElement.Media) e)
                .collect(Collectors.toList());
    }

    /** 是否包含媒体元素（懒判定，不触发解析）。 */
    public boolean hasMedia() {
        return elements.stream().anyMatch(e -> e instanceof MessageElement.Media);
    }

    /** 是否包含指定类型的元素（懒判定，不触发解析）。 */
    public boolean has(Class<? extends MessageElement> type) {
        return elements.stream().anyMatch(type::isInstance);
    }

    /**
     * 元素摘要（日志用）— 如 {@code [图片]}、{@code [图片x2][文件x1]}。
     * 与 {@code MediaSummary} 同口径：按元素类别聚合计数。
     */
    public String summary() {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (MessageElement e : elements) {
            String label = switch (e) {
                case MessageElement.Image ignored -> "图片";
                case MessageElement.Voice ignored -> "语音";
                case MessageElement.Video ignored -> "视频";
                case MessageElement.File ignored -> "文件";
                case MessageElement.Face ignored -> "表情";
                case MessageElement.Markdown ignored -> "Markdown";
                case MessageElement.Keyboard ignored -> "键盘";
                case MessageElement.Ark ignored -> "Ark";
                case MessageElement.At ignored -> "@";
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
        private final List<MessageElement> list = new ArrayList<>();

        public Builder text(String content)      { list.add(new MessageElement.Text(content)); return this; }
        public Builder at(String userId)          { list.add(new MessageElement.At(userId, "")); return this; }
        public Builder atAll()                    { list.add(MessageElement.At.all()); return this; }
        public Builder image(String url)          { list.add(new MessageElement.Image(url, "", 0, 0, 0)); return this; }
        public Builder face(int faceId)           { list.add(new MessageElement.Face(faceId)); return this; }
        public Builder quote(String msgId, String preview) { list.add(new MessageElement.Quote(msgId, "", preview)); return this; }
        public Builder reply(String msgId)        { list.add(new MessageElement.Reply(msgId)); return this; }
        public Builder markdown(String md)        { list.add(new MessageElement.Markdown(md)); return this; }
        public Builder add(MessageElement e)      { list.add(e); return this; }

        public MessageChain build() {
            return new MessageChain(list);
        }
    }
}
