package dev.xuanji.core.storage.log;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 统一消息日志服务 —— OUT 日志唯一入口。
 *
 * <p>所有 bot.reply*() / 处理器返回 String 的 OUT 消息均通过本服务记录，
 * 消除 GroupMessageHandler / QqXjBot / C2cXjBot 中的重复日志代码。
 */
@Service
public class MessageLogService {

    private final JdbcTemplate logJdbc;

    public MessageLogService(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbc) {
        this.logJdbc = logJdbc;
    }

    /** 记录群聊 OUT 消息 */
    public void logGroupOut(String appId, String groupId, String memberId, String msgType, String content, String rawJson) {
        try {
            logJdbc.update(
                "INSERT INTO xuanji_qqbot_group_message (direction, bot_id, group_id, member_id, msg_type, content, raw_json) VALUES (?,?,?,?,?,?,?)",
                "OUT", appId, groupId, memberId, msgType, content != null ? content : "", rawJson != null ? rawJson : ""
            );
        } catch (Exception ignored) {}
    }

    /** 记录单聊 OUT 消息 */
    public void logC2cOut(String appId, String userId, String msgType, String content, String rawJson) {
        try {
            logJdbc.update(
                "INSERT INTO xuanji_qqbot_c2c_message (direction, bot_id, user_id, msg_type, content, raw_json) VALUES (?,?,?,?,?,?)",
                "OUT", appId, userId, msgType, content != null ? content : "", rawJson != null ? rawJson : ""
            );
        } catch (Exception ignored) {}
    }
}
