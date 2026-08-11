package XuanJi.api.llm;

import XuanJi.api.event.XuanJiEvent;

/**
 * LLM 回复发送桥接 —— 平台适配器实现，把 LLM 生成的文本/语音发送到事件所在会话。
 *
 * <p>llm 模块保持平台无关：不依赖任何 adapter 的消息发送器，只调用本接口；
 * qqbot（或未来 onebot）各自实现 {@link #reply} / {@link #replyVoice}，
 * 从 {@link XuanJiEvent} 解析机器人实例 / 群 ID / 回复消息 ID 并走平台发送通道。
 */
public interface LlmReplySink {

    /**
     * 将 LLM 回复文本发送到事件来源会话（群聊/私聊）。
     *
     * @param event 触发 LLM 的原事件（含 bot / group / replyToMsgId 上下文）
     * @param text  LLM 生成的回复文本（已按平台长度限制分段）
     */
    void reply(XuanJiEvent event, String text);

    /**
     * 将合成的语音发送到事件来源会话（TTS 工具调用时使用）。
     *
     * <p>默认实现不支持（抛异常），平台适配器按自身能力覆盖：
     * 例如 qqbot 先落盘 wav 后发文本标注/富媒体语音消息。
     *
     * @param event    触发 LLM 的原事件
     * @param audio    合成后的音频字节（wav / mp3 等，取决于供应商返回）
     * @param format   音频格式标识（"wav" / "mp3" / "silk" 等，供适配器判断）
     * @param fallback 不支持/发送失败时的文本兜底（AI 说的话），用于发一条文本消息
     */
    default void replyVoice(XuanJiEvent event, byte[] audio, String format, String fallback) {
        throw new UnsupportedOperationException("当前平台不支持发送语音消息");
    }

    /**
     * 将生成的图片发送到事件来源会话（文生图工具调用时使用）。
     *
     * <p>默认实现不支持（抛异常），平台适配器按自身能力覆盖。
     *
     * @param event     触发 LLM 的原事件
     * @param imageUrl  图片公网 URL（AI 生成后返回）
     * @param fallback  不支持/发送失败时的文本兜底
     */
    default void replyImage(XuanJiEvent event, String imageUrl, String fallback) {
        throw new UnsupportedOperationException("当前平台不支持发送图片");
    }

    /**
     * 将图片字节（PNG/JPG 等）发送到事件来源会话（本地渲染卡片/表情包使用）。
     *
     * <p>默认实现不支持（抛异常），平台适配器按自身能力覆盖。与 {@link #replyImage}
     * 的区别：这里发送的是本地已生成的图片字节，无需公网 URL（走富媒体流上传）。
     *
     * @param event    触发 LLM 的原事件
     * @param image    图片字节（PNG 等）
     * @param fallback 不支持/发送失败时的文本兜底
     */
    default void replyImageFile(XuanJiEvent event, byte[] image, String fallback) {
        throw new UnsupportedOperationException("当前平台不支持发送图片文件");
    }
}
