package dev.xuanji.adapter.qq.storage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BotDataQuery {

    private static JdbcTemplate jdbc;       // 业务库（@Primary）：qqbot_group / user / info
    private static JdbcTemplate logJdbc;    // 日志库：xuanji_qqbot_event 流水

    public BotDataQuery(JdbcTemplate jdbc,
                       @Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        BotDataQuery.jdbc = jdbc;
        BotDataQuery.logJdbc = logJdbc;
    }

    // ==================== 计数 ====================

    public static int groupCount(String appId) {
        return count("xuanji_qqbot_group", "bot_id=? AND is_deleted=0", appId);
    }

    public static int userCount(String appId) {
        return count("xuanji_qqbot_user", "bot_id=? AND is_deleted=0", appId);
    }

    /** 今日新增群 */
    public static int todayGroupAdd(String appId) {
        return todayEventCount(appId, "GROUP_ADD_ROBOT");
    }

    /** 今日退群 */
    public static int todayGroupDel(String appId) {
        return todayEventCount(appId, "GROUP_DEL_ROBOT");
    }

    /** 今日新增好友 */
    public static int todayFriendAdd(String appId) {
        return todayEventCount(appId, "FRIEND_ADD");
    }

    /** 今日删除好友 */
    public static int todayFriendDel(String appId) {
        return todayEventCount(appId, "FRIEND_DEL");
    }

    /** 某群今日加入人数 */
    public static int todayGroupMemberAdd(String appId, String groupId) {
        return todayGroupEventCount(appId, groupId, "GROUP_MEMBER_ADD");
    }

    /** 某群今日退出人数 */
    public static int todayGroupMemberDel(String appId, String groupId) {
        return todayGroupEventCount(appId, groupId, "GROUP_MEMBER_REMOVE");
    }

    public static Map<String, String> botInfo(String appId) {
        Map<String, String> m = new LinkedHashMap<>();
        if (jdbc == null || appId == null) return m;
        try {
            var row = jdbc.queryForMap("SELECT * FROM xuanji_qqbot_info WHERE bot_id = ?", appId);
            for (var e : row.entrySet())
                m.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
        } catch (Exception ex) {}
        return m;
    }

    private static int count(String table, String where, String appId) {
        if (jdbc == null || appId == null) return 0;
        try {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where,
                    Integer.class, appId);
            return c != null ? c : 0;
        } catch (Exception e) { return 0; }
    }

    private static int todayEventCount(String appId, String eventType) {
        if (logJdbc == null || appId == null) return 0;
        try {
            Integer c = logJdbc.queryForObject(
                    "SELECT COUNT(*) FROM xuanji_qqbot_event WHERE bot_id=? AND event_type=? AND create_time >= CURRENT_DATE",
                    Integer.class, appId, eventType);
            return c != null ? c : 0;
        } catch (Exception e) { return 0; }
    }

    private static int todayGroupEventCount(String appId, String groupId, String eventType) {
        if (logJdbc == null || appId == null) return 0;
        try {
            Integer c = logJdbc.queryForObject(
                    "SELECT COUNT(*) FROM xuanji_qqbot_event WHERE bot_id=? AND group_id=? AND event_type=? AND create_time >= CURRENT_DATE",
                    Integer.class, appId, groupId, eventType);
            return c != null ? c : 0;
        } catch (Exception e) { return 0; }
    }
}
