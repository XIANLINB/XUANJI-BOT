package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 限流注解 — 标注在 @Command 或 @OnMessage 方法上。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** 时间窗口内允许的次数 */
    int count() default 5;
    /** 时间窗口（秒） */
    int seconds() default 10;
    /** 限流维度：user（每用户）/ group（每群）/ global（全局） */
    String scope() default "user";
}
