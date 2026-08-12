package XuanJi.console.controller;

import XuanJi.console.market.MarketSettings;
import XuanJi.console.market.PluginMarketService;
import XuanJi.console.service.AuditService;
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

    public PluginMarketController(PluginMarketService marketService, MarketSettings settings,
                                  AuditService auditService) {
        this.marketService = marketService;
        this.settings = settings;
        this.auditService = auditService;
    }

    /** 市场已上架插件列表（后端代理拉取；downloadUrl 为框架端点，不暴露仓库地址）。 */
    @GetMapping("/plugins")
    public List<Map<String, Object>> listMarket() {
        return marketService.listMarket();
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
                "repoUrl", maskUrl(settings.getRepoUrl()),
                "hasUploadToken", settings.hasUploadToken(),
                "hasAdminToken", settings.hasAdminToken());
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

    /** 开发者上传插件（需已配置上传令牌；multipart：name/description/version/category/submitter + jar）。 */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestParam(value = "name", required = false) String name,
                                      @RequestParam(value = "description", required = false) String description,
                                      @RequestParam(value = "version", required = false) String version,
                                      @RequestParam(value = "category", required = false) String category,
                                      @RequestParam(value = "submitter", required = false) String submitter,
                                      @RequestParam("jar") MultipartFile jar,
                                      jakarta.servlet.http.HttpServletRequest req) throws IOException {
        try {
            Map<String, Object> r = marketService.submit(name, description, version, category,
                    submitter, jar.getOriginalFilename(), jar.getBytes());
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

    /** 验证管理员令牌（进入审核台鉴权；通过才显示内容与操作）。 */
    @PostMapping("/pending/verify")
    public Map<String, Object> verifyAdmin(@RequestBody Map<String, Object> body) {
        String token = body == null ? null : str(body.get("adminToken"));
        boolean ok = settings.verifyAdminToken(token);
        return Map.of("status", ok ? "ok" : "error",
                "message", ok ? "管理员验证通过" : "管理员令牌错误，无审核权限");
    }

    /** 待审 jar 代理下载（审核用，需管理员令牌 query；不暴露仓库地址）。 */
    @GetMapping("/pending/{id}/download")
    public ResponseEntity<byte[]> downloadPending(@PathVariable String id,
                                                  @RequestParam String adminToken) {
        if (!settings.verifyAdminToken(adminToken)) {
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
        try {
            Map<String, Object> r = marketService.approve(id, official, token);
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
            auditService.record("MARKET_INSTALL", "安装插件: " + pluginId + "@" + version, req);
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
