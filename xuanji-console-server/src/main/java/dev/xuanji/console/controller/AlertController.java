package dev.xuanji.console.controller;

import dev.xuanji.console.service.AlertService;
import dev.xuanji.core.web.XuanjiApi;
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
@XuanjiApi
@RestController
@RequestMapping("/console/alert")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 各 bot 预警配置（含未配置的 bot，默认关闭）。 */
    @GetMapping("/config")
    public List<Map<String, Object>> configs() {
        return alertService.listConfigs();
    }

    /** 保存某 bot 预警配置。 */
    @PutMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body) {
        String botKey = str(body.get("botKey"));
        if (botKey.isBlank()) return Map.of("status", "error", "msg", "botKey 不能为空");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        String alertUserId = str(body.get("alertUserId"));
        if (enabled && alertUserId.isBlank()) {
            return Map.of("status", "error", "msg", "启用预警必须填写预警用户 ID（单聊 openid）");
        }
        return alertService.saveConfig(botKey, enabled, alertUserId);
    }

    /** 告警记录（倒序）。 */
    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = alertService.records(limit);
        return Map.of("count", rows.size(), "rows", rows);
    }

    /** 手动触发一次检查。 */
    @PostMapping("/check")
    public Map<String, Object> check() {
        return alertService.checkNow();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
