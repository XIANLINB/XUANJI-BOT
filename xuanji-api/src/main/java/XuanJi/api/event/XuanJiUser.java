package XuanJi.api.event;

import java.time.Instant;

/**
 * 统一用户档案 — 跨平台、跨 bot 实例的用户身份单元。
 *
 * <p>通过 {@link XuanJi.api.adapter.XuanJiUserBinding} 实现多平台账号归属归一。
 * 事件流入时由框架影子同步自动维护，插件不直接构造。
 */
public record XuanJiUser(
        /** 框架内部唯一用户 ID */
        String id,

        /** 平台原始用户 ID（如 QQ 号） */
        String platformUserId,

        /** 用户显示昵称 */
        String nickname,

        /** 框架级角色（BOT_MASTER / SUPER_ADMIN，普通用户为 null） */
        String frameworkRole,

        /** 自定义权限值（Koishi 风格 authority，数值越大权限越高，默认 0） */
        int authority,

        /** 档案首次创建时间 */
        Instant createdAt
) {}
