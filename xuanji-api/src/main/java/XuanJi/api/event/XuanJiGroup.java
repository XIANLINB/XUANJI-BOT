package XuanJi.api.event;

import java.time.Instant;

/**
 * 群档案 — 每个 bot 实例在各个群中的状态记录。
 *
 * <p>同一物理群被多个 bot 共用时，各 bot 维护独立档案（数据三级域隔离）。
 * 事件流入时框架影子同步自动维护。
 */
public record XuanJiGroup(
        /** 群在框架内的唯一 ID */
        String id,

        /** 所属 bot 的配置别名（追踪数据归属） */
        String botKey,

        /** 平台原始群号 */
        String groupId,

        /** 群名称 */
        String name,

        /** 当前成员数 */
        int memberCount,

        /** 档案首次记录时间 */
        Instant createdAt
) {}
