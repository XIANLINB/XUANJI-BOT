package XuanJi.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 机器人归档服务 —— 删 bot 时「防误删」兜底。
 *
 * <p>删除机器人的语义从「彻底删除」升级为「删除并归档」：
 * <ol>
 *   <li>把 {@code data/{platform}/{instanceId}/}（数据 + 日志 + 媒体目录）整体移入
 *       {@code data/_archive/{platform}/{instanceId}/{时间戳}/}，物理数据零丢失；</li>
 *   <li>在框架库 {@code xuanji_bot_archive} 表登记归档元信息（含平台档案 JSON，用于恢复时重建）；</li>
 *   <li>TTL 默认 30 天（{@link #TTL_DAYS}）：到期后可手动恢复，未恢复的由清理任务删除（目录 + 记录）；</li>
 *   <li>恢复时把归档目录移回原位置 + 重建平台库档案与框架索引记录。</li>
 * </ol>
 *
 * <p>核心层只做「文件 + 表」管理；重建平台库档案（qqbot_bot / onebot_bot 行）由调用方
 * （starter 的 BotConfigController）根据 {@code extra_json} 执行，避免 core 反向依赖 adapter。
 */
@Slf4j
@Component
public class BotArchiveService {

    /** 归档保留天数（可恢复窗口）。 */
    public static final int TTL_DAYS = 30;

    private final JdbcTemplate jdbc;

    public BotArchiveService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 归档根目录：data/_archive。 */
    private Path archiveRoot() {
        return Paths.get("data", "_archive");
    }

    /**
     * 删除并归档一个机器人。
     *
     * @param platform   平台标识（qqbot / onebot）
     * @param instanceId 机器人实例 ID（appId / selfId）
     * @param botName    显示名（可选，前端展示用）
     * @param extraJson  平台档案 JSON（qqbot_bot / onebot_bot 行快照，恢复时重建档案）
     * @return 归档记录 ID（-1 表示归档失败）
     */
    public long archive(String platform, String instanceId, String botName, String extraJson) {
        long ts = System.currentTimeMillis();
        Path srcDir = Paths.get("data", platform, instanceId);
        Path destDir = archiveRoot().resolve(platform).resolve(instanceId).resolve(String.valueOf(ts));
        long size = 0;
        try {
            if (Files.isDirectory(srcDir)) {
                Files.createDirectories(destDir.getParent());
                moveDir(srcDir, destDir);
                size = dirSize(destDir);
                log.info("[BotArchive] 已归档数据目录: {} → {} ({}B)", srcDir, destDir, size);
            } else {
                log.warn("[BotArchive] 数据目录不存在，仅登记元信息: {}", srcDir);
            }
        } catch (Exception e) {
            log.error("[BotArchive] 移动数据目录失败，中止删除（防误删）: {}", e.getMessage());
            return -1;
        }

        // 登记归档记录（TTL 30 天）
        String dirRel = archiveRoot().relativize(destDir).toString();
        try {
            int n = jdbc.update("""
                INSERT INTO xuanji_bot_archive
                  (platform, instance_id, bot_key, bot_name, status, archive_time, expire_at, archive_dir, data_size, extra_json)
                VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, ?, CURRENT_TIMESTAMP), ?, ?, ?)
                """, platform, instanceId, instanceId, botName, TTL_DAYS, dirRel, size, extraJson);
            return n > 0 ? jdbc.queryForObject("SELECT MAX(id) FROM xuanji_bot_archive", Long.class) : -1;
        } catch (Exception e) {
            log.error("[BotArchive] 登记归档记录失败: {}", e.getMessage());
            return -1;
        }
    }

    /** 未过期且未恢复的归档列表（惰性清理过期项）。 */
    public List<Map<String, Object>> listActive() {
        deleteExpired();
        return lowerKeys(jdbc.queryForList("""
            SELECT id, platform, instance_id, bot_key, bot_name, status,
                   archive_time, expire_at, archive_dir, data_size
            FROM xuanji_bot_archive WHERE status = 'ACTIVE'
            ORDER BY archive_time DESC
            """));
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = lowerKeys(jdbc.queryForList(
                "SELECT * FROM xuanji_bot_archive WHERE id = ?", id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 恢复：把归档目录移回原位置 {@code data/{platform}/{instanceId}/}。
     * 平台档案与框架索引由调用方依据 {@code extra_json} 重建。
     *
     * @return true=文件已移回（或本就没有归档目录）
     */
    public boolean restoreFiles(long id) {
        Map<String, Object> rec = get(id);
        if (rec == null) {
            return false;
        }
        String dirRel = String.valueOf(rec.get("archive_dir"));
        String platform = String.valueOf(rec.get("platform"));
        String instanceId = String.valueOf(rec.get("instance_id"));
        Path srcDir = archiveRoot().resolve(dirRel);
        Path destDir = Paths.get("data", platform, instanceId);
        try {
            if (Files.isDirectory(srcDir)) {
                if (Files.exists(destDir)) {
                    log.warn("[BotArchive] 恢复目标已存在，跳过移动（避免覆盖）: {}", destDir);
                } else {
                    Files.createDirectories(destDir.getParent());
                    moveDir(srcDir, destDir);
                    log.info("[BotArchive] 已恢复数据目录: {} → {}", srcDir, destDir);
                }
            }
            jdbc.update("UPDATE xuanji_bot_archive SET status='RESTORED' WHERE id=?", id);
            return true;
        } catch (Exception e) {
            log.error("[BotArchive] 恢复数据目录失败: {}", e.getMessage());
            return false;
        }
    }

    /** 删除过期归档（目录 + 记录），TTL 30 天。 */
    public int deleteExpired() {
        List<Map<String, Object>> expired = lowerKeys(jdbc.queryForList("""
            SELECT id, archive_dir FROM xuanji_bot_archive
            WHERE status = 'ACTIVE' AND expire_at < CURRENT_TIMESTAMP
            """));
        int removed = 0;
        for (Map<String, Object> rec : expired) {
            try {
                String dirRel = String.valueOf(rec.get("archive_dir"));
                deleteQuietly(archiveRoot().resolve(dirRel));
                jdbc.update("UPDATE xuanji_bot_archive SET status='EXPIRED' WHERE id=?", rec.get("id"));
                removed++;
            } catch (Exception e) {
                log.debug("[BotArchive] 清理过期归档失败 id={}: {}", rec.get("id"), e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("[BotArchive] 已清理过期归档 {} 条", removed);
        }
        return removed;
    }

    /** H2 默认返回大写列名，统一转小写供后端/前端读取。 */
    private static List<Map<String, Object>> lowerKeys(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.forEach((k, v) -> m.put(k.toLowerCase(), v));
            out.add(m);
        }
        return out;
    }

    // ──────────────── 工具 ────────────────

    /** 递归移动目录（跨盘安全）。 */
    private void moveDir(Path src, Path dst) throws java.io.IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            Files.createDirectories(dst);
            for (Path p : paths) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        deleteQuietly(src);
    }

    private void deleteQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private long dirSize(Path dir) {
        long[] total = {0};
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    total[0] += Files.size(p);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        return total[0];
    }

    /** 归档记录 → 前端可读 Map（脱敏等）。 */
    public static Map<String, Object> toView(Map<String, Object> rec) {
        Map<String, Object> out = new LinkedHashMap<>(rec);
        out.put("_ttlDays", TTL_DAYS);
        return out;
    }
}
