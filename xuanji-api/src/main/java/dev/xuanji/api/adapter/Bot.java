package dev.xuanji.api.adapter;

import java.util.Set;

/**
 * 机器人实例 — 表示一个已连接的 bot，由 BotManager 管理生命周期。
 */
public record Bot(
        /** 实例 ID（格式：{@code platform:appId}，如 {@code qq:102000xxx}） */
        String id,

        /** 平台标识（"qq"/"onebot"/"feishu"…） */
        String platform,

        /** 平台端机器人账号 ID */
        String selfId,

        /** 当前连接状态 */
        Status status,

        /** 适配器声明的能力位（如 can_recall, can_ban 等） */
        Set<String> capabilities
) {
    public enum Status { CONNECTING, ONLINE, OFFLINE, ERROR }

    public boolean isOnline() { return status == Status.ONLINE; }
}
