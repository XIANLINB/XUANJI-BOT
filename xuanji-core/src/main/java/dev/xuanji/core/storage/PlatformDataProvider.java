package dev.xuanji.core.storage;

import java.util.List;
import java.util.Map;

/**
 * 平台数据提供者 SPI — 各适配器实现，控制台按 platform 聚合。
 *
 * <p>core 不出现平台字样；全部按 {@code instanceId}（真实 appId / selfId）查询。
 * 缺失/不可用时实现应返回空集合或 0，不抛异常。
 */
public interface PlatformDataProvider {

    // ==================== 常量 ====================

    String CHAT_GROUP = "group";
    String CHAT_C2C = "c2c";
    String EVT_GROUP_ADD = "GROUP_ADD_ROBOT";
    String EVT_GROUP_DEL = "GROUP_DEL_ROBOT";
    String EVT_FRIEND_ADD = "FRIEND_ADD";
    String EVT_FRIEND_DEL = "FRIEND_DEL";

    // ==================== 实例 ====================

    /** 平台标识：qqbot / onebot。 */
    String platform();

    /** 该平台已注册的全部实例 id。 */
    List<String> listInstanceIds();

    /** 某实例的 bot 配置（供控制台展示，可能为 null）。 */
    Map<String, Object> getBotConfig(String instanceId);

    /** 某实例的 botinfo（头像/名称等），缺失返回空 Map。 */
    Map<String, Object> getBotInfo(String instanceId);

    /** 连接方式（websocket / webhook），未知返回空串。 */
    String getConnectionType(String instanceId);

    // ==================== 群 / 好友 / 成员 ====================

    List<Map<String, Object>> listGroups(String instanceId);

    List<Map<String, Object>> listFriends(String instanceId);

    List<Map<String, Object>> listGroupMembers(String instanceId, String groupId);

    // ==================== 消息 / 事件 ====================

    List<Map<String, Object>> listMessages(String instanceId, String chatType, int limit);

    List<Map<String, Object>> listMessagesByTarget(String instanceId, String chatType, String targetId, int limit);

    List<Map<String, Object>> listEvents(String instanceId, int limit);

    long countGroups(String instanceId);

    long countFriends(String instanceId);

    long countMessagesSince(String instanceId, String chatType, long sinceEpochSeconds);

    long countEventsSince(String instanceId, String eventType, long sinceEpochSeconds);

    long countAllEvents(String instanceId);

    // ==================== 生命周期 ====================

    void startBot(String instanceId, String envType);

    void stopBot(String instanceId);
}
