package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类为璇玑插件。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XuanJiPlugin {
    /** 插件唯一 ID（推荐 {@code group:pluginId} 格式，全局唯一） */
    String id();
    /** 显示名称 */
    String name() default "";
    /** 版本号 */
    String version() default "1.0.0";
    /** 作者 */
    String author() default "";
    /** 描述 */
    String description() default "";
    /** 权限声明 */
    Perm[] permissions() default {};
    /** 依赖的能力（如 EconomyService） */
    String[] dependsOn() default {};
    /** 消息触发频率限制（秒，0=不限制，5=同一用户5秒内仅触发一次） */
    int rateLimit() default 0;
    /** 插件级平台默认（空=全部平台）；方法级 @GroupMessage(platforms=...) 可覆盖 */
    String[] platforms() default {};

    /**
     * 插件默认机器人（botKey，如 {@code qq:1905134745}）。
     *
     * <p>用于<b>非事件场景</b>的主动发送 / 定时任务：事件驱动的消息链路默认自动携带
     * 对应机器人的 botKey（A 收到 A 发），无需声明；但当插件要在
     * {@link #onEnable()} 主动推送、或 {@code @Scheduled} 定时任务里发消息时，
     * 没有「当前事件」可借 botKey，必须显式指定用哪个机器人。
     *
     * <p>空串=不指定（由框架按默认平台挑一个机器人）；多机器人部署下建议显式声明，
     * 否则定时任务随机落到某机器人可能不符合预期。
     */
    String defaultBot() default "";

    enum Perm {
        /** 联网（调用外部 API） */
        NETWORK,
        /** 读写文件系统 */
        FILESYSTEM,
        /** 主动发送消息（非事件响应） */
        PROACTIVE_MESSAGE,
        /** 群管/撤回/审批等写操作（禁言/踢人/撤回/入群审批等） */
        GROUP_ADMIN
    }
}
