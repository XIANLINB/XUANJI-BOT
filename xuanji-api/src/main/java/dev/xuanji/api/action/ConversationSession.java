package dev.xuanji.api.action;

import dev.xuanji.api.message.MessageChain;
import java.time.Duration;

/**
 * 会话等待 — 向用户发一条消息并等待其下一条回复。
 *
 * <p>多轮交互插件的核心能力（如"是否确认签到？[是/否]"）。
 */
public interface ConversationSession {

    /**
     * 发送提示消息并等待用户下一条回复，超时返回 null。
     */
    MessageChain awaitResponse(MessageChain prompt, Duration timeout);
}
