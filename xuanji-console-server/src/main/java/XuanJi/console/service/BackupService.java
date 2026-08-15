package XuanJi.console.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import XuanJi.core.config.ConfigService;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 备份恢复服务 — H2 在线 SCRIPT 导出/导入（AUTO_SERVER 下不锁库，应用运行中可用）。
 *
 * <p>备份范围可<b>自由勾选</b>（默认全选），各分类对应内容：
 * <ul>
 *   <li>{@code framework} — 框架核心库（xuanji.mv.db：框架配置/机器人注册/设置）</li>
 *   <li>{@code platform}  — 平台共享库（qqbot/qqbot.mv.db：机器人档案）</li>
 *   <li>{@code business}  — 机器人业务库（per-bot 实例库：群/成员/好友档案）</li>
 *   <li>{@code logs}     — 运行日志（框架日志库 + per-bot 日志库的 qqbot_event / qqbot_op_log）</li>
 *   <li>{@code messages} — 聊天消息（per-bot 日志库的 qqbot_message，群聊+单聊）★独立可勾选</li>
 * </ul>
 * 前三者为<b>整库文件级</b>导出；{@code logs}/{@code messages} 为<b>表级</b>导出（同一日志库可拆出多张表）。
 * 备份产物为 zip（含 manifest.json，记录 categories 与 units），存 {@code backups/} 目录。
 *
 * <p>恢复策略：先自动快照当前库到 {@code backups/pre_restore_时间戳/}（保护现场），
 * 再按 manifest 的 units 逐个库 RUNSCRIPT 导入；导入后建议重启应用（连接池旧连接可能读到旧数据）。
 *
 * <p>自动备份：
 * <ul>
 *   <li>{@link #autoBackup()} 每日 03:00 执行（读 backup.enabled / backup.categories / backup.retention，默认开启 / 全选 / 7 份滚动）</li>
 *   <li>{@link #autoMessageBackup()} 默认<b>每 30 天</b>单独备份一次聊天消息（读 backup.msg.enabled / interval_days / retention，默认开启 / 30 天 / 12 份）</li>
 * </ul>
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String ZIP_PREFIX = "xuanji-backup-";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path BACKUP_DIR = Path.of("backups");

    /** 全部可选备份分类（默认全选）。 */
    static final Set<String> ALL_CATEGORIES = Set.of("framework", "platform", "business", "logs", "messages");

    private final ConfigService configService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BackupService(ConfigService configService, AuditService auditService) {
        this.configService = configService;
        this.auditService = auditService;
    }

    // ═════════════════ 备份 ====================

    /**
     * 立即备份。categories 为空/全空 → 全选。tag 用于文件名区分（如 "msg" 表示聊天消息备份）。
     *
     * @return zip 文件名（如 xuanji-backup-20260807-183000.zip 或 xuanji-backup-msg-20260807-183000.zip）
     */
    public String create(List<String> categories, String tag, String ip) {
        Set<String> cats = normalizeCategories(categories);
        String safeTag = (tag == null || tag.isBlank()) ? "" : tag.trim().replaceAll("[^a-zA-Z0-9_-]", "") + "-";
        Path tmp = BACKUP_DIR.resolve(".tmp_" + System.currentTimeMillis());
        try {
            Files.createDirectories(tmp);
            // 1. 收集备份单元（库文件级 / 表级）
            List<Map<String, Object>> units = collectUnits(cats);
            if (units.isEmpty()) {
                throw new IllegalStateException("没有匹配的数据库文件（categories=" + cats + "）");
            }
            // 2. 逐个导出 sql
            ArrayNode unitArr = objectMapper.createArrayNode();
            for (Map<String, Object> u : units) {
                String entry = (String) u.get("entry");
                String dbRel = (String) u.get("dbRel");
                String type = (String) u.get("type");
                String table = (String) u.get("table");
                Path sqlFile = tmp.resolve(entry);
                Files.createDirectories(sqlFile.getParent());
                exportDb(DATA_DIR.resolve(dbRel).toAbsolutePath().toString(), sqlFile,
                        "table".equals(type) ? table : null);
                ObjectNode un = objectMapper.createObjectNode();
                un.put("entry", entry);
                un.put("dbRel", dbRel);
                un.put("type", type);
                if (table != null) un.put("table", table);
                unitArr.add(un);
            }
            // 3. manifest
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("createdAt", System.currentTimeMillis() / 1000L);
            manifest.put("scope", "custom");
            manifest.put("appVersion", "1.3.2");
            ArrayNode catArr = objectMapper.createArrayNode();
            cats.forEach(catArr::add);
            manifest.set("categories", catArr);
            manifest.set("units", unitArr);
            objectMapper.writeValue(tmp.resolve("manifest.json").toFile(), manifest);

            // 4. 打包 zip
            Files.createDirectories(BACKUP_DIR);
            String zipName = ZIP_PREFIX + safeTag + LocalDateTime.now().format(TS) + ".zip";
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
            auditService.record("BACKUP_CREATE", "categories=" + cats + (safeTag.isEmpty() ? "" : " tag=" + safeTag) + " -> " + zipName, ip);
            log.info("[Backup] 备份完成: {} ({} 个单元)", zipName, units.size());
            return zipName;
        } catch (Exception e) {
            log.error("[Backup] 备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("备份失败: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tmp);
        }
    }

    /** 备份列表（zip 文件名/大小/修改时间/分类，倒序）。 */
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
                                m.put("categories", readManifestCategories(p));
                                out.add(m);
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception e) {
            log.debug("[Backup] 列表失败: {}", e.getMessage());
        }
        return out;
    }

    /** 备份文件绝对路径（不存在/非法返回 null）。 */
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
     * 恢复备份：先快照当前库 → 解压 zip → 按 manifest 的 units 逐个库 RUNSCRIPT 导入。
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
            // 3. 读取 units
            List<Map<String, Object>> units = readUnits(tmp);
            if (units.isEmpty()) throw new IllegalArgumentException("备份包内没有可恢复的库");
            int restored = 0;
            for (Map<String, Object> u : units) {
                String entry = (String) u.get("entry");
                String dbRel = (String) u.get("dbRel");
                String type = (String) u.get("type");
                String table = (String) u.get("table");
                Path sql = tmp.resolve(entry);
                if (!Files.isRegularFile(sql)) continue;
                Path dbFile = DATA_DIR.resolve(dbRel);
                Files.createDirectories(dbFile.getParent());
                importDb(dbFile, sql, "table".equals(type), table);
                restored++;
            }
            auditService.record("BACKUP_RESTORE", name + " -> 恢复 " + restored + " 个单元", ip);
            log.info("[Backup] 恢复完成: {} ({} 个单元)", name, restored);
            return "恢复完成（" + restored + " 个单元）。旧数据已快照至 backups/pre_restore_*/，建议重启应用后确认数据。";
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

    /** 每天 03:00 自动备份（读全局配置：范围跟随勾选分类）。 */
    @Scheduled(cron = "0 0 3 * * *")
    public void autoBackup() {
        try {
            Map<String, String> g = configService.getGlobalConfig();
            if (!"true".equalsIgnoreCase(g.getOrDefault("backup.enabled", "true"))) return;
            List<String> cats = parseCategories(g.get("backup.categories"));
            int retention = parseInt(g.get("backup.retention"), 7);
            String name = create(cats, "", "auto");
            prune(retention, "");
            log.info("[Backup] 自动备份完成: {} (保留 {} 份)", name, retention);
        } catch (Exception e) {
            log.error("[Backup] 自动备份失败: {}", e.getMessage());
        }
    }

    /**
     * 聊天消息自动备份：默认每 30 天一次（独立于每日自动备份）。
     * 仅备份 {@code messages} 分类；按 backup.msg.last_run 计算间隔，避免重启/短运行遗漏。
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void autoMessageBackup() {
        try {
            Map<String, String> g = configService.getGlobalConfig();
            if (!"true".equalsIgnoreCase(g.getOrDefault("backup.msg.enabled", "true"))) return;
            int interval = parseInt(g.get("backup.msg.interval_days"), 30);
            interval = Math.max(interval, 1);
            long now = System.currentTimeMillis() / 1000;
            long lastRun = parseLong(g.get("backup.msg.last_run"));
            if (lastRun > 0 && (now - lastRun) < (long) interval * 86400L) return; // 未到间隔
            String name = create(List.of("messages"), "msg", "auto");
            configService.setGlobal("backup.msg.last_run", String.valueOf(now));
            prune(parseInt(g.get("backup.msg.retention"), 12), "msg");
            log.info("[Backup] 聊天消息自动备份完成: {} (间隔 {} 天)", name, interval);
        } catch (Exception e) {
            log.error("[Backup] 聊天消息自动备份失败: {}", e.getMessage());
        }
    }

    // ═════════════════ 设置 ====================

    public Map<String, Object> settings() {
        Map<String, String> g = configService.getGlobalConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", !"false".equalsIgnoreCase(g.getOrDefault("backup.enabled", "true")));
        m.put("categories", parseCategories(g.get("backup.categories")));
        m.put("retention", parseInt(g.get("backup.retention"), 7));
        m.put("msgEnabled", !"false".equalsIgnoreCase(g.getOrDefault("backup.msg.enabled", "true")));
        m.put("msgIntervalDays", parseInt(g.get("backup.msg.interval_days"), 30));
        m.put("msgRetention", parseInt(g.get("backup.msg.retention"), 12));
        return m;
    }

    public void saveSettings(Map<String, Object> body) {
        configService.setGlobal("backup.enabled",
                Boolean.TRUE.equals(body.get("enabled")) ? "true" : "false");
        // categories：数组或逗号串都兼容，缺省全选
        List<String> cats = new ArrayList<>();
        Object c = body.get("categories");
        if (c instanceof List<?> l) {
            l.forEach(x -> { String s = String.valueOf(x); if (ALL_CATEGORIES.contains(s)) cats.add(s); });
        } else if (c != null) {
            cats.addAll(parseCategories(String.valueOf(c)));
        }
        if (cats.isEmpty()) {
            configService.setGlobal("backup.categories", String.join(",", ALL_CATEGORIES));
        } else {
            configService.setGlobal("backup.categories", String.join(",", cats));
        }
        int retention = Math.min(Math.max(parseInt(String.valueOf(body.getOrDefault("retention", 7)), 7), 1), 30);
        configService.setGlobal("backup.retention", String.valueOf(retention));
        // 聊天消息自动备份
        configService.setGlobal("backup.msg.enabled",
                Boolean.TRUE.equals(body.get("msgEnabled")) ? "true" : "false");
        int iv = Math.min(Math.max(parseInt(String.valueOf(body.getOrDefault("msgIntervalDays", 30)), 30), 1), 365);
        configService.setGlobal("backup.msg.interval_days", String.valueOf(iv));
        int mr = Math.min(Math.max(parseInt(String.valueOf(body.getOrDefault("msgRetention", 12)), 12), 1), 100);
        configService.setGlobal("backup.msg.retention", String.valueOf(mr));
    }

    // ═════════════════ 内部 ====================

    /** 收集备份单元：库文件级（整库 SCRIPT）或 表级（SCRIPT TABLE）。
     *  unit: { entry( zip 内路径 ), dbRel( data/ 下相对 .mv.db 路径 ), type( whole|table ), table } */
    private List<Map<String, Object>> collectUnits(Set<String> cats) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(DATA_DIR)) return out;
        try (var stream = Files.walk(DATA_DIR)) {
            for (Path p : stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mv.db"))
                    .filter(p -> !p.toString().contains("backups"))
                    .toList()) {
                String rel = DATA_DIR.relativize(p).toString().replace('\\', '/');
                String base = rel.substring(0, rel.length() - ".mv.db".length());
                boolean isXuanji = base.startsWith("xuanji/");
                boolean isLog = base.endsWith("log");
                boolean isPlatform = base.equals("qqbot/qqbot");
                boolean isPerBotLog = base.contains("/log/");
                boolean isPerBotInstance = base.contains("/data/");

                // 框架核心库
                if (isXuanji && !isLog && cats.contains("framework")) {
                    out.add(unit(base + ".sql", rel, "whole", null));
                }
                // 框架日志库（xuanji.log.mv.db，属「运行日志」）
                if (isXuanji && isLog && cats.contains("logs")) {
                    out.add(unit(base + ".sql", rel, "whole", null));
                }
                // 平台共享库
                if (isPlatform && cats.contains("platform")) {
                    out.add(unit(base + ".sql", rel, "whole", null));
                }
                // 机器人业务库（实例库）
                if (isPerBotInstance && !isPerBotLog && cats.contains("business")) {
                    out.add(unit(base + ".sql", rel, "whole", null));
                }
                // per-bot 日志库：消息 / 日志 表级拆分
                if (isPerBotLog) {
                    if (cats.contains("messages")) {
                        out.add(unit(base + "/qqbot_message.sql", rel, "table", "qqbot_message"));
                    }
                    if (cats.contains("logs")) {
                        out.add(unit(base + "/qqbot_event.sql", rel, "table", "qqbot_event"));
                        out.add(unit(base + "/qqbot_op_log.sql", rel, "table", "qqbot_op_log"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Backup] 收集数据库失败: {}", e.getMessage());
        }
        out.sort(Comparator.comparing(a -> (String) a.get("entry")));
        return out;
    }

    private Map<String, Object> unit(String entry, String dbRel, String type, String table) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry", entry);
        m.put("dbRel", dbRel);
        m.put("type", type);
        m.put("table", table);
        return m;
    }

    /** H2 SCRIPT / SCRIPT TABLE 导出（在线，AUTO_SERVER 多连接）。table 为 null 时整库导出。 */
    private void exportDb(String dbFile, Path sqlFile, String table) throws Exception {
        String url = "jdbc:h2:file:" + dbFile + ";AUTO_SERVER=TRUE";
        String sqlPath = sqlFile.toAbsolutePath().toString().replace('\\', '/');
        String sql = table == null
                ? "SCRIPT TO '" + sqlPath + "'"
                : "SCRIPT TABLE " + table + " TO '" + sqlPath + "'";
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** H2 RUNSCRIPT 导入。tableLevel=true（表级备份）时先 DROP 再导入，确保整表替换。 */
    private void importDb(Path dbFile, Path sqlFile, boolean tableLevel, String table) throws Exception {
        String url = "jdbc:h2:file:" + dbFile.toAbsolutePath().toString().replace('\\', '/') + ";AUTO_SERVER=TRUE";
        String sqlPath = sqlFile.toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement st = c.createStatement()) {
            if (tableLevel && table != null) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
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

    /** 从备份 zip 读取 manifest 中的 units（解析失败回退到 legacy 整库遍历）。 */
    private List<Map<String, Object>> readUnits(Path tmp) {
        Path manifestPath = tmp.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) return legacyUnits(tmp);
        try {
            var m = objectMapper.readTree(manifestPath.toFile());
            var unitsNode = m.get("units");
            if (unitsNode == null || !unitsNode.isArray()) return legacyUnits(tmp);
            List<Map<String, Object>> units = new ArrayList<>();
            for (var u : unitsNode) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("entry", u.get("entry").asText());
                map.put("dbRel", u.get("dbRel").asText());
                map.put("type", u.get("type").asText("whole"));
                map.put("table", u.has("table") ? u.get("table").asText() : null);
                units.add(map);
            }
            return units;
        } catch (Exception e) {
            log.warn("[Backup] 读取 manifest units 失败，回退 legacy: {}", e.getMessage());
            return legacyUnits(tmp);
        }
    }

    /** legacy：遍历 .sql 文件，整库级（dbRel = entry 去 .sql 加 .mv.db）。 */
    private List<Map<String, Object>> legacyUnits(Path tmp) {
        List<Map<String, Object>> units = new ArrayList<>();
        try (var stream = Files.walk(tmp)) {
            for (Path sql : stream.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                String entry = tmp.relativize(sql).toString().replace('\\', '/');
                if (entry.equals("manifest.json")) continue;
                if (!entry.endsWith(".sql")) continue;
                String dbRel = entry.substring(0, entry.length() - 4) + ".mv.db";
                units.add(unit(entry, dbRel, "whole", null));
            }
        } catch (Exception ignored) {}
        return units;
    }

    /** 从备份 zip 读取 manifest.categories（用于列表展示）。 */
    private List<String> readManifestCategories(Path zip) {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("manifest.json");
            if (e == null) return List.of();
            try (InputStream in = zf.getInputStream(e)) {
                var m = objectMapper.readTree(in);
                var cats = m.get("categories");
                if (cats != null && cats.isArray()) {
                    List<String> r = new ArrayList<>();
                    cats.forEach(c -> r.add(c.asText()));
                    return r;
                }
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    /** 按 tag 过滤并滚动删除超出 retention 份的旧备份。tag 为空 → 非 msg 备份。 */
    private void prune(int retention, String tag) {
        List<Map<String, Object>> all = list();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> b : all) {
            String n = String.valueOf(b.get("name"));
            boolean match;
            if ("msg".equals(tag)) match = n.contains("-msg-");
            else if (tag.isEmpty()) match = !n.contains("-msg-");
            else match = n.contains("-" + tag + "-");
            if (match) filtered.add(b);
        }
        filtered.sort(Comparator.comparing((Map<String, Object> b) -> String.valueOf(b.get("name"))));
        for (int i = retention; i < filtered.size(); i++) {
            delete(String.valueOf(filtered.get(i).get("name")), "auto");
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

    /** 规范化分类：去重 + 仅保留已知分类；空 → 全选。 */
    private static Set<String> normalizeCategories(List<String> categories) {
        Set<String> set = new LinkedHashSet<>();
        if (categories != null) {
            for (String c : categories) {
                if (c != null && ALL_CATEGORIES.contains(c.trim())) set.add(c.trim());
            }
        }
        return set.isEmpty() ? new LinkedHashSet<>(ALL_CATEGORIES) : set;
    }

    /** 解析分类（逗号串 → 已知分类集合；空 → 全选）。 */
    private static List<String> parseCategories(String v) {
        Set<String> set = new LinkedHashSet<>();
        if (v != null && !v.isBlank()) {
            for (String c : v.split(",")) {
                if (c != null && ALL_CATEGORIES.contains(c.trim())) set.add(c.trim());
            }
        }
        return set.isEmpty() ? new ArrayList<>(ALL_CATEGORIES) : new ArrayList<>(set);
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v == null ? "" : v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(String v) {
        try {
            return Long.parseLong(v == null ? "" : v.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
