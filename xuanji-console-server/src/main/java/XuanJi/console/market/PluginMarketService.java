package XuanJi.console.market;

import XuanJi.api.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
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
import java.util.UUID;
import java.util.jar.JarFile;

/**
 * 插件市场业务 — 基于「公开 git 仓库 + raw 匿名读取」的中央插件市场。
 *
 * <p>仓库约定结构（CNB 公开仓库，已验证链路）：
 * <pre>
 *   index.json                    已上架插件清单（公开，raw 匿名读取）
 *   plugins/&lt;pluginId&gt;/&lt;version&gt;/xxx.jar   已上架插件 jar（raw 匿名下载）
 *   .pending/&lt;submissionId&gt;/meta.json       待审提交元数据
 *   .pending/&lt;submissionId&gt;/xxx.jar         待审 jar
 * </pre>
 *
 * <p>权限模型：
 * <ul>
 *   <li>上传（开发者）：用<b>上传令牌</b> git push 到 {@code .pending/}，状态 PENDING（无公开入口，不可发现）</li>
 *   <li>审核（管理员）：进入审核台需验证<b>管理员令牌</b>；通过（移入 {@code plugins/} + 更新 index.json）/ 拒绝（meta 标 REJECTED），操作持久化到本地审核记录</li>
 *   <li>公开：所有实例经<b>框架后端代理</b>拉 index.json + 下载 jar（sha256 校验），前端不接触仓库真实地址</li>
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

    /** 拉取已上架插件清单（后端代理；downloadUrl 返回框架端点，不暴露仓库真实地址）。 */
    public List<Map<String, Object>> listMarket() {
        try {
            byte[] body = httpGet(rawUrl(settings, MarketSettings.INDEX_PATH));
            if (body == null) return List.of();
            JsonNode root = Json.mapper().readTree(body);
            JsonNode plugins = root == null ? null : root.get("plugins");
            if (plugins == null || !plugins.isArray()) return List.of();
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode p : plugins) {
                Map<String, Object> row = obj(p);
                // 前端只拿框架代理下载端点（仓库真实地址不出后端）
                row.put("downloadUrl", "/console/market/download?pluginId=" + p.path("pluginId").asText()
                        + "&version=" + p.path("version").asText());
                list.add(row);
            }
            return list;
        } catch (Exception e) {
            log.warn("[PluginMarket] 拉取市场清单失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 已上架 jar 下载（后端代理）：从 index.json 找真实 raw 地址，匿名拉取。 */
    public byte[] downloadPluginJar(String pluginId, String version) {
        try {
            JsonNode root = Json.mapper().readTree(httpGet(rawUrl(settings, MarketSettings.INDEX_PATH)));
            if (root == null || !root.path("plugins").isArray()) return null;
            for (JsonNode p : root.path("plugins")) {
                if (pluginId.equals(p.path("pluginId").asText()) && version.equals(p.path("version").asText())) {
                    return httpGet(p.path("downloadUrl").asText());
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 下载插件失败 {}@{}: {}", pluginId, version, e.getMessage());
        }
        return null;
    }

    /** 安装插件到本地 plugins/（downloadUrl 拉取 + sha256 校验）。 */
    public Map<String, Object> install(String pluginId, String version) throws Exception {
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
        String fileName = safeName(fileNameFromUrl((String) target.get("downloadUrl")));
        Path pluginsDir = Paths.get("plugins");
        Files.createDirectories(pluginsDir);
        Path dest = pluginsDir.resolve(fileName);
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

    // ═══════════════════ 上传 / 我的提交 ═══════════════════

    /** 开发者上传插件 → .pending/&lt;id&gt;/ → 审核中（用上传令牌 git push）。 */
    public Map<String, Object> submit(String name, String description, String version, String category,
                                      String submitter, String jarFileName, byte[] jarBytes) throws Exception {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("插件名称不能为空");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("插件版本不能为空");
        if (jarBytes == null || jarBytes.length == 0) throw new IllegalArgumentException("jar 包为空");
        if (jarBytes.length > MAX_JAR_SIZE) throw new IllegalArgumentException("jar 包过大（>20MB）");
        if (!jarFileName.toLowerCase().endsWith(".jar")) throw new IllegalArgumentException("仅支持 .jar 插件包");

        String gitToken = settings.getUploadToken();
        if (gitToken == null || gitToken.isBlank()) {
            throw new IllegalStateException("上传插件需要配置「上传令牌」（设置页填写，获取后仅用于提交）");
        }

        // 从 jar manifest 读取插件 ID（PF4J 插件必需）
        String pluginId = readPluginId(jarBytes);
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("jar 不是有效的插件包（缺少 Plugin-Id 清单项）");
        }
        pluginId = safeName(pluginId);
        if (category == null || category.isBlank()) category = "other";

        String submissionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String pendingDir = MarketSettings.PENDING_DIR + "/" + submissionId;

        try (Git git = gitService.openOrPull(settings)) {
            Path repo = gitService.repoDir();
            Path targetDir = repo.resolve(pendingDir);
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(safeName(jarFileName)), jarBytes);

            String sha = sha256Hex(jarBytes);
            ObjectNode meta = Json.obj();
            meta.put("submissionId", submissionId);
            meta.put("pluginId", pluginId);
            meta.put("name", name);
            meta.put("version", version);
            meta.put("category", category);
            meta.put("description", description == null ? "" : description);
            meta.put("submitter", submitter == null ? "unknown" : submitter);
            meta.put("status", "PENDING");
            meta.putNull("rejectReason");
            meta.put("jar", pendingDir + "/" + safeName(jarFileName));
            meta.put("sha256", sha);
            meta.put("submittedAt", Instant.now().toString());
            Files.write(targetDir.resolve("meta.json"), Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));

            gitService.commitAndPush(git, gitToken, "submit: " + pluginId + " " + version + " (审核中)");
        }

        appendLocalSubmission(pluginId, name, version, category, submissionId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("submissionId", submissionId);
        r.put("pluginId", pluginId);
        r.put("name", name);
        r.put("version", version);
        r.put("message", "提交成功，等待审核");
        return r;
    }

    /** 我的提交：本地记录 + 匿名探测当前状态（PENDING / APPROVED / REJECTED + 拒绝理由）。 */
    public List<Map<String, Object>> mySubmissions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> rec : readLocalSubmissions()) {
            Map<String, Object> row = new LinkedHashMap<>(rec);
            String id = (String) rec.get("submissionId");
            Map<String, Object> probe = probeStatus((String) rec.get("pluginId"), (String) rec.get("version"), id);
            row.put("status", probe.get("status"));
            row.put("rejectReason", probe.get("rejectReason"));
            out.add(row);
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("submittedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    // ═══════════════════ 审核（管理员令牌） ═══════════════════

    /** 待审列表：拉取 .pending/ 下 status=PENDING 的提交（已拒绝不显示）。 */
    public List<Map<String, Object>> listPending() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Git git = gitService.openOrPull(settings)) {
            Path pendingRoot = gitService.repoDir().resolve(MarketSettings.PENDING_DIR);
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
                    // 只展示审核中；已拒绝（REJECTED）不进入审核台
                    if (!"PENDING".equals(node.path("status").asText())) continue;
                    Map<String, Object> row = obj(node);
                    // 审核下载地址走框架代理（带管理员令牌校验，后端不暴露仓库地址）
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
        if (!settings.verifyAdminToken(adminToken)) return null;
        try (Git git = gitService.openOrPull(settings)) {
            Path meta = gitService.repoDir().resolve(MarketSettings.PENDING_DIR + "/" + submissionId + "/meta.json");
            if (!Files.isRegularFile(meta)) return null;
            JsonNode node = Json.mapper().readTree(Files.readAllBytes(meta));
            return httpGet(rawUrl(settings, node.path("jar").asText()));
        } catch (Exception e) {
            log.warn("[PluginMarket] 下载待审 jar 失败 {}: {}", submissionId, e.getMessage());
            return null;
        }
    }

    /** 通过审核（需管理员令牌）：jar 移入 plugins/ + 更新 index.json + 删除 .pending/<id>。 */
    public Map<String, Object> approve(String submissionId, boolean official, String adminToken) throws Exception {
        requireAdmin(adminToken);
        try (Git git = gitService.openOrPull(settings)) {
            Path repo = gitService.repoDir();
            Path pending = repo.resolve(MarketSettings.PENDING_DIR + "/" + submissionId);
            if (!Files.isDirectory(pending)) throw new IllegalStateException("待审提交不存在: " + submissionId);
            ObjectNode meta = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(pending.resolve("meta.json")));
            String pluginId = meta.path("pluginId").asText();
            String version = meta.path("version").asText();
            String jarPath = meta.path("jar").asText();
            String jarName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
            String sha = meta.path("sha256").asText();
            String name = meta.path("name").asText();
            String category = meta.path("category").asText();
            String description = meta.path("description").asText();
            String submitter = meta.path("submitter").asText();

            Path srcJar = pending.resolve(safeName(jarName));
            if (!Files.isRegularFile(srcJar)) throw new IllegalStateException("待审 jar 缺失: " + jarPath);
            Path destDir = repo.resolve(MarketSettings.PLUGINS_DIR + "/" + pluginId + "/" + version);
            Files.createDirectories(destDir);
            Files.move(srcJar, destDir.resolve(safeName(jarName)), StandardCopyOption.REPLACE_EXISTING);

            // 更新 index.json
            Path indexFile = repo.resolve(MarketSettings.INDEX_PATH);
            ObjectNode index;
            if (Files.isRegularFile(indexFile)) {
                index = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(indexFile));
            } else {
                index = Json.obj();
                index.put("market", "xuanji-plugins");
                index.put("schemaVersion", 1);
                index.put("updatedAt", Instant.now().toString());
                index.set("plugins", Json.arr());
            }
            if (!index.has("plugins")) index.set("plugins", Json.arr());
            ArrayNode plugins = (ArrayNode) index.get("plugins");

            ObjectNode entry = Json.obj();
            entry.put("pluginId", pluginId);
            entry.put("name", name);
            entry.put("version", version);
            entry.put("category", category == null || category.isBlank() ? "other" : category);
            entry.put("official", official);
            entry.put("description", description == null ? "" : description);
            entry.put("author", submitter == null ? "" : submitter);
            entry.put("publishedAt", Instant.now().toString());
            entry.put("downloadUrl", rawUrl(settings, MarketSettings.PLUGINS_DIR + "/" + pluginId + "/" + version + "/" + safeName(jarName)));
            entry.put("sha256", sha);

            boolean replaced = false;
            for (int i = 0; i < plugins.size(); i++) {
                JsonNode old = plugins.get(i);
                if (pluginId.equals(old.path("pluginId").asText()) && version.equals(old.path("version").asText())) {
                    plugins.set(i, entry);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) plugins.add(entry);
            index.put("updatedAt", Instant.now().toString());
            Files.write(indexFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(index));

            deleteRecursively(pending);

            gitService.commitAndPush(git, settings.getAdminToken(), "approve: " + pluginId + " " + version + " 已上架");
            appendAudit(submissionId, pluginId, version, name, "APPROVED", official, null);
            return Map.of("status", "ok", "pluginId", pluginId, "version", version, "message", "已上架");
        }
    }

    /** 拒绝审核（需管理员令牌）：meta 标记 REJECTED + 拒绝理由。 */
    public Map<String, Object> reject(String submissionId, String reason, String adminToken) throws Exception {
        requireAdmin(adminToken);
        try (Git git = gitService.openOrPull(settings)) {
            Path repo = gitService.repoDir();
            Path pending = repo.resolve(MarketSettings.PENDING_DIR + "/" + submissionId);
            if (!Files.isDirectory(pending)) throw new IllegalStateException("待审提交不存在: " + submissionId);
            Path metaFile = pending.resolve("meta.json");
            ObjectNode meta = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(metaFile));
            meta.put("status", "REJECTED");
            meta.put("rejectReason", reason == null ? "" : reason);
            Files.write(metaFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            gitService.commitAndPush(git, settings.getAdminToken(),
                    "reject: " + meta.path("pluginId").asText() + " " + meta.path("version").asText());
            appendAudit(submissionId, meta.path("pluginId").asText(), meta.path("version").asText(),
                    meta.path("name").asText(), "REJECTED", false, reason);
            return Map.of("status", "ok", "submissionId", submissionId, "message", "已拒绝");
        }
    }

    /** 本地审核记录列表。 */
    public List<Map<String, Object>> auditLog() {
        return readAuditLog();
    }

    // ═══════════════════ 工具 ═══════════════════

    /** 探测本机提交的当前状态：已上架 / 已拒绝（含理由） / 审核中。 */
    private Map<String, Object> probeStatus(String pluginId, String version, String submissionId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "PENDING");
        r.put("rejectReason", null);
        try {
            // 已上架：index.json 中存在
            byte[] idx = httpGet(rawUrl(settings, MarketSettings.INDEX_PATH));
            if (idx != null) {
                JsonNode root = Json.mapper().readTree(idx);
                if (root != null && root.path("plugins").isArray()) {
                    for (JsonNode p : root.path("plugins")) {
                        if (pluginId.equals(p.path("pluginId").asText()) && version.equals(p.path("version").asText())) {
                            r.put("status", "APPROVED");
                            return r;
                        }
                    }
                }
            }
            // 已拒绝：.pending/<id>/meta.json 标 REJECTED
            byte[] meta = httpGet(rawUrl(settings, MarketSettings.PENDING_DIR + "/" + submissionId + "/meta.json"));
            if (meta != null) {
                JsonNode node = Json.mapper().readTree(meta);
                String st = node.path("status").asText();
                if ("REJECTED".equals(st)) {
                    r.put("status", "REJECTED");
                    r.put("rejectReason", node.path("rejectReason").isNull() ? null : node.path("rejectReason").asText());
                }
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 探测提交状态失败: {}", e.getMessage());
        }
        return r;
    }

    private void requireAdmin(String adminToken) {
        if (!settings.verifyAdminToken(adminToken)) {
            throw new SecurityException("无管理员审核权限（管理员令牌错误或未配置）");
        }
    }

    /** raw 匿名 URL 构造（从仓库 URL 推导，仅后端使用）。 */
    static String rawUrl(MarketSettings s, String pathInRepo) {
        String url = s.getRepoUrl();
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

    /** 从 jar MANIFEST.MF 读取 Plugin-Id。 */
    private static String readPluginId(byte[] jar) {
        try {
            Path tmp = Files.createTempFile("xuanji-market-", ".jar");
            Files.write(tmp, jar);
            try (JarFile jf = new JarFile(tmp.toFile())) {
                var attr = jf.getManifest().getMainAttributes().getValue("Plugin-Id");
                return attr == null ? null : attr.trim();
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            log.warn("[PluginMarket] 读取插件 ID 失败: {}", e.getMessage());
            return null;
        }
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
        String n = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return n.startsWith(".") ? "_" + n : n;
    }

    private static String fileNameFromUrl(String url) {
        String p = url.substring(url.lastIndexOf('/') + 1);
        return p.isEmpty() ? "plugin.jar" : p;
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
