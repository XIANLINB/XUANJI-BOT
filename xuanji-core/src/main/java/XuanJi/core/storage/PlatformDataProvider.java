package XuanJi.core.storage;

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

    /** 机器人在某群的角色（owner/admin/member）；未持久化/未知返回 null。默认不支持。 */
    default String getGroupRobotRole(String instanceId, String groupId) {
        return null;
    }

    /** 某群内各机器人的群内状态列表（一 bot 一行，盖 BOT_APPID）；默认不支持。 */
    default List<Map<String, Object>> listGroupRobotStates(String groupOpenid) {
        return List.of();
    }

    /** 标记某群消息已撤回（retracted=1）。默认空实现。 */
    default void markMessageRetracted(String instanceId, String groupId, String msgId) {
    }

    // ==================== 消息 / 事件 ====================

    List<Map<String, Object>> listMessages(String instanceId, String chatType, int limit);

    List<Map<String, Object>> listMessagesByTarget(String instanceId, String chatType, String targetId, int limit);

    /**
     * 单会话消息范围查询（控制台 contactMessages 分页/日期过滤用）：
     * {@code CREATE_TIME >= since} 且 {@code < until}（until=Long.MAX_VALUE 表示无上界）
     * 且 {@code < before}（before=Long.MAX_VALUE 表示无上界；用于上滑加载更早消息）。
     * 默认空实现，平台适配器按需覆盖。
     */
    default List<Map<String, Object>> listMessagesByTargetRange(String instanceId, String chatType, String targetId,
                                                                long sinceEpochSeconds, long untilEpochSeconds,
                                                                long beforeEpochSeconds, int limit) {
        return List.of();
    }

    List<Map<String, Object>> listEvents(String instanceId, int limit);

    /**
     * 管理操作日志（禁言/撤回/审批等出站审计流水，含失败与被拒记录）。
     * 默认空实现；平台适配器按需覆盖。filter 可含 opType / status / groupId / keyword。
     */
    default List<Map<String, Object>> listOpLogs(String instanceId, String opType, String status,
                                                 String groupId, String keyword, int limit) {
        return List.of();
    }

    long countGroups(String instanceId);

    long countFriends(String instanceId);

    long countMessagesSince(String instanceId, String chatType, long sinceEpochSeconds);

    long countEventsSince(String instanceId, String eventType, long sinceEpochSeconds);

    /**
     * 自 sinceEpochSeconds 起的全部系统事件数（不限事件类型），预警中心事件突增检查用。
     * 默认返回 0；平台适配器应覆盖。
     */
    default long countAllEventsSince(String instanceId, long sinceEpochSeconds) {
        return 0L;
    }

    long countAllEvents(String instanceId);

    /**
     * 消息按天聚合（趋势图）：自 sinceEpochSeconds 起按 {@code DAY / CHAT_TYPE / CNT} 返回。
     * 默认空实现：未实现趋势能力的适配器返回空集合，控制台自动忽略。
     */
    default List<Map<String, Object>> messageTrend(String instanceId, long sinceEpochSeconds) {
        return List.of();
    }

    /**
     * 消息方向统计（{@code {in, out}}）：自 sinceStart（epoch 秒，含）到 untilEnd（epoch 秒，不含）之间，
     * 入站（IN）/出站（OUT）消息计数。默认空实现：未实现返回 {@code {in:0, out:0}}。
     */
    default Map<String, Object> messageDirectionStats(String instanceId, long sinceStart, long untilEnd) {
        return Map.of("in", 0, "out", 0);
    }

    /**
     * 切换机器人连接方式（websocket ↔ webhook）：更新平台库的连接模式字段（QQ 适配器 = qqbot_bot.conn_mode），
     * 并同步更新内存 RobotRegistry 的 Robot.connectionMethod。默认空实现：非 QQ 平台不支持直接调用（控制台 Controller 兜底）。
     */
    default void updateConnMode(String instanceId, String mode) {
        // no-op default
    }

    /**
     * 数据中心聚合统计（默认空实现，平台适配器按需覆盖）：
     * 返回 key = heatmap / typeDist / activeGroups / activeUsers / activeBots / directionDist / eventTypeDist / dayTrend。
     * heatmap 行含 dow(1=周日..7)/hr(0-23)/cnt；typeDist 含 msgType/cnt；
     * activeGroups 含 id/name/cnt；activeUsers 含 id/name/cnt；
     * activeBots 含 appId/name/cnt；directionDist 含 direction(IN/OUT)/cnt；
     * eventTypeDist 含 eventType/cnt；dayTrend 含 date(Y-M-D)/cnt。
     */
    default Map<String, Object> stats(String instanceId, long sinceEpochSeconds) {
        return Map.of();
    }

    /**
     * 各群风控状态聚合（默认空实现，平台适配器按需覆盖）：风控中心「各群风控状态」数据源。
     * 返回行含 gid（群ID）/gname（群名，可空，前端用 ID 前 8 位兜底）/msgCnt（近 since 秒消息数）
     * /memberCnt（群成员数，未知 0）。按消息量倒序，最多 limit 行。
     */
    default List<Map<String, Object>> groupRiskStats(String instanceId, long sinceEpochSeconds, int limit) {
        return List.of();
    }

    /**
     * 群变动数据（默认空实现）。控制台「群变动 / 成员变动」统计卡用。
     * 返回 key：todayNewGroups / ydayNewGroups / todayActiveMembers / ydayActiveMembers（皆 Long）。
     */
    default Map<String, Long> groupVariation(String instanceId, long today0, long yday0, long now) {
        return new java.util.LinkedHashMap<>();
    }

    /**
     * 单聊用户变动数据（默认空实现）。控制台「用户变动」统计卡用。
     * 返回 key：todayNewFriends / ydayNewFriends / todayActiveUsers / ydayActiveUsers（皆 Long）。
     */
    default Map<String, Long> friendVariation(String instanceId, long today0, long yday0, long now) {
        return new java.util.LinkedHashMap<>();
    }

    // ==================== 生命周期 ====================

    void startBot(String instanceId, String envType);

    void stopBot(String instanceId);
}
