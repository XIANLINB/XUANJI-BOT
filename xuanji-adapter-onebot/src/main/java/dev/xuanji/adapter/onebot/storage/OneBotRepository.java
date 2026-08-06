/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.core.storage.BotDataSourceRegistry
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.dao.DataAccessException
 *  org.springframework.jdbc.core.JdbcTemplate
 */
package dev.xuanji.adapter.onebot.storage;

import dev.xuanji.core.storage.BotDataSourceRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class OneBotRepository {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotRepository.class);
    private final BotDataSourceRegistry dataSourceRegistry;
    private final OneBotSchemaProvider schemaProvider;
    private final Map<String, Long> botIdCache = new ConcurrentHashMap<String, Long>();
    private volatile boolean platformSchemaReady = false;
    private final java.util.Set<String> instanceSchemaReady = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public OneBotRepository(BotDataSourceRegistry dataSourceRegistry, OneBotSchemaProvider schemaProvider) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.schemaProvider = schemaProvider;
    }

    public void upsertBot(String selfId, String status) {
        this.platformJdbc().update("    MERGE INTO onebot_bot (bot_appid, bot_clientSecret, conn_mode, is_sandbox, status, webhook_url)\n    KEY (bot_appid) VALUES (?, '', 'websocket', 0, ?, NULL)\n", new Object[]{selfId, status});
    }

    public Map<String, Object> getBotRow(String selfId) {
        try {
            List rows = this.platformJdbc().queryForList("    SELECT bot_appid, bot_clientSecret, conn_mode, is_sandbox, status, webhook_url\n    FROM onebot_bot WHERE bot_appid = ?\n", new Object[]{selfId});
            return rows.isEmpty() ? Map.of() : (Map)rows.get(0);
        }
        catch (DataAccessException e) {
            log.debug("[OneBot] \u8bfb\u53d6 onebot_bot({}) \u5931\u8d25: {}", (Object)selfId, (Object)e.getMessage());
            return Map.of();
        }
    }

    public List<String> listInstanceIds() {
        try {
            return this.platformJdbc().queryForList("SELECT bot_appid FROM onebot_bot WHERE is_deleted=0 ORDER BY bot_appid", String.class);
        }
        catch (DataAccessException e) {
            log.debug("[OneBot] \u679a\u4e3e onebot_bot \u5931\u8d25\uff08\u53ef\u80fd\u5c1a\u65e0\u6ce8\u518c\uff09: {}", (Object)e.getMessage());
            return List.of();
        }
    }

    public Map<String, Map<String, Object>> loadAllBotRows() {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
        for (String selfId : this.listInstanceIds()) {
            Map<String, Object> row = this.getBotRow(selfId);
            if (row.isEmpty()) continue;
            result.put(selfId, row);
        }
        return result;
    }

    public void updateBotStatus(String selfId, String status) {
        try {
            this.platformJdbc().update("UPDATE onebot_bot SET status = ? WHERE bot_appid = ?", new Object[]{status, selfId});
        }
        catch (DataAccessException e) {
            log.debug("[OneBot] \u66f4\u65b0 status({}) \u5931\u8d25: {}", (Object)selfId, (Object)e.getMessage());
        }
    }

    public void upsertBotInfo(String selfId, String botId, String name, String avatar, Boolean isBot, String unionOpenid, String shareUrl, String welcomeMsg) {
        JdbcTemplate jdbc = this.platformJdbc();
        Long botFk = (Long)jdbc.queryForObject("SELECT id FROM onebot_bot WHERE bot_appid=?", Long.class, new Object[]{selfId});
        jdbc.update("    MERGE INTO onebot_botinfo (botid, bot_id, name, avatar, is_bot, union_openid, share_url, welcome_msg)\n    KEY (botid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)\n", new Object[]{botFk, botId, name, avatar, isBot, unionOpenid, shareUrl, welcomeMsg});
    }

    public Map<String, Object> getBotInfo(String selfId) {
        try {
            return this.platformJdbc().queryForMap("    SELECT i.name, i.avatar, i.share_url FROM onebot_botinfo i\n    JOIN onebot_bot b ON b.id = i.botid WHERE b.bot_appid = ?\n", new Object[]{selfId});
        }
        catch (DataAccessException e) {
            log.debug("\u8bfb\u53d6 botinfo \u5931\u8d25\uff08\u53ef\u80fd\u5c1a\u672a\u540c\u6b65\uff09: {}", (Object)e.getMessage());
            return Map.of();
        }
    }

    public void deleteBot(String selfId) {
        this.botIdCache.remove(selfId);
        // 数据目录会被删除重建：内存「已建表」标记失效，重新添加后须重新 initSchema
        this.instanceSchemaReady.remove(selfId);
        try {
            JdbcTemplate jdbc = this.platformJdbc();
            jdbc.update("    DELETE FROM onebot_botinfo WHERE botid IN (SELECT id FROM onebot_bot WHERE bot_appid=?)\n", new Object[]{selfId});
            jdbc.update("DELETE FROM onebot_bot WHERE bot_appid=?", new Object[]{selfId});
        }
        catch (DataAccessException e) {
            log.warn("[OneBot] \u5220\u9664\u5e73\u53f0\u5e93\u6863\u6848\u5931\u8d25 selfId={}: {}", (Object)selfId, (Object)e.getMessage());
        }
    }

    public boolean ensureGroup(String selfId, String groupId) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(groupId)) {
            return false;
        }
        JdbcTemplate jdbc = this.jdbc(selfId);
        if (OneBotRepository.exists(jdbc, "SELECT COUNT(*) FROM onebot_group WHERE bot_id=? AND group_id=?", botId, groupId)) {
            return false;
        }
        try {
            jdbc.update("INSERT INTO onebot_group (bot_id, group_id, status, is_deleted) VALUES (?, ?, 'active', 0)", new Object[]{botId, groupId});
            return true;
        }
        catch (DataAccessException e) {
            return false;
        }
    }

    public void upsertGroup(String selfId, String groupId, String groupName, String ownerId, Integer memberCount, Long joinTime, String status) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(groupId)) {
            return;
        }
        this.ensureGroup(selfId, groupId);
        ArrayList<String> sets = new ArrayList<String>();
        ArrayList<Object> args = new ArrayList<Object>();
        OneBotRepository.addSet(sets, args, "group_name", groupName);
        OneBotRepository.addSet(sets, args, "owner_id", ownerId);
        OneBotRepository.addSet(sets, args, "member_count", memberCount);
        OneBotRepository.addSet(sets, args, "join_time", joinTime);
        OneBotRepository.addSet(sets, args, "status", status);
        if (sets.isEmpty()) {
            return;
        }
        args.add(botId);
        args.add(groupId);
        this.jdbc(selfId).update("UPDATE onebot_group SET " + String.join((CharSequence)", ", sets) + " WHERE bot_id=? AND group_id=?", args.toArray());
    }

    public void markGroupRemoved(String selfId, String groupId) {
        try {
            this.jdbc(selfId).update("UPDATE onebot_group SET is_deleted=1, status='removed' WHERE bot_id=? AND group_id=?", new Object[]{this.resolveBotId(selfId), groupId});
        }
        catch (DataAccessException dataAccessException) {
            // empty catch block
        }
    }

    public boolean ensureGroupMember(String selfId, String groupId, String memberId, String role) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(groupId) || OneBotRepository.isBlank(memberId)) {
            return false;
        }
        JdbcTemplate jdbc = this.jdbc(selfId);
        if (OneBotRepository.exists(jdbc, "SELECT COUNT(*) FROM onebot_group_member WHERE bot_id=? AND group_id=? AND member_id=?", botId, groupId, memberId)) {
            return false;
        }
        try {
            jdbc.update("    INSERT INTO onebot_group_member (bot_id, group_id, member_id, role, is_deleted)\n    VALUES (?, ?, ?, ?, 0)\n", new Object[]{botId, groupId, memberId, role == null ? "member" : role});
            return true;
        }
        catch (DataAccessException e) {
            return false;
        }
    }

    public void upsertGroupMember(String selfId, String groupId, String memberId, String role, String nickname, Long joinTime) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(groupId) || OneBotRepository.isBlank(memberId)) {
            return;
        }
        this.ensureGroupMember(selfId, groupId, memberId, role);
        ArrayList<String> sets = new ArrayList<String>();
        ArrayList<Object> args = new ArrayList<Object>();
        OneBotRepository.addSet(sets, args, "role", role);
        OneBotRepository.addSet(sets, args, "nickname", nickname);
        OneBotRepository.addSet(sets, args, "join_time", joinTime);
        if (sets.isEmpty()) {
            return;
        }
        args.add(botId);
        args.add(groupId);
        args.add(memberId);
        this.jdbc(selfId).update("UPDATE onebot_group_member SET " + String.join((CharSequence)", ", sets) + " WHERE bot_id=? AND group_id=? AND member_id=?", args.toArray());
    }

    public void markMemberRemoved(String selfId, String groupId, String memberId) {
        try {
            this.jdbc(selfId).update("    UPDATE onebot_group_member SET is_deleted=1 WHERE bot_id=? AND group_id=? AND member_id=?\n", new Object[]{this.resolveBotId(selfId), groupId, memberId});
        }
        catch (DataAccessException dataAccessException) {
            // empty catch block
        }
    }

    public boolean ensureUser(String selfId, String platformUserId) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(platformUserId)) {
            return false;
        }
        JdbcTemplate jdbc = this.jdbc(selfId);
        if (OneBotRepository.exists(jdbc, "SELECT COUNT(*) FROM onebot_user WHERE bot_id=? AND platform_user_id=?", botId, platformUserId)) {
            return false;
        }
        try {
            jdbc.update("INSERT INTO onebot_user (bot_id, platform_user_id, is_deleted) VALUES (?, ?, 0)", new Object[]{botId, platformUserId});
            return true;
        }
        catch (DataAccessException e) {
            return false;
        }
    }

    public void upsertUser(String selfId, String platformUserId, String nickname, Long joinTime) {
        Long botId = this.resolveBotId(selfId);
        if (botId == null || OneBotRepository.isBlank(platformUserId)) {
            return;
        }
        this.ensureUser(selfId, platformUserId);
        ArrayList<String> sets = new ArrayList<String>();
        ArrayList<Object> args = new ArrayList<Object>();
        OneBotRepository.addSet(sets, args, "nickname", nickname);
        OneBotRepository.addSet(sets, args, "join_time", joinTime);
        if (sets.isEmpty()) {
            return;
        }
        args.add(botId);
        args.add(platformUserId);
        this.jdbc(selfId).update("UPDATE onebot_user SET " + String.join((CharSequence)", ", sets) + " WHERE bot_id=? AND platform_user_id=?", args.toArray());
    }

    public void markUserRemoved(String selfId, String platformUserId) {
        try {
            this.jdbc(selfId).update("UPDATE onebot_user SET is_deleted=1 WHERE bot_id=? AND platform_user_id=?", new Object[]{this.resolveBotId(selfId), platformUserId});
        }
        catch (DataAccessException dataAccessException) {
            // empty catch block
        }
    }

    public void insertMessage(String selfId, String chatType, String groupId, String userId, String direction, String msgType, String content, String msgId, String msgSeq, String eventId, String rawJson, long createTime) {
        try {
            this.jdbc(selfId).update("    INSERT INTO onebot_message (bot_id, chat_type, group_id, user_id, direction, msg_type,\n                                content, msg_id, msg_seq, event_id, raw_json, create_time)\n    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n", new Object[]{this.resolveBotId(selfId), chatType, groupId, userId, direction, msgType, content, msgId, msgSeq, eventId, rawJson, createTime});
        }
        catch (DataAccessException e) {
            log.warn("[OneBot] \u6d88\u606f\u843d\u5e93\u5931\u8d25 selfId={}: {}", (Object)selfId, (Object)e.getMessage());
        }
    }

    public void insertEvent(String selfId, String eventType, String groupId, String userId, String rawJson, long createTime) {
        try {
            this.jdbc(selfId).update("    INSERT INTO onebot_event (bot_id, event_type, group_id, user_id, raw_json, create_time)\n    VALUES (?, ?, ?, ?, ?, ?)\n", new Object[]{this.resolveBotId(selfId), eventType, groupId, userId, rawJson, createTime});
        }
        catch (DataAccessException e) {
            log.warn("[OneBot] \u4e8b\u4ef6\u843d\u5e93\u5931\u8d25 selfId={}: {}", (Object)selfId, (Object)e.getMessage());
        }
    }

    public List<Map<String, Object>> listGroups(String selfId) {
        return this.query(selfId, "SELECT * FROM onebot_group ORDER BY id", new Object[0]);
    }

    public List<Map<String, Object>> listUsers(String selfId) {
        return this.query(selfId, "SELECT * FROM onebot_user ORDER BY id", new Object[0]);
    }

    public List<Map<String, Object>> listGroupMembers(String selfId, String groupId) {
        return this.query(selfId, "SELECT * FROM onebot_group_member WHERE group_id=? ORDER BY id", groupId);
    }

    public List<Map<String, Object>> listMessages(String selfId, String chatType, int limit) {
        return this.query(selfId, "SELECT * FROM onebot_message WHERE chat_type=? ORDER BY id DESC LIMIT ?", chatType, limit);
    }

    public List<Map<String, Object>> listMessagesByTarget(String selfId, String chatType, String targetId, int limit) {
        if ("GROUP".equals(chatType)) {
            return this.query(selfId, "SELECT * FROM onebot_message WHERE chat_type=? AND group_id=? ORDER BY id DESC LIMIT ?", chatType, targetId, limit);
        }
        return this.query(selfId, "SELECT * FROM onebot_message WHERE chat_type=? AND user_id=? ORDER BY id DESC LIMIT ?", chatType, targetId, limit);
    }

    public List<Map<String, Object>> listEvents(String selfId, int limit) {
        return this.query(selfId, "SELECT * FROM onebot_event ORDER BY id DESC LIMIT ?", limit);
    }

    public long countGroups(String selfId) {
        return this.count(selfId, "SELECT COUNT(*) FROM onebot_group WHERE is_deleted=0", new Object[0]);
    }

    public long countUsers(String selfId) {
        return this.count(selfId, "SELECT COUNT(*) FROM onebot_user WHERE is_deleted=0", new Object[0]);
    }

    public long countMessagesSince(String selfId, String chatType, long sinceEpochSeconds) {
        return this.count(selfId, "SELECT COUNT(*) FROM onebot_message WHERE chat_type=? AND create_time>=?", chatType, sinceEpochSeconds);
    }

    public long countEventsSince(String selfId, Collection<String> eventTypes, long sinceEpochSeconds) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return 0L;
        }
        String in = String.join((CharSequence)",", Collections.nCopies(eventTypes.size(), "?"));
        return this.count(selfId, "SELECT COUNT(*) FROM onebot_event WHERE event_type IN (" + in + ") AND create_time>=?", eventTypes.toArray(), sinceEpochSeconds);
    }

    public long countAllEvents(String selfId) {
        return this.count(selfId, "SELECT COUNT(*) FROM onebot_event", new Object[0]);
    }

    private JdbcTemplate jdbc(String selfId) {
        JdbcTemplate jdbc = this.dataSourceRegistry.forInstance("onebot", selfId);
        this.ensureInstanceSchema(selfId, jdbc);
        return jdbc;
    }

    /** 平台级共享库（onebot_bot / onebot_botinfo 所在，data/onebot/onebot.mv.db）。 */
    private JdbcTemplate platformJdbc() {
        JdbcTemplate jdbc = this.dataSourceRegistry.forPlatform("onebot");
        this.ensurePlatformSchema(jdbc);
        return jdbc;
    }

    /** 平台共享库惰性建表（幂等；schema 初始化调用链曾断链导致 onebot_bot 缺失）。 */
    private synchronized void ensurePlatformSchema(JdbcTemplate jdbc) {
        if (this.platformSchemaReady) return;
        this.schemaProvider.initPlatformSchema(jdbc);
        this.platformSchemaReady = true;
        log.info("[OneBot] 平台库 schema 就绪: data/onebot/onebot.mv.db");
    }

    /** per-bot 实例库惰性建表（幂等，按 selfId 一次；initSchema 成功后才标记 ready，失败可重试）。 */
    private void ensureInstanceSchema(String selfId, JdbcTemplate jdbc) {
        if (this.instanceSchemaReady.contains(selfId)) return;
        synchronized (this.instanceSchemaReady) {
            if (this.instanceSchemaReady.contains(selfId)) return;
            this.schemaProvider.initSchema(jdbc);
            this.instanceSchemaReady.add(selfId);
            log.info("[OneBot] 实例库 schema 就绪: selfId={}", selfId);
        }
    }

    private List<Map<String, Object>> query(String selfId, String sql, Object ... args) {
        try {
            return this.jdbc(selfId).queryForList(sql, args);
        }
        catch (Exception e) {
            log.debug("[OneBotRepo] \u67e5\u8be2\u5931\u8d25 selfId={}: {}", (Object)selfId, (Object)e.getMessage());
            return List.of();
        }
    }

    private long count(String selfId, String sql, Object ... args) {
        try {
            Long v = (Long)this.jdbc(selfId).queryForObject(sql, Long.class, args);
            return v != null ? v : 0L;
        }
        catch (Exception e) {
            log.debug("[OneBotRepo] \u7edf\u8ba1\u5931\u8d25 selfId={}: {}", (Object)selfId, (Object)e.getMessage());
            return 0L;
        }
    }

    private static boolean exists(JdbcTemplate jdbc, String sql, Object ... args) {
        Integer c = (Integer)jdbc.queryForObject(sql, Integer.class, args);
        return c != null && c > 0;
    }

    private static void addSet(List<String> sets, List<Object> args, String column, Object value) {
        if (value == null) {
            return;
        }
        sets.add(column + "=?");
        args.add(value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Long resolveBotId(String selfId) {
        Long cached = this.botIdCache.get(selfId);
        if (cached != null) {
            return cached;
        }
        JdbcTemplate jdbc = this.platformJdbc();
        Long id = (Long)jdbc.queryForObject("SELECT id FROM onebot_bot WHERE bot_appid=?", Long.class, new Object[]{selfId});
        if (id == null) {
            this.upsertBot(selfId, "ONLINE");
            id = (Long)jdbc.queryForObject("SELECT id FROM onebot_bot WHERE bot_appid=?", Long.class, new Object[]{selfId});
        }
        if (id != null) {
            this.botIdCache.put(selfId, id);
        }
        return id;
    }

    public static String msgTypeLabel(String type) {
        String t;
        if (type == null || type.isBlank()) {
            return "text";
        }
        return switch (t = type.toLowerCase()) {
            case "text" -> "text";
            case "image" -> "image";
            case "face" -> "face";
            case "at" -> "at";
            case "record" -> "voice";
            case "video" -> "video";
            case "file" -> "file";
            case "reply" -> "reply";
            default -> t;
        };
    }
}

