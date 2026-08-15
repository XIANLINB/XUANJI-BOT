package XuanJi.console.market;

import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Enumeration;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 插件市场业务 — 双仓库模型（审核仓库 + 正式仓库）。
 *
 * <p>仓库结构：
 * <pre>
 *   审核仓库（XuanJiBot-plugin）
 *     .pending/&lt;submissionId&gt;/meta.json     待审提交元数据（状态：审核中 / 已上架 / 拒绝上架）
 *     .pending/&lt;submissionId&gt;/&lt;jar&gt;         待审 jar
 *
 *   正式仓库（XuanJiBot-plugins）
 *     plugins/&lt;pluginId&gt;/plugin.json          该插件专属清单（状态：已上架 / 已下架）
 *     plugins/&lt;pluginId&gt;/&lt;version&gt;/xxx.jar   当前上架版本 jar
 * </pre>
 *
 * <p>权限模型：
 * <ul>
 *   <li>上传（所有框架实例使用者）：用<b>审核仓库上传令牌</b> git push 到 .pending/，状态「审核中」</li>
 *   <li>审核（管理员）：进入审核台需验证<b>管理员令牌</b>；通过（jar 移入正式仓库 plugins/ + 写 plugin.json）/ 拒绝（meta 标「拒绝上架」）</li>
 *   <li>公开：所有实例经<b>框架后端代理</b>拉清单 + 下载 jar（sha256 校验），前端不接触仓库真实地址</li>
 * </ul>
 */
@Slf4j
@Service
public class PluginMarketService {

    /** 本地提交记录（本机发起的上传，用于"我的提交"状态感知） */
    private static final String LOCAL_SUBMISSIONS = "data/plugin-market/local-submissions.json";
    /** 本地审核记录（本机执行的审核操作审计） */
    private static final String AUDIT_LOG = "data/plugin-market/audit-log.json";
    private static final String BRANCH = "main";
    private static final long MAX_JAR_SIZE = 20 * 1024 * 1024; // 20MB

    private final MarketSettings settings;
    private final MarketGitService gitService;

    public PluginMarketService(MarketSettings settings, MarketGitService gitService) {
        this.settings = settings;
        this.gitService = gitService;
    }

    // ═══════════════════ 公开读取（匿名，后端代理） ═══════════════════

