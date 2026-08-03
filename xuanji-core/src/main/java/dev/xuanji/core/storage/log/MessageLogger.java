package dev.xuanji.core.storage.log;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息/事件流水记录器 — 写入日志库的三张表。
 */
@Component
public class MessageLogger {

    private static JdbcTemplate logJdbc;

    public MessageLogger(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        MessageLogger.logJdbc = logJdbc;
    }

    /** 记录群聊消息 */
    public static void groupMessage(String direction, String botId, String groupId,
                                     String memberId, String msgType, String content, String rawJson) {
        if (logJdbc == null) return;
        try {
            logJdbc.update("INSERT INTO xuanji_qqbot_group_message (direction,bot_id,group_id,member_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?,?)",
                    direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
        } catch (Exception ignored) {}
    }

    /** 记录单聊消息 */
    public static void c2cMessage(String direction, String botId, String userId,
                                   String msgType, String content, String rawJson) {
        if (logJdbc == null) return;
        try {
            logJdbc.update("INSERT INTO xuanji_qqbot_c2c_message (direction,bot_id,user_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?)",
                    direction, botId, userId, msgType, truncate(content, 4000), rawJson);
        } catch (Exception ignored) {}
    }

    /** 记录事件 */
    public static void event(String direction, String botId, String eventType, String rawJson) {
        event(direction, botId, eventType, null, rawJson);
    }

    public static void event(String direction, String botId, String eventType, String groupId, String rawJson) {
        if (logJdbc == null) return;
        try {
            logJdbc.update("INSERT INTO xuanji_qqbot_event (direction,bot_id,event_type,group_id,raw_json) VALUES (?,?,?,?,?)",
                    direction, botId, eventType, groupId, rawJson);
        } catch (Exception ignored) {}
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ==================== OneBot 流水（同构 QQ） ====================

    public static void onebotGroupMessage(String direction, String botId, String groupId,
                                           String memberId, String msgType, String content, String rawJson) {
        if (logJdbc == null) return;
        try {
            logJdbc.update("INSERT INTO xuanji_onebot_group_message (direction,bot_id,group_id,member_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?,?)",
                    direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
        } catch (Exception ignored) {}
    }

    public static void onebotEvent(String direction, String botId, String eventType, String groupId, String rawJson) {
        if (logJdbc == null) return;
        try {
            logJdbc.update("INSERT INTO xuanji_onebot_event (direction,bot_id,event_type,group_id,raw_json) VALUES (?,?,?,?,?)",
                    direction, botId, eventType, groupId, rawJson);
        } catch (Exception ignored) {}
    }
}
