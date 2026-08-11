package XuanJi.api.plugin;

import java.util.Map;

/**
 * 机器人在群内的状态（类型化封装，{@link PluginServices#getBotGroupState}）。
 *
 * <p>{@code botState} 平台语义：1=正常，2=机器人被移出群，3=机器人被群解散，4=机器人被禁言。
 * {@link #isOnline()} 即 {@code botState == 1}。
 */
public record BotGroupState(
        int botState,
        String groupId,
        String groupName,
        Integer memberCount,
        Map<String, Object> raw) {

    public static BotGroupState from(Map<?, ?> m) {
        if (m == null) return null;
        Integer st = MapKeys.intVal(m, "bot_state", "botState", "BOT_STATE");
        return new BotGroupState(
                st == null ? 0 : st,
                MapKeys.str(m, "group_id", "groupId", "GROUP_ID"),
                MapKeys.str(m, "group_name", "name", "GROUP_NAME"),
                MapKeys.intVal(m, "member_count", "memberCount", "MEMBER_COUNT"),
                MapKeys.raw(m));
    }

    /** 是否在线（bot_state == 1）。 */
    public boolean isOnline() {
        return botState == 1;
    }
}