    /** 拉取已上架插件清单（正式仓库）：遍历各插件 plugin.json，downloadUrl 返回框架代理端点。 */
    public List<Map<String, Object>> listMarket() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Git git = gitService.openOrPull(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR)) {
            Path pluginsRoot = gitService.repoDir(MarketSettings.RELEASE_DIR).resolve(MarketSettings.PLUGINS_DIR);
            if (!Files.isDirectory(pluginsRoot)) return out;
            List<Path> dirs;
            try (var stream = Files.list(pluginsRoot)) {
                dirs = stream.filter(Files::isDirectory).toList();
            }
            for (Path dir : dirs) {
                Path manifest = dir.resolve(MarketSettings.PLUGIN_MANIFEST);
                if (!Files.isRegularFile(manifest)) continue;
                try {
                    Map<String, Object> row = obj(Json.mapper().readTree(Files.readAllBytes(manifest)));
                    // 已下架插件不进入公开市场（保留 jar 与清单，仅状态标记）
                    if (MarketSettings.STATUS_DELISTED.equals(String.valueOf(row.getOrDefault("status", "")))) {
                        continue;
                    }
                    row.put("downloadUrl", "/console/market/download?pluginId=" + row.get("pluginId")
                            + "&version=" + row.get("version"));
                    out.add(row);
                } catch (Exception e) {
                    log.warn("[PluginMarket] 读取插件清单失败 {}: {}", dir.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 拉取市场清单失败: {}", e.getMessage());
            return List.of();
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("updatedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    /** 已上架（含已下架）插件列表（管理员视图）：遍历正式仓库全部 plugin.json，含 status / delistReason / delistedAt。 */
    public List<Map<String, Object>> listReleased() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Git git = gitService.openOrPull(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR)) {
            Path pluginsRoot = gitService.repoDir(MarketSettings.RELEASE_DIR).resolve(MarketSettings.PLUGINS_DIR);
            if (!Files.isDirectory(pluginsRoot)) return out;
            List<Path> dirs;
            try (var stream = Files.list(pluginsRoot)) {
                dirs = stream.filter(Files::isDirectory).toList();
            }
            for (Path dir : dirs) {
                Path manifest = dir.resolve(MarketSettings.PLUGIN_MANIFEST);
                if (!Files.isRegularFile(manifest)) continue;
                try {
                    Map<String, Object> row = obj(Json.mapper().readTree(Files.readAllBytes(manifest)));
                    row.put("downloadUrl", "/console/market/download?pluginId=" + row.get("pluginId")
                            + "&version=" + row.get("version"));
                    out.add(row);
                } catch (Exception e) {
                    log.warn("[PluginMarket] 读取已上架插件清单失败 {}: {}", dir.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 拉取已上架插件清单失败: {}", e.getMessage());
            return List.of();
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("updatedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    /** 已上架 jar 下载（后端代理）：从 plugin.json 读当前版本 jar 路径，匿名拉取（正式仓库 raw）。 */
    public byte[] downloadPluginJar(String pluginId, String version) {
        try {
            String base = MarketSettings.PLUGINS_DIR + "/" + pluginId + "/" + MarketSettings.PLUGIN_MANIFEST;
            JsonNode m = Json.mapper().readTree(httpGet(rawUrl(settings.getReleaseRepoUrl(), base)));
            if (m == null) return null;
            if (!version.equals(m.path("version").asText())) return null; // 只提供当前上架版本
            String jarName = m.path("jarName").asText();
            if (jarName.isBlank()) return null;
            return httpGet(rawUrl(settings.getReleaseRepoUrl(),
                    MarketSettings.PLUGINS_DIR + "/" + pluginId + "/" + version + "/" + safeName(jarName)));
        } catch (Exception e) {
            log.warn("[PluginMarket] 下载插件失败 {}@{}: {}", pluginId, version, e.getMessage());
            return null;
        }
    }

    /** 安装插件到本地 plugins/（代理拉取 + sha256 校验）。已安装同 ID 插件时拒绝，防止重复安装。 */
    public Map<String, Object> install(String pluginId, String version) throws Exception {
        String installed = findInstalledJar(pluginId);
        if (installed != null) {
            throw new IllegalStateException("本地已安装插件 " + pluginId + "（" + installed + "），如需更新请先到「本地插件」卸载后再安装");
        }
        Map<String, Object> target = null;
        for (Map<String, Object> p : listMarket()) {
            if (pluginId.equals(p.get("pluginId")) && version.equals(p.get("version"))) {
                target = p;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("市场中未找到插件: " + pluginId + "@" + version);
        }
        byte[] jar = downloadPluginJar(pluginId, version);
        if (jar == null || jar.length == 0) {
            throw new IllegalStateException("插件下载失败: " + pluginId);
        }
        if (jar.length > MAX_JAR_SIZE) {
            throw new IllegalStateException("插件包过大（>20MB），拒绝安装");
        }
        String expectedSha = (String) target.get("sha256");
        if (expectedSha != null && !expectedSha.isBlank()) {
            String actual = sha256Hex(jar);
            if (!expectedSha.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("插件校验失败（sha256 不匹配），已拒绝安装：预期 "
                        + expectedSha + " 实际 " + actual);
            }
        }
        String jarName = safeName(String.valueOf(target.getOrDefault("jarName", pluginId + "-" + version + ".jar")));
        Path pluginsDir = Paths.get("plugins");
        Files.createDirectories(pluginsDir);
        Path dest = pluginsDir.resolve(jarName);
        Files.write(dest, jar);
        log.info("[PluginMarket] 已安装插件包: {} -> {} ({}B)", pluginId, dest, jar.length);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("pluginId", pluginId);
        r.put("version", version);
        r.put("jar", dest.getFileName().toString());
        r.put("size", jar.length);
        return r;
    }

    /** 查找本地 plugins/ 目录中已安装的指定插件 jar 文件名；未安装返回 null。 */
    private static String findInstalledJar(String pluginId) {
        try {
            Path pluginsDir = Paths.get("plugins");
            if (!Files.isDirectory(pluginsDir)) return null;
            try (var stream = Files.list(pluginsDir)) {
                for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                    try (JarFile jf = new JarFile(jar.toFile())) {
                        String id = jf.getManifest() == null ? null
                                : jf.getManifest().getMainAttributes().getValue("Plugin-Id");
                        if (pluginId.equals(id == null ? null : id.trim())) {
                            return jar.getFileName().toString();
                        }
                    } catch (Exception ignored) {
                        // 被占用/非插件 jar 跳过
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 已安装检测失败: {}", e.getMessage());
        }
        return null;
    }

    // ═══════════════════ 上传 / 我的提交（审核仓库） ═══════════════════

    /**
     * 开发者上传插件 → 审核仓库 .pending/&lt;id&gt;/ → 审核中。
     * 上传字段（名称/版本/分类/作者/描述）必须与 jar 内 @XuanJiPlugin 声明一致。
     */
    public Map<String, Object> submit(String name, String description, String version, String category,
                                      String author, String jarFileName, byte[] jarBytes) throws Exception {
        if (jarBytes == null || jarBytes.length == 0) throw new IllegalArgumentException("jar 包为空");
        if (jarBytes.length > MAX_JAR_SIZE) throw new IllegalArgumentException("jar 包过大（>20MB）");
        if (!jarFileName.toLowerCase().endsWith(".jar")) throw new IllegalArgumentException("仅支持 .jar 插件包");

        // 读取 @XuanJiPlugin 声明（以声明为准）
        Map<String, Object> decl = extractDeclaration(jarBytes);
        if (decl == null || !Boolean.TRUE.equals(decl.get("declared"))) {
            String err = decl == null ? "无法读取插件声明" : String.valueOf(decl.getOrDefault("error", "无法读取插件声明"));
            throw new IllegalArgumentException("jar 不是合法的插件包：" + err);
        }
        // 表单与声明一致性校验（表单留空则自动取声明值）
        String effName = pick("插件名称", name, str(decl.get("name")));
        String effVersion = pick("版本", version, str(decl.get("version")));
        // 分类不在上传时确定：仅取表单填写值，缺省留空，由审核管理员手动选择
        String effCategory = blankToNull(category);
        String effAuthor = pick("作者", author, str(decl.get("author")));
        String effDescription = pick("描述", description, str(decl.get("description")));
        String effPermissions = str(decl.get("permissions"));
        String effDependsOn = str(decl.get("dependsOn"));
        String effRateLimit = str(decl.get("rateLimit"));
        String effPlatforms = str(decl.get("platforms"));
        String effDefaultBot = str(decl.get("defaultBot"));
        String declId = str(decl.get("id"));
        String pluginId = str(decl.get("pluginId"));

        // 分类不再自动取 MANIFEST 的 Plugin-Category，保持空白待审核时由管理员填写

        String gitToken = settings.getReviewUploadToken();
        String submissionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String pendingDir = MarketSettings.PENDING_DIR + "/" + submissionId;

        try (Git git = gitService.openOrPull(settings.getReviewRepoUrl(), MarketSettings.REVIEW_DIR)) {
            Path repo = gitService.repoDir(MarketSettings.REVIEW_DIR);
            Path targetDir = repo.resolve(pendingDir);
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(safeName(jarFileName)), jarBytes);

            String sha = sha256Hex(jarBytes);
            ObjectNode meta = Json.obj();
            meta.put("submissionId", submissionId);
            meta.put("pluginId", pluginId);
            meta.put("name", effName == null ? "" : effName);
            meta.put("version", effVersion == null ? "" : effVersion);
            meta.put("category", effCategory == null ? "" : effCategory);
            meta.put("author", effAuthor == null ? "" : effAuthor);
            meta.put("description", effDescription == null ? "" : effDescription);
            meta.put("id", declId == null ? "" : declId);
            meta.put("permissions", effPermissions == null ? "" : effPermissions);
            meta.put("dependsOn", effDependsOn == null ? "" : effDependsOn);
            meta.put("rateLimit", effRateLimit == null ? "" : effRateLimit);
            meta.put("platforms", effPlatforms == null ? "" : effPlatforms);
            meta.put("defaultBot", effDefaultBot == null ? "" : effDefaultBot);
            meta.put("status", MarketSettings.STATUS_PENDING);
            meta.putNull("rejectReason");
            meta.put("jar", pendingDir + "/" + safeName(jarFileName));
            meta.put("jarName", safeName(jarFileName));
            meta.put("sha256", sha);
            meta.put("submittedAt", Instant.now().toString());
            Files.write(targetDir.resolve("meta.json"), Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));

            gitService.commitAndPush(git, gitToken, "submit: " + pluginId + " " + effVersion + " (审核中)");
        }

        appendLocalSubmission(pluginId, effName, effVersion, category, submissionId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("submissionId", submissionId);
        r.put("pluginId", pluginId);
        r.put("name", effName);
        r.put("version", effVersion);
        r.put("message", "提交成功，等待审核");
        return r;
    }

    /** 我的提交：本地记录 + 探测当前状态（审核中 / 已上架 / 拒绝上架）。 */
    public List<Map<String, Object>> mySubmissions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> rec : readLocalSubmissions()) {
            Map<String, Object> row = new LinkedHashMap<>(rec);
            Map<String, Object> probe = probeStatus((String) rec.get("pluginId"), (String) rec.get("version"),
                    (String) rec.get("submissionId"));
            row.put("status", probe.get("status"));
            row.put("rejectReason", probe.get("rejectReason"));
            out.add(row);
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("submittedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    // ═══════════════════ 审核（管理员令牌） ═══════════════════

    /** 待审列表（审核仓库）：status=审核中 的提交（已拒绝/已上架不再展示）。 */
    public List<Map<String, Object>> listPending() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Git git = gitService.openOrPull(settings.getReviewRepoUrl(), MarketSettings.REVIEW_DIR)) {
            Path pendingRoot = gitService.repoDir(MarketSettings.REVIEW_DIR).resolve(MarketSettings.PENDING_DIR);
            if (!Files.isDirectory(pendingRoot)) return out;
            List<Path> dirs;
            try (var stream = Files.list(pendingRoot)) {
                dirs = stream.filter(Files::isDirectory).toList();
            }
            for (Path dir : dirs) {
                Path meta = dir.resolve("meta.json");
                if (!Files.isRegularFile(meta)) continue;
                try {
                    JsonNode node = Json.mapper().readTree(Files.readAllBytes(meta));
                    if (!MarketSettings.STATUS_PENDING.equals(node.path("status").asText())) continue;
                    Map<String, Object> row = obj(node);
                    row.put("downloadUrl", "/console/market/pending/" + node.path("submissionId").asText() + "/download");
                    out.add(row);
                } catch (Exception e) {
                    log.warn("[PluginMarket] 读取待审 meta 失败 {}: {}", dir.getFileName(), e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("submittedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    /** 待审 jar 下载（审核用，需管理员令牌，后端代理不暴露仓库地址）。 */
    public byte[] downloadPendingJar(String submissionId, String adminToken) {
        if (adminToken == null || adminToken.isBlank()) return null;
        try (Git git = gitService.openOrPull(settings.getReviewRepoUrl(), MarketSettings.REVIEW_DIR)) {
            Path meta = gitService.repoDir(MarketSettings.REVIEW_DIR)
                    .resolve(MarketSettings.PENDING_DIR + "/" + submissionId + "/meta.json");
            if (!Files.isRegularFile(meta)) return null;
            JsonNode node = Json.mapper().readTree(Files.readAllBytes(meta));
            return httpGet(rawUrl(settings.getReviewRepoUrl(), node.path("jar").asText()));
        } catch (Exception e) {
            log.warn("[PluginMarket] 下载待审 jar 失败 {}: {}", submissionId, e.getMessage());
            return null;
        }
    }

    /**
     * 通过审核（需管理员令牌）：
     * 1) 从审核仓库读取待审 jar + meta
     * 2) 推送到<b>正式仓库</b> plugins/&lt;pluginId&gt;/&lt;version&gt;/ + 写/覆盖 plugin.json（状态 已上架）
     * 3) 更新审核仓库 meta 状态为「已上架」并删除待审 jar
     */
    public Map<String, Object> approve(String submissionId, boolean official, String category, String adminToken) throws Exception {
        if (adminToken == null || adminToken.isBlank()) {
            throw new SecurityException("缺少管理员令牌，无法上架到正式仓库");
        }
        try (Git reviewGit = gitService.openOrPull(settings.getReviewRepoUrl(), MarketSettings.REVIEW_DIR)) {
            Path reviewRepo = gitService.repoDir(MarketSettings.REVIEW_DIR);
            Path pending = reviewRepo.resolve(MarketSettings.PENDING_DIR + "/" + submissionId);
            if (!Files.isDirectory(pending)) throw new IllegalStateException("待审提交不存在: " + submissionId);
            ObjectNode meta = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(pending.resolve("meta.json")));
            String pluginId = safeName(meta.path("pluginId").asText());
            String version = safeName(meta.path("version").asText());
            String jarName = meta.path("jarName").asText();
            if (jarName.isBlank()) {
                String jarPath = meta.path("jar").asText();
                jarName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
            }
            jarName = safeName(jarName);
            String sha = meta.path("sha256").asText();
            String name = meta.path("name").asText();
            // 分类优先级：管理员手动选择 > 待审 meta 原值 > other
            String metaCategory = meta.path("category").asText();
            String chosenCategory = blankToNull(category);
            if (chosenCategory == null) chosenCategory = blankToNull(metaCategory);
            if (chosenCategory == null) chosenCategory = "other";
            String description = meta.path("description").asText();
            String author = meta.path("author").asText();

            // 1) 读待审 jar 字节
            Path srcJar = pending.resolve(jarName);
            if (!Files.isRegularFile(srcJar)) throw new IllegalStateException("待审 jar 缺失: " + jarName);
            byte[] jarBytes = Files.readAllBytes(srcJar);

            // 2) 推送到正式仓库
            try (Git releaseGit = gitService.openOrPull(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR)) {
                Path releaseRepo = gitService.repoDir(MarketSettings.RELEASE_DIR);
                Path pluginDir = releaseRepo.resolve(MarketSettings.PLUGINS_DIR + "/" + pluginId);
                Path versionDir = pluginDir.resolve(version);
                Files.createDirectories(versionDir);
                Files.write(versionDir.resolve(jarName), jarBytes);

                // 清理旧版本目录（只保留当前上架版本）
                if (Files.isDirectory(pluginDir)) {
                    List<Path> stale;
                    try (var stream = Files.list(pluginDir)) {
                        stale = stream.filter(Files::isDirectory)
                                .filter(d -> !d.getFileName().toString().equals(version))
                                .toList();
                    }
                    for (Path d : stale) deleteRecursively(d);
                }

                ObjectNode manifest = Json.obj();
                manifest.put("pluginId", pluginId);
                manifest.put("name", name);
                manifest.put("version", version);
                manifest.put("category", chosenCategory);
                manifest.put("official", official);
                manifest.put("description", description == null ? "" : description);
                manifest.put("author", author == null ? "" : author);
                manifest.put("id", meta.path("id").asText(""));
                manifest.put("permissions", meta.path("permissions").asText(""));
                manifest.put("dependsOn", meta.path("dependsOn").asText(""));
                manifest.put("rateLimit", meta.path("rateLimit").asText(""));
                manifest.put("platforms", meta.path("platforms").asText(""));
                manifest.put("defaultBot", meta.path("defaultBot").asText(""));
                manifest.put("jarName", jarName);
                manifest.put("sha256", sha);
                manifest.put("status", MarketSettings.STATUS_APPROVED);
                manifest.put("publishedAt", Instant.now().toString());
                manifest.put("updatedAt", Instant.now().toString());
                Files.write(pluginDir.resolve(MarketSettings.PLUGIN_MANIFEST),
                        Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));

                gitService.commitAndPush(releaseGit, adminToken,
                        "approve: " + pluginId + " " + version + " 已上架");
            }

            // 3) 审核仓库 meta 标记已上架 + 记录管理员选定的分类 + 删除待审 jar
            meta.put("category", chosenCategory);
            meta.put("status", MarketSettings.STATUS_APPROVED);
            Files.write(pending.resolve("meta.json"),
                    Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            Files.deleteIfExists(srcJar);
            gitService.commitAndPush(reviewGit, settings.getReviewUploadToken(),
                    "approve-done: " + pluginId + " " + version);

            appendAudit(submissionId, pluginId, version, name, "APPROVED", official, null);
            return Map.of("status", "ok", "pluginId", pluginId, "version", version, "message", "已上架");
        }
    }

    /** 拒绝审核（需管理员令牌）：审核仓库 meta 标记「拒绝上架」 + 拒绝理由。 */
    public Map<String, Object> reject(String submissionId, String reason, String adminToken) throws Exception {
        if (adminToken == null || adminToken.isBlank()) {
            throw new SecurityException("缺少管理员令牌，无法执行拒绝操作");
        }
        try (Git git = gitService.openOrPull(settings.getReviewRepoUrl(), MarketSettings.REVIEW_DIR)) {
            Path repo = gitService.repoDir(MarketSettings.REVIEW_DIR);
            Path pending = repo.resolve(MarketSettings.PENDING_DIR + "/" + submissionId);
            if (!Files.isDirectory(pending)) throw new IllegalStateException("待审提交不存在: " + submissionId);
            Path metaFile = pending.resolve("meta.json");
            ObjectNode meta = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(metaFile));
            meta.put("status", MarketSettings.STATUS_REJECTED);
            meta.put("rejectReason", reason == null ? "" : reason);
            Files.write(metaFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            gitService.commitAndPush(git, settings.getReviewUploadToken(),
                    "reject: " + meta.path("pluginId").asText() + " " + meta.path("version").asText());
            appendAudit(submissionId, meta.path("pluginId").asText(), meta.path("version").asText(),
                    meta.path("name").asText(), "REJECTED", false, reason);
            return Map.of("status", "ok", "submissionId", submissionId, "message", "已拒绝");
        }
    }

    /**
     * 下架已上架插件（需管理员令牌）：改写正式仓库 plugin.json 状态为「已下架」并附理由，真实 push。
     * 下架仅标记状态、保留 jar 与清单，不删除文件；不更新 updatedAt（避免影响下架前后排序语义）。
     */
    public Map<String, Object> delist(String pluginId, String reason, String adminToken) throws Exception {
        if (adminToken == null || adminToken.isBlank()) {
            throw new SecurityException("缺少管理员令牌，无法下架插件");
        }
        String safeId = safeName(pluginId);
        try (Git git = gitService.openOrPull(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR)) {
            Path releaseRepo = gitService.repoDir(MarketSettings.RELEASE_DIR);
            Path manifestFile = releaseRepo.resolve(MarketSettings.PLUGINS_DIR + "/" + safeId + "/" + MarketSettings.PLUGIN_MANIFEST);
            if (!Files.isRegularFile(manifestFile)) {
                throw new IllegalStateException("插件不存在或尚未上架: " + pluginId);
            }
            ObjectNode manifest = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(manifestFile));
            manifest.put("status", MarketSettings.STATUS_DELISTED);
            manifest.put("delistReason", reason == null ? "" : reason);
            manifest.put("delistedAt", Instant.now().toString());
            Files.write(manifestFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            gitService.commitAndPush(git, adminToken, "delist: " + safeId + " 已下架");
            appendAudit(null, safeId, manifest.path("version").asText(""),
                    manifest.path("name").asText(""), "DELISTED", false, reason);
            return Map.of("status", "ok", "pluginId", safeId, "message", "已下架");
        }
    }

    /**
     * 重新上架已下架插件（需管理员令牌）：将正式仓库 plugin.json 状态由「已下架」改回「已上架」，
     * 清除 delistReason / delistedAt，真实 push。jar 与清单本就保留，故无需重传。
     */
    public Map<String, Object> relist(String pluginId, String adminToken) throws Exception {
        if (adminToken == null || adminToken.isBlank()) {
            throw new SecurityException("缺少管理员令牌，无法重新上架插件");
        }
        String safeId = safeName(pluginId);
        try (Git git = gitService.openOrPull(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR)) {
            Path releaseRepo = gitService.repoDir(MarketSettings.RELEASE_DIR);
            Path manifestFile = releaseRepo.resolve(MarketSettings.PLUGINS_DIR + "/" + safeId + "/" + MarketSettings.PLUGIN_MANIFEST);
            if (!Files.isRegularFile(manifestFile)) {
                throw new IllegalStateException("插件不存在或尚未上架: " + pluginId);
            }
            ObjectNode manifest = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(manifestFile));
            manifest.put("status", MarketSettings.STATUS_APPROVED);
            manifest.putNull("delistReason");
            manifest.putNull("delistedAt");
            manifest.put("updatedAt", Instant.now().toString());
            Files.write(manifestFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            gitService.commitAndPush(git, adminToken, "relist: " + safeId + " 重新上架");
            appendAudit(null, safeId, manifest.path("version").asText(""),
                    manifest.path("name").asText(""), "RELISTED", false, null);
            return Map.of("status", "ok", "pluginId", safeId, "message", "已重新上架");
        }
    }

    /** 本地审核记录列表。 */
    public List<Map<String, Object>> auditLog() {
        return readAuditLog();
    }

    // ═══════════════════ 声明提取 / 校验 ═══════════════════

    /**
     * 从 jar 包读取插件声明（@XuanJiPlugin 注解 + MANIFEST）。
     * 用于前端选 jar 后自动填充表单，以及后端提交时一致性校验。
     *
     * <p>实现：写临时文件 → 读 MANIFEST 取 Plugin-Class/Plugin-Id → 用隔离 URLClassLoader
     * 以「不初始化」方式加载主类 → 读取 {@link XuanJiPlugin} 注解字段。
     *
     * @return {declared, pluginId, name, version, author, description, category, error?}
     */
    public Map<String, Object> extractDeclaration(byte[] jar) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("declared", false);
        Path tmp = null;
        try {
            tmp = Files.createTempFile("xuanji-decl-", ".jar");
            Files.write(tmp, jar);
            try (JarFile jf = new JarFile(tmp.toFile())) {
                Manifest mf = jf.getManifest();
                String pluginClass = mf == null ? null : mf.getMainAttributes().getValue("Plugin-Class");
                String pluginId = mf == null ? null : mf.getMainAttributes().getValue("Plugin-Id");
                // 分类来自 jar MANIFEST 的 Plugin-Category（@XuanJiPlugin 无此字段）
                String category = mf == null ? null : mf.getMainAttributes().getValue("Plugin-Category");
                r.put("pluginId", blankToNull(pluginId));
                r.put("category", blankToNull(category));
                if (pluginClass == null || pluginClass.isBlank()) {
                    r.put("error", "jar MANIFEST 缺少 Plugin-Class，不是合法的插件包");
                    return r;
                }
                URL jarUrl = tmp.toUri().toURL();
                try (URLClassLoader cl = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader())) {
                    // 从插件自身的类加载器解析 @XuanJiPlugin 注解类型并反射取值。
                    // 不直接用服务端的 XuanJiPlugin.class 调 getAnnotation：当运行时类路径存在
                    // 多份 xuanji-api（或插件经不同类加载器加载）时，插件类上的注解类型可能是
                    // 与服务端不同的 Class 实例，引用不相等导致 getAnnotation 恒返回 null。
                    Class<? extends java.lang.annotation.Annotation> annType;
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends java.lang.annotation.Annotation> t =
                                (Class<? extends java.lang.annotation.Annotation>)
                                        Class.forName("XuanJi.api.annotation.XuanJiPlugin", false, cl);
                        annType = t;
                    } catch (ClassNotFoundException cnf) {
                        annType = XuanJiPlugin.class;
                    }
                    // 注解可能在主类上，也可能在嵌套类（如 Commands / Main）上；
                    // 故先查主类及其嵌套类，再兜底扫描整个 jar 定位标注 @XuanJiPlugin 的类。
                    Class<?> declClass = findAnnotatedClass(jf, cl, pluginClass, annType);
                    if (declClass == null) {
                        r.put("error", "主类 " + pluginClass + " 及其嵌套类均未标注 @XuanJiPlugin");
                        return r;
                    }
                    java.lang.annotation.Annotation ann = declClass.getAnnotation(annType);
                    r.put("declared", true);
                    r.put("id", blankToNull(readAnn(annType, ann, "id")));
                    r.put("name", blankToNull(readAnn(annType, ann, "name")));
                    r.put("version", blankToNull(readAnn(annType, ann, "version")));
                    r.put("author", blankToNull(readAnn(annType, ann, "author")));
                    r.put("description", blankToNull(readAnn(annType, ann, "description")));
                    r.put("permissions", blankToNull(readAnn(annType, ann, "permissions")));
                    r.put("dependsOn", blankToNull(readAnn(annType, ann, "dependsOn")));
                    r.put("rateLimit", blankToNull(readAnn(annType, ann, "rateLimit")));
                    r.put("platforms", blankToNull(readAnn(annType, ann, "platforms")));
                    r.put("defaultBot", blankToNull(readAnn(annType, ann, "defaultBot")));
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 读取插件声明失败: {}", e.getMessage());
            r.put("error", e.getMessage());
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
        return r;
    }

    // ═══════════════════ 工具 ═══════════════════

    /** 表单留空则取声明值；声明有值时表单必须与之一致，否则抛错（声明缺省则不强制）。 */
    private static String pick(String label, String formVal, String declVal) {
        String dv = blankToNull(declVal);
        String fv = blankToNull(formVal);
        if (fv == null) return dv;          // 表单留空 → 以声明为准（可为 null）
        if (dv == null) return fv;          // 声明缺省 → 接受表单填写值
        if (!fv.trim().equals(dv.trim())) {
            throw new IllegalArgumentException("「" + label + "」与插件声明不一致（声明=" + dv + "）");
        }
        return fv.trim();
    }

    /** 探测本机提交的当前状态：审核中 / 已上架 / 拒绝上架 / 已下架。
     *  顺序：先查本提交在审核仓库的 meta——仍处于「审核中」即判定为审核中（这样已下架插件用相同
     *  pluginId/version 重新上传时，能正确显示为「审核中」而非「已下架」）；再查正式仓库；最后查拒绝。 */
    private Map<String, Object> probeStatus(String pluginId, String version, String submissionId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", MarketSettings.STATUS_PENDING);
        r.put("rejectReason", null);
        try {
            // 1) 本提交在审核仓库的 meta（优先，决定该笔提交自身的状态）
            byte[] meta = httpGet(rawUrl(settings.getReviewRepoUrl(),
                    MarketSettings.PENDING_DIR + "/" + submissionId + "/meta.json"));
            if (meta != null) {
                JsonNode node = Json.mapper().readTree(meta);
                String st = node.path("status").asText();
                if (MarketSettings.STATUS_REJECTED.equals(st)) {
                    // 已拒绝：返回拒绝状态
                    r.put("status", MarketSettings.STATUS_REJECTED);
                    r.put("rejectReason", node.path("rejectReason").isNull() ? null : node.path("rejectReason").asText());
                    return r;
                }
                if (MarketSettings.STATUS_PENDING.equals(st)) {
                    // 仍在审核中（首次提交或下架后重传）：直接判定为审核中，不再下探正式仓库
                    r.put("status", MarketSettings.STATUS_PENDING);
                    return r;
                }
                // meta 已是「已上架」（本提交曾被通过）：下探正式仓库看当前状态（可能已被下架）
            }
            // 2) 已上架 / 已下架：正式仓库 plugins/<pluginId>/plugin.json 存在且版本一致
            byte[] m = httpGet(rawUrl(settings.getReleaseRepoUrl(),
                    MarketSettings.PLUGINS_DIR + "/" + pluginId + "/" + MarketSettings.PLUGIN_MANIFEST));
            if (m != null) {
                JsonNode node = Json.mapper().readTree(m);
                if (version.equals(node.path("version").asText())) {
                    String st = node.path("status").asText(MarketSettings.STATUS_APPROVED);
                    if (MarketSettings.STATUS_DELISTED.equals(st)) {
                        r.put("status", MarketSettings.STATUS_DELISTED);
                        r.put("rejectReason", null);
                        r.put("delistReason", node.path("delistReason").isNull() ? null : node.path("delistReason").asText());
                    } else {
                        r.put("status", MarketSettings.STATUS_APPROVED);
                    }
                    return r;
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 探测提交状态失败: {}", e.getMessage());
        }
        return r;
    }

    /**
     * 真实验证正式仓库管理员令牌：拿令牌去正式仓库做一次「写文件 → push → 删除 → push」往返。
     * 失败即视为令牌无效或无写权限；后端绝不保存/比较令牌。
     *
     * @return true=令牌可写访问正式仓库；false=令牌无效/无权限/网络异常
     */
    public boolean testReleaseToken(String token) {
        return gitService.testWriteAccess(settings.getReleaseRepoUrl(), MarketSettings.RELEASE_DIR, token);
    }

    /** raw 匿名 URL 构造（从指定仓库 URL 推导，仅后端使用）。 */
    static String rawUrl(String repoUrl, String pathInRepo) {
        String url = repoUrl;
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        url = url.replace("https://", "").replace("http://", "");
        return "https://" + url + "/-/git/raw/" + BRANCH + "/" + pathInRepo;
    }

    private static byte[] httpGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        return resp.statusCode() == 200 ? resp.body() : null;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 计算失败", e);
        }
    }

    /** 文件名安全化：仅保留字母数字 ._- ，防止路径穿越。 */
    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return n.startsWith(".") ? "_" + n : n;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 反射读取注解字段并序列化为展示字符串（兼容 String / int / enum / 数组）。 */
    private static String readAnn(Class<? extends java.lang.annotation.Annotation> annType,
                                  java.lang.annotation.Annotation ann, String method) {
        try {
            Object v = annType.getMethod(method).invoke(ann);
            return annToStr(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** 将注解字段值序列化为可读字符串：枚举取 name()，数组用 ", " 连接。 */
    private static String annToStr(Object v) {
        if (v == null) return null;
        if (v.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(v);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                Object e = java.lang.reflect.Array.get(v, i);
                if (i > 0) sb.append(", ");
                sb.append(e instanceof Enum ? ((Enum<?>) e).name() : String.valueOf(e));
            }
            return sb.toString();
        }
        if (v instanceof Enum) return ((Enum<?>) v).name();
        return String.valueOf(v);
    }

    /**
     * 在 jar 内定位标注了 {@code @XuanJiPlugin} 的类。
     * 注解可能在主类上，也可能在其嵌套类（如 {@code Commands} / {@code Main}）上，
     * 因此先查主类及其嵌套类，再兜底扫描 jar 内全部类。
     *
     * @return 标注了注解的类；找不到返回 {@code null}
     */
    private static Class<?> findAnnotatedClass(JarFile jf, URLClassLoader cl, String pluginClass,
                                               Class<? extends java.lang.annotation.Annotation> annType) {
        // 1) 主类本身
        try {
            Class<?> main = Class.forName(pluginClass, false, cl);
            if (main.isAnnotationPresent(annType)) return main;
            Class<?> nested = findInNested(main, cl, annType);   // 2) 递归查嵌套类
            if (nested != null) return nested;
        } catch (Throwable ignored) { }
        // 3) 兜底：扫描 jar 内全部 .class 定位
        try {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class")) continue;
                String cn = n.substring(0, n.length() - 6).replace('/', '.');
                if (cn.equals("module-info") || cn.endsWith("package-info")) continue;
                try {
                    Class<?> c = Class.forName(cn, false, cl);
                    if (c.isAnnotationPresent(annType)) return c;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** 递归检查类的嵌套类（含更深层级）是否标注了目标注解。 */
    private static Class<?> findInNested(Class<?> parent, URLClassLoader cl,
                                        Class<? extends java.lang.annotation.Annotation> annType) {
        for (Class<?> nc : parent.getDeclaredClasses()) {
            if (nc.isAnnotationPresent(annType)) return nc;
            Class<?> deeper = findInNested(nc, cl, annType);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static Map<String, Object> obj(JsonNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return m;
        ((tools.jackson.databind.node.ObjectNode) node).properties()
                .forEach(e -> m.put(e.getKey(), scalar(e.getValue())));
        return m;
    }

    private static Object scalar(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isNumber()) return n.numberValue();
        return n.asText();
    }

    // ---- 本地提交记录（data/plugin-market/local-submissions.json） ----

    private static void appendLocalSubmission(String pluginId, String name, String version, String category,
                                              String submissionId) {
        try {
            List<Map<String, Object>> list = readLocalSubmissions();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("submissionId", submissionId);
            rec.put("pluginId", pluginId);
            rec.put("name", name);
            rec.put("version", version);
            rec.put("category", category);
            rec.put("submittedAt", Instant.now().toString());
            list.add(rec);
            writeJson(LOCAL_SUBMISSIONS, list);
        } catch (Exception e) {
            log.warn("[PluginMarket] 本地提交记录写入失败: {}", e.getMessage());
        }
    }

    private static List<Map<String, Object>> readLocalSubmissions() {
        try {
            Path f = Paths.get(LOCAL_SUBMISSIONS);
            if (!Files.isRegularFile(f)) return new ArrayList<>();
            JsonNode root = Json.mapper().readTree(Files.readAllBytes(f));
            if (root == null || !root.isArray()) return new ArrayList<>();
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode n : root) list.add(obj(n));
            return list;
        } catch (Exception e) {
            log.warn("[PluginMarket] 本地提交记录读取失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ---- 本地审核记录（data/plugin-market/audit-log.json） ----

    private static void appendAudit(String submissionId, String pluginId, String version, String name,
                                    String action, boolean official, String reason) {
        try {
            List<Map<String, Object>> list = readAuditLog();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("submissionId", submissionId);
            rec.put("pluginId", pluginId);
            rec.put("version", version);
            rec.put("name", name);
            rec.put("action", action);
            rec.put("official", official);
            rec.put("reason", reason == null ? "" : reason);
            rec.put("time", Instant.now().toString());
            list.add(rec);
            writeJson(AUDIT_LOG, list);
        } catch (Exception e) {
            log.warn("[PluginMarket] 审核记录写入失败: {}", e.getMessage());
        }
    }

    private static List<Map<String, Object>> readAuditLog() {
        try {
            Path f = Paths.get(AUDIT_LOG);
            if (!Files.isRegularFile(f)) return new ArrayList<>();
            JsonNode root = Json.mapper().readTree(Files.readAllBytes(f));
            if (root == null || !root.isArray()) return new ArrayList<>();
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode n : root) list.add(obj(n));
            list.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("time", "")), Comparator.reverseOrder()));
            return list;
        } catch (Exception e) {
            log.warn("[PluginMarket] 审核记录读取失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void writeJson(String path, Object data) throws Exception {
        Path f = Paths.get(path);
        if (f.getParent() != null) Files.createDirectories(f.getParent());
        Files.write(f, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
