package XuanJi.api.llm;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * LLM 供应商能力标记 —— 声明某个 {@link LlmProvider} 支持哪些能力。
 *
 * <p>框架按能力位决定功能可用性：例如控制台 AI 设置页把「图片生成」置灰，
 * 本质是检查当前选中供应商的 {@code capabilities()} 是否包含 {@link #IMAGE_GEN}。
 * P0 仅实现 {@link #CHAT}，其余能力随阶段逐步解锁。
 *
 * <p>本枚举是「能力契约」的<b>唯一事实来源</b>：类型级（{@code LlmProvider.capabilities()}）、
 * 模型级（DB {@code xuanji_llm_model.capabilities} 逗号串）、前端勾选，三处都应以本枚举为准，
 * 避免各写各的字符串导致脱钩（见优化框架分析报告）。{@link #parse(String)} 提供统一的字符串解析，
 * 未知 token 静默忽略，是模型级字段参与运行时决策的唯一解析入口。
 */
public enum LlmCapability {

    /** 文本对话（一次性 / 流式）。P0 提供，所有场景的地基。 */
    CHAT,

    /** 向量 Embedding —— P4 记忆 / RAG 检索的地基。 */
    EMBEDDING,

    /** 图片生成。 */
    IMAGE_GEN,

    /** 图片理解（多模态输入）。 */
    IMAGE_UNDERSTAND,

    /** 文字转语音（TTS）。 */
    TTS,

    /** 语音转文字（STT）。 */
    STT,

    /** 视频理解（多模态输入）。 */
    VIDEO_UNDERSTAND,

    /** 视频生成。 */
    VIDEO_GEN,

    /** 语音克隆（音色复刻）。 */
    VOICE_CLONE,

    /** 角色模型（角色扮演/对话专用）。 */
    ROLE;

    /**
     * 把逗号分隔的能力字符串（如 {@code "CHAT,IMAGE_GEN"} 或前端小写 {@code "chat,image_gen"}）
     * 解析为枚举集合。空串返回空集；未知 token 静默忽略（不抛异常），保证健壮性。
     */
    public static Set<LlmCapability> parse(String csv) {
        Set<LlmCapability> set = EnumSet.noneOf(LlmCapability.class);
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String token : csv.split(",")) {
            String s = token.trim().toUpperCase(Locale.ROOT);
            if (!s.isEmpty()) {
                try {
                    set.add(LlmCapability.valueOf(s));
                } catch (IllegalArgumentException ignored) {
                    // 未知能力位（如历史残留/拼写错误）忽略，不阻断解析
                }
            }
        }
        return set;
    }
}
