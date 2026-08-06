package dev.xuanji.console.controller;

import dev.xuanji.console.service.ConsoleQueryService;
import dev.xuanji.core.storage.PlatformDataProvider;
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
@RestController
@RequestMapping("/xuanji/api/console")
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

    /** 单个会话的消息记录（实时监控右侧聊天窗）。type=group|c2c。返回 qqbot_message 原表全部字段 + BOT_APPID。 */
    @GetMapping("/contact-messages")
    public List<Map<String, Object>> contactMessages(@RequestParam String type,
                                                     @RequestParam String targetId) {
        String chatType = "group".equalsIgnoreCase(type)
                ? PlatformDataProvider.CHAT_GROUP : PlatformDataProvider.CHAT_C2C;
        List<Map<String, Object>> all = new ArrayList<>();
        for (ConsoleQueryService.BotRef ref : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(ref.platform());
            if (p == null) continue;
            for (Map<String, Object> row : p.listMessagesByTarget(ref.instanceId(), chatType, targetId, 200)) {
                Map<String, Object> r = new LinkedHashMap<>(row);
                r.put("BOT_APPID", ref.instanceId());
                all.add(r);
            }
        }
        all.sort(Comparator.comparingLong((Map<String, Object> r) -> ConsoleQueryService.asLong(r.get("CREATE_TIME"))).reversed());
        return all;
    }
}
