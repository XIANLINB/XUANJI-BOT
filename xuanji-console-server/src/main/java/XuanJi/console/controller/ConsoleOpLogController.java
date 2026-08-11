package XuanJi.console.controller;

import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理操作日志查询（qqbot_op_log 出站审计：禁言/撤回/审批等，含失败与被拒记录）。
 *
 * <p>跨所有 bot 实例库聚合，按时间倒序合并后内存分页；每行盖 {@code BOT_APPID} 章。
 * 支持 bot / 操作类型 / 状态 / 群 / 关键字筛选。
 */
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleOpLogController {

    private final ConsoleQueryService queryService;

    public ConsoleOpLogController(ConsoleQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/op-log")
    public Map<String, Object> opLog(@RequestParam(defaultValue = "") String bot,
                                     @RequestParam(defaultValue = "") String opType,
                                     @RequestParam(defaultValue = "") String status,
                                     @RequestParam(defaultValue = "") String groupId,
                                     @RequestParam(defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "200") int limit) {
        int cap = Math.min(Math.max(limit, 1), 500);
        String opTypeF = opType.isBlank() ? null : opType;
        String statusF = status.isBlank() ? null : status;
        String groupF = groupId.isBlank() ? null : groupId;
        String kw = keyword.isBlank() ? null : keyword;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> e : p.listOpLogs(ref.instanceId(), opTypeF, statusF, groupF, kw, cap)) {
                Map<String, Object> row = new LinkedHashMap<>(e);
                row.put("BOT_APPID", ref.instanceId());
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> ConsoleQueryService.asLong(r.get("CREATE_TIME"))).reversed());
        if (rows.size() > cap) rows = rows.subList(0, cap);
        return Map.of("rows", rows, "count", rows.size());
    }
}
