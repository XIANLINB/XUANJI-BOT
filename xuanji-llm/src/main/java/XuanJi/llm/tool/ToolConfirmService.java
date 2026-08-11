package XuanJi.llm.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具确认服务 —— confirm=true 的工具执行前，先向用户征询"确认执行？"，
 * 用户回复确认词后执行。pending 存内存，维度为 bot:群:用户，带过期时间。
 *
 * <p>流程：
 * <ol>
 *   <li>LLM 工具循环里发现 confirm=true 工具 → {@link #request} 存 pending，
 *       返回"需要用户确认"文本给模型，模型转述征询用户</li>
 *   <li>用户回复「确认 / 同意 / 可以 / 是」 → {@link #tryConfirm} 命中 pending →
 *       执行工具并返回结果（不调 LLM 生成）</li>
 * </ol>
 */
@Slf4j
@Component
public class ToolConfirmService {

    /** pending 有效期（秒），超时自动作废 */
    private static final long TTL_SECONDS = 120;

    private final ToolRegistry registry;

    /** key: botKey:groupId:userId */
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    public ToolConfirmService(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 确认词匹配：兼容「@机器人 确认」等带前缀消息。
     * 先剥离 @ 提及前缀（QQ content 里为 {@code <@id> 用户名}，其他平台为 {@code @昵称}），
     * 再对剩余正文判断是否短确认词；限制长度避免长问句误匹配。
     */
    public static boolean isConfirmText(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 24) return false;
        // 剥离 @ 提及前缀（平台差异兜底）
        t = t.replaceAll("(?s)^<@[^>]+>\\s*", "").replaceAll("(?s)^@\\S+\\s*", "").trim();
        if (t.isEmpty() || t.length() > 16) return false;
        return t.matches("(?s).*(确认|同意|可以|好的|好|是|嗯|确定|执行|继续).*");
    }

    /** 是否存在待确认工具（供触发前优先处理确认回复）。 */
    public boolean hasPending(String botKey, String groupId, String userId) {
        Pending p = pending.get(key(botKey, groupId, userId));
        return p != null && p.expireAt > System.currentTimeMillis();
    }

    /** 请求确认：登记 pending，返回给模型的提示文本。 */
    public String request(String botKey, String groupId, String userId, String toolName, String argsJson,
                          LlmToolContext context) {
        String k = key(botKey, groupId, userId);
        pending.put(k, new Pending(toolName, argsJson, context,
                System.currentTimeMillis() + TTL_SECONDS * 1000));
        return "该操作（" + toolName + "）需要用户确认后才能执行，请向用户询问「确认执行 " + toolName + " 吗？」，等待用户回复确认。";
    }

    /**
     * 尝试确认并执行：命中 pending 则执行工具并返回结果，否则返回 null。
     * 无论是否命中都清除 pending（单次有效）。
     */
    public String tryConfirm(String botKey, String groupId, String userId) {
        String k = key(botKey, groupId, userId);
        Pending p = pending.remove(k);
        if (p == null) return null;
        if (p.expireAt < System.currentTimeMillis()) {
            return "（确认超时，请重新发起）";
        }
        try {
            String result = registry.execute(p.toolName, p.argsJson, p.context);
            log.info("[TOOL] 确认后执行完成: tool={}, bot={}, group={}", p.toolName, botKey, groupId);
            return result;
        } catch (Exception e) {
            log.warn("[TOOL] 确认后执行失败: tool={}, err={}", p.toolName, e.getMessage());
            return "执行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 待确认工具描述（前端/日志）。 */
    public String describe(String botKey, String groupId, String userId) {
        Pending p = pending.get(key(botKey, groupId, userId));
        return p != null ? p.toolName : null;
    }

    private static String key(String botKey, String groupId, String userId) {
        return (botKey == null ? "" : botKey) + ":" + (groupId == null ? "" : groupId) + ":" + (userId == null ? "" : userId);
    }

    private record Pending(String toolName, String argsJson, LlmToolContext context, long expireAt) {
    }
}
