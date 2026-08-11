package XuanJi.llm;

import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmChatResponse;
import XuanJi.api.llm.LlmCredentials;
import XuanJi.api.llm.LlmMessage;
import XuanJi.api.llm.LlmProvider;
import XuanJi.api.llm.LlmToolCall;
import XuanJi.api.llm.LlmToolDefinition;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import XuanJi.llm.provider.LlmProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 服务门面 —— 组合配置存储 + 供应商注册表，向控制台与运行时提供统一入口。
 *
 * <p>P0 职责：配置读写、供应商清单、一次性对话、连通性测试。
 * P1 起追加：流式对话（SSE）、会话上下文保持、冷却/限额拦截。
 */
@Slf4j
@Service
public class LlmService {

    private final LlmConfigStore configStore;
    private final LlmProviderRegistry registry;
    private final XuanJi.llm.provider.ProviderService providerService;

    /** 最近一次 chatWithTools 的真实 usage（{prompt, completion}）；线程隔离，供用量统计读取。 */
    private final ThreadLocal<long[]> lastUsage = new ThreadLocal<>();

    /** 取走并清空本线程最近一次对话的真实 token 用量；无记录返回 {0,0}。 */
    public long[] takeLastUsage() {
        long[] u = lastUsage.get();
        lastUsage.remove();
        return u == null ? new long[]{0, 0} : u;
    }

    public LlmService(LlmConfigStore configStore, LlmProviderRegistry registry,
                      XuanJi.llm.provider.ProviderService providerService) {
        this.configStore = configStore;
        this.registry = registry;
        this.providerService = providerService;
    }

    // ──────────── 配置 ────────────

    public LlmConfig getConfig() {
        return configStore.get();
    }

    public void saveConfig(LlmConfig config) {
        configStore.save(config);
        log.info("[LLM] 配置已保存: provider={}, enabled={}, model={}",
                config.getProviderId(), config.isEnabled(), config.getModel());
    }

    // ──────────── 供应商 ────────────

    /** 供应商清单（控制台下拉 + 能力矩阵）。 */
    public List<Map<String, Object>> providers() {
        return registry.all().stream().map(p -> Map.of(
                "id", p.id(),
                "name", p.displayName(),
                "capabilities", p.capabilities().stream().map(Enum::name).sorted().toList(),
                "defaultModel", p.defaultModel()
        )).toList();
    }

    // ──────────── 对话 ────────────

    /** 对话绑定：供应商类型 + 凭据 + 模型名。 */
    private record ChatBinding(String providerType, LlmCredentials credentials, String model) {}

