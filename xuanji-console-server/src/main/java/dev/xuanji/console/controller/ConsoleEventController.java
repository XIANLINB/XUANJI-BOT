package dev.xuanji.console.controller;

import dev.xuanji.console.service.ConsoleQueryService;
import dev.xuanji.core.storage.PlatformDataProvider;
import dev.xuanji.core.web.XuanjiApi;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 控制台 · 消息监控与系统事件流水。
 *
 * <p>跨所有 bot 实例库聚合（qqbot_message / qqbot_event 原表全部字段），
 * 按时间倒序合并后内存分页；每行盖 {@code BOT_APPID} 章。
 */
@XuanjiApi
@RestController
@RequestMapping("/console")
public class ConsoleEventController {

    /** 消息类事件类型（不属于「系统事件」）——系统事件页过滤掉，只展示加群/退群/加好友/成员变动等。 */
    private static final Set<String> MESSAGE_EVENT_TYPES = Set.of(
            "GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE", "C2C_MESSAGE_CREATE");

    private final ConsoleQueryService queryService;

    public ConsoleEventController(ConsoleQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/group-messages")
    public Map<String, Object> groupMessages(@RequestParam(defaultValue = "") String bot,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return pagedMessages(PlatformDataProvider.CHAT_GROUP, bot, page, size);
    }

    @GetMapping("/c2c-messages")
    public Map<String, Object> c2cMessages(@RequestParam(defaultValue = "") String bot,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return pagedMessages(PlatformDataProvider.CHAT_C2C, bot, page, size);
    }

    /**
     * 系统事件流水（qqbot_event 原表全部字段），按 Bot 过滤、倒序返回。
     * 与「消息监控」里的群/单聊消息分开：本接口只展示系统事件表（加群/退群/加好友/被管理员等），
     * 消息类事件（GROUP_MESSAGE_CREATE 等）在此过滤掉，避免 189 条消息事件淹没真正的系统事件。
     */
    @GetMapping("/event-log")
    public Map<String, Object> eventLog(@RequestParam(defaultValue = "") String bot,
                                        @RequestParam(defaultValue = "200") int limit) {
        int cap = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> e : p.listEvents(ref.instanceId(), cap)) {
                Object et = e.get("EVENT_TYPE");
                if (et != null && MESSAGE_EVENT_TYPES.contains(String.valueOf(et))) continue;
                Map<String, Object> row = new LinkedHashMap<>(e);
                row.put("BOT_APPID", ref.instanceId());
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> ConsoleQueryService.asLong(r.get("CREATE_TIME"))).reversed());
        if (rows.size() > cap) rows = rows.subList(0, cap);
        return Map.of("rows", rows, "count", rows.size());
    }

    /**
     * 消息趋势（仪表盘）：跨所有 bot 聚合近 days 天（1~30）的单聊/群聊/总消息量，按天补齐。
     * 返回 [{date, c2c, group, total}, ...] 从最早一天到今天。
     */
    @GetMapping("/message-trend")
    public Map<String, Object> messageTrend(@RequestParam(defaultValue = "7") int days,
                                            @RequestParam(defaultValue = "") String bot) {
        int dd = Math.min(Math.max(days, 1), 30);
        long since = LocalDate.now().minusDays(dd - 1L).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        java.util.TreeMap<String, long[]> byDay = new java.util.TreeMap<>(); // date -> [c2c, group]
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> row : p.messageTrend(ref.instanceId(), since)) {
                String day = ConsoleQueryService.str(row.get("D"));
                String ct = String.valueOf(row.getOrDefault("CHAT_TYPE", ""));
                if (day == null) continue;
                long[] acc = byDay.computeIfAbsent(day, k -> new long[2]);
                long cnt = ((Number) row.getOrDefault("CNT", 0L)).longValue();
                if (PlatformDataProvider.CHAT_C2C.equalsIgnoreCase(ct)) acc[0] += cnt;
                else acc[1] += cnt; // group / 其他一律归群聊
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(dd - 1L);
        for (int i = 0; i < dd; i++) {
            LocalDate day = start.plusDays(i);
            long[] acc = byDay.get(day.toString());
            long c2c = acc == null ? 0 : acc[0];
            long g = acc == null ? 0 : acc[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("c2c", c2c);
            m.put("group", g);
            m.put("total", c2c + g);
            out.add(m);
        }
        return Map.of("rows", out, "days", dd);
    }

    /** 跨所有 bot 实例库聚合消息流水，按时间倒序合并后内存分页。 */
    private Map<String, Object> pagedMessages(String chatType, String botFilter, int page, int size) {
        int pageSize = size <= 0 ? 50 : Math.min(size, 500);
        int pageNo = Math.max(page, 0);
        int fetch = pageSize * (pageNo + 1);

        List<Map<String, Object>> all = new ArrayList<>();
        long total = 0;
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!botFilter.isBlank() && !botFilter.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            total += p.countMessagesSince(ref.instanceId(), chatType, 0L);
            for (Map<String, Object> row : p.listMessages(ref.instanceId(), chatType, fetch)) {
                Map<String, Object> r = new LinkedHashMap<>(row);
                r.put("BOT_APPID", ref.instanceId());
                all.add(r);
            }
        }
        all.sort(Comparator.comparingLong((Map<String, Object> r) -> ConsoleQueryService.asLong(r.get("CREATE_TIME"))).reversed());
        List<Map<String, Object>> rows = all.stream()
                .skip((long) pageNo * pageSize)
                .limit(pageSize)
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", rows);
        m.put("total", total);
        m.put("page", pageNo);
        m.put("size", pageSize);
        return m;
    }
}
