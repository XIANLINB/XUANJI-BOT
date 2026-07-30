package dev.xuanji.core.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 黑名单管理接口。
 */
@RestController
@RequestMapping("/xuanji/api/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final PermissionService permissionService;

    @PostMapping("/add")
    public Map<String, String> add(@RequestParam String scope,
                                    @RequestParam String targetType,
                                    @RequestParam String targetId,
                                    @RequestParam(defaultValue = "") String reason) {
        permissionService.addBlacklist(scope, targetType, targetId, reason, "api");
        return Map.of("status", "ok");
    }

    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam String platform,
                                      @RequestParam String userId,
                                      @RequestParam(defaultValue = "") String groupId) {
        return Map.of("blacklisted", permissionService.isBlacklisted(platform, userId, groupId));
    }

    @GetMapping("/master")
    public Map<String, Object> master() {
        return Map.of("master", permissionService.getMasterUserId() != null
                ? permissionService.getMasterUserId() : "未设置");
    }
}