    /** 解析对话绑定列表（多选：按序尝试，第一个可用即用；每个绑定的多 key 依次轮询）。 */
    private List<ChatBinding> resolveChatBindings(LlmConfig cfg) {
        List<ChatBinding> out = new ArrayList<>();
        // 多选绑定优先（"providerId:modelName"）
        if (cfg.getChatBindings() != null && !cfg.getChatBindings().isEmpty()) {
            for (String b : cfg.getChatBindings()) {
                out.addAll(bindingFromString(b, cfg.getModel()));
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        // 兼容旧的单值绑定
        if (cfg.getChatProviderId() != null && cfg.getChatProviderId() > 0) {
            out.addAll(bindingFromProvider(cfg.getChatProviderId(), cfg.getChatModel(), cfg.getModel()));
            if (!out.isEmpty()) {
                return out;
            }
        }
        // 退回旧单供应商配置
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("尚未配置 LLM API Key，请到「AI 能力 → AI 设置」填写");
        }
        out.add(new ChatBinding(cfg.getProviderId(),
                new LlmCredentials(cfg.getBaseUrl(), cfg.getApiKey()), cfg.getModel()));
        return out;
    }

    /** 解析 "providerId:modelName" 字符串为绑定列表（按 key 展开）；供应商不存在返回空。 */
    private List<ChatBinding> bindingFromString(String binding, String fallbackModel) {
        int idx = binding == null ? -1 : binding.indexOf(':');
        if (idx <= 0) {
            return List.of();
        }
        long providerId;
        try {
            providerId = Long.parseLong(binding.substring(0, idx));
        } catch (NumberFormatException e) {
            return List.of();
        }
        return bindingFromProvider(providerId, binding.substring(idx + 1), fallbackModel);
    }

    /** 供应商绑定 → 按多 key 展开为绑定列表。 */
    private List<ChatBinding> bindingFromProvider(long providerId, String model, String fallbackModel) {
        Map<String, Object> p = providerService.getProvider(providerId);
        if (p == null) {
            return List.of();
        }
        String useModel = (model != null && !model.isBlank()) ? model : fallbackModel;
        String type = String.valueOf(p.get("providerType"));
        String baseUrl = String.valueOf(p.get("baseUrl"));
        List<LlmCredentials> credsList = new ArrayList<>();
        for (String key : providerService.enabledKeys(providerId)) {
            credsList.add(new LlmCredentials(baseUrl, key));
        }
        if (credsList.isEmpty()) {
            credsList.add(new LlmCredentials(baseUrl, String.valueOf(p.get("apiKey"))));
        }
        return credsList.stream().map(c -> new ChatBinding(type, c, useModel)).toList();
    }

    /**
     * 一次性对话。按全局配置选供应商并组装凭据（多选绑定按序尝试）。
     *
     * @throws IllegalStateException 未配置 apiKey / 供应商不存在 / 供应商调用失败
     */
    public String chat(List<LlmMessage> messages, LlmChatOptions options) {
        LlmConfig cfg = configStore.get();
        List<String> errors = new ArrayList<>();
        for (ChatBinding b : resolveChatBindings(cfg)) {
            LlmProvider provider = registry.byId(b.providerType());
            if (provider == null) {
                errors.add(b.providerType() + " 供应商实现不存在");
                continue;
            }
            LlmChatOptions effective = options != null ? options
                    : new LlmChatOptions(b.model(), cfg.getTemperature(), cfg.getMaxTokens(), null);
            try {
                return provider.chat(messages, effective, b.credentials());
            } catch (Exception e) {
                errors.add(b.providerType() + "(" + b.model() + "): " + e.getMessage());
                log.warn("[LLM] 对话供应商 {} 失败，尝试下一个: {}", b.providerType(), e.getMessage());
            }
        }
        throw new IllegalStateException(errors.isEmpty()
                ? "尚未配置可用的对话供应商" : String.join("；", errors));
    }

    /** 连通性测试：发一条最小消息验证 key/网络/模型。 */
    public String testConnection() {
        return chat(List.of(
                LlmMessage.system("你是连接性测试助手，只回复两个字：连通"),
                LlmMessage.user("ping")
        ), LlmChatOptions.defaults());
    }

    /**
     * 流式对话 —— 逐段回调增量文本（控制台 SSE / 群聊分句用）。
     * 校验与选型逻辑同 {@link #chat}。
     */
    public void chatStream(List<LlmMessage> messages, LlmChatOptions options,
                           java.util.function.Consumer<String> onDelta) {
        LlmConfig cfg = configStore.get();
        List<String> errors = new ArrayList<>();
        for (ChatBinding b : resolveChatBindings(cfg)) {
            LlmProvider provider = registry.byId(b.providerType());
            if (provider == null) {
                errors.add(b.providerType() + " 供应商实现不存在");
                continue;
            }
            LlmChatOptions effective = options != null ? options
                    : new LlmChatOptions(b.model(), cfg.getTemperature(), cfg.getMaxTokens(), null);
            try {
                provider.chatStream(messages, effective, b.credentials(), onDelta);
                return;
            } catch (Exception e) {
                errors.add(b.providerType() + ": " + e.getMessage());
                log.warn("[LLM] 流式供应商 {} 失败，尝试下一个: {}", b.providerType(), e.getMessage());
            }
        }
        throw new IllegalStateException(errors.isEmpty()
                ? "尚未配置可用的对话供应商" : String.join("；", errors));
    }

    // ──────────── 工具调用（Function Calling）────────────

    /** 工具调用最大轮次（防模型死循环；Agent 模式支持多步规划）。 */
    private static final int MAX_TOOL_ROUNDS = 8;

    /**
     * 带 Function Calling 的对话 —— 内部完成工具调用循环：
     * 模型请求调工具 → {@link ToolExecutor} 执行 → 结果回填 → 再请求 → 直到模型给出最终答复。
     *
     * @param messages 初始对话上下文（含 system 人格）
     * @param tools    可用工具定义（null/空 = 无工具）
     * @param executor 工具执行回调（由调用方注入 ToolRegistry 执行）
     * @return 模型最终回复文本
     */
    public String chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools,
                                ToolExecutor executor) {
        LlmConfig cfg = configStore.get();
        lastUsage.set(new long[]{0, 0}); // 本线程累计 usage（工具循环多轮累加）
        List<String> errors = new ArrayList<>();
        for (ChatBinding b : resolveChatBindings(cfg)) {
            LlmProvider provider = registry.byId(b.providerType());
            if (provider == null) {
                errors.add(b.providerType() + " 供应商实现不存在");
                continue;
            }
            LlmChatOptions options = new LlmChatOptions(b.model(), cfg.getTemperature(), cfg.getMaxTokens(), tools);
            LlmCredentials creds = b.credentials();

            List<LlmMessage> msgs = new ArrayList<>(messages);
            boolean failed = false;
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                LlmChatResponse resp;
                try {
                    resp = provider.chatWithTools(msgs, tools, options, creds);
                } catch (Exception e) {
                    errors.add(b.providerType() + "(" + b.model() + "): " + e.getMessage());
                    log.warn("[LLM] 工具供应商 {} 失败，尝试下一个: {}", b.providerType(), e.getMessage());
                    failed = true;
                    break;
                }
                // 累加真实 usage（prompt/completion）
                long[] acc = lastUsage.get();
                acc[0] += resp.promptTokens();
                acc[1] += resp.completionTokens();
                if (!resp.hasToolCalls()) {
                    return resp.content() != null ? resp.content() : "";
                }
                // 组装 assistant 消息（编码 tool_calls）+ 逐条执行并回填 tool 结果
                StringBuilder callsJson = new StringBuilder("[");
                List<String> results = new ArrayList<>();
                List<LlmToolCall> calls = resp.toolCalls();
                for (int i = 0; i < calls.size(); i++) {
                    LlmToolCall call = calls.get(i);
                    if (i > 0) callsJson.append(",");
                    callsJson.append("{\"id\":\"").append(jsonEscape(call.id()))
                            .append("\",\"name\":\"").append(jsonEscape(call.name()))
                            .append("\",\"arguments\":").append(call.arguments() == null ? "{}" : call.arguments())
                            .append("}");
                    String result;
                    try {
                        result = executor.execute(call.name(), call.arguments());
                    } catch (Exception e) {
                        result = "执行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    }
                    results.add("{\"id\":\"" + jsonEscape(call.id()) + "\",\"result\":\"" + jsonEscape(result) + "\"}");
                }
                callsJson.append("]");
                msgs.add(LlmMessage.assistant(LlmMessage.TOOL_CALLS_PREFIX + callsJson));
                for (String r : results) {
                    msgs.add(LlmMessage.tool(LlmMessage.TOOL_RESULT_PREFIX + r));
                }
            }
            if (!failed) {
                return "（工具调用轮次过多，未完成）";
            }
        }
        throw new IllegalStateException(errors.isEmpty()
                ? "尚未配置可用的对话供应商" : String.join("；", errors));
    }

    /** 工具执行回调：调用方返回工具执行结果文本。 */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, String argsJson) throws Exception;
    }

    /** 简单 JSON 字符串转义（工具协议编码用）。 */
    static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
