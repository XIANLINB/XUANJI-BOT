package XuanJi.adapter.qqbot.storage;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK XuanJiBot 接口的统计数据源（getGroupCount / getUserCount / getBotInfo / today* 等）。
 *
 * <p>v3.3 全部走 per-bot 实例库（qqbot_group / qqbot_user / qqbot_botinfo / qqbot_event），
 * 通过 {@link QqBotRepository} 查询，不再直连框架库旧表（xuanji_qqbot_* 已废弃）。
 */
@Component
public class BotDataQuery {

    private static QqBotRepository repo;

    public BotDataQuery(QqBotRepository qqBotRepository) {
        BotDataQuery.repo = qqBotRepository;
    }

    // ==================== 计数 ====================

    public static int groupCount(String appId) {
        return repo == null ? 0 : (int) repo.countGroups(appId);
    }

    public static int userCount(String appId) {
        return repo == null ? 0 : (int) repo.countUsers(appId);
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

    /** 某群今日加入人数（按事件类型统计，忽略群维度） */
    public static int todayGroupMemberAdd(String appId, String groupId) {
        return todayEventCount(appId, "GROUP_MEMBER_ADD");
    }

    /** 某群今日退出人数（按事件类型统计，忽略群维度） */
    public static int todayGroupMemberDel(String appId, String groupId) {
        return todayEventCount(appId, "GROUP_MEMBER_REMOVE");
    }

    /** XuanJiBot 基础信息（来自平台库 qqbot_botinfo：name/avatar/share_url）。 */
    public static Map<String, String> botInfo(String appId) {
        Map<String, String> out = new LinkedHashMap<>();
        if (repo == null || appId == null) return out;
        try {
            for (Map.Entry<String, Object> e : repo.getBotInfo(appId).entrySet()) {
                out.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 当天 0 点（UTC+8）起的指定类型事件数。 */
    private static int todayEventCount(String appId, String eventType) {
        if (repo == null || appId == null) return 0;
        try {
            long now = XuanJi.core.util.TimeUtils.nowEpochSeconds();
            long dayStart = now - Math.floorMod(now + 8 * 3600L, 86400L);
            return (int) repo.countEventsSince(appId, List.of(eventType), dayStart);
        } catch (Exception e) {
            return 0;
        }
    }
}
