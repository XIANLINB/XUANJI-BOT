package XuanJi.console.controller;

import XuanJi.console.market.MarketSettings;
import XuanJi.console.market.PluginMarketService;
import XuanJi.console.service.AuditService;
import XuanJi.core.plugin.XuanJiPluginManager;
import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 插件市场（中央插件库：浏览 / 上传 / 审核 / 安装）。
 *
 * <p>安全模型：
 * <ul>
 *   <li>浏览/安装经<b>后端代理</b>（jar 下载端点），前端不接触仓库真实地址</li>
 *   <li>上传用<b>上传令牌</b>（开发者配置）；审核台需先验证<b>管理员令牌</b>，通过/拒绝操作再校验</li>
 *   <li>审核通过/拒绝均写入本地审核记录（audit-log）</li>
 * </ul>
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console/market")
public class PluginMarketController {

    private final PluginMarketService marketService;
    private final MarketSettings settings;
    private final AuditService auditService;
    private final XuanJiPluginManager pluginManager;

    public PluginMarketController(PluginMarketService marketService, MarketSettings settings,
                                  AuditService auditService, XuanJiPluginManager pluginManager) {
        this.marketService = marketService;
        this.settings = settings;
        this.auditService = auditService;
        this.pluginManager = pluginManager;
    }

    /** 市场已上架插件列表（后端代理拉取；downloadUrl 为框架端点，不暴露仓库地址）。 */
    @GetMapping("/plugins")
    public List<Map<String, Object>> listMarket() {
        return marketService.listMarket();
    }

    /** 已上架（含已下架）插件列表（管理员视图，用于下架管理）。 */
    @GetMapping("/released")
    public List<Map<String, Object>> released() {
        return marketService.listReleased();
    }

    /** 下架已上架插件（需管理员令牌，附理由）。 */
    @PostMapping("/released/{id}/delist")
    public Map<String, Object> delist(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      jakarta.servlet.http.HttpServletRequest req) {
        String token = body == null ? null : str(body.get("adminToken"));
        String reason = body == null ? null : str(body.get("reason"));
        try {
            Map<String, Object> r = marketService.delist(id, reason, token);
            auditService.record("MARKET_DELIST", "插件下架: " + id + (reason == null ? "" : " 理由=" + reason), req);
            return r;
        } catch (SecurityException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 下架插件失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** 重新上架已下架插件（需管理员令牌）。 */
    @PostMapping("/released/{id}/relist")
    public Map<String, Object> relist(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      jakarta.servlet.http.HttpServletRequest req) {
        String token = body == null ? null : str(body.get("adminToken"));
        try {
            Map<String, Object> r = marketService.relist(id, token);
            auditService.record("MARKET_RELIST", "插件重新上架: " + id, req);
            return r;
        } catch (SecurityException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 重新上架插件失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** 已上架 jar 代理下载（浏览器直接下载；内部从仓库 raw 拉取）。 */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String pluginId, @RequestParam String version) {
        byte[] jar = marketService.downloadPluginJar(pluginId, version);
        if (jar == null || jar.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String name = safeFileName(pluginId + "-" + version + ".jar");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(jar);
    }

    /** 市场配置状态（令牌/仓库均为内置，只返回是否就绪，无需设置页）。 */
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of(
                "enabled", settings.isEnabled(),
                "reviewRepoUrl", maskUrl(settings.getReviewRepoUrl()),
                "releaseRepoUrl", maskUrl(settings.getReleaseRepoUrl()),
                "hasUploadToken", settings.hasUploadToken());
    }

    /** 开发者上传前读取 jar 内 @XuanJiPlugin 声明（用于前端自动填充表单 + 后端一致性校验）。 */
    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestParam("jar") MultipartFile jar) {
        try {
            if (jar == null || jar.isEmpty()) return Map.of("declared", false, "error", "jar 为空");
            return marketService.extractDeclaration(jar.getBytes());
        } catch (Exception e) {
            return Map.of("declared", false, "error", e.getMessage());
        }
    }

    private static String maskUrl(String url) {
        try {
            int scheme = url.indexOf("://");
            if (scheme < 0) return "***";
            String head = url.substring(0, scheme + 3);
            String rest = url.substring(scheme + 3);
            int slash = rest.indexOf('/');
            String host = slash < 0 ? rest : rest.substring(0, slash);
            return head + host + "/***";
        } catch (Exception e) {
            return "***";
        }
    }

    /** 开发者上传插件（multipart：name/description/version/category/author + jar；字段须与 @XuanJiPlugin 一致）。 */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestParam(value = "name", required = false) String name,
                                      @RequestParam(value = "description", required = false) String description,
                                      @RequestParam(value = "version", required = false) String version,
                                      @RequestParam(value = "category", required = false) String category,
                                      @RequestParam(value = "author", required = false) String author,
                                      @RequestParam("jar") MultipartFile jar,
                                      jakarta.servlet.http.HttpServletRequest req) throws IOException {
        try {
            Map<String, Object> r = marketService.submit(name, description, version, category,
                    author, jar.getOriginalFilename(), jar.getBytes());
            auditService.record("MARKET_SUBMIT",
                    "上传插件: " + r.get("pluginId") + "@" + r.get("version") + "（审核中）", req);
            return r;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 上传插件失败", e);
            return Map.of("status", "error", "message", "上传失败: " + e.getMessage());
        }
    }

    /** 我的提交（本机发起的上传，含当前状态与拒绝理由）。 */
    @GetMapping("/submissions")
    public List<Map<String, Object>> mySubmissions() {
        return marketService.mySubmissions();
    }

    /** 待审列表（仅审核中 PENDING；已拒绝不再展示）。 */
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        try {
            return marketService.listPending();
        } catch (Exception e) {
            log.error("[PluginMarket] 拉取待审列表失败", e);
            return List.of();
        }
    }

