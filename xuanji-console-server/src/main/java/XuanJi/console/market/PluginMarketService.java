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
 * <p>流程：
 * <ul>
 *   <li>上传（开发者）：jar+元数据 → {@code .pending/&lt;id&gt;/} → 状态 PENDING（无公开入口，不可发现）</li>
 *   <li>审核（管理员）：列 {@code .pending/} → 下载 jar 测试 → 通过（移入 {@code plugins/} + 更新 index.json）/ 拒绝（meta 标 REJECTED）</li>
 *   <li>公开：所有实例匿名拉 index.json 浏览 + raw 下载 jar（sha256 校验后装入 plugins/ 热加载）</li>
 * </ul>
 */
@Slf4j
@Service
public class PluginMarketService {

    /** 本地提交记录（本机发起的上传，用于"我的提交"状态感知） */
    private static final String LOCAL_SUBMISSIONS = "data/plugin-market/local-submissions.json";
    private static final String BRANCH = "main";
    private static final long MAX_JAR_SIZE = 20 * 1024 * 1024; // 20MB

    private final MarketSettings settings;
    private final MarketGitService gitService;

    public PluginMarketService(MarketSettings settings, MarketGitService gitService) {
        this.settings = settings;
        this.gitService = gitService;
    }

    // ═══════════════════ 公开读取（匿名） ═══════════════════

    /** 拉取已上架插件清单（raw 匿名读取）。 */
    public List<Map<String, Object>> listMarket() {
        try {
            byte[] body = httpGet(rawUrl(settings, MarketSettings.INDEX_PATH));
            if (body == null) return List.of();
            JsonNode root = Json.mapper().readTree(body);
            JsonNode plugins = root == null ? null : root.get("plugins");
            if (plugins == null || !plugins.isArray()) return List.of();
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode p : plugins) {
                list.add(obj(p));
            }
            return list;
        } catch (Exception e) {
            log.warn("[PluginMarket] 拉取市场清单失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 根据 index.json 中的下载地址 + sha256，安装插件到本地 plugins/ 并热加载。 */
    public Map<String, Object> install(String pluginId, String version) throws Exception {
        List<Map<String, Object>> list = listMarket();
        Map<String, Object> target = null;
        for (Map<String, Object> p : list) {
            if (pluginId.equals(p.get("pluginId")) && version.equals(p.get("version"))) {
                target = p;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("市场中未找到插件: " + pluginId + "@" + version);
        }
        String url = (String) target.get("downloadUrl");
        String expectedSha = (String) target.get("sha256");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("插件缺少下载地址: " + pluginId);
        }
        byte[] jar = httpGet(url);
        if (jar == null || jar.length == 0) {
            throw new IllegalStateException("插件下载失败: " + pluginId);
        }
        if (jar.length > MAX_JAR_SIZE) {
            throw new IllegalStateException("插件包过大（>20MB），拒绝安装");
        }
        if (expectedSha != null && !expectedSha.isBlank()) {
            String actual = sha256Hex(jar);
            if (!expectedSha.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("插件校验失败（sha256 不匹配），已拒绝安装：预期 "
                        + expectedSha + " 实际 " + actual);
            }
        }
        String fileName = fileNameFromUrl(url);
        Path pluginsDir = Paths.get("plugins");
        Files.createDirectories(pluginsDir);
        Path dest = pluginsDir.resolve(safeName(fileName));
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

    /** 开发者上传插件 → .pending/&lt;id&gt;/ → 审核中。 */
    public Map<String, Object> submit(String name, String description, String version, String category,
                                      String submitter, String jarFileName, byte[] jarBytes) throws Exception {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("插件名称不能为空");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("插件版本不能为空");
        if (jarBytes == null || jarBytes.length == 0) throw new IllegalArgumentException("jar 包为空");
        if (jarBytes.length > MAX_JAR_SIZE) throw new IllegalArgumentException("jar 包过大（>20MB）");
        if (!jarFileName.toLowerCase().endsWith(".jar")) throw new IllegalArgumentException("仅支持 .jar 插件包");

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

            gitService.commitAndPush(git, settings, "submit: " + pluginId + " " + version + " (审核中)");
        }

        // 本地记录（我的提交状态感知）
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

    /** 我的提交：本地记录 + 匿名探测当前状态（PENDING / APPROVED / REJECTED）。 */
    public List<Map<String, Object>> mySubmissions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> rec : readLocalSubmissions()) {
            Map<String, Object> row = new LinkedHashMap<>(rec);
            String id = (String) rec.get("submissionId");
            row.put("status", probeStatus((String) rec.get("pluginId"), (String) rec.get("version"), id));
            out.add(row);
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("submittedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    // ═══════════════════ 审核（管理员） ═══════════════════

    /** 待审列表：拉取 .pending/ 下所有 meta.json。 */
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
                    Map<String, Object> row = obj(node);
                    // 审核下载地址（.pending 在仓库 main 分支，raw 可匿名读，供管理员下载测试）
                    row.put("downloadUrl", rawUrl(settings, String.valueOf(row.get("jar"))));
                    out.add(row);
                } catch (Exception e) {
                    log.warn("[PluginMarket] 读取待审 meta 失败 {}: {}", dir.getFileName(), e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("submittedAt", "")), Comparator.reverseOrder()));
        return out;
    }

    /** 通过审核：jar 移入 plugins/&lt;pluginId&gt;/&lt;version&gt;/ + 更新 index.json + 删除 .pending/&lt;id&gt;。 */
    public Map<String, Object> approve(String submissionId, boolean official) throws Exception {
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

            // 同 pluginId+version 覆盖，否则追加
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

            // 删除待审目录
            deleteRecursively(pending);

            gitService.commitAndPush(git, settings, "approve: " + pluginId + " " + version + " 已上架");
            return Map.of("status", "ok", "pluginId", pluginId, "version", version, "message", "已上架");
        }
    }

