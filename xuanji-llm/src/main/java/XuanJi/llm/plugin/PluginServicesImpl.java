package XuanJi.llm.plugin;

import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmMessage;
import XuanJi.api.plugin.PluginServices;
import XuanJi.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件能力门面实现 — 仅暴露 LLM 对话能力。
 *
 * <p>群管 / 主动发送 / 平台查询已统一收敛到 {@code XuanJi.sdk.bot.Bot} 门面，
 * 由框架在命令方法参数注入时提供。本实现只负责把 {@link PluginServices} 的
 * {@code chat} 调用转发到 {@link LlmService}。
 *
 * <p>插件命令方法声明 {@link PluginServices} 参数即由 CommandRegistry 自动注入本实现。
 */
@Slf4j
@Component
public class PluginServicesImpl implements PluginServices {

    private final LlmService llmService;

    public PluginServicesImpl(LlmService llmService) {
        this.llmService = llmService;
    }

    // ──────────── LLM ────────────

    @Override
    public String chat(String user) {
        return chat(null, user);
    }

    @Override
    public String chat(String system, String user) {
        List<LlmMessage> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(LlmMessage.system(system));
        }
        messages.add(LlmMessage.user(user == null ? "" : user));
        return llmService.chat(messages, LlmChatOptions.defaults());
    }
}
