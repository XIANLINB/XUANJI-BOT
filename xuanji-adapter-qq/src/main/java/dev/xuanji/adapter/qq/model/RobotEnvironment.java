package dev.xuanji.adapter.qq.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器人环境配置实体类
 *
 * <p>对应数据库中的机器人环境配置表，存储每个机器人在不同环境（SANDBOX/PRODUCTION）下的配置。
 * 每个机器人可以有多个环境配置，通过 {@code robotId + envType} 唯一标识。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link dev.xuanji.adapter.qq.registry.RobotRegistry} — 注册表中缓存环境配置</li>
 *   <li>{@link dev.xuanji.adapter.qq.webhook.SignatureVerifier} — 使用 ed25519Secret 进行签名验证</li>
 *   <li>{@link dev.xuanji.adapter.qq.webhook.WebhookServiceImpl} — 使用环境配置处理事件</li>
 * </ul>
 *
 * <h3>连接模式</h3>
 * <p>connectMode 字段决定机器人使用哪种方式接收事件：
 * <ul>
 *   <li>WEBSOCKET — 通过 WebSocket 长连接接收事件</li>
 *   <li>WEBHOOK — 通过 HTTP 回调接收事件</li>
 * </ul>
 *
 * @see Robot           机器人基本信息
 * @see RobotRegistry   机器人注册表（内存缓存）
 */
@Data
public class RobotEnvironment {

    /** 环境配置唯一标识（数据库主键） */
    private Long id;

    /** 所属机器人 ID（关联 Robot.id） */
    private Long robotId;

    /**
     * 环境类型
     * <p>取值 "SANDBOX"（沙箱环境）或 "PRODUCTION"（正式环境）。
     * 与 robotId 组合唯一标识一条环境配置。
     */
    private String envType;

    /**
     * Webhook 回调地址
     * <p>QQ 平台推送事件的目标 URL，格式如 https://your-domain.com/webhook/xuanji/{robotId}
     * 仅在 connectMode 为 WEBHOOK 时使用。
     */
    private String webhookUrl;

    /**
     * Webhook 回调地址注册状态
     * <p>0 = 未注册，1 = 已注册。
     * 标记回调地址是否已在 QQ 平台注册成功。
     */
    private Integer webhookRegistered;

    /**
     * Ed25519 密钥（AES 加密存储）
     * <p>用于 Webhook 签名验证。在 {@link dev.xuanji.adapter.qq.webhook.SignatureVerifier}
     * 注册时通过 {@link dev.xuanji.starter.security.util.AesUtil} 解密。
     * 密钥以 botSecret 的 UTF-8 字节作为 Ed25519 私钥种子。
     */
    private String ed25519SecretEncrypted;

    /**
     * 最后一次收到回调的时间
     * <p>用于监控 Webhook 连接的活跃度，长时间未收到回调可能表示连接异常。
     */
    private LocalDateTime lastCallbackAt;

    /**
     * 连接模式
     * <p>决定机器人使用哪种方式接收 QQ 平台的事件推送：
     * <ul>
     *   <li>WEBSOCKET — 通过 WebSocket 长连接接收（推荐，延迟低）</li>
     *   <li>WEBHOOK — 通过 HTTP 回调接收（需要公网可访问的 URL）</li>
     * </ul>
     */
    private String connectMode;

    /**
     * 环境配置状态
     * <p>1 = 正常启用，其他值表示已禁用。
     */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
