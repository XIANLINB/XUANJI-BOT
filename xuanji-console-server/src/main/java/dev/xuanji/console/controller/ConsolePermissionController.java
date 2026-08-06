package dev.xuanji.console.controller;

import dev.xuanji.core.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 权限管理控制台接口（前缀 {@code /xuanji/api/console/permission}）。
 *
 * <p>管理机器人主人（每 bot 唯一）与群黑名单（每群可多名）。两者均持久化于框架库。
 */
@RestController
@RequestMapping("/xuanji/api/console/permission")
@RequiredArgsConstructor
public class ConsolePermissionController {

    private final PermissionService permissionService;

    // ==================== 主人（BOT_MASTER） ====================

    @GetMapping("/owner")
    public Map<String, Object> getOwner(@RequestParam String botKey) {
        String owner = permissionService.getOwner(botKey);
        return Map.of("botKey", botKey, "ownerOpenid", owner == null ? "" : owner);
    }

    @PostMapping("/owner")
    public Map<String, String> setOwner(@RequestParam String botKey, @RequestParam String ownerOpenid) {
        permissionService.setOwner(botKey, ownerOpenid);
        return Map.of("status", "ok");
    }

    @DeleteMapping("/owner")
    public Map<String, String> clearOwner(@RequestParam String botKey) {
        permissionService.clearOwner(botKey);
        return Map.of("status", "ok");
    }

    // ==================== 黑名单（BLACKLIST） ====================

    @GetMapping("/blacklist")
    public List<Map<String, Object>> listBlacklist(@RequestParam String botKey,
                                                    @RequestParam(required = false) String groupId) {
        return permissionService.listBlacklist(botKey, groupId);
    }

    @PostMapping("/blacklist")
    public Map<String, String> addBlacklist(@RequestParam String botKey,
                                            @RequestParam(required = false, defaultValue = "") String groupId,
                                            @RequestParam String userId,
                                            @RequestParam(defaultValue = "") String reason) {
        permissionService.addBlacklist(botKey, groupId, userId, reason);
        return Map.of("status", "ok");
    }

    @DeleteMapping("/blacklist")
    public Map<String, String> removeBlacklist(@RequestParam(required = false, defaultValue = "0") long id,
                                               @RequestParam(required = false) String botKey,
                                               @RequestParam(required = false) String groupId,
                                               @RequestParam(required = false) String userId) {
        if (id > 0) {
            permissionService.removeBlacklistById(id);
        } else if (botKey != null && groupId != null && userId != null) {
            permissionService.removeBlacklist(botKey, groupId, userId);
        }
        return Map.of("status", "ok");
    }
}
