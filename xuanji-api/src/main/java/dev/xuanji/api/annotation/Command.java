package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 指令处理器 — 标注一个方法为聊天指令。
 *
 * <p>支持五种匹配模式：精确/前缀/后缀/包含/正则。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    /** 指令名 / 匹配模式 */
    String value();
    /** 匹配模式（默认精确） */
    Match match() default Match.EXACT;
    /** 冷却时间（秒） */
    int cooldown() default 0;
    /** 所需最低角色 */
    String role() default "MEMBER";
    /** 优先级（越大越优先） */
    int priority() default 0;
    /** 描述 */
    String description() default "";

    enum Match {
        EXACT, PREFIX, SUFFIX, CONTAINS, REGEX
    }
}
