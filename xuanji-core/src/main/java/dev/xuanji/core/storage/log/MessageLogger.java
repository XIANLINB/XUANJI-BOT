package dev.xuanji.core.storage.log;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息/事件流水记录器 — 写入日志库的三张表。
 *
 * <p>P2-3：所有 INSERT 改走 {@link BatchLogWriter} 批量落库（100ms 窗 / 50 条阈值），
 * 缓解高并发下 H2 单文件写锁压力。批写组件不可用时自动降级为同步单条写（fallback）。
 */
@Component
public class MessageLogger {

    private static final String SQL_GROUP =
            "INSERT INTO xuanji_qqbot_group_message (direction,bot_id,group_id,member_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?,?)";
    private static final String SQL_C2C =
            "INSERT INTO xuanji_qqbot_c2c_message (direction,bot_id,user_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?)";
    private static final String SQL_EVENT =
            "INSERT INTO xuanji_qqbot_event (direction,bot_id,event_type,group_id,raw_json) VALUES (?,?,?,?,?)";
    private static final String SQL_ONEBOT_GROUP =
            "INSERT INTO xuanji_onebot_group_message (direction,bot_id,group_id,member_id,msg_type,content,raw_json) VALUES (?,?,?,?,?,?,?)";
    private static final String SQL_ONEBOT_EVENT =
            "INSERT INTO xuanji_onebot_event (direction,bot_id,event_type,group_id,raw_json) VALUES (?,?,?,?,?)";

    private static BatchLogWriter batchWriter;
    private static JdbcTemplate fallbackJdbc;

    public MessageLogger(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbc,
                         BatchLogWriter batchLogWriter) {
        MessageLogger.fallbackJdbc = logJdbc;
        MessageLogger.batchWriter = batchLogWriter;
        // 注册批量 SQL（BatchLogWriter 内部按表缓存）
        if (batchLogWriter != null) {
            batchLogWriter.registerSql("xuanji_qqbot_group_message", SQL_GROUP);
            batchLogWriter.registerSql("xuanji_qqbot_c2c_message", SQL_C2C);
            batchLogWriter.registerSql("xuanji_qqbot_event", SQL_EVENT);
            batchLogWriter.registerSql("xuanji_onebot_group_message", SQL_ONEBOT_GROUP);
            batchLogWriter.registerSql("xuanji_onebot_event", SQL_ONEBOT_EVENT);
        }
    }

    /** 记录群聊消息 */
    public static void groupMessage(String direction, String botId, String groupId,
                                     String memberId, String msgType, String content, String rawJson) {
        if (batchWriter != null) {
            batchWriter.enqueue("xuanji_qqbot_group_message", SQL_GROUP,
                    direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
            return;
        }
        // fallback：同步单条
        try {
            if (fallbackJdbc != null) {
                fallbackJdbc.update(SQL_GROUP,
                        direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
            }
        } catch (Exception ignored) {}
    }

    /** 记录单聊消息 */
    public static void c2cMessage(String direction, String botId, String userId,
                                   String msgType, String content, String rawJson) {
        if (batchWriter != null) {
            batchWriter.enqueue("xuanji_qqbot_c2c_message", SQL_C2C,
                    direction, botId, userId, msgType, truncate(content, 4000), rawJson);
            return;
        }
        try {
            if (fallbackJdbc != null) {
                fallbackJdbc.update(SQL_C2C,
                        direction, botId, userId, msgType, truncate(content, 4000), rawJson);
            }
        } catch (Exception ignored) {}
    }

    /** 记录事件 */
    public static void event(String direction, String botId, String eventType, String rawJson) {
        event(direction, botId, eventType, null, rawJson);
    }

    public static void event(String direction, String botId, String eventType, String groupId, String rawJson) {
        if (batchWriter != null) {
            batchWriter.enqueue("xuanji_qqbot_event", SQL_EVENT,
                    direction, botId, eventType, groupId, rawJson);
            return;
        }
        try {
            if (fallbackJdbc != null) {
                fallbackJdbc.update(SQL_EVENT,
                        direction, botId, eventType, groupId, rawJson);
            }
        } catch (Exception ignored) {}
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ==================== OneBot 流水（同构 QQ） ====================

    public static void onebotGroupMessage(String direction, String botId, String groupId,
                                           String memberId, String msgType, String content, String rawJson) {
        if (batchWriter != null) {
            batchWriter.enqueue("xuanji_onebot_group_message", SQL_ONEBOT_GROUP,
                    direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
            return;
        }
        try {
            if (fallbackJdbc != null) {
                fallbackJdbc.update(SQL_ONEBOT_GROUP,
                        direction, botId, groupId, memberId, msgType, truncate(content, 4000), rawJson);
            }
        } catch (Exception ignored) {}
    }

    public static void onebotEvent(String direction, String botId, String eventType, String groupId, String rawJson) {
        if (batchWriter != null) {
            batchWriter.enqueue("xuanji_onebot_event", SQL_ONEBOT_EVENT,
                    direction, botId, eventType, groupId, rawJson);
            return;
        }
        try {
            if (fallbackJdbc != null) {
                fallbackJdbc.update(SQL_ONEBOT_EVENT,
                        direction, botId, eventType, groupId, rawJson);
            }
        } catch (Exception ignored) {}
    }
}