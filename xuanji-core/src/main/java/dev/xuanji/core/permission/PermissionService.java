package dev.xuanji.core.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 权限裁决服务 — 五层优先级链。
 *
 * <p>裁决顺序：L4 黑名单（命中即拒）→ L0 主人/超管（直接放行）→ 放行。
 */
@Slf4j
@Component
public class PermissionService {

    private final JdbcTemplate jdbc;

    /** 每个 bot 的主人 member_openid（来自 xuanji.master.botKey） */
    private final Map<String, String> masterMap;

    public PermissionService(JdbcTemplate jdbc,
            @Value("#{${xuanji.master:{}}}") Map<String, String> masterMap) {
        this.jdbc = jdbc;
        this.masterMap = masterMap != null ? masterMap : Collections.emptyMap();
        log.info("[权限] 主人配置: {}", this.masterMap);
    }

    // ==================== L0 主人 ====================

    public boolean isMaster(String botKey, String memberOpenid) {
        if (memberOpenid == null || memberOpenid.isEmpty()) return false;
        String expected = masterMap.get(botKey);
        return expected != null && expected.equals(memberOpenid);
    }

    // ==================== 群超管 ====================

    public boolean isSuperAdmin(String botKey, String groupOpenid, String memberOpenid) {
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM xuanji_super_admin WHERE bot_key=? AND group_id=? AND member_openid=?",
            Integer.class, botKey, groupOpenid, memberOpenid);
        return count > 0;
    }

    public void addSuperAdmin(String botKey, String groupOpenid, String memberOpenid) {
        jdbc.update("""
            MERGE INTO xuanji_super_admin (bot_key, group_id, member_openid)
            KEY (bot_key, group_id, member_openid) VALUES (?, ?, ?)
        """, botKey, groupOpenid, memberOpenid);
        log.info("[超管] 已添加: bot={}, group={}, user={}", botKey, groupOpenid, memberOpenid);
    }

    // ==================== L4 黑名单 ====================

    public boolean isBlacklisted(String botKey, String groupOpenid, String memberOpenid) {
        String sql = """
            SELECT COUNT(*) FROM xuanji_blacklist
            WHERE target_id = ? AND target_type = 'user'
              AND (expires_at IS NULL OR expires_at > ?)
              AND (scope = 'framework'
                   OR scope = ?
                   OR scope = ?)
        """;
        int count = jdbc.queryForObject(sql, Integer.class,
                memberOpenid, Instant.now(),
                "bot:" + botKey,
                "group:" + botKey + ":" + groupOpenid);
        return count > 0;
    }

    public void addBlacklist(String scope, String targetType, String targetId, String reason) {
        jdbc.update("""
            INSERT INTO xuanji_blacklist (scope, target_type, target_id, reason, created_by)
            VALUES (?, ?, ?, ?, ?)
        """, scope, targetType, targetId, reason, "cmd");
        log.info("[黑名单] scope={}, target={}, id={}", scope, targetType, targetId);
    }

    // ==================== 综合裁决 ====================

    /** 全面检查权限 */
    public boolean check(String botKey, String groupOpenid, String memberOpenid, String requiredRole) {
        if (memberOpenid == null || memberOpenid.isEmpty()) return false;

        // L4: 黑名单一票否决（主人/超管免黑名单）
        if (!isMaster(botKey, memberOpenid) && !isSuperAdmin(botKey, groupOpenid, memberOpenid)) {
            if (isBlacklisted(botKey, groupOpenid, memberOpenid)) {
                log.info("[权限] 黑名单拦截: bot={}, user={}", botKey, memberOpenid);
                return false;
            }
        }

        // L0: 主人/超管直接放行
        if (isMaster(botKey, memberOpenid)) return true;
        if (isSuperAdmin(botKey, groupOpenid, memberOpenid)) return true;

        // 需要主人/超管权限的操作
        if ("BOT_MASTER".equals(requiredRole) || "SUPER_ADMIN".equals(requiredRole)) {
            return false;
        }

        return true; // L1-L3 待后续
    }
}
