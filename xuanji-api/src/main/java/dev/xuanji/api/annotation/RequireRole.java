package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 权限要求 — 标注在 @Command 或 @OnMessage 方法上。
 *
 * <p>裁决顺序：黑名单 → L0 特权 → L1 平台角色 → L3 权限点。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 所需角色：BOT_MASTER / SUPER_ADMIN / OWNER / ADMIN / MEMBER */
    String value() default "MEMBER";
    /** 所需权限点（如 "sign.manage"），与角色 OR 关系 */
    String[] permissions() default {};
}
