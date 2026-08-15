package XuanJi.console.controller;

import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 联系人 / 会话（群、好友、群成员、会话消息）。
 *
 * <p>跨所有 bot 实例库聚合，每行盖 {@code BOT_APPID}（真实 appId）章供前端区分；
 * 各行 {@code BOT_ID} 是 per-bot 库内局部自增 id，不能跨 bot 当身份用。
 */
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleContactController {

    private final ConsoleQueryService queryService;

    public ConsoleContactController(ConsoleQueryService queryService) {
        this.queryService = queryService;
    }

    /** 群列表（qqbot_group 原表全部字段，跨所有 bot 聚合）。 */
    @GetMapping("/contacts/groups")
    public List<Map<String, Object>> contactGroups() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> g : p.listGroups(ref.instanceId())) {
                Map<String, Object> row = new LinkedHashMap<>(g);
                row.put("BOT_APPID", ref.instanceId());
                list.add(row);
            }
        }
        return list;
    }

    /** 群列表分页（Q11）：page/size 分页 + bot 过滤 + 关键词（群号/群名）+ 是否含已删除。
     *  返回 { rows, total, notDeleted, deleted, memberSum }：汇总项仅受 bot 过滤影响（不受关键词/已删除开关影响）。 */
    @GetMapping("/contacts/groups-page")
    public Map<String, Object> groupsPage(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String bot,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "false") boolean showDeleted) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (bot != null && !bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> g : p.listGroups(ref.instanceId())) {
                Map<String, Object> row = new LinkedHashMap<>(g);
                row.put("BOT_APPID", ref.instanceId());
                all.add(row);
            }
        }
        // 汇总（仅受 bot 过滤影响）
        long notDeleted = 0, deleted = 0, memberSum = 0;
        for (Map<String, Object> g : all) {
            if (isDeleted(g)) deleted++;
            else {
                notDeleted++;
                memberSum += ConsoleQueryService.asLong(g.get("MEMBER_COUNT"));
            }
        }
        // 行过滤：已删除开关 + 关键词
        List<Map<String, Object>> rows = new ArrayList<>();
        String kw = q == null ? "" : q.trim().toLowerCase();
        for (Map<String, Object> g : all) {
            if (!showDeleted && isDeleted(g)) continue;
            if (!kw.isEmpty()) {
                String gid = ConsoleQueryService.strOrEmpty(g.get("GROUP_ID")).toLowerCase();
                String gname = ConsoleQueryService.strOrEmpty(g.get("GROUP_NAME")).toLowerCase();
                if (!gid.contains(kw) && !gname.contains(kw)) continue;
            }
            rows.add(g);
        }
        int total = rows.size();
        int sz = Math.min(Math.max(size, 1), 200);
        int pg = Math.max(page, 1);
        int from = (pg - 1) * sz;
        List<Map<String, Object>> slice = from >= total ? List.of()
                : new ArrayList<>(rows.subList(from, Math.min(from + sz, total)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", slice);
        m.put("total", total);
        m.put("notDeleted", notDeleted);
        m.put("deleted", deleted);
        m.put("memberSum", memberSum);
        return m;
    }

    private static boolean isDeleted(Map<String, Object> g) {
        return ConsoleQueryService.asLong(g.get("IS_DELETED")) == 1;
    }

    /** 某群内各机器人的群内状态（qqbot_group_robot，跨所有 bot 聚合，每行盖 BOT_APPID）。 */
    @GetMapping("/contacts/group-robot-states")
    public List<Map<String, Object>> groupRobotStates(@RequestParam String groupOpenid) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            list.addAll(p.listGroupRobotStates(groupOpenid));
        }
        return list;
    }

    /** 单聊用户列表（qqbot_user 原表全部字段，跨所有 bot 聚合）。 */
    @GetMapping("/contacts/friends")
    public List<Map<String, Object>> contactFriends() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> f : p.listFriends(ref.instanceId())) {
                Map<String, Object> row = new LinkedHashMap<>(f);
                row.put("BOT_APPID", ref.instanceId());
                list.add(row);
            }
        }
        return list;
    }

    /** 单聊用户列表分页：page/size 分页 + bot 过滤 + 关键词（用户ID/昵称）+ 是否含已删除。
     *  返回 { rows, total, notDeleted, deleted }。 */
    @GetMapping("/contacts/friends-page")
    public Map<String, Object> friendsPage(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String bot,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "false") boolean showDeleted) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (bot != null && !bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> f : p.listFriends(ref.instanceId())) {
                Map<String, Object> row = new LinkedHashMap<>(f);
                row.put("BOT_APPID", ref.instanceId());
                all.add(row);
            }
        }
        long notDeleted = 0, deleted = 0;
        for (Map<String, Object> f : all) {
            if (ConsoleQueryService.asLong(f.get("IS_DELETED")) == 1) deleted++; else notDeleted++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        String kw = q == null ? "" : q.trim().toLowerCase();
        for (Map<String, Object> f : all) {
            if (!showDeleted && ConsoleQueryService.asLong(f.get("IS_DELETED")) == 1) continue;
            if (!kw.isEmpty()) {
                String uid = ConsoleQueryService.strOrEmpty(f.get("PLATFORM_USER_ID")).toLowerCase();
                String nick = ConsoleQueryService.strOrEmpty(f.get("NICKNAME")).toLowerCase();
                if (!uid.contains(kw) && !nick.contains(kw)) continue;
            }
            rows.add(f);
        }
        int total = rows.size();
        int sz = Math.min(Math.max(size, 1), 200);
        int pg = Math.max(page, 1);
        int from = (pg - 1) * sz;
        List<Map<String, Object>> slice = from >= total ? List.of()
                : new ArrayList<>(rows.subList(from, Math.min(from + sz, total)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", slice);
        m.put("total", total);
        m.put("notDeleted", notDeleted);
        m.put("deleted", deleted);
        return m;
    }

    /** 某群成员列表（qqbot_group_member 原表全部字段）。同群号可能跨 bot，各行盖 BOT_APPID 章供前端区分。
     *  bot 可选：传 appId 则只查该机器人实例库（省流量）。 */
    @GetMapping("/contacts/group-members")
    public List<Map<String, Object>> groupMembers(@RequestParam String groupId,
                                                  @RequestParam(required = false) String bot) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            if (bot != null && !bot.isBlank() && !bot.equals(ref.instanceId())) continue;
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> m : p.listGroupMembers(ref.instanceId(), groupId)) {
                Map<String, Object> row = new LinkedHashMap<>(m);
                row.put("BOT_APPID", ref.instanceId());
                list.add(row);
            }
        }
        return list;
    }

    /**
     * 单个会话的消息记录（实时监控右侧聊天窗）。
     * <ul>
     *   <li>type=group|c2c（必填）</li>
     *   <li>targetId=群号/用户 openid（必填）</li>
     *   <li>startTime/endTime=时间范围 epoch 秒（可选；startTime 默认 0，endTime 默认 Long.MAX_VALUE）</li>
     *   <li>beforeTime=加载更早消息：create_time < beforeTime（可选，配合上滑加载更多）</li>
     *   <li>limit=本次最大条数（默认 100，上限 500；内部 +1 用于判断 hasMore）</li>
     * </ul>
     * 返回 {@code { rows: [...], hasMore: bool }}：rows 按 create_time 升序（早→晚），hasMore 表示还有更早消息。
     */
    @GetMapping("/contact-messages")
    public Map<String, Object> contactMessages(@RequestParam String type,
                                                @RequestParam String targetId,
                                                @RequestParam(required = false, defaultValue = "0") long startTime,
                                                @RequestParam(required = false, defaultValue = "0") long endTime,
                                                @RequestParam(required = false, defaultValue = "0") long beforeTime,
                                                @RequestParam(defaultValue = "100") int limit) {
        String chatType = "group".equalsIgnoreCase(type)
                ? PlatformDataProvider.CHAT_GROUP : PlatformDataProvider.CHAT_C2C;
        long since = startTime;
        long until = endTime > 0 ? endTime : Long.MAX_VALUE;
        long before = beforeTime > 0 ? beforeTime : Long.MAX_VALUE;
        int lim = Math.min(Math.max(limit, 1), 500);

        List<Map<String, Object>> all = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            all.addAll(p.listMessagesByTargetRange(ref.instanceId(), chatType, targetId, since, until, before, lim + 1));
        }
        // 单 session 不会有跨 bot 重叠，直接按时间排序
        all.sort(Comparator.comparingLong((Map<String, Object> r) -> ConsoleQueryService.asLong(r.get("CREATE_TIME"))));
        boolean hasMore = all.size() > lim;
        if (hasMore) all = all.subList(0, lim);
        return Map.of("rows", all, "hasMore", hasMore);
    }

    /**
     * 批量会话预览（Q4）：一次性返回多个会话各最新一条消息（预览/时间/类型），替代前端逐会话轮询。
     * 入参 { targets: [{ bot, type:'group'|'c2c', targetId }] }，
     * 返回 { previews: { "type:bot:targetId": { preview, previewTime, msgType } } }。
     */
    @PostMapping("/contact-previews")
    public Map<String, Object> contactPreviews(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> targets = body.get("targets") instanceof List
                ? (List<Map<String, Object>>) body.get("targets") : List.of();
        Map<String, Object> previews = new LinkedHashMap<>();
        for (Map<String, Object> t : targets) {
            String type = ConsoleQueryService.str(t.get("type"));
            String targetId = ConsoleQueryService.str(t.get("targetId"));
            String bot = ConsoleQueryService.str(t.get("bot"));
            if (targetId.isEmpty()) continue;
            String chatType = "group".equalsIgnoreCase(type) ? PlatformDataProvider.CHAT_GROUP : PlatformDataProvider.CHAT_C2C;
            String preview = "";
            long previewTime = 0;
            String lastType = "";
            for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
                if (!bot.isEmpty() && !bot.equals(ref.instanceId())) continue;
                PlatformDataProvider p = queryService.providerFor(ref.platform());
                if (p == null) continue;
                List<Map<String, Object>> rows = p.listMessagesByTargetRange(
                        ref.instanceId(), chatType, targetId, 0, Long.MAX_VALUE, Long.MAX_VALUE, 1);
                if (!rows.isEmpty()) {
                    Map<String, Object> m = rows.get(rows.size() - 1); // 倒序，最后一条=最新
                    preview = ConsoleQueryService.str(m.get("CONTENT") != null ? m.get("CONTENT") : m.get("content"));
                    previewTime = ConsoleQueryService.asLong(m.get("CREATE_TIME") != null ? m.get("CREATE_TIME") : m.get("create_time"));
                    lastType = ConsoleQueryService.str(m.get("MSG_TYPE") != null ? m.get("MSG_TYPE") : m.get("msg_type"));
                    break;
                }
            }
            String key = type + ":" + bot + ":" + targetId;
            Map<String, Object> pv = new LinkedHashMap<>();
            pv.put("preview", preview);
            pv.put("previewTime", previewTime);
            pv.put("msgType", lastType);
            previews.put(key, pv);
        }
        return Map.of("previews", previews);
    }
}
