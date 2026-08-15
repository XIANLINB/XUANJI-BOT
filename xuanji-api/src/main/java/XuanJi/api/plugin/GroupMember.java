package XuanJi.api.plugin;

import java.util.Map;

/**
 * 群成员（类型化封装，{@code Bot.listGroupMembers()}）。
 *
 * <p>来自本地库 {@code qqbot_group_member}（查库免限频）；{@code role} 为 owner/admin/member。
 */
public record GroupMember(
        String memberId,
        String nickname,
        String role,
        Long joinTime,
        Map<String, Object> raw) {

    public static GroupMember from(Map<?, ?> m) {
        if (m == null) return null;
        return new GroupMember(
                MapKeys.str(m, "member_id", "memberId", "MEMBER_ID"),
                MapKeys.str(m, "nickname", "NICKNAME", "username", "USERNAME"),
                MapKeys.str(m, "role", "ROLE"),
                MapKeys.longVal(m, "join_time", "joinTime", "JOIN_TIME"),
                MapKeys.raw(m));
    }
}
