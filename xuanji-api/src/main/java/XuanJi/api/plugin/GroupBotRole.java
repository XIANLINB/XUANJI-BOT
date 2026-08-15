package XuanJi.api.plugin;

import java.util.Map;

/**
 * 机器人在群内的角色（类型化封装，{@code Bot.getGroupBotRole()}）。
 *
 * <p>来自本地库 {@code qqbot_group_robot.member_role}（查库免限频）；
 * {@code role} 取值 owner/admin/member；未同步/不在群时为空串。
 */
public record GroupBotRole(
        String role,
        Map<String, Object> raw) {

    public static GroupBotRole from(Map<?, ?> m) {
        if (m == null) return null;
        return new GroupBotRole(
                MapKeys.str(m, "role", "ROLE", "member_role", "MEMBER_ROLE"),
                MapKeys.raw(m));
    }

    public boolean isOwner() {
        return "owner".equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    /** 是否拥有群管权限（群主或管理员）。 */
    public boolean isManager() {
        return isOwner() || isAdmin();
    }
}
