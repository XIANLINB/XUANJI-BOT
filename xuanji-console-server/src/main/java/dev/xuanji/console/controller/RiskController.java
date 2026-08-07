package dev.xuanji.console.controller;

import dev.xuanji.console.service.RiskService;
import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 控制台 · 风控中心：命令限速 / 消息去重命中统计、各群风控状态、黑名单操作时间线。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/console/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    /** 全局风控命中概览（限速 / 去重 / 黑名单）。 */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return riskService.overview();
    }

    /** 各群风控状态（近 7 天消息量 + 黑名单人数）。 */
    @GetMapping("/groups")
    public List<Map<String, Object>> groups() {
        return riskService.groups();
    }

    /** 黑名单操作时间线（拉黑 / 解除）。 */
    @GetMapping("/blacklist-timeline")
    public Map<String, Object> blacklistTimeline(@RequestParam(required = false) String botKey,
                                                 @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = riskService.blacklistTimeline(botKey, limit);
        return Map.of("count", rows.size(), "rows", rows);
    }
}