    /** 验证管理员令牌：拿令牌去正式仓库做一次「上传/删除测试文件」真实往返；失败即令牌无效。 */
    @PostMapping("/pending/verify")
    public Map<String, Object> verifyAdmin(@RequestBody Map<String, Object> body) {
        String token = body == null ? null : str(body.get("adminToken"));
        boolean ok = marketService.testReleaseToken(token);
        return Map.of("status", ok ? "ok" : "error",
                "message", ok ? "令牌可访问正式仓库，验证通过" : "令牌无效或无法访问正式仓库（上传测试文件失败）");
    }

    /** 待审 jar 代理下载（审核用，需管理员令牌 query；不暴露仓库地址）。 */
    @GetMapping("/pending/{id}/download")
    public ResponseEntity<byte[]> downloadPending(@PathVariable String id,
                                                  @RequestParam String adminToken) {
        if (adminToken == null || adminToken.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        byte[] jar = marketService.downloadPendingJar(id, adminToken);
        if (jar == null || jar.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pending-" + id + ".jar\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(jar);
    }

    /** 通过审核（上架；需管理员令牌）。 */
    @PostMapping("/pending/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       jakarta.servlet.http.HttpServletRequest req) {
        boolean official = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("official", false)));
        String token = body == null ? null : str(body.get("adminToken"));
        String category = body == null ? null : str(body.get("category"));
        try {
            Map<String, Object> r = marketService.approve(id, official, category, token);
            auditService.record("MARKET_APPROVE",
                    "插件上架: " + r.get("pluginId") + "@" + r.get("version") + (official ? "（官方）" : ""), req);
            return r;
        } catch (SecurityException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 审核通过失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** 拒绝审核（附理由；需管理员令牌）。 */
    @PostMapping("/pending/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      jakarta.servlet.http.HttpServletRequest req) {
        String token = body == null ? null : str(body.get("adminToken"));
        String reason = body == null ? null : str(body.get("reason"));
        try {
            Map<String, Object> r = marketService.reject(id, reason, token);
            auditService.record("MARKET_REJECT", "插件拒绝: " + id + (reason == null ? "" : " 理由=" + reason), req);
            return r;
        } catch (SecurityException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 审核拒绝失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** 本地审核记录（通过/拒绝历史，含官方标注与理由）。 */
    @GetMapping("/audit")
    public List<Map<String, Object>> auditLog() {
        return marketService.auditLog();
    }

    /** 安装插件到本地 plugins/ 并热加载（downloadUrl + sha256 校验）。 */
    @PostMapping("/install")
    public Map<String, Object> install(@RequestBody Map<String, Object> body,
                                       jakarta.servlet.http.HttpServletRequest req) {
        String pluginId = str(body.get("pluginId"));
        String version = str(body.get("version"));
        try {
            Map<String, Object> r = marketService.install(pluginId, version);
            // 安装写盘成功后立即触发热加载，使插件出现在「本地插件」列表，无需手动扫描/重启
            java.util.List<String> loaded = pluginManager.scanNewPlugins();
            r.put("loaded", loaded);
            auditService.record("MARKET_INSTALL", "安装插件: " + pluginId + "@" + version
                    + (loaded.isEmpty() ? "（已写入但未加载）" : " 已加载: " + String.join(", ", loaded)), req);
            return r;
        } catch (Exception e) {
            log.error("[PluginMarket] 安装插件失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
