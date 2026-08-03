package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类为璇玑插件。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XuanjiPlugin {
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

    enum Perm {
        /** 联网（调用外部 API） */
        NETWORK,
        /** 读写文件系统 */
        FILESYSTEM,
        /** 主动发送消息（非事件响应） */
        PROACTIVE_MESSAGE
    }
}
