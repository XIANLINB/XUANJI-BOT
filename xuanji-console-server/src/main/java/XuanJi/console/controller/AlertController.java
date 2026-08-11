package XuanJi.console.controller;

import XuanJi.console.service.AlertService;
import XuanJi.console.service.AuditService;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 控制台 · 预警中心：每 bot 预警配置（预警用户 ID + 开关）、告警记录、手动触发检查。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console/alert")
public class AlertController {

    private final AlertService alertService;
    private final AuditService auditService;
    private final XuanJi.core.config.ConfigService configService;

    public AlertController(AlertService alertService, AuditService auditService,
                           XuanJi.core.config.ConfigService configService) {
        this.alertService = alertService;
        this.auditService = auditService;
        this.configService = configService;
    }

    /** 各 bot 预警配置（含未配置的 bot，默认关闭）。 */
    @GetMapping("/config")
    public List<Map<String, Object>> configs() {
        return alertService.listConfigs();
    }

    /** 保存某 bot 预警配置（含规则 JSON：{规则key: {enabled, threshold}}）。 */
    @PutMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String botKey = str(body.get("botKey"));
        if (botKey.isBlank()) return Map.of("status", "error", "msg", "botKey 不能为空");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        String alertUserId = str(body.get("alertUserId"));
        if (enabled && alertUserId.isBlank()) {
            return Map.of("status", "error", "msg", "启用预警必须填写预警用户 ID（单聊 openid）");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) body.get("rules");
        Map<String, Object> r = alertService.saveConfig(botKey, enabled, alertUserId, rules);
        auditService.record("ALERT_CONFIG_UPDATE",
                "bot=" + botKey + " 预警" + (enabled ? "开启" : "关闭")
                        + (alertUserId.isBlank() ? "" : " 用户=" + alertUserId), req);
        return r;
    }

    /** 告警记录（倒序）。 */
    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = alertService.records(limit);
        return Map.of("count", rows.size(), "rows", rows);
    }

    /** 手动触发一次检查。 */
    @PostMapping("/check")
    public Map<String, Object> check(HttpServletRequest req) {
        auditService.record("ALERT_CHECK", "手动触发预警检查", req);
        return alertService.checkNow();
    }

    /** 预警全局设置：检查间隔(ms) + 同指标冷却(分钟)。返回当前生效值。 */
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of(
                "checkIntervalMs", alertService.checkIntervalMs(),
                "cooldownMinutes", alertService.cooldownMs() / 60_000L);
    }

    /** 保存预警全局设置：body {checkIntervalMs?, cooldownMinutes?}，写入 xuanji_config。 */
    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        try {
            if (body.get("checkIntervalMs") instanceof Number n) {
                long v = Math.min(Math.max(n.longValue(), 10_000L), 600_000L);
                configService.setGlobal(XuanJi.console.service.AlertService.CFG_CHECK_INTERVAL, String.valueOf(v));
            }
            if (body.get("cooldownMinutes") instanceof Number n2) {
                long v = Math.min(Math.max(n2.longValue(), 1L), 1440L);
                configService.setGlobal(XuanJi.console.service.AlertService.CFG_COOLDOWN_MINUTES, String.valueOf(v));
            }
            auditService.record("ALERT_CONFIG_UPDATE",
                    "预警全局设置: 检查间隔=" + alertService.checkIntervalMs() + "ms 冷却=" + alertService.cooldownMs() / 60_000L + "分钟", req);
            return Map.of("status", "ok",
                    "checkIntervalMs", alertService.checkIntervalMs(),
                    "cooldownMinutes", alertService.cooldownMs() / 60_000L);
        } catch (Exception e) {
            return Map.of("status", "error", "msg", e.getMessage());
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
