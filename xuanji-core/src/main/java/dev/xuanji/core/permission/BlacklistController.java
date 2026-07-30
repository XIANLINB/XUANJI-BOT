package dev.xuanji.core.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 黑名单管理接口。
 */
@RestController
@RequestMapping("/xuanji/api/permission")
@RequiredArgsConstructor
public class BlacklistController {

    private final PermissionService permissionService;

    @PostMapping("/blacklist/add")
    public Map<String, String> addBlacklist(@RequestParam String scope,
                                             @RequestParam String targetType,
                                             @RequestParam String targetId,
                                             @RequestParam(defaultValue = "") String reason) {
        permissionService.addBlacklist(scope, targetType, targetId, reason);
        return Map.of("status", "ok");
    }

    @PostMapping("/super-admin/add")
    public Map<String, String> addSuperAdmin(@RequestParam String botKey,
                                              @RequestParam String groupId,
                                              @RequestParam String memberOpenid) {
        permissionService.addSuperAdmin(botKey, groupId, memberOpenid);
        return Map.of("status", "ok");
    }

    @GetMapping("/blacklist/check")
    public Map<String, Object> checkBlacklist(@RequestParam String botKey,
                                               @RequestParam String groupId,
                                               @RequestParam String memberOpenid) {
        return Map.of("blacklisted", permissionService.isBlacklisted(botKey, groupId, memberOpenid));
    }

    @GetMapping("/master")
    public Map<String, Object> master(@RequestParam String botKey,
                                       @RequestParam String memberOpenid) {
        return Map.of("isMaster", permissionService.isMaster(botKey, memberOpenid));
    }
}
