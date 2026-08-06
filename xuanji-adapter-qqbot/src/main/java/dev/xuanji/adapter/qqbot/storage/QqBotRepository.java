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
        JdbcTemplate jdbc = platformJdbc();
        Long botFk = jdbc.queryForObject("SELECT id FROM qqbot_bot WHERE bot_appid=?", Long.class, appId);
        jdbc.update("""
            MERGE INTO qqbot_botinfo (botid, bot_id, name, avatar, is_bot, union_openid, share_url, welcome_msg)
            KEY (botid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, botFk, botId, name, avatar, isBot, unionOpenid, shareUrl, welcomeMsg);
    }

    /** 读取指定 bot 的基础信息（平台库 qqbot_botinfo），降级返回空 Map。 */
    public Map<String, Object> getBotInfo(String appId) {
        try {
            return platformJdbc().queryForMap("""
                SELECT i.name, i.avatar, i.share_url FROM qqbot_botinfo i
                JOIN qqbot_bot b ON b.id = i.botid WHERE b.bot_appid = ?
            """, appId);
        } catch (DataAccessException e) {
            log.debug("读取 botinfo 失败（可能尚未同步）: {}", e.getMessage());
            return Map.of();
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

    /** 系统事件流水（qqbot_event，原表全部字段，倒序取最近 limit 条）。 */
    public List<Map<String, Object>> listEvents(String appId, int limit) {
        return query(appId, """
            SELECT * FROM qqbot_event ORDER BY id DESC LIMIT ?
        """, limit);
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
}
