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

    /** 某群成员列表（qqbot_group_member 原表全部字段）。同群号可能跨 bot，各行盖 BOT_APPID 章供前端区分。 */
    @GetMapping("/contacts/group-members")
    public List<Map<String, Object>> groupMembers(@RequestParam String groupId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
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
}
