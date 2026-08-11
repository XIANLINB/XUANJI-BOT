package XuanJi.llm.tool;

import XuanJi.api.event.XuanJiEvent;

/**
 * 工具执行上下文 —— 内置工具方法可声明该类型参数，执行时由 ToolRegistry 自动注入当前会话信息。
 *
 * <p>工具方法签名示例：
 * <pre>{@code
 * @LlmTool(name="group_stats", description="查询当前群活跃度")
 * public String groupStats(LlmToolContext ctx) { ... }
 * }</pre>
 */
public record LlmToolContext(
        /** 机器人 key（selfId） */
        String botKey,
        /** 群 OpenID（私聊场景为空） */
        String groupId,
        /** 触发用户 ID */
        String userId,
        /** 用户当前消息文本 */
        String text,
        /** 原 XuanJiEvent（用于工具发语音/图片等需 event 的场景） */
        XuanJiEvent event
) {
}
