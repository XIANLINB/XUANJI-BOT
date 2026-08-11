package XuanJi.adapter.qqbot.llm;

import XuanJi.adapter.qqbot.api.MessageSender;
import XuanJi.adapter.qqbot.sender.QqXuanJiMessageSender;
import XuanJi.api.llm.ProactiveSender;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.sender.XuanJiSendReceipt;
import XuanJi.api.sender.XuanJiTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * QQ 平台主动搭话发送 —— 冷场时机器人主动向群发文本 / markdown。
 *
 * <p>文本 / markdown 走框架统一发送出口 {@link QqXuanJiMessageSender#send}：
 * 先经 {@link MessageSender#runWithRobotContext} 绑定机器人上下文，再组装
 * {@link XuanJiMessage}（普通话）交给统一出口翻译为 QQ 协议。
 *
 * <p><b>@ 群成员协议</b>：统一出口已内置升级逻辑——普通文本消息（{@code msg_type=0}）里写
 * {@code <@openid>} 只会被客户端显示为明文，不会渲染成真正的 @ 标签；统一出口发现文本含
 * {@code <@...>} 或 {@link XuanJiMessageElement.At} 元素时自动改走 markdown 消息
 * （{@code msg_type=2}）+ {@code <qqbot-at-user id=""/>} 协议。调用方仍按习惯写
 * {@code <@openid>} 即可。
 *
 * <p>图片字节走专用方法（协议 P3 决策：字节媒体不进入 MessageChain，保留专口）。
 */
@Slf4j
@Component
public class QqProactiveSender implements ProactiveSender {

    private final MessageSender messageSender;
    private final QqXuanJiMessageSender xuanJiSender;

    public QqProactiveSender(MessageSender messageSender, QqXuanJiMessageSender xuanJiSender) {
        this.messageSender = messageSender;
        this.xuanJiSender = xuanJiSender;
    }

    @Override
    public boolean sendText(String botKey, String groupOpenid, String text) {
        if (groupOpenid == null || text == null || text.isBlank()) return false;
        return sendChain(botKey, groupOpenid, XuanJiMessage.text(text), "主动文本");
    }

    @Override
    public boolean sendMarkdown(String botKey, String groupOpenid, String markdown) {
        if (groupOpenid == null || markdown == null || markdown.isBlank()) return false;
        return sendChain(botKey, groupOpenid, XuanJiMessage.builder().markdown(markdown).build(), "主动 markdown");
    }

    @Override
    public boolean sendImageBytes(String botKey, String groupOpenid, byte[] imageBytes) {
        if (groupOpenid == null || imageBytes == null || imageBytes.length == 0) return false;
        try {
            messageSender.uploadAndSendGroupMediaData(botKey, "PRODUCTION", groupOpenid,
                    1, imageBytes, null);
            log.info("[LLM] 主动图片已发送: group={}, {}B", groupOpenid, imageBytes.length);
            return true;
        } catch (Exception e) {
            log.warn("[LLM] 主动图片发送失败: group={}, err={}", groupOpenid, e.getMessage());
            return false;
        }
    }

    /** 组装普通话消息链交给统一发送出口（先绑定机器人上下文）。 */
    private boolean sendChain(String botKey, String groupOpenid, XuanJiMessage chain, String tag) {
        boolean[] ok = {false};
        try {
            messageSender.runWithRobotContext(botKey, () -> {
                XuanJiSendReceipt r = xuanJiSender.send(new XuanJiTarget.Group(groupOpenid), chain);
                ok[0] = r.success();
            });
            log.info("[LLM] {}已发送: group={}", tag, groupOpenid);
            return ok[0];
        } catch (Exception e) {
            log.warn("[LLM] {}发送失败: group={}, err={}", tag, groupOpenid, e.getMessage());
            return false;
        }
    }
}
