package XuanJi.api.llm;

import java.time.Instant;

/**
 * LLM 会话快照 —— 一次「连续对话 / Agent 任务」的上下文标识。
 *
 * <p>与 {@code ConversationSession}（api/action）的关系：
 * ConversationSession 是框架级「多步交互」状态机（begin/get/update/end），
 * 面向命令注册表与 Agent；{@code ChatSession} 是 LLM 消息上下文的轻量句柄
 * （记住该会话用哪些消息、属于哪个 bot/群/用户）。P3 Agent 落地时两者协作。
 *
 * <p>P0 仅定义模型与自动过期语义，具体存储/缓存由 llm 模块实现。
 */
public record ChatSession(
        /** 会话唯一 id */
        String sessionId,
        /** 会话归属：bot_key */
        String botKey,
        /** 会话归属：群 openid / 用户 openid（二者按场景取一） */
        String scopeId,
        /** 创建时间 */
        Instant createdAt,
        /** 过期时间（相对 now 由服务端 ttlSeconds 计算） */
        Instant expireAt
) {

    public boolean expired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }
}
