package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类为璇玑插件。
 *
 * <p>启动时框架自动扫描并加载；可与 PF4J、XPBP sidecar 等插件运行时配合使用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XuanjiPlugin {
    /** 插件唯一 ID（推荐 {@code group:pluginId} 格式） */
    String id();
    /** 显示名称 */
    String name() default "";
    /** 版本号 */
    String version() default "1.0.0";
}