    /** 拒绝审核：meta 标记 REJECTED + 拒绝理由。 */
    public Map<String, Object> reject(String submissionId, String reason) throws Exception {
        try (Git git = gitService.openOrPull(settings)) {
            Path repo = gitService.repoDir();
            Path pending = repo.resolve(MarketSettings.PENDING_DIR + "/" + submissionId);
            if (!Files.isDirectory(pending)) throw new IllegalStateException("待审提交不存在: " + submissionId);
            Path metaFile = pending.resolve("meta.json");
            ObjectNode meta = (ObjectNode) Json.mapper().readTree(Files.readAllBytes(metaFile));
            meta.put("status", "REJECTED");
            meta.put("rejectReason", reason == null ? "" : reason);
            Files.write(metaFile, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            gitService.commitAndPush(git, settings, "reject: " + meta.path("pluginId").asText() + " " + meta.path("version").asText());
            return Map.of("status", "ok", "submissionId", submissionId, "message", "已拒绝");
        }
    }

    // ═══════════════════ 工具 ═══════════════════

    /** 探测本机提交的当前状态：已上架 / 已拒绝 / 审核中。 */
    private String probeStatus(String pluginId, String version, String submissionId) {
        try {
            // 已上架：index.json 中存在
            byte[] idx = httpGet(rawUrl(settings, MarketSettings.INDEX_PATH));
            if (idx != null) {
                JsonNode root = Json.mapper().readTree(idx);
                if (root != null && root.path("plugins").isArray()) {
                    for (JsonNode p : root.path("plugins")) {
                        if (pluginId.equals(p.path("pluginId").asText()) && version.equals(p.path("version").asText())) {
                            return "APPROVED";
                        }
                    }
                }
            }
            // 已拒绝：.pending/<id>/meta.json 标 REJECTED
            byte[] meta = httpGet(rawUrl(settings, MarketSettings.PENDING_DIR + "/" + submissionId + "/meta.json"));
            if (meta != null) {
                JsonNode node = Json.mapper().readTree(meta);
                String st = node.path("status").asText();
                if ("REJECTED".equals(st)) return "REJECTED";
            }
            // 都不满足：仍在审核中
            return "PENDING";
        } catch (Exception e) {
            log.warn("[PluginMarket] 探测提交状态失败: {}", e.getMessage());
            return "PENDING";
        }
    }

    /** raw 匿名 URL 构造（从仓库 URL 推导，无需凭据）。 */
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
            writeLocalSubmissions(list);
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

    private static void writeLocalSubmissions(List<Map<String, Object>> list) throws Exception {
        Path f = Paths.get(LOCAL_SUBMISSIONS);
        if (f.getParent() != null) Files.createDirectories(f.getParent());
        Files.write(f, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(list));
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
