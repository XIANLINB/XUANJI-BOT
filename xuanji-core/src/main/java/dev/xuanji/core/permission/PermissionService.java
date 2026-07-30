package dev.xuanji.core.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 权限裁决服务 — 五层优先级链。
 *
 * <p>裁决顺序：L4 黑名单（命中即拒）→ L0 主人/超管（直接放行）→ 放行。
 *
 * <p>L1 平台角色 + L3 权限点待后续迭代实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionService {

    private final JdbcTemplate jdbc;

    /** 机器人主人 ID（环境变量 XUANJI_MASTER 或 application.yml xuanji.master） */
    @Value("${xuanji.master:#{null}}")
    private String masterUserId;

    // ==================== L4 黑名单 ====================

    public boolean isBlacklisted(String platform, String userId, String groupId) {
        String sql = """
            SELECT COUNT(*) FROM xuanji_blacklist
            WHERE target_id = ?
              AND target_type = 'user'
              AND (expires_at IS NULL OR expires_at > ?)
              AND (scope = 'framework'
                   OR scope = ?
                   OR (scope = 'group' AND scope LIKE ?))
        """;
        int count = jdbc.queryForObject(sql, Integer.class,
                userId, Instant.now(),
                "bot:" + platform,
                "group:" + groupId + "%");
        return count > 0;
    }

    public void addBlacklist(String scope, String targetType, String targetId, String reason, String createdBy) {
        jdbc.update("""
            INSERT INTO xuanji_blacklist (scope, target_type, target_id, reason, created_by)
            VALUES (?, ?, ?, ?, ?)
        """, scope, targetType, targetId, reason, createdBy);
        log.info("[黑名单] 已添加: scope={}, target={}, id={}", scope, targetType, targetId);
    }

    // ==================== L0 主人/超管 ====================

    public boolean isMaster(String userId) {
        if (masterUserId == null || masterUserId.isEmpty()) return false;
        return masterUserId.equals(userId);
    }

    public String getMasterUserId() {
        return masterUserId;
    }

    // ==================== 综合裁决 ====================

    /**
     * 检查用户是否可以执行操作。
     * @return true=允许，false=拒绝
     */
    public boolean check(String platform, String userId, String groupId, String requiredRole) {
        // L4: 黑名单一票否决
        if (isBlacklisted(platform, userId, groupId)) {
            log.info("[权限] 黑名单拦截: user={}", userId);
            return false;
        }

        // L0: 主人直接放行
        if ("BOT_MASTER".equals(requiredRole) || "SUPER_ADMIN".equals(requiredRole)) {
            return isMaster(userId);
        }

        if (isMaster(userId)) {
            return true;
        }

        // L1-L3: 暂放行（后续迭代）
        return true;
    }
}
