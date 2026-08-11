package XuanJi.llm.tool;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.core.command.CommandRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 框架命令类 LLM 工具 —— 意图路由落地：
 * 模型把用户"人话"识别为命令意图后，调用 {@code run_command} 工具执行框架命令。
 *
 * <p>{@code run_command} 是 {@code confirm=true}（执行命令有副作用），走确认流程后执行。
 * 执行前通过 {@link CommandRegistry#setContext} 准备机器人/群上下文（事件 DTO 传 null，
 * 命令 handler 若仅依赖文本参数可正常工作；依赖完整事件结构的命令在意图路由场景下受限）。
 */
@Slf4j
@Component
public class CommandTools {

    private final CommandRegistry commandRegistry;

    public CommandTools(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @LlmTool(name = "run_command",
            descriptionZh = "执行框架命令（如 /help、签到）",
            description = "执行框架的命令。用户用自然语言表达了某个命令意图（如「签到」「帮我看看帮助」「打卡」）时调用。命令示例：/help、/签到、/help 签到",
            confirm = true)
    public String runCommand(LlmToolContext ctx,
                             @LlmToolParam(name = "command", value = "要执行的命令文本，如 '/help' 或 '签到'") String command) {
        if (command == null || command.isBlank()) return "命令为空";
        String botKey = ctx != null ? ctx.botKey() : "";
        try {
            CommandRegistry.setContext(botKey,
                    ctx != null ? ctx.groupId() : null,
                    "", ctx != null ? ctx.userId() : null,
                    null, null, "qqbot");
            try {
                String result = commandRegistry.executeGroupMessage(command);
                return result != null ? result : "命令「" + command + "」已执行";
            } finally {
                CommandRegistry.clearContext();
            }
        } catch (Exception e) {
            log.warn("[TOOL] run_command 失败: cmd={}, err={}", command, e.getMessage());
            return "命令执行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
