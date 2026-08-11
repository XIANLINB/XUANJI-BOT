package XuanJi.llm.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 全局配置 —— 对应控制台「AI 能力 → AI 设置」页，持久化于独立表 {@code xuanji_llm_config}。
 *
 * <p>默认值遵循「安全优先」决策：聊天总开关默认关、必须 @ 才回复、带冷却与每日 token 上限；
 * 意图路由 / AI 审核两项开关预留（P2 / P4 解锁，P0 仅存值不生效）。
 */
@Data
public class LlmConfig {

    // ──────────── 供应商 ────────────
    /** 供应商 id（见 LlmProvider.id()），默认 DeepSeek */
    private String providerId = "deepseek";

    /** API Key（Bearer 认证） */
    private String apiKey = "";

    /** 兼容协议基地址，DeepSeek 默认官方地址 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名，默认 deepseek-v4-flash（DeepSeek 当前主推，1M 上下文） */
    private String model = "deepseek-v4-flash";

    /** 采样温度 0~2 */
    private double temperature = 0.7;

    /** 单次回复最大 token 数 */
    private int maxTokens = 1024;

    // ──────────── 能力绑定（多供应商多模型：每能力选「供应商+模型」） ────────────
    /** 对话能力绑定供应商 id（xuanji_llm_provider.id；空=退回上方 providerId/apiKey/baseUrl/model） */
    private Long chatProviderId;
    /** 对话能力绑定模型名（空=用 model） */
    private String chatModel;
    /** 图片理解绑定供应商 id（空=用 visionApiKey/visionModel） */
    private Long visionProviderId;
    /** 图片理解绑定模型名（空=用 visionModel） */
    private String visionModelBinding;
    /** 图像生成绑定供应商 id（空=复用图片理解/智谱） */
    private Long imageProviderId;
    /** 图像生成绑定模型名（空=用 imageModel） */
    private String imageModelBinding;
    /** 语音生成绑定供应商 id（空=退回 tts 或 fish 旧配置） */
    private Long ttsProviderId;
    /** 语音生成绑定模型名（空=用 ttsModel） */
    private String ttsModelBinding;

    /** 对话能力多选绑定（"providerId:modelName" 列表，按序尝试：第一个可用即用，失败自动切下一个） */
    private List<String> chatBindings = new ArrayList<>();
    /** 图片理解多选绑定 */
    private List<String> visionBindings = new ArrayList<>();
    /** 图像生成多选绑定 */
    private List<String> imageBindings = new ArrayList<>();
    /** 语音生成多选绑定 */
    private List<String> ttsBindings = new ArrayList<>();
    /** 视频理解多选绑定（V3） */
    private List<String> videoBindings = new ArrayList<>();
    /** 视频生成多选绑定（V3） */
    private List<String> videoGenBindings = new ArrayList<>();
    /** 语音克隆多选绑定（V3，P4+ 待接入） */
    private List<String> voiceCloneBindings = new ArrayList<>();

    // ──────────── 开关矩阵 ────────────
    /** 聊天总开关（默认关，显式开才启用 AI 对话） */
    private boolean enabled = false;

    /** 启用群白名单（空 = 全群开启） */
    private List<String> groupIds = new ArrayList<>();
    /** 启用单聊白名单（空 = 全单聊开启；填写允许的私聊用户 openid 列表） */
    private List<String> c2cUserIds = new ArrayList<>();

    /** 是否必须 @ 机器人才回复（省 token；私聊场景由 mentionRequired=0 不需校验） */
    private boolean mentionRequired = true;

    /** 每日 token 上限（成本护栏，默认 100 万；bot 级统一统计） */
    private long dailyTokenLimit = 1_000_000L;

    /** 同一群两次回复最小间隔秒（冷却） */
    private int cooldownSeconds = 10;

    // ──────────── 单聊独立维度（C2C 与群聊开关逻辑共用，名单各自独立） ────────────
    /** C2C 是否启用 AI（默认 false；私聊比群聊更需谨慎） */
    private boolean c2cEnabled = false;
    /** C2C mentionRequired 关闭（私聊直接进入，无需 @）—— 此值仅用于反向兼容，私聊永远单聊直达 */
    private boolean c2cMentionRequired = false;
    /** C2C 日 token 上限（私聊建议比群聊严格，默认 50 万） */
    private long c2cDailyTokenLimit = 500_000L;
    /** C2C 同一用户两次回复最小间隔秒 */
    private int c2cCooldownSeconds = 30;

