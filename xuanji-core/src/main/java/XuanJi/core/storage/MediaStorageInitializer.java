package XuanJi.core.storage;

import XuanJi.api.media.MediaFileDownloader;
import XuanJi.core.config.ConfigService;
import XuanJi.core.config.XuanJiRobotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 媒体存储初始化、开关快照刷新与清理（P1-D convertToFilePath 存储侧）。
 *
 * <p>启动时从 ConfigService 读取配置并注入 {@link MediaFileDownloader}：
 * <ul>
 *   <li>全局开关 {@code media.download.enabled}（默认 false，按需开启）</li>
 *   <li>单文件上限 {@code media.download.max_file_bytes}（默认 200MB）</li>
 *   <li>保留天数 {@code media.storage.ttl_days}（默认 7 天）</li>
 *   <li>总配额 {@code media.storage.max_bytes}（默认 4GB），超限删最旧</li>
 *   <li>bot 级开关 {@code media_download_enabled}（xuanji_bot_setting，覆盖全局）</li>
 * </ul>
 * 存储目录 {@code data/xuanji/media}（框架级，跨 bot 内容哈希去重共享）。
 * 每 30s 刷新开关快照（运行设置改动即时生效）；每天 03:30 清理过期/超配额文件。
 */
@Slf4j
@Component
public class MediaStorageInitializer {

    private static final String DEFAULT_DIR = "data/xuanji/media";

    private final ConfigService configService;
    private final XuanJiRobotProperties robotProperties;

    public MediaStorageInitializer(ConfigService configService, XuanJiRobotProperties robotProperties) {
        this.configService = configService;
        this.robotProperties = robotProperties;
        configure();
        refreshSwitches();
        // 启动清理一次过期/超配额文件（cron 的 @Scheduled 不支持 initialDelay，这里手动调）
        try {
            long removed = MediaFileDownloader.cleanup();
            if (removed > 0) {
                log.info("[媒体存储] 启动清理完成，删除 {} 个过期/超配额文件", removed);
            }
        } catch (Exception e) {
            log.debug("[媒体存储] 启动清理失败: {}", e.getMessage());
        }
    }

    private void configure() {
        try {
            long maxFile = longConfig("media.download.max_file_bytes", 200L * 1024 * 1024);
            long ttlDays = longConfig("media.storage.ttl_days", 7L);
            long maxTotal = longConfig("media.storage.max_bytes", 4L * 1024 * 1024 * 1024);
            Path dir = Path.of(DEFAULT_DIR).toAbsolutePath();
            MediaFileDownloader.configure(dir, maxFile, ttlDays, maxTotal);
            log.info("[媒体存储] 已初始化: dir={}, 单文件上限={}MB, 保留={}天, 总配额={}MB",
                    dir, maxFile / 1024 / 1024, ttlDays, maxTotal / 1024 / 1024);
        } catch (Exception e) {
            log.warn("[媒体存储] 初始化失败（按需下载将不可用）: {}", e.getMessage());
        }
    }

    /** 刷新开关快照（全局 + per-bot），运行设置改动 30s 内生效。 */
    @Scheduled(fixedDelay = 30_000)
    public void refreshSwitches() {
        try {
            boolean global = "true".equalsIgnoreCase(configService.getGlobalConfig().get("media.download.enabled"));
            Map<String, Boolean> perBot = new LinkedHashMap<>();
            // 显式设置了 bot 级开关的机器人都纳入快照
            Map<String, Map<String, String>> botCfg = configService.getBotConfigMap();
            botCfg.forEach((bot, kv) -> {
                if (kv.containsKey("media_download_enabled")) {
                    perBot.put(bot, "true".equalsIgnoreCase(kv.get("media_download_enabled")));
                }
            });
            MediaFileDownloader.refreshConfig(global, perBot);
        } catch (Exception e) {
            log.debug("[媒体存储] 开关快照刷新失败: {}", e.getMessage());
        }
    }

    /** 每天 03:30 清理过期/超配额文件（启动清理已在构造器执行；cron 不支持 initialDelay）。 */
    @Scheduled(cron = "0 30 3 * * *")
    public void scheduledCleanup() {
        long removed = MediaFileDownloader.cleanup();
        if (removed > 0) {
            log.info("[媒体存储] 定时清理完成，删除 {} 个过期/超配额文件", removed);
        }
    }

    private long longConfig(String key, long dflt) {
        try {
            String v = configService.getGlobalConfig().get(key);
            if (v == null || v.isBlank()) return dflt;
            long n = Long.parseLong(v.trim());
            return n > 0 ? n : dflt;
        } catch (Exception e) {
            return dflt;
        }
    }
}
