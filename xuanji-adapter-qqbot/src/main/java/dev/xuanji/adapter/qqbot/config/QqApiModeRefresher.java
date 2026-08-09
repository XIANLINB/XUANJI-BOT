package dev.xuanji.adapter.qqbot.config;

import dev.xuanji.adapter.qqbot.api.QqApiService;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * QQ 开放平台 API 基地址模式刷新器。
 *
 * <p>把「新统一地址 / 老平台（正式+沙箱区分）」从启动时静态读取改为<b>运行时全局配置</b>：
 * <ul>
 *   <li>配置键：{@code framework.qqbot.api_base_mode}（存于 xuanji_config，运行设置页可改）</li>
 *   <li>值：{@code new} = 新统一地址 {@code api.bot.qq.com}（默认，不区分沙箱/正式）</li>
 *   <li>值：{@code legacy} = 老平台 {@code api.sgroup.qq.com}，按机器人环境（SANDBOX/PRODUCTION）自动选地址</li>
 * </ul>
 * 启动时 + 每 30 秒从 ConfigService 读取并同步到 {@link QqApiService}（HTTP API 调用）与
 * {@link QqBotWsManager}（WebSocket 网关），运行设置页改配置后 30 秒内生效。
 *
 * @see QqPlatformConfig#getApiBaseUrl(boolean, String)
 */
@Slf4j
@Component
public class QqApiModeRefresher {

    /** 全局配置键：QQ 开放平台 API 基地址模式（new / legacy），默认 new。 */
    public static final String MODE_KEY = "framework.qqbot.api_base_mode";

    private final ConfigService configService;
    private final QqApiService qqApiService;
    private final ObjectProvider<QqBotWsManager> wsManager;

    public QqApiModeRefresher(ConfigService configService,
                              QqApiService qqApiService,
                              ObjectProvider<QqBotWsManager> wsManager) {
        this.configService = configService;
        this.qqApiService = qqApiService;
        this.wsManager = wsManager;
        apply();
    }

    /** 每 30 秒同步一次（与 MediaStorageInitializer 同模式，运行设置改动 30s 内生效）。 */
    @Scheduled(initialDelay = 5_000, fixedDelay = 30_000)
    public void refresh() {
        apply();
    }

    /** 当前是否使用新统一地址（配置缺省 = new）。 */
    public boolean currentIsNewOpenBot() {
        try {
            String mode = configService.getGlobalConfig().get(MODE_KEY);
            if (mode == null || mode.isBlank()) return true;
            return !"legacy".equalsIgnoreCase(mode.trim());
        } catch (Exception e) {
            return true;
        }
    }

    private void apply() {
        try {
            boolean isNew = currentIsNewOpenBot();
            qqApiService.setNewOpenBot(isNew);
            wsManager.ifAvailable(ws -> ws.setNewOpenBot(isNew));
            log.debug("[QqApiMode] 开放平台版本: {} ({})",
                    isNew ? "新统一地址" : "老平台（按环境区分）",
                    isNew ? "api.bot.qq.com" : "api.sgroup.qq.com");
        } catch (Exception e) {
            log.debug("[QqApiMode] 刷新失败: {}", e.getMessage());
        }
    }
}
