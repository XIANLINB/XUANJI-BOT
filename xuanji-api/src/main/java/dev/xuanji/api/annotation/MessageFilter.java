package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 消息过滤器 — 配合 {@link GroupMessage} / {@link PrivateMessage} 使用。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageFilter {
    /** 触发命令（支持正则，如 "签到|打卡"） */
    String cmd() default "";
    /** @机器人模式 */
    AtMode at() default AtMode.IGNORE;
    /** 限定群 ID（空=全部群） */
    String[] groups() default {};
    /** 限定发送者 member_openid */
    String[] senders() default {};
    /** 前缀触发 */
    String startWith() default "";
    /** 后缀触发 */
    String endWith() default "";
    /** 反转过滤器（满足条件时跳过） */
    boolean invert() default false;
    /** 限定角色（空=不限制，如 {"owner","admin"}） */
    String[] roles() default {};
}
