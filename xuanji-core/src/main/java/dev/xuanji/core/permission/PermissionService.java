package dev.xuanji.core.permission;

import dev.xuanji.core.config.XuanjiRobotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限裁决服务（v3.3 等级制，2026-08-05）。
 *
 * <p>唯一真相源 {@link #getLevel(botKey, groupId, userId, senderRole)}：
 * <pre>NONE(0) &lt; BLACKLIST(1) &lt; MEMBER(2) &lt; ADMIN(3) &lt; GROUP_OWNER(4) &lt; BOT_MASTER(5)</pre>
 * 一律用 {@link PermissionLevel#rank()} 数值比较，不做字符串精确匹配。
 *
 * <p>主人表 {@code xuanji_bot_owner}（bot_key 主键，每 bot 唯一）；黑名单表
 * {@code xuanji_blacklist}（UNIQUE(bot_key, group_id, user_id)）。
 * 群主/管理/成员来自事件 {@code senderRole}（小写 owner/admin/member）。
 */
@Slf4j
@Component
public class PermissionService {

    private final JdbcTemplate jdbc;
    private final XuanjiRobotProperties props;

    public PermissionService(JdbcTemplate jdbc, XuanjiRobotProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    // ==================== 等级裁决 ====================

    /**
     * 唯一真相源：计算用户在 (botKey, groupId) 的权限等级。
     *
     * <p>黑名单一票否决 → 主人 = BOT_MASTER → 事件角色映射（owner/admin/member 小写）。
     */
    public PermissionLevel getLevel(String botKey, String groupId, String userId, String senderRole) {
        if (isBlacklisted(botKey, groupId, userId)) return PermissionLevel.BLACKLIST;
        if (isMaster(botKey, userId)) return PermissionLevel.BOT_MASTER;
        String role = senderRole == null ? "" : senderRole.toLowerCase();
        if ("owner".equals(role)) return PermissionLevel.GROUP_OWNER;
        if ("admin".equals(role)) return PermissionLevel.ADMIN;
        return PermissionLevel.MEMBER;
    }

    /** 全局闸门：黑名单否决 / 主人放行 / 要求 BOT_MASTER 时校验（命令级等级比较在 CommandRegistry）。 */
    public boolean check(String botKey, String groupId, String userId, String requiredRole) {
        PermissionLevel level = getLevel(botKey, groupId, userId, null);
        if (level == PermissionLevel.BLACKLIST) return false;
        if (level == PermissionLevel.BOT_MASTER) return true;
        if ("BOT_MASTER".equals(requiredRole) || "SUPER_ADMIN".equals(requiredRole)) {
            return false;
        }
        return true;
    }

    // ==================== 主人（xuanji_bot_owner） ====================

    public boolean isMaster(String botKey, String memberOpenid) {
        if (memberOpenid == null || memberOpenid.isBlank()) return false;
        String owner = getOwner(botKey);
        if (owner != null && !owner.isBlank()) return owner.equals(memberOpenid);
        // 兼容旧配置：xuanji.master.botKey
        Map<String, String> masterMap = props.getMaster() != null ? props.getMaster() : Collections.emptyMap();
        String expected = masterMap.get(botKey);
        return expected != null && expected.equals(memberOpenid);
    }

    /** 查询机器人主人 openid（无则 null）。 */
    public String getOwner(String botKey) {
        try {
            List<String> rows = jdbc.query(
                    "SELECT owner_openid FROM xuanji_bot_owner WHERE bot_key=?",
                    (rs, i) -> rs.getString("owner_openid"), botKey);
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (Exception e) {
            log.debug("[权限] 读主人失败（表未就绪？）: {}", e.getMessage());
            return null;
        }
    }

    public void setOwner(String botKey, String ownerOpenid) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_bot_owner (bot_key, owner_openid)
                KEY (bot_key) VALUES (?, ?)
            """, botKey, ownerOpenid);
            log.info("[权限] 设置主人: botKey={}, owner={}", botKey, ownerOpenid);
        } catch (Exception e) {
            log.warn("[权限] 设置主人失败: {}", e.getMessage());
        }
    }

    public void clearOwner(String botKey) {
        jdbc.update("DELETE FROM xuanji_bot_owner WHERE bot_key=?", botKey);
    }

    // ==================== 黑名单（xuanji_blacklist） ====================

    /** 是否命中黑名单（groupId 为空时按全局黑名单判定）。 */
    public boolean isBlacklisted(String botKey, String groupId, String userId) {
        if (userId == null || userId.isBlank()) return false;
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM xuanji_blacklist WHERE user_id=? AND (bot_key=? OR bot_key='*')");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(botKey);
        if (groupId != null && !groupId.isBlank()) {
            sql.append(" AND (group_id=? OR group_id IS NULL OR group_id='')");
            args.add(groupId);
        }
        try {
            Integer c = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
            return c != null && c > 0;
        } catch (Exception e) {
            log.debug("[权限] 黑名单查询失败（表未就绪？）: {}", e.getMessage());
            return false;
        }
    }

    /** 列出某 bot 黑名单（可按群过滤）。 */
    public List<Map<String, Object>> listBlacklist(String botKey, String groupId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, bot_key, group_id, user_id, reason, create_time FROM xuanji_blacklist WHERE bot_key=?");
            List<Object> args = new ArrayList<>();
            args.add(botKey);
            if (groupId != null && !groupId.isBlank()) {
                sql.append(" AND group_id=?");
                args.add(groupId);
            }
            sql.append(" ORDER BY id DESC");
            jdbc.query(sql.toString(), rs -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("botKey", rs.getString("bot_key"));
                row.put("groupId", rs.getString("group_id"));
                row.put("userId", rs.getString("user_id"));
                row.put("reason", rs.getString("reason"));
                row.put("createTime", rs.getObject("create_time") != null ? rs.getObject("create_time").toString() : "");
                out.add(row);
            }, args.toArray());
        } catch (Exception e) {
            log.debug("[权限] 黑名单列表失败（表未就绪？）: {}", e.getMessage());
        }
        return out;
    }

    public void addBlacklist(String botKey, String groupId, String userId, String reason) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_blacklist (bot_key, group_id, user_id, reason)
                KEY (bot_key, group_id, user_id) VALUES (?, ?, ?, ?)
            """, botKey, groupId, userId, reason == null ? "" : reason);
            log.info("[权限] 拉黑: bot={}, group={}, user={}", botKey, groupId, userId);
        } catch (Exception e) {
            log.warn("[权限] 拉黑失败: {}", e.getMessage());
        }
    }

    public void removeBlacklist(String botKey, String groupId, String userId) {
        jdbc.update("DELETE FROM xuanji_blacklist WHERE bot_key=? AND group_id=? AND user_id=?",
                botKey, groupId, userId);
    }

    public void removeBlacklistById(long id) {
        jdbc.update("DELETE FROM xuanji_blacklist WHERE id=?", id);
    }

    // ==================== 群超管（兼容旧接口，v3.3 不再使用） ====================

    public boolean isSuperAdmin(String botKey, String groupOpenid, String memberOpenid) {
        return false;
    }

    public void addSuperAdmin(String botKey, String groupOpenid, String memberOpenid) {
        // v3.3 超管并入 BOT_MASTER / 群主体系，原 xuanji_super_admin 表不再维护
    }
}
