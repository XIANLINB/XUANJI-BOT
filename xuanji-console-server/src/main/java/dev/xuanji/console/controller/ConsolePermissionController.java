package dev.xuanji.console.controller;

import dev.xuanji.console.service.AuditService;
import dev.xuanji.core.permission.PermissionService;
import dev.xuanji.core.web.XuanjiApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 权限管理控制台接口（实际暴露前缀 {@code /xuanji/api/v1/console/permission}）。
 *
 * <p>管理机器人主人（每 bot 唯一）与群黑名单（每群可多名）。两者均持久化于框架库。
 */
@XuanjiApi
@RestController
@RequestMapping("/console/permission")
@RequiredArgsConstructor
public class ConsolePermissionController {

    private final PermissionService permissionService;
    private final AuditService auditService;

    // ==================== 主人（BOT_MASTER） ====================

    @GetMapping("/owner")
    public Map<String, Object> getOwner(@RequestParam String botKey) {
        String owner = permissionService.getOwner(botKey);
        return Map.of("botKey", botKey, "ownerOpenid", owner == null ? "" : owner);
    }

    @PostMapping("/owner")
    public Map<String, String> setOwner(@RequestParam String botKey, @RequestParam String ownerOpenid,
                                        HttpServletRequest req) {
        permissionService.setOwner(botKey, ownerOpenid);
        auditService.record("BOT_OWNER_SET", "bot=" + botKey + " 设置主人 openid=" + ownerOpenid, req);
        return Map.of("status", "ok");
    }

    @DeleteMapping("/owner")
    public Map<String, String> clearOwner(@RequestParam String botKey, HttpServletRequest req) {
        permissionService.clearOwner(botKey);
        auditService.record("BOT_OWNER_CLEAR", "bot=" + botKey + " 清除主人", req);
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
                                            @RequestParam(defaultValue = "") String reason,
                                            HttpServletRequest req) {
        permissionService.addBlacklist(botKey, groupId, userId, reason);
        auditService.record("BLACKLIST_ADD",
                "bot=" + botKey + (groupId.isBlank() ? "" : " group=" + groupId)
                        + " 拉黑 user=" + userId + (reason.isBlank() ? "" : " 原因=" + reason), req);
        return Map.of("status", "ok");
    }

    @DeleteMapping("/blacklist")
    public Map<String, String> removeBlacklist(@RequestParam(required = false, defaultValue = "0") long id,
                                               @RequestParam(required = false) String botKey,
                                               @RequestParam(required = false) String groupId,
                                               @RequestParam(required = false) String userId,
                                               HttpServletRequest req) {
        if (id > 0) {
            permissionService.removeBlacklistById(id);
        } else if (botKey != null && groupId != null && userId != null) {
            permissionService.removeBlacklist(botKey, groupId, userId);
        }
        auditService.record("BLACKLIST_REMOVE",
                "id=" + id + " bot=" + (botKey == null ? "" : botKey)
                        + " group=" + (groupId == null ? "" : groupId)
                        + " user=" + (userId == null ? "" : userId), req);
        return Map.of("status", "ok");
    }
}
