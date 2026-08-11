package XuanJi.api.adapter;

/**
 * 平台能力枚举 —— 机器人/适配器声明的能力位，核心据此决定动作降级策略。
 *
 * <p>取代原先 {@link XuanJiBot#capabilities()} 的裸字符串（"can_recall" 等各写各的），
 * 统一为强类型枚举：核心执行动作前先查询能力，平台不支持则降级（如发文本+提示）。
 *
 * <p>新平台接入时按自身能力声明；能力位只增不改，缺省 = 不具备该能力。
 */
public enum PlatformCapability {

    // ──────────── 发送类 ────────────
    /** 发送纯文本 */
    SEND_TEXT,
    /** 发送图片 */
    SEND_IMAGE,
    /** 发送语音 */
    SEND_VOICE,
    /** 发送视频 */
    SEND_VIDEO,
    /** 发送文件 */
    SEND_FILE,
    /** 发送 Markdown 卡片 */
    SEND_MARKDOWN,
    /** 发送按钮键盘 */
    SEND_KEYBOARD,
    /** 发送 ARK 卡片 */
    SEND_ARK,
    /** @全体成员 */
    SEND_AT_ALL,

    // ──────────── 动作类 ────────────
    /** 撤回消息 */
    RECALL,
    /** 踢出群成员 */
    KICK,
    /** 禁言群成员 */
    MUTE,
    /** 审批加群 / 好友请求 */
    APPROVE,
    /** 设置群名片 */
    SET_CARD,

    // ──────────── 接收类 ────────────
    /** 接收富媒体（图片/语音/视频/文件） */
    RECEIVE_MEDIA,
    /** 接收 Markdown / 按钮交互回调 */
    RECEIVE_INTERACTION
}
