package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 指令处理器 — 标注一个方法为聊天指令。
 *
 * <p>框架启动时扫描所有插件中的 @Command 方法，建立指令→处理器的映射表。
 * 支持声明式冷却、权限、作用域约束。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    /** 指令名（不含前缀，用户输入匹配此值） */
    String value();
    /** 冷却时间（秒），同一用户在此时间内不可再次触发 */
    int cooldown() default 0;
    /** 所需最低角色（OWNER / ADMIN / MEMBER） */
    String role() default "MEMBER";
    /** 优先级（数值越大越优先匹配） */
    int priority() default 0;
    /** 匹配后是否阻断后续处理链 */
    boolean block() default true;
    /** 指令别名 */
    String[] alias() default {};
    /** 帮助说明 */
    String description() default "";
}
