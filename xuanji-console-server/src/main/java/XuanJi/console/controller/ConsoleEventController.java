package XuanJi.console.controller;

import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
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
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleEventController {

    /** 消息类事件类型（不属于「系统事件」）——系统事件页过滤掉，只展示加群/退群/加好友/成员变动等。 */
    private static final Set<String> MESSAGE_EVENT_TYPES = Set.of(
            "GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE", "C2C_MESSAGE_CREATE");

    private final ConsoleQueryService queryService;
    private final PlatformActionHub actionHub;

    public ConsoleEventController(ConsoleQueryService queryService, PlatformActionHub actionHub) {
        this.queryService = queryService;
        this.actionHub = actionHub;
    }

    /** 撤回群消息：机器人自己发的免角色校验；他人消息须机器人本群角色为 owner/admin（读持久化表）。 */
    @PostMapping("/group-messages/recall")
    public Map<String, Object> recallGroupMessage(@RequestBody Map<String, String> body) {
        String appId = body.get("appId");
        String groupOpenid = body.get("groupOpenid");
        String msgId = body.get("msgId");
        boolean isOwn = Boolean.parseBoolean(body.getOrDefault("isOwn", "false"));
        if (appId == null || appId.isBlank() || groupOpenid == null || groupOpenid.isBlank()
                || msgId == null || msgId.isBlank()) {
            return Map.of("error", "参数不完整（需要 appId / groupOpenid / msgId）");
        }
        // 1. 他人消息 → 校验机器人在本群角色（读持久化表，避免实时调接口）
        if (!isOwn) {
            String role = robotRole(appId, groupOpenid);
            if (!"owner".equalsIgnoreCase(role) && !"admin".equalsIgnoreCase(role)) {
                return Map.of("error", "机器人当前角色不是群管理员（member_role=" + role + "），无法撤回他人消息");
            }
        }
        // 2. 撤回（自己消息免角色校验；超 2 分钟/权限不足由平台返回错误透传）
        Map<String, Object> out = actionHub.dispatch(appId, PlatformActions.GROUP_RECALL,
                Map.of("groupOpenid", groupOpenid, "msgId", msgId));
        if (out != null && Boolean.TRUE.equals(out.get("ok"))) {
            // 3. 标记消息已撤回（前端按钮显示「已撤回」）
            markRetracted(appId, groupOpenid, msgId);
            return Map.of("status", "ok");
        }
        return Map.of("error", out == null ? "撤回无响应" : String.valueOf(out.get("error")));
    }

    /** 标记某群消息已撤回（跨所有平台 provider，retracted=1）。 */
    private void markRetracted(String appId, String groupOpenid, String msgId) {
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!ref.instanceId().equals(appId)) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            try {
                p.markMessageRetracted(ref.instanceId(), groupOpenid, msgId);
            } catch (Exception ignored) { }
        }
    }

    /** 从所有平台 provider 读机器人在某群的角色（读 qqbot_group_robot 持久化表）。 */
    private String robotRole(String appId, String groupOpenid) {
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            try {
                String role = p.getGroupRobotRole(ref.instanceId(), groupOpenid);
                if (role != null) return role;
            } catch (Exception ignored) { }
        }
        return null;
    }

    @GetMapping("/group-messages")
    public Map<String, Object> groupMessages(@RequestParam(defaultValue = "") String bot,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size,
                                             @RequestParam(required = false) String dir,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "0") long startTime,
                                             @RequestParam(defaultValue = "0") long endTime,
                                             @RequestParam(required = false) String q) {
        return pagedMessagesFiltered(PlatformDataProvider.CHAT_GROUP, bot, page, size, dir, type, startTime, endTime, q);
    }

    @GetMapping("/c2c-messages")
    public Map<String, Object> c2cMessages(@RequestParam(defaultValue = "") String bot,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size,
                                           @RequestParam(required = false) String dir,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(defaultValue = "0") long startTime,
                                           @RequestParam(defaultValue = "0") long endTime,
                                           @RequestParam(required = false) String q) {
        return pagedMessagesFiltered(PlatformDataProvider.CHAT_C2C, bot, page, size, dir, type, startTime, endTime, q);
    }

    /**
     * 系统事件流水（qqbot_event 原表全部字段），按 XuanJiBot 过滤、倒序返回。
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

    /** 服务端过滤 + 分页聚合（群/单聊消息页用）：dir/type/时间范围/关键词 下推 DB，返回 rows/total/ins/outs/typeDist。 */
    private Map<String, Object> pagedMessagesFiltered(String chatType, String botFilter, int page, int size,
                                                       String dir, String type, long startTime, long endTime, String q) {
        int pageSize = size <= 0 ? 50 : Math.min(size, 500);
        int pageNo = Math.max(page, 0);
        int fetch = pageSize * (pageNo + 1);
        List<Map<String, Object>> all = new ArrayList<>();
        long total = 0, ins = 0, outs = 0;
        Map<String, Long> typeCnt = new LinkedHashMap<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (!botFilter.isBlank() && !botFilter.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            Map<String, Object> r = p.queryMessagesFiltered(ref.instanceId(), chatType, dir, type, startTime, endTime, q, fetch);
            if (r == null || r.isEmpty()) continue;
            total += ConsoleQueryService.asLong(r.get("total"));
            for (Map<String, Object> row : castList(r.get("dirDist"))) {
                String d = ConsoleQueryService.str(row.get("D"));
                long c = ConsoleQueryService.asLong(row.get("CNT"));
                if ("IN".equalsIgnoreCase(d)) ins += c;
                else if ("OUT".equalsIgnoreCase(d)) outs += c;
            }
            for (Map<String, Object> row : castList(r.get("typeDist"))) {
                String t = ConsoleQueryService.str(row.get("T"));
                typeCnt.merge(t == null ? "unknown" : t, ConsoleQueryService.asLong(row.get("CNT")), Long::sum);
            }
            for (Map<String, Object> row : castList(r.get("rows"))) {
                Map<String, Object> rr = new LinkedHashMap<>(row);
                rr.put("BOT_APPID", ref.instanceId());
                all.add(rr);
            }
        }
        all.sort(Comparator.comparingLong((Map<String, Object> x) -> ConsoleQueryService.asLong(x.get("CREATE_TIME"))).reversed());
        List<Map<String, Object>> rows = all.stream()
                .skip((long) pageNo * pageSize)
                .limit(pageSize)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Map<String, Object>> typeDist = new ArrayList<>();
        typeCnt.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> typeDist.add(Map.of("type", e.getKey(), "cnt", e.getValue())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", rows);
        m.put("total", total);
        m.put("ins", ins);
        m.put("outs", outs);
        m.put("typeDist", typeDist);
        m.put("page", pageNo);
        m.put("pageSize", pageSize);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object v) {
        return v instanceof List ? (List<Map<String, Object>>) (List<?>) v : List.of();
    }
}
