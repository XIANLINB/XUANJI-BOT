package XuanJi.api.annotation;

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
    /** 限定平台（空=不限制，如 {"qq"} / {"onebot"}） */
    String[] platforms() default {};

    /**
     * 富媒体模式：{@code NEED}=必须含富媒体，{@code NOT}=必须纯文本，{@code IGNORE}=不限制（默认）。
     *
     * <p>纯图片消息的 content 为空串，命令式过滤（cmd/startWith）必然 miss；
     * 用本维度即可在无文本的情况下订阅到消息。
     */
    MediaMode media() default MediaMode.IGNORE;

    /**
     * 限定富媒体类型（空=任意类型）。
     *
     * <p>声明后会触发消息链懒解析以判定具体类型；未声明则仅按附件标记判定，零解析开销。
     */
    MediaType[] mediaTypes() default {};
}
