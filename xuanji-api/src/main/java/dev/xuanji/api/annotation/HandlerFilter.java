package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 消息过滤器 — 配合 {@link GroupMessageHandler} 使用，Shiro 的 {@code @MessageHandlerFilter} 同款。
 *
 * <pre>
 * // 只响应"签到"或"打卡"，且必须 @机器人
 * @HandlerFilter(cmd = "签到|打卡", at = AtMode.NEED)
 *
 * // 只响应指定群的消息
 * @HandlerFilter(groups = {"群ID1", "群ID2"})
 *
 * // 以 "!" 开头触发
 * @HandlerFilter(startWith = "!")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HandlerFilter {

    /** 命令/正则匹配（支持 {@code |} 多值） */
    String cmd() default "";

    /** @机器人模式 */
    AtMode at() default AtMode.IGNORE;

    /** 限定群 ID（空=全部群） */
    String[] groups() default {};

    /** 限定发送者 member_openid（空=全部用户） */
    String[] senders() default {};

    /** 前缀触发 */
    String startWith() default "";

    /** 后缀触发 */
    String endWith() default "";

    /** 反转过滤器（满足条件时跳过） */
    boolean invert() default false;
}
