package XuanJi.llm.persona;

import lombok.Data;

/**
 * 结构化人格（角色卡）模型 —— 对应 {@code xuanji_llm_persona} 表。
 *
 * <p>设计参考 SillyTavern Character Card V2：name / personality / background /
 * scenario / speech_style / mes_example 等字段组合成完整 system 提示词。
 * {@code legacyPersona} 为旧版单文本 {@code persona} 的迁移兜底。
 */
@Data
public class LlmPersona {

    private Long id;

    /** BOT / GROUP / USER */
    private String scope = "BOT";

    private String botKey = "";
    private String groupId;
    private String userId;

    // ──── 结构化字段（角色卡）────
    /** 姓名（为空则用机器人名） */
    private String name;
    /** 年龄/年龄段 */
    private String age;
    /** 称呼（她/他/它，中文表达用） */
    private String gender;
    /** 性格描述（建议第二人称："你是.../你总是..."） */
    private String personality;
    /** 背景故事 / 外貌 / 身份 */
    private String background;
    /** 场景设定（"你在经营一家深夜咖啡馆"） */
    private String scenario;
    /** 说话风格 / 口头禅 / 句长 */
    private String speechStyle;
    /** 开场白（新会话首条） */
    private String firstMes;
    /** 示例对话（{{char}}: ... / {{user}}: ...，教语气） */
    private String mesExample;
    /** 额外系统指令 */
    private String systemExtra;
    /** 旧版 persona 文本（迁移兜底，组装时并入末尾） */
    private String legacyPersona;

    /** 人设锚点（3~5 条，用 | 或换行分隔）：说话风格关键词 / 世界观设定 / 禁忌。
     *  用于 P2 自评：AI 回复后轻量校验是否偏离人设（仅 roleplayMode 开启时生效）。 */
    private String anchors;

    /** 角色扮演模式：开启后注入「不自称 AI / 按角色身份发言」规则，模版默认开启。 */
    private boolean roleplayMode;

    /** TTS 音色（冰糖/茉莉/苏打/白桦/Mia/Chloe/Milo/Dean），留空用全局配置默认 */
    private String ttsVoice;

    /** TTS 风格控制（自然语言指令，描述这个角色的声音风格） */
    private String ttsStylePrompt;

    private String updatedAt;
}
