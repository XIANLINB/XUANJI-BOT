package dev.xuanji.adapter.qqbot.storage;

import dev.xuanji.core.storage.BotDataSourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 平台两级库仓储（v3.2）。
 *
 * <h3>平台级共享库 {@code data/qqbot/qqbot.mv.db}（{@link #platformJdbc()}）</h3>
 * <ul>
 *   <li>{@code qqbot_bot} — 全部机器人档案合并成一张表，一 bot 一行，主键 id 全局唯一</li>
 *   <li>{@code qqbot_botinfo} — Bot 基础信息，物理 FK 关联 qqbot_bot（同库）</li>
 * </ul>
 *
 * <h3>per-bot 实例库 {@code data/qqbot/{appid}/...}（{@link #jdbc(String)}）</h3>
 * <ul>
 *   <li>qqbot_group / qqbot_group_member / qqbot_user / qqbot_message / qqbot_event，
 *       bot_id 逻辑关联平台库 qqbot_bot.id（跨库文件无物理 FK）</li>
 * </ul>
 *
 * <p>框架库 xuanji_bot 由 {@code FrameworkBotRepository} 负责。
 *
 * <h3>建档语义（重要）</h3>
 * <ul>
 *   <li>{@code ensureXxx} — 「有则不动，无则新建」。消息路径专用：收到消息时补建档案，
 *       但绝不覆盖已有的群名/昵称/加入时间等字段。</li>
 *   <li>{@code upsertXxx} — 「有则增量更新非空字段，无则新建」。加群/加好友事件与 API 回填专用，
 *       只更新调用方显式传入的非 null 字段，不会把已有值抹成 NULL。</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnQqbotEnabled
public class QqBotRepository {

    private final BotDataSourceRegistry dataSourceRegistry;
    private final QqBotSchemaProvider schemaProvider;

    /** appId → qqbot_bot.id 的解析缓存（避免每次写都查一次）。 */
    private final Map<String, Long> botIdCache = new ConcurrentHashMap<>();

    // ==================== qqbot_bot / qqbot_botinfo ====================

    /** 注册/刷新 qqbot_bot（平台级共享库 data/qqbot/qqbot.mv.db）。webhookUrl 为 null 时不写该列（避免覆盖已有）。 */
    public void upsertBot(String appId, String clientSecret, String connMode, boolean isSandbox, String status) {
        upsertBot(appId, clientSecret, connMode, isSandbox, status, null);
    }

    /** 注册/刷新 qqbot_bot（平台级共享库）。webhookUrl 仅 webhook 模式有值，可为 null（不覆盖已有）。 */
    public void upsertBot(String appId, String clientSecret, String connMode, boolean isSandbox,
                          String status, String webhookUrl) {
        JdbcTemplate jdbc = platformJdbc();
        if (webhookUrl != null) {
            jdbc.update("""
                MERGE INTO qqbot_bot (bot_appid, bot_clientSecret, conn_mode, is_sandbox, status, webhook_url)
                KEY (bot_appid) VALUES (?, ?, ?, ?, ?, ?)
            """, appId, clientSecret, connMode, isSandbox ? 1 : 0, status, webhookUrl);
        } else {
            jdbc.update("""
                MERGE INTO qqbot_bot (bot_appid, bot_clientSecret, conn_mode, is_sandbox, status)
                KEY (bot_appid) VALUES (?, ?, ?, ?, ?)
            """, appId, clientSecret, connMode, isSandbox ? 1 : 0, status);
        }
    }

    /** 读取 webhook 回调域名（平台库 qqbot_bot.webhook_url），无则返回 null。 */
    public String getWebhookUrl(String appId) {
        try {
            return platformJdbc().queryForObject(
                    "SELECT webhook_url FROM qqbot_bot WHERE bot_appid=?", String.class, appId);
        } catch (DataAccessException e) {
            return null;
        }
    }

    /** 同步 Bot 信息（/users/@me）到平台库 qqbot_botinfo。 */
    public void upsertBotInfo(String appId, String botId, String name, String avatar,
                              Boolean isBot, String unionOpenid, String shareUrl, String welcomeMsg) {
        try {
            JdbcTemplate jdbc = platformJdbc();
            Long botFk = jdbc.queryForObject("SELECT id FROM qqbot_bot WHERE bot_appid=?", Long.class, appId);
            jdbc.update("""
                MERGE INTO qqbot_botinfo (botid, bot_id, name, avatar, is_bot, union_openid, share_url, welcome_msg)
                KEY (botid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, botFk, botId, name, avatar, isBot, unionOpenid, shareUrl, welcomeMsg);
        } catch (DataAccessException e) {
            log.warn("同步 botinfo 失败: appId={}, {}", appId, e.getMessage());
        }
    }

    /** 读取指定 bot 的基础信息（平台库 qqbot_botinfo），降级返回空 Map。 */
    public Map<String, Object> getBotInfo(String appId) {
        try {
            return platformJdbc().queryForMap("""
                SELECT i.bot_id, i.name, i.avatar, i.is_bot, i.union_openid, i.share_url, i.welcome_msg
                FROM qqbot_botinfo i
                JOIN qqbot_bot b ON b.id = i.botid WHERE b.bot_appid = ?
            """, appId);
        } catch (DataAccessException e) {
            log.debug("读取 botinfo 失败（可能尚未同步）: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 更新平台库 qqbot_bot.conn_mode（控制台切换连接方式时用）。 */
    public void updateConnMode(String appId, String mode) {
        try {
            int n = platformJdbc().update("UPDATE qqbot_bot SET CONN_MODE=? WHERE bot_appid=?", mode, appId);
            log.info("[QqRepo] 更新 conn_mode: appId={}, mode={}, 影响行={}", appId, mode, n);
        } catch (DataAccessException e) {
            log.warn("更新 conn_mode 失败: appId={}, {}", appId, e.getMessage());
        }
    }

    /**
     * 消息统计（按时段 + 入站/出站方向聚合）。
     *
     * @param appId   机器人 AppID
     * @param sinceStart 开始时间戳（epoch 秒，含）
     * @param untilEnd   结束时间戳（epoch 秒，不含；MAX_VALUE 表示到当前）
     * @return {"in": count, "out": count}
     */
    public Map<String, Object> messageDirectionStats(String appId, long sinceStart, long untilEnd) {
        try {
            // 注意：qqbot_message 在 per-bot 实例库（jdbc(appId)），不能用 platformJdbc()（共享库只有 qqbot_bot/qqbot_botinfo）！
            List<Map<String, Object>> rows = jdbc(appId).queryForList("""
                SELECT DIRECTION, COUNT(*) AS CNT
                FROM qqbot_message
                WHERE CREATE_TIME >= ? AND CREATE_TIME < ?
                GROUP BY DIRECTION
            """, sinceStart, untilEnd);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("in", 0);
            r.put("out", 0);
            for (Map<String, Object> row : rows) {
                String d = String.valueOf(row.getOrDefault("DIRECTION", "")).toUpperCase();
                long c = ((Number) row.getOrDefault("CNT", 0)).longValue();
                if ("OUT".equals(d)) r.put("out", c);
                else if ("IN".equals(d)) r.put("in", c);
            }
            return r;
        } catch (Exception e) {
            log.debug("消息方向统计失败: {}", e.getMessage());
            return java.util.Map.of("in", 0, "out", 0);
        }
    }

    /**
     * 枚举全部已注册 QQ 机器人 appId。
     *
     * <p>数据源：平台级共享库 {@code qqbot_bot} 表（取代 xuanji-robots.yml 与目录扫描），
     * 未软删的行即已注册实例。表不存在/读失败时返回空列表。</p>
     */
    public List<String> listInstanceIds() {
        try {
            return platformJdbc().queryForList(
                    "SELECT bot_appid FROM qqbot_bot WHERE is_deleted=0 ORDER BY bot_appid", String.class);
        } catch (DataAccessException e) {
            log.debug("[QQ] 枚举 qqbot_bot 失败（可能尚无注册）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 读取平台库 qqbot_bot 整行配置（appId / clientSecret / conn_mode / is_sandbox / status / webhook_url）。
     * 表不存在或无记录时返回空 Map。
     */
    public Map<String, Object> getBotRow(String appId) {
        try {
            List<Map<String, Object>> rows = platformJdbc().queryForList("""
                SELECT bot_appid, bot_clientSecret, conn_mode, is_sandbox, status, webhook_url
                FROM qqbot_bot WHERE bot_appid = ?
            """, appId);
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } catch (DataAccessException e) {
            log.debug("[QQ] 读取 qqbot_bot({}) 失败: {}", appId, e.getMessage());
            return Map.of();
        }
    }

    /** 枚举全部实例并读取各自 qqbot_bot 行，返回 appId → 配置行（跳过读不到的实例）。 */
    public Map<String, Map<String, Object>> loadAllBotRows() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String appId : listInstanceIds()) {
            Map<String, Object> row = getBotRow(appId);
            if (!row.isEmpty()) result.put(appId, row);
        }
        return result;
    }

    /** 更新平台库 qqbot_bot.status（不改其它列）。 */
    public void updateBotStatus(String appId, String status) {
        try {
            platformJdbc().update("UPDATE qqbot_bot SET status = ? WHERE bot_appid = ?", status, appId);
        } catch (DataAccessException e) {
            log.debug("[QQ] 更新 status({}) 失败: {}", appId, e.getMessage());
        }
    }

    /** 彻底删除平台库中该 bot 的档案（qqbot_botinfo + qqbot_bot 行），并清理解析缓存。 */
    public void deleteBot(String appId) {
        botIdCache.remove(appId);
        // 数据目录会被删除重建：内存「已建表」标记失效，重新添加后须重新 initSchema
        instanceSchemaReady.remove(appId);
        try {
            JdbcTemplate jdbc = platformJdbc();
            jdbc.update("""
                DELETE FROM qqbot_botinfo WHERE botid IN (SELECT id FROM qqbot_bot WHERE bot_appid=?)
            """, appId);
            jdbc.update("DELETE FROM qqbot_bot WHERE bot_appid=?", appId);
        } catch (DataAccessException e) {
            log.warn("[QQ] 删除平台库档案失败 appId={}: {}", appId, e.getMessage());
        }
    }

    // ==================== qqbot_group ====================

    /**
     * 收到群消息时补建群档案：<b>已存在则完全不动</b>（不覆盖群名/群主/成员数，也不复活软删）。
     *
     * @return true 表示本次真的新建了一条
     */
    public boolean ensureGroup(String appId, String groupId) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(groupId)) return false;
        JdbcTemplate jdbc = jdbc(appId);
        if (exists(jdbc, "SELECT COUNT(*) FROM qqbot_group WHERE bot_id=? AND group_id=?", botId, groupId)) {
            return false;
        }
        try {
            jdbc.update("""
                INSERT INTO qqbot_group (bot_id, group_id, status, is_deleted) VALUES (?, ?, 'active', 0)
            """, botId, groupId);
            return true;
        } catch (DataAccessException e) {
            // 并发下唯一键冲突 → 视为已存在
            return false;
        }
    }

    /** 插入/增量更新群档案：只写入非 null 字段，已有值不会被抹成 NULL。 */
    public void upsertGroup(String appId, String groupId, String groupName, String ownerId,
                            Integer memberCount, Long joinTime, String status) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(groupId)) return;
        ensureGroup(appId, groupId);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "group_name", groupName);
        addSet(sets, args, "owner_id", ownerId);
        addSet(sets, args, "member_count", memberCount);
        addSet(sets, args, "join_time", joinTime);
        addSet(sets, args, "status", status);
        sets.add("is_deleted=0");

        args.add(botId);
        args.add(groupId);
        jdbc(appId).update("UPDATE qqbot_group SET " + String.join(", ", sets)
                + " WHERE bot_id=? AND group_id=?", args.toArray());
    }

    /** 群软删除（机器人退群）。 */
    public void markGroupRemoved(String appId, String groupId) {
        Long botId = resolveBotId(appId);
        if (botId == null) return;
        jdbc(appId).update("UPDATE qqbot_group SET is_deleted=1, status='removed' WHERE bot_id=? AND group_id=?",
                botId, groupId);
    }

    // ==================== qqbot_group_member ====================

    /**
     * 收到群消息时补建成员档案：<b>已存在则完全不动</b>（不覆盖昵称/角色/加入时间）。
     * 群消息的发言人只进本表，不会写入 qqbot_user（qqbot_user 专属单聊用户）。
     *
     * @return true 表示本次真的新建了一条
     */
    /**
     * 群消息/入群事件时补建成员档案：<b>不存在则创建（带 role/nickname），已存在则增量更新</b>
     * （仅更新传入的非 null 字段，昵称/角色变化实时刷新，保证数据最新）。
     * 群消息的发言人只进本表，不会写入 qqbot_user（qqbot_user 专属单聊用户）。
     *
     * @return true 表示本次真的新建了一条
     */
    public boolean ensureGroupMember(String appId, String groupId, String memberId,
                                     String role, String nickname) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(groupId) || isBlank(memberId)) return false;
        JdbcTemplate jdbc = jdbc(appId);
        if (!exists(jdbc, "SELECT COUNT(*) FROM qqbot_group_member WHERE bot_id=? AND group_id=? AND member_id=?",
                botId, groupId, memberId)) {
            try {
                jdbc.update("""
                    INSERT INTO qqbot_group_member (bot_id, group_id, member_id, role, nickname, is_deleted)
                    VALUES (?, ?, ?, ?, ?, 0)
                """, botId, groupId, memberId, role, nickname);
                return true;
            } catch (DataAccessException e) {
                return false;
            }
        }
        // 已存在：增量更新昵称/角色（只写非 null，避免抹空）
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "role", role);
        addSet(sets, args, "nickname", nickname);
        if (sets.isEmpty()) return false;
        args.add(botId);
        args.add(groupId);
        args.add(memberId);
        jdbc.update("UPDATE qqbot_group_member SET " + String.join(", ", sets)
                + " WHERE bot_id=? AND group_id=? AND member_id=?", args.toArray());
        return false;
    }

    /** 插入/增量更新群成员：只写入非 null 字段。 */
    public void upsertGroupMember(String appId, String groupId, String memberId,
                                  String role, String nickname, Long joinTime) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(groupId) || isBlank(memberId)) return;
        ensureGroupMember(appId, groupId, memberId, role, nickname);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "role", role);
        addSet(sets, args, "nickname", nickname);
        addSet(sets, args, "join_time", joinTime);
        sets.add("is_deleted=0");

        args.add(botId);
        args.add(groupId);
        args.add(memberId);
        jdbc(appId).update("UPDATE qqbot_group_member SET " + String.join(", ", sets)
                + " WHERE bot_id=? AND group_id=? AND member_id=?", args.toArray());
    }

    /** 群成员软删除（退群）。 */
    public void markMemberRemoved(String appId, String groupId, String memberId) {
        Long botId = resolveBotId(appId);
        if (botId == null) return;
        jdbc(appId).update("UPDATE qqbot_group_member SET is_deleted=1 WHERE bot_id=? AND group_id=? AND member_id=?",
                botId, groupId, memberId);
    }

    // ==================== qqbot_user（单聊用户） ====================

    /**
     * 收到单聊消息时补建用户档案：<b>不存在则创建（带昵称），已存在则增量更新昵称</b>。
     * 本表只收单聊（C2C）用户，群成员请用 {@link #ensureGroupMember}。
     *
     * @return true 表示本次真的新建了一条
     */
    public boolean ensureUser(String appId, String platformUserId, String nickname) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(platformUserId)) return false;
        JdbcTemplate jdbc = jdbc(appId);
        if (!exists(jdbc, "SELECT COUNT(*) FROM qqbot_user WHERE bot_id=? AND platform_user_id=?",
                botId, platformUserId)) {
            try {
                jdbc.update("""
                    INSERT INTO qqbot_user (bot_id, platform_user_id, nickname, is_deleted) VALUES (?, ?, ?, 0)
                """, botId, platformUserId, nickname);
                return true;
            } catch (DataAccessException e) {
                return false;
            }
        }
        // 已存在：增量更新昵称（非 null 才写）
        if (nickname == null || nickname.isBlank()) return false;
        jdbc.update("UPDATE qqbot_user SET nickname=? WHERE bot_id=? AND platform_user_id=?",
                nickname, botId, platformUserId);
        return false;
    }

    /** 插入/增量更新 C2C 好友 / 单聊用户：只写入非 null 字段。 */
    public void upsertUser(String appId, String platformUserId, String nickname, Long joinTime) {
        Long botId = resolveBotId(appId);
        if (botId == null || isBlank(platformUserId)) return;
        ensureUser(appId, platformUserId, nickname);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "nickname", nickname);
        addSet(sets, args, "join_time", joinTime);
        sets.add("is_deleted=0");

        args.add(botId);
        args.add(platformUserId);
        jdbc(appId).update("UPDATE qqbot_user SET " + String.join(", ", sets)
                + " WHERE bot_id=? AND platform_user_id=?", args.toArray());
    }

    /** 好友软删除（删除好友）。 */
    public void markUserRemoved(String appId, String platformUserId) {
        Long botId = resolveBotId(appId);
        if (botId == null) return;
        jdbc(appId).update("UPDATE qqbot_user SET is_deleted=1 WHERE bot_id=? AND platform_user_id=?",
                botId, platformUserId);
    }

    // ==================== qqbot_message ====================

    /** 写入一条消息流水（群/C2C 合并，append-only）。 */
    public void insertMessage(String appId, String chatType, String groupId, String userId,
                              String direction, String msgType, String content,
                              String msgId, String msgSeq, String eventId, String rawJson, Long createTime) {
        Long botId = resolveBotId(appId);
        if (botId == null) return;
        jdbc(appId).update("""
            INSERT INTO qqbot_message (bot_id, chat_type, group_id, user_id, direction, msg_type,
                                       content, msg_id, msg_seq, event_id, raw_json, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, botId, chatType, groupId, userId, direction, msgType, content, msgId, msgSeq, eventId, rawJson, createTime);
    }

    // ==================== qqbot_event ====================

    /** 写入一条系统事件流水（append-only）。 */
    public void insertEvent(String appId, String eventType, String groupId, String userId,
                            String rawJson, Long createTime) {
        Long botId = resolveBotId(appId);
        if (botId == null) return;
        jdbc(appId).update("""
            INSERT INTO qqbot_event (bot_id, event_type, group_id, user_id, raw_json, create_time)
            VALUES (?, ?, ?, ?, ?, ?)
        """, botId, eventType, groupId, userId, rawJson, createTime);
    }

    // ==================== 控制台聚合查询（原表全部字段，SELECT * 透传） ====================

    /** 群列表（未软删，原表全部字段）。 */
    public List<Map<String, Object>> listGroups(String appId) {
        return query(appId, """
            SELECT * FROM qqbot_group WHERE is_deleted=0 ORDER BY id
        """);
    }

    /** 单聊用户列表（未软删，原表全部字段）。 */
    public List<Map<String, Object>> listUsers(String appId) {
        return query(appId, """
            SELECT * FROM qqbot_user WHERE is_deleted=0 ORDER BY id
        """);
    }

    /** 某群的成员列表（未软删，原表全部字段）。 */
    public List<Map<String, Object>> listGroupMembers(String appId, String groupId) {
        return query(appId, """
            SELECT * FROM qqbot_group_member WHERE is_deleted=0 AND group_id=? ORDER BY id
        """, groupId);
    }

    /** 消息流水（按 chat_type 过滤，原表全部字段，倒序取最近 limit 条）。 */
    public List<Map<String, Object>> listMessages(String appId, String chatType, int limit) {
        return query(appId, """
            SELECT * FROM qqbot_message WHERE chat_type=? ORDER BY id DESC LIMIT ?
        """, chatType, limit);
    }

    /** 某个会话（群 或 单聊用户）的消息流水，原表全部字段，倒序取最近 limit 条。 */
    public List<Map<String, Object>> listMessagesByTarget(String appId, String chatType,
                                                          String targetId, int limit) {
        String col = "GROUP".equalsIgnoreCase(chatType) ? "group_id" : "user_id";
        return query(appId, """
            SELECT * FROM qqbot_message WHERE chat_type=? AND %s=? ORDER BY id DESC LIMIT ?
        """.formatted(col), chatType, targetId, limit);
    }

    /**
     * 单会话消息范围查询（控制台分页用）：按 create_time 范围 + 上界过滤，倒序取最近 limit 条。
     * until / before 为 Long.MAX_VALUE 表示无上界。
     */
    public List<Map<String, Object>> listMessagesByTargetRange(String appId, String chatType, String targetId,
                                                                long since, long until, long before, int limit) {
        String col = "GROUP".equalsIgnoreCase(chatType) ? "group_id" : "user_id";
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM qqbot_message WHERE chat_type=? AND ").append(col).append("=? AND create_time>=?");
        List<Object> args = new ArrayList<>();
        args.add(chatType);
        args.add(targetId);
        args.add(since);
        if (until < Long.MAX_VALUE) {
            sql.append(" AND create_time<=?");
            args.add(until);
        }
        if (before < Long.MAX_VALUE) {
            sql.append(" AND create_time<?");
            args.add(before);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        return query(appId, sql.toString(), args.toArray());
    }

    // ═══════════════════ 数据中心聚合 ═══════════════════

    /** 热力图：星期×小时 消息数（DAY_OF_WEEK: 1=周日 … 7=周六；HOUR: 0-23）。 */
    public List<Map<String, Object>> heatmap(String appId, long sinceEpochSeconds) {
        return query(appId, """
            SELECT DAY_OF_WEEK(DATEADD('SECOND', create_time, TIMESTAMP '1970-01-01')) AS DOW,
                   HOUR(DATEADD('SECOND', create_time, TIMESTAMP '1970-01-01')) AS HR,
                   COUNT(*) AS CNT
            FROM qqbot_message
            WHERE create_time >= ?
            GROUP BY DOW, HR
        """, sinceEpochSeconds);
    }

    /** 消息类型分布。 */
    public List<Map<String, Object>> msgTypeDist(String appId, long sinceEpochSeconds) {
        return query(appId, """
            SELECT msg_type, COUNT(*) AS CNT
            FROM qqbot_message WHERE create_time >= ?
            GROUP BY msg_type ORDER BY CNT DESC
        """, sinceEpochSeconds);
    }

    /** 活跃群 TOP（群名 LEFT JOIN qqbot_group，仅群聊消息；群名缺失为空串，前端用群 ID 前 8 位兜底）。 */
    public List<Map<String, Object>> activeGroups(String appId, long sinceEpochSeconds, int limit) {
        return query(appId, """
            SELECT m.group_id AS GID, COUNT(*) AS CNT, MAX(COALESCE(g.group_name, '')) AS GNAME
            FROM qqbot_message m
            LEFT JOIN qqbot_group g ON g.group_id = m.group_id AND g.bot_id = m.bot_id
            WHERE m.chat_type='group' AND m.create_time >= ?
            GROUP BY m.group_id ORDER BY CNT DESC LIMIT ?
        """, sinceEpochSeconds, Math.min(Math.max(limit, 1), 50));
    }

    /** 活跃用户 TOP（昵称 LEFT JOIN qqbot_user；昵称缺失为空串，前端用用户 ID 前 8 位兜底）。 */
    public List<Map<String, Object>> activeUsers(String appId, long sinceEpochSeconds, int limit) {
        return query(appId, """
            SELECT m.user_id AS UID, COUNT(*) AS CNT, MAX(COALESCE(u.nickname, '')) AS NICK
            FROM qqbot_message m
            LEFT JOIN qqbot_user u ON u.platform_user_id = m.user_id AND u.bot_id = m.bot_id
            WHERE m.create_time >= ?
            GROUP BY m.user_id ORDER BY CNT DESC LIMIT ?
        """, sinceEpochSeconds, Math.min(Math.max(limit, 1), 50));
    }

    /**
     * 活跃机器人 TOP（按消息内 bot_id 聚合消息数）。
     * 不能跨实例库 qqbot_message + 共享库 qqbot_bot 做 JOIN（H2 物理文件不同），
     * 改为实例库按 bot_id 分组聚合 + 共享库读 bot_appid→bot_name 映射内存 join。
     * bot_name 实际由上层 DataCenterController 用 botRef.botName() 覆盖（框架库 xuanji_bot_setting），
     * 此处仅做兜底，最终展示由框架库权威。
     */
    public List<Map<String, Object>> activeBots(String appId, long sinceEpochSeconds, int limit) {
        try {
            List<Map<String, Object>> rows = jdbc(appId).queryForList("""
                SELECT bot_id AS APP_ID, COUNT(*) AS CNT
                FROM qqbot_message
                WHERE create_time >= ?
                GROUP BY bot_id ORDER BY CNT DESC LIMIT ?
            """, sinceEpochSeconds, Math.min(Math.max(limit, 1), 50));
            // 从共享库 qqbot_bot 读 bot_appid → 名字映射（虽然共享库没 bot_name 列，先占位）
            for (Map<String, Object> r : rows) {
                r.put("BNAME", "");
            }
            return rows;
        } catch (Exception e) {
            log.debug("[QqRepo] 查询失败 appId={}: {}", appId, e.getMessage());
            return List.of();
        }
    }

    /** 消息方向分布（IN=入站/OUT=出站，按 direction 计数）。 */
    public List<Map<String, Object>> directionDist(String appId, long sinceEpochSeconds) {
        return query(appId, """
            SELECT DIRECTION, COUNT(*) AS CNT
            FROM qqbot_message
            WHERE CREATE_TIME >= ?
            GROUP BY DIRECTION
        """, sinceEpochSeconds);
    }

    /** 事件类型分布（按 qqbot_event.event_type 计数）。 */
    public List<Map<String, Object>> eventTypeDist(String appId, long sinceEpochSeconds) {
        return query(appId, """
            SELECT event_type AS ETYPE, COUNT(*) AS CNT
            FROM qqbot_event
            WHERE create_time >= ?
            GROUP BY event_type ORDER BY CNT DESC
        """, sinceEpochSeconds);
    }

    /**
     * 各群风控状态：按群聚合近 since 秒群聊消息数 + 群名/成员数（LEFT JOIN 群档案）。
     * 群名缺失返回空串（前端用群 ID 前 8 位兜底），成员数未知为 0。
     */
    public List<Map<String, Object>> groupRiskStats(String appId, long sinceEpochSeconds, int limit) {
        return query(appId, """
            SELECT m.group_id AS GID,
                   COUNT(*) AS MSG_CNT,
                   MAX(COALESCE(g.group_name, '')) AS GNAME,
                   MAX(COALESCE(g.member_count, 0)) AS MEMBER_CNT
            FROM qqbot_message m
            LEFT JOIN qqbot_group g ON g.group_id = m.group_id AND g.bot_id = m.bot_id
            WHERE m.chat_type='group' AND m.create_time >= ?
            GROUP BY m.group_id ORDER BY MSG_CNT DESC LIMIT ?
        """, sinceEpochSeconds, Math.min(Math.max(limit, 1), 200));
    }

    /** 系统事件流水（qqbot_event，原表全部字段，倒序取最近 limit 条）。 */
    public List<Map<String, Object>> listEvents(String appId, int limit) {
        return query(appId, """
            SELECT * FROM qqbot_event ORDER BY id DESC LIMIT ?
        """, limit);
    }

    /** 消息按天聚合（趋势图）：create_time 为 epoch 秒，按日 + 聊天类型计数。注意 DAY 是 H2 保留字，别名用 D。 */
    public List<Map<String, Object>> messageTrend(String appId, long sinceEpochSeconds) {
        return query(appId, """
            SELECT FORMATDATETIME(DATEADD('SECOND', CREATE_TIME, TIMESTAMP '1970-01-01'), 'yyyy-MM-dd') AS D,
                   CHAT_TYPE,
                   COUNT(*) AS CNT
            FROM qqbot_message
            WHERE CREATE_TIME >= ?
            GROUP BY D, CHAT_TYPE
            ORDER BY D
        """, sinceEpochSeconds);
    }

    public long countGroups(String appId) {
        return count(appId, "SELECT COUNT(*) FROM qqbot_group WHERE is_deleted=0");
    }

    public long countUsers(String appId) {
        return count(appId, "SELECT COUNT(*) FROM qqbot_user WHERE is_deleted=0");
    }

    /** 某类型会话自 sinceEpochSeconds 起的消息数。 */
    public long countMessagesSince(String appId, String chatType, long sinceEpochSeconds) {
        return count(appId, "SELECT COUNT(*) FROM qqbot_message WHERE chat_type=? AND create_time>=?",
                chatType, sinceEpochSeconds);
    }

    /** 指定事件类型集合自 sinceEpochSeconds 起的发生次数。 */
    public long countEventsSince(String appId, Collection<String> eventTypes, long sinceEpochSeconds) {
        if (eventTypes == null || eventTypes.isEmpty()) return 0L;
        String in = String.join(",", java.util.Collections.nCopies(eventTypes.size(), "?"));
        List<Object> args = new ArrayList<>(eventTypes);
        args.add(sinceEpochSeconds);
        return count(appId, "SELECT COUNT(*) FROM qqbot_event WHERE event_type IN (" + in + ") AND create_time>=?",
                args.toArray());
    }

    /**
     * 群变动（控制台「群变动 / 成员变动」统计卡数据源）：
     *   todayNewGroups = qqbot_group JOIN_TIME 在 [today0, now)
     *   ydayNewGroups  = qqbot_group JOIN_TIME 在 [yday0, today0)
     *   todayActiveMembers = qqbot_message group chat 去重 user_id 在 [today0, now)
     *   ydayActiveMembers  = 同上 [yday0, today0)
     */
    public Map<String, Long> groupVariation(String appId, long today0, long yday0, long now) {
        Map<String, Long> r = new LinkedHashMap<>();
        try {
            long todayNew = jdbc(appId).queryForObject(
                    "SELECT COUNT(*) FROM qqbot_group WHERE JOIN_TIME>=? AND JOIN_TIME<? AND IS_DELETED=0",
                    Long.class, today0, now);
            long ydayNew = jdbc(appId).queryForObject(
                    "SELECT COUNT(*) FROM qqbot_group WHERE JOIN_TIME>=? AND JOIN_TIME<? AND IS_DELETED=0",
                    Long.class, yday0, today0);
            long todayAct = jdbc(appId).queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM qqbot_message WHERE chat_type='group' AND create_time>=? AND create_time<?",
                    Long.class, today0, now);
            long ydayAct = jdbc(appId).queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM qqbot_message WHERE chat_type='group' AND create_time>=? AND create_time<?",
                    Long.class, yday0, today0);
            r.put("todayNewGroups", todayNew);
            r.put("ydayNewGroups", ydayNew);
            r.put("todayActiveMembers", todayAct);
            r.put("ydayActiveMembers", ydayAct);
        } catch (Exception e) {
            log.debug("[QqRepo] 群变动查询失败 appId={}: {}", appId, e.getMessage());
            r.put("todayNewGroups", 0L);
            r.put("ydayNewGroups", 0L);
            r.put("todayActiveMembers", 0L);
            r.put("ydayActiveMembers", 0L);
        }
        return r;
    }

    /**
     * 单聊用户变动（控制台「用户变动」统计卡数据源）：
     *   todayNewFriends = qqbot_user JOIN_TIME 在 [today0, now)
     *   ydayNewFriends  = qqbot_user JOIN_TIME 在 [yday0, today0)
     *   todayActiveUsers = qqbot_message c2c chat 去重 user_id 在 [today0, now)
     *   ydayActiveUsers  = 同上 [yday0, today0)
     */
    public Map<String, Long> friendVariation(String appId, long today0, long yday0, long now) {
        Map<String, Long> r = new LinkedHashMap<>();
        try {
            long todayNew = jdbc(appId).queryForObject(
                    "SELECT COUNT(*) FROM qqbot_user WHERE JOIN_TIME>=? AND JOIN_TIME<? AND IS_DELETED=0",
                    Long.class, today0, now);
            long ydayNew = jdbc(appId).queryForObject(
                    "SELECT COUNT(*) FROM qqbot_user WHERE JOIN_TIME>=? AND JOIN_TIME<? AND IS_DELETED=0",
                    Long.class, yday0, today0);
            long todayAct = jdbc(appId).queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM qqbot_message WHERE chat_type='c2c' AND create_time>=? AND create_time<?",
                    Long.class, today0, now);
            long ydayAct = jdbc(appId).queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM qqbot_message WHERE chat_type='c2c' AND create_time>=? AND create_time<?",
                    Long.class, yday0, today0);
            r.put("todayNewFriends", todayNew);
            r.put("ydayNewFriends", ydayNew);
            r.put("todayActiveUsers", todayAct);
            r.put("ydayActiveUsers", ydayAct);
        } catch (Exception e) {
            log.debug("[QqRepo] 单聊变动查询失败 appId={}: {}", appId, e.getMessage());
            r.put("todayNewFriends", 0L);
            r.put("ydayNewFriends", 0L);
            r.put("todayActiveUsers", 0L);
            r.put("ydayActiveUsers", 0L);
        }
        return r;
    }

    /** 全部系统事件自 sinceEpochSeconds 起的发生次数（预警中心事件突增检查用）。 */
    public long countAllEventsSince(String appId, long sinceEpochSeconds) {
        return count(appId, "SELECT COUNT(*) FROM qqbot_event WHERE create_time>=?", sinceEpochSeconds);
    }

    /** 实例库全部系统事件数（不限时间，用于仪表盘汇总）。 */
    public long countAllEvents(String appId) {
        return count(appId, "SELECT COUNT(*) FROM qqbot_event");
    }

    // ==================== 内部工具 ====================

    /** 惰性 schema 初始化：平台共享库（qqbot_bot/qqbot_botinfo）每个进程只建一次。 */
    private volatile boolean platformSchemaReady = false;
    /** per-bot 实例库（qqbot_group 等 5 表）按 appId 惰性建表。 */
    private final java.util.Set<String> instanceSchemaReady = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private JdbcTemplate jdbc(String appId) {
        JdbcTemplate jdbc = dataSourceRegistry.forInstance("qqbot", appId);
        ensureInstanceSchema(appId, jdbc);
        return jdbc;
    }

    /** 平台级共享库（qqbot_bot / qqbot_botinfo 所在，data/qqbot/qqbot.mv.db）。 */
    private JdbcTemplate platformJdbc() {
        JdbcTemplate jdbc = dataSourceRegistry.forPlatform("qqbot");
        ensurePlatformSchema(jdbc);
        return jdbc;
    }

    /**
     * 平台共享库惰性建表（幂等）。schema 初始化调用链曾因事故断链导致 qqbot_bot 缺失、
     * 保存机器人报 bad SQL grammar，故在首次取连接处兜底建表。
     */
    private synchronized void ensurePlatformSchema(JdbcTemplate jdbc) {
        if (platformSchemaReady) return;
        schemaProvider.initPlatformSchema(jdbc);
        platformSchemaReady = true;
        log.info("[QqRepo] 平台库 schema 就绪: data/qqbot/qqbot.mv.db");
    }

    /** per-bot 实例库惰性建表（幂等，按 appId 一次；initSchema 成功后才标记 ready，失败可重试）。 */
    private void ensureInstanceSchema(String appId, JdbcTemplate jdbc) {
        if (instanceSchemaReady.contains(appId)) return;
        synchronized (instanceSchemaReady) {
            if (instanceSchemaReady.contains(appId)) return;
            schemaProvider.initSchema(jdbc);
            instanceSchemaReady.add(appId);
            log.info("[QqRepo] 实例库 schema 就绪: appId={}", appId);
        }
    }

    private List<Map<String, Object>> query(String appId, String sql, Object... args) {
        try {
            return jdbc(appId).queryForList(sql, args);
        } catch (Exception e) {
            log.debug("[QqRepo] 查询失败 appId={}: {}", appId, e.getMessage());
            return List.of();
        }
    }

    private long count(String appId, String sql, Object... args) {
        try {
            Long v = jdbc(appId).queryForObject(sql, Long.class, args);
            return v != null ? v : 0L;
        } catch (Exception e) {
            log.debug("[QqRepo] 统计失败 appId={}: {}", appId, e.getMessage());
            return 0L;
        }
    }

    private static boolean exists(JdbcTemplate jdbc, String sql, Object... args) {
        Integer c = jdbc.queryForObject(sql, Integer.class, args);
        return c != null && c > 0;
    }

    /** 仅当值非 null 时才加入 UPDATE 的 SET 子句（保证「不覆盖已有值」）。 */
    private static void addSet(List<String> sets, List<Object> args, String column, Object value) {
        if (value == null) return;
        sets.add(column + "=?");
        args.add(value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 把平台 appId 解析为平台库 qqbot_bot.id（per-bot 业务表 bot_id 的关联目标）。
     * 若 qqbot_bot 行尚不存在则惰性补建。
     */
    private Long resolveBotId(String appId) {
        Long cached = botIdCache.get(appId);
        if (cached != null) return cached;
        JdbcTemplate jdbc = platformJdbc();
        Long id = jdbc.queryForObject("SELECT id FROM qqbot_bot WHERE bot_appid=?", Long.class, appId);
        if (id == null) {
            upsertBot(appId, "", null, false, "ONLINE");
            id = jdbc.queryForObject("SELECT id FROM qqbot_bot WHERE bot_appid=?", Long.class, appId);
        }
        if (id != null) botIdCache.put(appId, id);
        return id;
    }

    /** 消息类型数值（0=文本/2=Markdown/7=富媒体）转可读标签。 */
    public static String msgTypeLabel(Integer type) {
        if (type == null) return "text";
        return switch (type) {
            case 0 -> "text";
            case 2 -> "markdown";
            case 7 -> "rich_media";
            default -> "text";
        };
    }

    /**
     * 结合消息类型数值与附件信息解析可读标签（解决「图片/语音/视频显示为 text」问题）。
     * <p>优先看附件：QQ 平台图片/语音/视频常以 {@code message_type=7} + 附件形式下发，
     * 仅靠数值会落入 rich_media；此处按 content_type / 文件名进一步细分为 image/voice/video/file。
     * 无附件时回退到 {@link #msgTypeLabel(Integer)}。</p>
     */
    public static String msgTypeLabel(Integer type, String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            if (contentType.startsWith("image")) return "image";
            if (contentType.startsWith("audio")) return "voice";
            if (contentType.startsWith("video")) return "video";
            if (contentType.startsWith("file")) return "file";
        }
        if (filename != null && !filename.isBlank()) {
            String l = filename.toLowerCase();
            if (l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg")
                    || l.endsWith(".gif") || l.endsWith(".webp") || l.endsWith(".bmp")) return "image";
            if (l.endsWith(".mp3") || l.endsWith(".amr") || l.endsWith(".wav") || l.endsWith(".m4a")) return "voice";
            if (l.endsWith(".mp4") || l.endsWith(".mov") || l.endsWith(".avi")) return "video";
            return "file";
        }
        return msgTypeLabel(type);
    }
}
