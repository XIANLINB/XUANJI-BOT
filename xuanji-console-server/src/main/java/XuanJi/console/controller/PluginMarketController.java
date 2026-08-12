package XuanJi.console.controller;

import XuanJi.console.market.MarketSettings;
import XuanJi.console.market.PluginMarketService;
import XuanJi.console.service.AuditService;
import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 插件市场（中央插件库：浏览 / 上传 / 审核 / 安装）。
 *
 * <p>市场仓库为公开 git 仓库（默认 CNB 官方市场），浏览与安装走 raw 匿名读取（无需凭据）；
 * 上传/审核为 git 写操作，需管理员在设置页配置 git 凭据。
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

    /** 市场已上架插件列表（匿名 raw 读取）。 */
    @GetMapping("/plugins")
    public List<Map<String, Object>> listMarket() {
        return marketService.listMarket();
    }

    /** 市场配置（token 不回显）。 */
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return settings.view();
    }

    /** 保存市场配置（仓库地址 + 管理员 git 凭据）。 */
    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body,
                                            jakarta.servlet.http.HttpServletRequest req) {
        settings.save(
                str(body.get("repoUrl")),
                str(body.get("gitUser")),
                str(body.get("gitToken")),
                body.get("enabled") == null ? null : Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        auditService.record("MARKET_SETTINGS", "保存插件市场配置", req);
        return Map.of("status", "ok", "settings", settings.view());
    }

    /** 开发者上传插件（multipart：name/description/version/category/submitter + jar 文件）。 */
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
        } catch (IllegalArgumentException e) {
            return Map.of("status", "error", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[PluginMarket] 上传插件失败", e);
            return Map.of("status", "error", "message", "上传失败: " + e.getMessage());
        }
    }

    /** 我的提交（本机发起的上传，含当前状态）。 */
    @GetMapping("/submissions")
    public List<Map<String, Object>> mySubmissions() {
        return marketService.mySubmissions();
    }

    /** 待审列表（管理员）。 */
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        try {
            return marketService.listPending();
        } catch (Exception e) {
            log.error("[PluginMarket] 拉取待审列表失败", e);
            return List.of();
        }
    }

    /** 通过审核（上架）。 */
    @PostMapping("/pending/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id,
                                       @RequestParam(value = "official", defaultValue = "false") boolean official,
                                       jakarta.servlet.http.HttpServletRequest req) {
        try {
            Map<String, Object> r = marketService.approve(id, official);
            auditService.record("MARKET_APPROVE",
                    "插件上架: " + r.get("pluginId") + "@" + r.get("version") + (official ? "（官方）" : ""), req);
            return r;
        } catch (Exception e) {
            log.error("[PluginMarket] 审核通过失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /** 拒绝审核（附理由）。 */
    @PostMapping("/pending/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      jakarta.servlet.http.HttpServletRequest req) {
        try {
            String reason = body == null ? null : str(body.get("reason"));
            Map<String, Object> r = marketService.reject(id, reason);
            auditService.record("MARKET_REJECT", "插件拒绝: " + id + (reason == null ? "" : " 理由=" + reason), req);
            return r;
        } catch (Exception e) {
            log.error("[PluginMarket] 审核拒绝失败", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
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
}
