package XuanJi.api.plugin;

import java.util.Map;

/**
 * 群信息（类型化封装）。
 *
 * <p>同时兼容两个数据源：
 * <ul>
 *   <li>远程查询 {@link PluginServices#getGroupInfo}（平台返回 {@code name / member_count / owner_id}）</li>
 *   <li>本地查询 {@link PluginServices#getLocalGroupInfo}（{@code found / group_name / member_count}，高频免限频）</li>
 * </ul>
 *
 * <p>{@link #raw()} 保留平台/本地库完整原始字段，字段扩展时可自行读取。
 */
public record GroupInfo(
        String groupId,
        String groupName,
        String ownerId,
        Integer memberCount,
        Integer memberMax,
        boolean found,
        Map<String, Object> raw) {

    public static GroupInfo from(Map<?, ?> m) {
        if (m == null) return null;
        return new GroupInfo(
                MapKeys.str(m, "group_id", "groupId", "GROUP_ID"),
                MapKeys.str(m, "group_name", "name", "GROUP_NAME"),
                MapKeys.str(m, "owner_id", "ownerId", "OWNER_ID"),
                MapKeys.intVal(m, "member_count", "memberCount", "MEMBER_COUNT"),
                MapKeys.intVal(m, "member_max", "max_member_count", "memberMax", "MEMBER_MAX"),
                MapKeys.bool(m, "found"),
                MapKeys.raw(m));
    }
}
