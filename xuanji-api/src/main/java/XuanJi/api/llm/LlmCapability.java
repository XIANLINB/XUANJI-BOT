package XuanJi.api.llm;

/**
 * LLM 供应商能力标记 —— 声明某个 {@link LlmProvider} 支持哪些能力。
 *
 * <p>框架按能力位决定功能可用性：例如控制台 AI 设置页把「图片生成」置灰，
 * 本质是检查当前选中供应商的 {@code capabilities()} 是否包含 {@link #IMAGE_GEN}。
 * P0 仅实现 {@link #CHAT}，其余能力随阶段逐步解锁。
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
    ROLE
}
