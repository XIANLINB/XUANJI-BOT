package XuanJi.adapter.qqbot.config;

/**
 * QQ 开放平台常量配置
 *
 * <p>集中管理 QQ 开放平台的所有地址和常量，供框架各模块统一引用。
 * 避免在多个服务类中重复定义相同的 URL 常量。
 *
 * <h3>平台版本说明</h3>
 * <ul>
 *   <li><b>老版本</b> — 区分正式环境和沙箱环境，使用不同的 API 基地址</li>
 *   <li><b>新版本</b> — 统一地址，不区分环境</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link XuanJi.adapter.qqbot.api.QqApiService} — API 调用时选择基地址</li>
 *   <li>{@link XuanJi.adapter.qqbot.websocket.GatewayService} — 获取网关地址时选择基地址</li>
 *   <li>{@link XuanJi.adapter.qqbot.registry.AccessTokenServiceImpl} — 获取 Token 时选择接口地址</li>
 * </ul>
 */
public final class QqPlatformConfig {

    private QqPlatformConfig() {
        // 工具类，禁止实例化
    }

    // ==================== API 基地址 ====================

    /** 老版本 API 基地址（正式环境） */
    public static final String OLD_API_BASE_URL = "https://api.sgroup.qq.com";

    /** 老版本 API 基地址（沙箱环境） */
    public static final String OLD_SANDBOX_API_BASE_URL = "https://sandbox.api.sgroup.qq.com";

    /** 新版本 API 基地址（统一地址，不区分沙箱/正式） */
    public static final String NEW_API_BASE_URL = "https://api.bot.qq.com";

    // ==================== Token 接口地址 ====================

    /** 老版本 Token 获取接口地址 */
    public static final String OLD_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    /** 新版本 Token 获取接口地址 */
    public static final String NEW_TOKEN_URL = "https://api.bot.qq.com/app/getAppAccessToken";

    // ==================== 默认配置 ====================

    /**
     * 默认的事件订阅意图位掩码（基础事件，无需特殊权限）
     *
     * <p>包含以下事件：
     * <ul>
     *   <li>GROUP_AND_C2C_EVENT (1 &lt;&lt; 24) — 群聊/单聊消息与事件（含入群申请 GROUP_JOIN_REQUEST、群成员进退群等）</li>
     *   <li>GROUP_AT_MESSAGE (1 &lt;&lt; 25) — 群 @ 消息</li>
     *   <li>C2C_MESSAGE (1 &lt;&lt; 26) — 单聊消息</li>
     * </ul>
     */
    public static final int DEFAULT_INTENTS = (1 << 24) + (1 << 25) + (1 << 26);  // 117440512

    /**
     * 根据平台版本和环境类型获取 API 基地址
     *
     * @param isNewOpenBot 是否使用新开放平台
     * @param envType      环境类型（SANDBOX / PRODUCTION）
     * @return API 基地址
     */
    public static String getApiBaseUrl(boolean isNewOpenBot, String envType) {
        if (isNewOpenBot) {
            return NEW_API_BASE_URL;
        }
        return "SANDBOX".equalsIgnoreCase(envType)
                ? OLD_SANDBOX_API_BASE_URL
                : OLD_API_BASE_URL;
    }

    /**
     * 根据平台版本获取 Token 接口地址
     *
     * @param isNewOpenBot 是否使用新开放平台
     * @return Token 接口地址
     */
    public static String getTokenUrl(boolean isNewOpenBot) {
        return isNewOpenBot ? NEW_TOKEN_URL : OLD_TOKEN_URL;
    }
}
