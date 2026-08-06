package dev.xuanji.adapter.qqbot.registry;

/**
 * QQ 机器人 AccessToken 管理服务接口
 *
 * <p>定义 AccessToken 获取、刷新和验证的契约。
 * AccessToken 是调用 QQ 开放平台 API 的必要凭证，有效期通常为 7200 秒（2 小时）。
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link AccessTokenServiceImpl} — 内存缓存实现，无 Redis 依赖</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link dev.xuanji.adapter.qqbot.api.QqApiService} — API 调用时获取 Token</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.websocket.GatewayService} — 获取网关地址时获取 Token</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.websocket.QqBotWsClient} — WebSocket 鉴权时获取 Token</li>
 * </ul>
 *
 * @see AccessTokenServiceImpl 具体实现
 */
public interface AccessTokenService {

    /**
     * 获取指定机器人的 AccessToken（自动缓存和刷新）
     *
     * <p>优先从缓存中获取有效的 Token，如果缓存中没有或已过期，
     * 则自动调用 QQ 平台的 OAuth2 接口获取新 Token。
     *
     * @param appId        机器人 AppID（在 QQ 开放平台注册时获得）
     * @param appSecret    机器人 AppSecret（明文），用于向 QQ 平台换取 Token
     * @param envType      环境类型（SANDBOX / PRODUCTION），不同环境的 Token 独立管理
     * @param isNewOpenBot 是否使用新开放平台（true=新平台 api.bot.qq.com，false=老平台）
     * @return AccessToken 字符串，可直接用于 Authorization 头
     * @throws RuntimeException 获取 Token 失败时抛出（网络异常、凭证无效等）
     */
    String getAccessToken(String appId, String appSecret, String envType, boolean isNewOpenBot);

    /**
     * 强制刷新指定机器人的 AccessToken
     *
     * <p>忽略缓存，直接调用 QQ 平台接口获取新 Token 并更新缓存。
     * 通常在收到 401 响应时调用。
     *
     * @param appId        机器人 AppID
     * @param appSecret    机器人 AppSecret（明文）
     * @param envType      环境类型
     * @param isNewOpenBot 是否使用新开放平台
     * @return 新的 AccessToken 字符串
     * @throws RuntimeException 刷新失败时抛出
     */
    String refreshAccessToken(String appId, String appSecret, String envType, boolean isNewOpenBot);

    /**
     * 检查 Token 是否有效（缓存中存在且未过期）
     *
     * @param appId   机器人 AppID
     * @param envType 环境类型
     * @return true=缓存中有有效的 Token，false=无缓存或已过期
     */
    boolean isTokenValid(String appId, String envType);
}
