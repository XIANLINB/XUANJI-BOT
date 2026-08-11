package XuanJi.api.llm;

import java.util.List;

/**
 * 对话生成参数 —— 供应商无关的通用请求参数。
 *
 * <p>null 字段表示「使用供应商默认值」，由各 Provider 自行兜底。
 */
public record LlmChatOptions(
        /** 模型名，如 deepseek-chat；null 时用全局配置默认模型 */
        String model,
        /** 采样温度 0~2；null 用默认 0.7 */
        Double temperature,
        /** 单次回复最大 token 数；null 用供应商默认 */
        Integer maxTokens,
        /** 可用的 Function Calling 工具清单；null/空 = 不启用工具 */
        List<LlmToolDefinition> tools
) {

    public static LlmChatOptions defaults() {
        return new LlmChatOptions(null, null, null, null);
    }

    /** 带工具清单的选项（其余默认）。 */
    public static LlmChatOptions withTools(List<LlmToolDefinition> tools) {
        return new LlmChatOptions(null, null, null, tools);
    }
}
