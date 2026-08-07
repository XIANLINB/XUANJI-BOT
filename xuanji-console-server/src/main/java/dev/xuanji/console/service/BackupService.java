package dev.xuanji.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.core.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 备份恢复服务 — H2 在线 SCRIPT 导出/导入（AUTO_SERVER 下不锁库，应用运行中可用）。
 *
 * <p>备份范围三选：{@code business}（业务库：框架+平台+实例，不含日志）、{@code log}（仅日志库）、
 * {@code all}（全部）。备份产物为 zip（含 manifest.json），存 {@code backups/} 目录。
 *
 * <p>恢复策略：先自动快照当前库到 {@code backups/pre_restore_时间戳/}（保护现场），
 * 再逐个库 RUNSCRIPT 导入；导入后建议重启应用（连接池旧连接可能读到旧数据）。
 *
 * <p>自动备份：{@link #autoBackup()} 每天 03:00 执行（读全局配置 backup.enabled / backup.scope /
 * backup.retention，默认开启 / all / 7 份滚动）。
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String ZIP_PREFIX = "xuanji-backup-";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path BACKUP_DIR = Path.of("backups");

    private final ConfigService configService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BackupService(ConfigService configService, AuditService auditService) {
        this.configService = configService;
        this.auditService = auditService;
    }

    // ═════════════════ 备份 ====================

    /**
     * 立即备份。scope: business | log | all。
     *
     * @return zip 文件名（如 xuanji-backup-20260807-183000.zip）
     */
    public String create(String scope, String ip) {
        String sc = normalizeScope(scope);
        Path tmp = BACKUP_DIR.resolve(".tmp_" + System.currentTimeMillis());
        try {
            Files.createDirectories(tmp);
            // 1. 导出各库为 sql
            List<Map<String, Object>> dbs = collectDbs(sc);
            ArrayNode dbArr = objectMapper.createArrayNode();
            for (Map<String, Object> db : dbs) {
                String rel = (String) db.get("rel");          // 相对 data/ 的路径（去 .mv.db）
                String file = (String) db.get("file");        // 库文件绝对路径
                Path sqlFile = tmp.resolve(rel + ".sql");
                Files.createDirectories(sqlFile.getParent());
                exportDb(file, sqlFile);
                dbArr.add(rel + ".sql");
            }
            if (dbs.isEmpty()) {
                throw new IllegalStateException("没有匹配的数据库文件（scope=" + sc + "）");
            }
            // 2. manifest
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("createdAt", System.currentTimeMillis() / 1000L);
            manifest.put("scope", sc);
            manifest.put("appVersion", "1.0.0");
            manifest.set("dbs", dbArr);
            objectMapper.writeValue(tmp.resolve("manifest.json").toFile(), manifest);

            // 3. 打包 zip
            Files.createDirectories(BACKUP_DIR);
            String zipName = ZIP_PREFIX + LocalDateTime.now().format(TS) + ".zip";
            Path zipPath = BACKUP_DIR.resolve(zipName);
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                try (var stream = Files.walk(tmp)) {
                    for (Path p : stream.filter(Files::isRegularFile).toList()) {
                        String entry = tmp.relativize(p).toString().replace('\\', '/');
                        zos.putNextEntry(new ZipEntry(entry));
                        Files.copy(p, zos);
                        zos.closeEntry();
                    }
                }
            }
            auditService.record("BACKUP_CREATE", "scope=" + sc + " -> " + zipName, ip);
            log.info("[Backup] 备份完成: {} ({} 个库)", zipName, dbs.size());
            return zipName;
        } catch (Exception e) {
            log.error("[Backup] 备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("备份失败: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tmp);
        }
    }

    /** 备份列表（zip 文件名/大小/修改时间，倒序）。 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Files.createDirectories(BACKUP_DIR);
            try (var stream = Files.list(BACKUP_DIR)) {
                stream.filter(p -> p.getFileName().toString().startsWith(ZIP_PREFIX)
                        && p.getFileName().toString().endsWith(".zip"))
                        .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                        .forEach(p -> {
                            try {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("name", p.getFileName().toString());
                                m.put("size", Files.size(p));
                                m.put("mtime", Files.getLastModifiedTime(p).toMillis());
                                out.add(m);
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception e) {
            log.debug("[Backup] 列表失败: {}", e.getMessage());
        }
        return out;
    }

    /** 备份文件绝对路径（不存在返回 null）。 */
    public Path resolve(String name) {
        if (name == null || !name.startsWith(ZIP_PREFIX) || !name.endsWith(".zip")
                || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        Path p = BACKUP_DIR.resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    public boolean delete(String name, String ip) {
        Path p = resolve(name);
        if (p == null) return false;
        try {
            Files.delete(p);
            auditService.record("BACKUP_DELETE", name, ip);
            return true;
        } catch (Exception e) {
            log.error("[Backup] 删除失败: {}", e.getMessage());
            return false;
        }
    }

    // ═════════════════ 恢复 ====================

    /**
     * 恢复备份：先快照当前库 → 解压 zip → 逐个库 RUNSCRIPT 导入。
     *
     * @return 提示文案
     */
    public String restore(String name, String ip) {
        Path zip = resolve(name);
        if (zip == null) throw new IllegalArgumentException("备份文件不存在: " + name);
        Path tmp = BACKUP_DIR.resolve(".restore_" + System.currentTimeMillis());
        try {
            // 1. 解压
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                var entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    Path target = tmp.resolve(e.getName());
                    if (e.isDirectory()) {
                        Files.createDirectories(target);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            // 2. 恢复前快照（保护现场）
            snapshotCurrent();
            // 3. 逐个库导入
            List<Path> sqls;
            try (var stream = Files.walk(tmp)) {
                sqls = stream.filter(p -> p.toString().endsWith(".sql"))
                        .sorted().toList();
            }
            if (sqls.isEmpty()) throw new IllegalArgumentException("备份包内没有可恢复的库");
            int restored = 0;
            for (Path sql : sqls) {
                String rel = tmp.relativize(sql).toString().replace('\\', '/');
                if (rel.equals("manifest.json")) continue;
                if (!rel.endsWith(".sql")) continue;
                String dbRel = rel.substring(0, rel.length() - 4) + ".mv.db";
                Path dbFile = DATA_DIR.resolve(dbRel);
                Files.createDirectories(dbFile.getParent());
                importDb(dbFile, sql);
                restored++;
            }
            auditService.record("BACKUP_RESTORE", name + " -> 恢复 " + restored + " 个库", ip);
            log.info("[Backup] 恢复完成: {} ({} 个库)", name, restored);
            return "恢复完成（" + restored + " 个库）。旧数据已快照至 backups/pre_restore_*/，建议重启应用后确认数据。";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Backup] 恢复失败: {}", e.getMessage(), e);
            throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tmp);
        }
    }

    // ═════════════════ 自动备份 ====================

    /** 每天 03:00 自动备份（读全局配置）。 */
    @Scheduled(cron = "0 0 3 * * *")
    public void autoBackup() {
        try {
            Map<String, String> g = configService.getGlobalConfig();
            if (!"true".equalsIgnoreCase(g.getOrDefault("backup.enabled", "true"))) return;
            String scope = normalizeScope(g.getOrDefault("backup.scope", "all"));
            int retention = parseInt(g.get("backup.retention"), 7);
            String name = create(scope, "auto");
            // 滚动删除：保留最近 retention 份
            List<Map<String, Object>> all = list();
            for (int i = retention; i < all.size(); i++) {
                delete((String) all.get(i).get("name"), "auto");
            }
            log.info("[Backup] 自动备份完成: {} (保留 {} 份)", name, retention);
        } catch (Exception e) {
            log.error("[Backup] 自动备份失败: {}", e.getMessage());
        }
    }

    // ═════════════════ 设置 ====================

    public Map<String, Object> settings() {
        Map<String, String> g = configService.getGlobalConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", !"false".equalsIgnoreCase(g.getOrDefault("backup.enabled", "true")));
        m.put("scope", normalizeScope(g.getOrDefault("backup.scope", "all")));
        m.put("retention", parseInt(g.get("backup.retention"), 7));
        return m;
    }

    public void saveSettings(Map<String, Object> body) {
        configService.setGlobal("backup.enabled", Boolean.TRUE.equals(body.get("enabled")) ? "true" : "false");
        configService.setGlobal("backup.scope", normalizeScope(String.valueOf(body.getOrDefault("scope", "all"))));
        int retention = Math.min(Math.max(parseInt(String.valueOf(body.getOrDefault("retention", 7)), 7), 1), 30);
        configService.setGlobal("backup.retention", String.valueOf(retention));
    }

    // ═════════════════ 内部 ====================

    /** 收集范围内数据库：rel=相对 data/ 路径（去 .mv.db），file=库文件路径。 */
    private List<Map<String, Object>> collectDbs(String scope) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(DATA_DIR)) return out;
        try (var stream = Files.walk(DATA_DIR)) {
            for (Path p : stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mv.db"))
                    .filter(p -> !p.toString().contains("backups"))
                    .toList()) {
                String rel = DATA_DIR.relativize(p).toString().replace('\\', '/');
                rel = rel.substring(0, rel.length() - ".mv.db".length());
                boolean isLog = rel.endsWith("xuanji.log");
                if ("log".equals(scope) && !isLog) continue;
                if ("business".equals(scope) && isLog) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rel", rel);
                m.put("file", p.toAbsolutePath().toString());
                out.add(m);
            }
        }
        out.sort(Comparator.comparing(a -> (String) a.get("rel")));
        return out;
    }

    /** H2 SCRIPT 导出（在线，AUTO_SERVER 多连接）。 */
    private void exportDb(String dbFile, Path sqlFile) throws Exception {
        String url = "jdbc:h2:file:" + dbFile + ";AUTO_SERVER=TRUE";
        String sqlPath = sqlFile.toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("SCRIPT TO '" + sqlPath + "'");
        }
    }

    /** H2 RUNSCRIPT 导入。 */
    private void importDb(Path dbFile, Path sqlFile) throws Exception {
        String url = "jdbc:h2:file:" + dbFile + ";AUTO_SERVER=TRUE";
        String sqlPath = sqlFile.toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("RUNSCRIPT FROM '" + sqlPath + "'");
        }
    }

    /** 复制当前 data/ 全部库到 backups/pre_restore_{ts}/（保护现场）。 */
    private void snapshotCurrent() {
        try {
            Path dest = BACKUP_DIR.resolve("pre_restore_" + LocalDateTime.now().format(TS));
            if (!Files.isDirectory(DATA_DIR)) return;
            try (var stream = Files.walk(DATA_DIR)) {
                for (Path p : stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".mv.db"))
                        .filter(p -> !p.toString().contains("backups"))
                        .toList()) {
                    Path target = dest.resolve(DATA_DIR.relativize(p));
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.info("[Backup] 恢复前快照: {}", dest);
        } catch (Exception e) {
            log.warn("[Backup] 恢复前快照失败（继续恢复）: {}", e.getMessage());
        }
    }

    private static void deleteRecursively(Path p) {
        if (p == null || !Files.exists(p)) return;
        try (var stream = Files.walk(p)) {
            for (Path f : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(f);
            }
        } catch (Exception ignored) {}
    }

    private static String normalizeScope(String scope) {
        if (scope == null) return "all";
        return switch (scope.trim().toLowerCase()) {
            case "business", "log" -> scope.trim().toLowerCase();
            default -> "all";
        };
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v == null ? "" : v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
