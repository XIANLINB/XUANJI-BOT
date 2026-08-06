package dev.xuanji.adapter.qqbot.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器人实体类
 *
 * <p>对应数据库中的机器人信息表，存储机器人的基本配置和凭证。
 * 是系统中最核心的业务实体，被多个模块引用。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link dev.xuanji.adapter.qqbot.registry.RobotRegistry} — 注册表中缓存机器人配置</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.webhook.WebhookController} — Webhook 入口查找机器人</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.api.QqApiService} — API 调用时获取凭证</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.websocket.QqBotWsManager} — WebSocket 连接时获取凭证</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>id — 机器人唯一标识（数据库主键）</li>
 *   <li>userId — 所属用户 ID（多用户系统中用于隔离）</li>
 *   <li>appId / appSecretEncrypted — QQ 开放平台的应用凭证</li>
 *   <li>activeEnv — 当前激活的环境类型（SANDBOX / PRODUCTION）</li>
 *   <li>status — 机器人状态（1=正常，其他=停用）</li>
 * </ul>
 *
 * @see RobotEnvironment 机器人的环境配置（每个机器人可有多个环境）
 * @see RobotRegistry    机器人注册表（内存缓存）
 */
@Data
public class Robot {

    /** 机器人唯一标识（数据库主键），在 {@link RobotRegistry} 中作为 Map 的 key */
    private String id;

    /** 所属用户 ID，用于多用户系统中的数据隔离 */
    private Long userId;

    /** QQ 开放平台的 AppID，用于 API 调用和 WebSocket 鉴权 */
    private String appId;

    /**
     * QQ 开放平台的 AppSecret（加密存储）
     * <p>在当前框架模式下，该字段存储的是明文密钥。
     * 使用前需要通过 {@link dev.xuanji.starter.security.util.AesUtil} 解密。
     */
    private String appSecretEncrypted;

    /** 机器人名称（用户自定义，用于展示） */
    private String robotName;

    /** 机器人头像 URL */
    private String robotAvatar;

    /** QQ 平台分配的 Bot ID */
    private String botId;

    /** 机器人分享链接 */
    private String shareUrl;

    /** 欢迎消息（用户首次添加机器人时发送） */
    private String welcomeMsg;

    /** 机器人描述信息 */
    private String description;

    /**
     * 当前激活的环境类型
     * <p>取值 "SANDBOX" 或 "PRODUCTION"，决定使用哪个环境的配置和连接。
     * 为空时默认使用 "SANDBOX"。
     */
    private String activeEnv;

    /**
     * 机器人状态
     * <p>1 = 正常运行，其他值表示已停用。
     * Webhook 和 WebSocket 处理时会检查此字段，停用的机器人不处理事件。
     */
    private Integer status;

    /**
     * 是否使用沙箱环境
     * <p>true = 使用沙箱环境，false = 使用正式环境
     */
    private Boolean isSandbox;

    /**
     * 连接方式
     * <p>取值 "websocket" 或 "webhook"，决定使用哪种方式接收 QQ 平台事件
     */
    private String connectionMethod;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
