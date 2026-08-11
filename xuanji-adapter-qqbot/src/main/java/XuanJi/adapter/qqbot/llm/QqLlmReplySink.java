package XuanJi.adapter.qqbot.llm;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.adapter.qqbot.sender.QqXuanJiMessageSender;
import XuanJi.api.context.BotContext;
import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.llm.LlmReplySink;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.sender.XuanJiSendReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 平台 LLM 回复发送 —— 把 LLM 生成的回复通过框架统一发送出口发回事件会话。
 *
 * <p>文本/图片（URL）回复走 {@link QqXuanJiMessageSender}（协议「普通话」出口）：
 * 在 llm 异步线程内用 {@link BotContext} ScopedValue 绑定原事件再调 {@link #reply}，
 * 使统一出口能正确解析机器人实例 / 群 ID / replyToMsgId。
 *
 * <p>语音 / 本地图片字节类回复保留专用方法（协议决定：字节媒体不在 MessageChain 上承载）：
 * 直接调 qqbot {@link MessageSender} 的富媒体通道（base64 / multipart 上传）。
 */
@Slf4j
@Component
public class QqLlmReplySink implements LlmReplySink {

    private final QqXuanJiMessageSender xuanJiSender;
    private final MessageSender messageSender;

    public QqLlmReplySink(QqXuanJiMessageSender xuanJiSender, MessageSender messageSender) {
        this.xuanJiSender = xuanJiSender;
        this.messageSender = messageSender;
    }

    @Override
    public void reply(XuanJiEvent event, String text) {
        if (event == null || text == null || text.isBlank()) {
            return;
        }
        try {
            ScopedValue.where(BotContext.currentEvent, event).run(() -> {
                XuanJiSendReceipt r = xuanJiSender.reply(XuanJiMessage.text(text));
                if (r.success()) {
                    log.info("[LLM] 回复已发送: {}ms", r.elapsedMs());
                } else {
                    log.warn("[LLM] 回复发送失败: {}", r.errorMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[LLM] 回复发送异常: {}", e.getMessage());
        }
    }

    /**
     * 语音回复：QQ 富媒体语音（msg_type=7）。
     *
     * <p>字节类媒体专用方法（协议 P3 决策：字节媒体不进入 MessageChain，保留专口）。
     * QQ 开放平台 /v2/groups/{id}/files 上传支持 {@code file_data}（base64）字段，
     * 免公网 URL；语音格式支持 wav/mp3/flac/silk（file_type=3）。发送失败仅记日志，不发送任何兜底内容。
     */
    @Override
    public void replyVoice(XuanJiEvent event, byte[] audio, String format, String fallback) {
        if (event == null) {
            throw new UnsupportedOperationException("当前事件不支持语音发送");
        }
        if (audio == null || audio.length == 0) {
            log.warn("[LLM] 语音数据为空，不发送");
            return;
        }
        try {
            String robotId = event.bot() != null ? event.bot().selfId() : "";
            String envType = event.envType() != null ? event.envType() : "PRODUCTION";
            if (event.group() != null) {
                messageSender.uploadAndSendGroupVoice(robotId, envType, event.group().groupId(), audio, event.replyToMsgId());
                log.info("[LLM] 群聊语音已发送: group={}, {}B", event.group().groupId(), audio.length);
            } else if (event.sender() != null && event.sender().id() != null && !event.sender().id().isBlank()) {
                messageSender.uploadAndSendC2cMediaData(robotId, envType, event.sender().id(), 3, audio, event.replyToMsgId());
                log.info("[LLM] 单聊语音已发送: user={}, {}B", event.sender().id(), audio.length);
            }
        } catch (Exception e) {
            log.warn("[LLM] 语音发送失败: {}", e.getMessage());
        }
    }

    /**
     * 图片回复（URL）：走统一发送出口 —— 组装 {@code [Image(url)]} 消息链交给
     * {@link QqXuanJiMessageSender}（富媒体 msg_type=7，URL 转存）。发送失败仅记日志，不发送任何兜底内容。
     */
    @Override
    public void replyImage(XuanJiEvent event, String imageUrl, String fallback) {
        if (event == null) {
            throw new UnsupportedOperationException("当前事件不支持图片发送");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("[LLM] 图片 URL 为空，不发送");
            return;
        }
        try {
            ScopedValue.where(BotContext.currentEvent, event).run(() -> {
                XuanJiSendReceipt r = xuanJiSender.reply(XuanJiMessage.builder().image(imageUrl).build());
                if (!r.success()) {
                    throw new RuntimeException(r.errorMessage());
                }
            });
            log.info("[LLM] 图片已发送: url={}", imageUrl);
        } catch (Exception e) {
            log.warn("[LLM] 图片发送失败: {}", e.getMessage());
        }
    }

    /**
     * 本地图片字节回复：QQ 富媒体图片（msg_type=7），multipart 流上传。
     *
     * <p>字节类媒体专用方法（协议 P3 决策：字节媒体不进入 MessageChain，保留专口）。
     * 用于本地渲染的卡片/表情包：无需公网 URL，直接以文件流方式
     * （multipart /v2/groups/{id}/files，file_type=1）上传 → file_info → 发图片消息。
     * 发送失败仅记日志，不发送任何兜底内容。
     */
    @Override
    public void replyImageFile(XuanJiEvent event, byte[] image, String fallback) {
        if (event == null) {
            throw new UnsupportedOperationException("当前事件不支持图片发送");
        }
        if (image == null || image.length == 0) {
            log.warn("[LLM] 图片数据为空，不发送");
            return;
        }
        try {
            String robotId = event.bot() != null ? event.bot().selfId() : "";
            String envType = event.envType() != null ? event.envType() : "PRODUCTION";
            if (event.group() != null) {
                messageSender.uploadAndSendGroupMediaData(robotId, envType, event.group().groupId(),
                        1, image, event.replyToMsgId());
                log.info("[LLM] 群聊图片(本地)已发送: group={}, {}B", event.group().groupId(), image.length);
            } else if (event.sender() != null && event.sender().id() != null && !event.sender().id().isBlank()) {
                messageSender.uploadAndSendC2cMediaData(robotId, envType, event.sender().id(),
                        1, image, event.replyToMsgId());
                log.info("[LLM] 单聊图片(本地)已发送: user={}, {}B", event.sender().id(), image.length);
            }
        } catch (Exception e) {
            log.warn("[LLM] 图片(本地)发送失败: {}", e.getMessage());
        }
    }
}
