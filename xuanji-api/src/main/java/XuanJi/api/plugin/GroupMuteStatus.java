package XuanJi.api.plugin;

import java.util.Map;

/**
 * 群禁言状态（类型化封装，{@link PluginServices#getGroupMuteStatus}）。
 *
 * <p>来自平台 {@code GET /v2/groups/{openid}/restrict_chat_setting}；字段缺失时返回 null/0，
 * 插件可依据 {@link #isMuted()} 快速判断。
 */
public record GroupMuteStatus(
        String muteExpireAt,
        Long muteSecondLeft,
        Map<String, Object> raw) {

    public static GroupMuteStatus from(Map<?, ?> m) {
        if (m == null) return null;
        return new GroupMuteStatus(
                MapKeys.str(m, "mute_expire_at", "muteExpireAt", "MUTE_EXPIRE_AT"),
                MapKeys.longVal(m, "mute_second_left", "muteSecondLeft", "MUTE_SECOND_LEFT"),
                MapKeys.raw(m));
    }

    /** 是否处于禁言状态（剩余禁言秒数 &gt; 0，或到期时间非空）。 */
    public boolean isMuted() {
        if (muteSecondLeft != null && muteSecondLeft > 0) return true;
        return muteExpireAt != null && !muteExpireAt.isBlank();
    }
}