    // ──────────── AI 能力独立总开关（用户可在高级页一键启停） ────────────
    private boolean toolCallingEnabled = true;      // 工具调用（Tool Calling）
    private boolean mcpEnabled = true;               // MCP 工具桥接
    /** 工具执行需用户确认（true=危险工具先征求确认；false=全部直接执行） */
    private boolean toolConfirmRequired = true;
    private boolean visionEnabled = true;             // 图片理解
    private boolean videoUnderstandEnabled = false;   // 视频理解（默认关，模型能力差异大）
    private boolean videoGenEnabled = false;          // 视频生成（默认关，资源贵）
    private boolean imageGenEnabled = true;          // 图像生成
    private boolean ttsEnabled = false;               // 语音合成（默认关，发语音是骚扰）
    private boolean voiceCloneEnabled = false;        // 语音克隆（合规风险，默认关）
    private boolean dailyReportEnabled = true;        // AI 日报
    private boolean renderCardEnabled = true;         // 图文卡片渲染（Playwright）

    // ──────────── 预留开关（P2 / P4 解锁，P0 仅存值） ────────────
    /** 意图路由（人话→命令），P2 实现 */
    private boolean intentRouting = false;

    /** AI 内容审核，P4 实现 */
    private boolean aiAudit = false;

    // ──────────── 用户画像（P1.6：全量消息认知 + 12h LLM 提炼） ────────────
    /** 用户画像总开关（本地统计零成本，LLM 提炼受此开关约束） */
    private boolean profileEnabled = false;

    /** 用户画像 LLM 提炼间隔小时（默认 12h，用户拍板） */
    private int profileExtractHours = 12;

    /** 每用户新增多少条消息触发一次提前提炼（0=仅按时间） */
    private int profileExtractMsgThreshold = 30;

    // ──────────── 主动搭话（P1.6：冷场检测 + 主动活跃气氛） ────────────
    /** 主动搭话总开关（默认关，防骚扰） */
    private boolean proactiveEnabled = false;

    /** 每群每日主动次数上限（0=禁止） */
    private int proactiveDailyLimit = 3;

    /** 两次主动的最小间隔分钟（冷却） */
    private int proactiveCooldownMinutes = 120;

    /** 群空闲多少分钟判定为"冷场" */
    private int proactiveIdleMinutes = 30;

    /** 主动时间段 HH:mm 起始 */
    private String proactiveTimeStart = "09:00";

    /** 主动时间段 HH:mm 结束 */
    private String proactiveTimeEnd = "22:00";

    // ──────────── 下游调用护栏（背压，#405） ────────────
    /** 同时进行的 LLM 出站调用并发上限（背压，防突发打爆供应商 API / 本地线程堆积 OOM）；
     *  默认 64 宽松值，正常流量无感；≤0 视为未配置，回退默认。 */
    private int maxConcurrency = 64;

    // ──────────── 多模态行为参数（凭据已迁至「供应商管理」+「能力选择」绑定） ────────────
    /** TTS 默认音色（人格未指定音色时用）：冰糖/茉莉/苏打/白桦/Mia/Chloe/Milo/Dean/mimo_default */
    private String ttsVoice = "冰糖";

    /** 默认风格控制文本（user 消息自然语言指令），如"温柔少女音，语速偏慢，带着些许娇羞" */
    private String ttsStylePrompt = "";

    /** TTS 输出音频格式：wav / pcm16 / mp3，默认 wav */
    private String ttsAudioFormat = "wav";

    // ──────────── TTS 备选：Fish Audio S2.1 Pro（免费，quota 不卡脖子） ────────────
    /** Fish 音色 reference_id（fish.audio 音色库或克隆音色 ID；留空用 Fish 默认音色） */
    private String fishVoice = "";

    /** Fish 默认风格描述（自然语言，合成时拼进文本的 [方括号] 情感控制，如清璃/落落风格） */
    private String fishStylePrompt = "清冷低沉、疏离慵懒的少女御姐音，语速偏慢，声线平稳克制，尾音气声收束，藏着一丝温柔倦意";

    /** Fish 语速（0.5-2.0，默认 1.0） */
    private double fishSpeed = 1.0;

    // ──────────── 图文卡片渲染（Playwright，可选） ────────────
    /** 图文卡片渲染总开关（默认开；关掉后 render_card / 日报图片版不启动 Chromium，返回"未启用"） */
    private boolean renderEnabled = true;
}
