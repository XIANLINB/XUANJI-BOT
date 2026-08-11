package XuanJi.api.plugin;

import java.util.Map;

/**
 * 单聊用户（类型化封装，{@link PluginServices#listUsers}）。
 *
 * <p>来自本地库 {@code qqbot_user}（查库免限频）。
 */
public record UserInfo(
        String userId,
        String nickname,
        String remark,
        String unionOpenid,
        Long joinTime,
        Map<String, Object> raw) {

    public static UserInfo from(Map<?, ?> m) {
        if (m == null) return null;
        return new UserInfo(
                MapKeys.str(m, "platform_user_id", "userId", "user_openid", "PLATFORM_USER_ID"),
                MapKeys.str(m, "nickname", "NICKNAME"),
                MapKeys.str(m, "remark", "REMARK"),
                MapKeys.str(m, "union_openid", "unionOpenid", "UNION_OPENID"),
                MapKeys.longVal(m, "join_time", "joinTime", "JOIN_TIME"),
                MapKeys.raw(m));
    }
}
