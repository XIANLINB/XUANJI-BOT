package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 消息事件监听器 — 标注一个方法为原始消息事件处理器。
 *
 * <p>比 @Command 更底层：接收未解析的 XuanJiEvent，适合非指令场景
 * （如自动回复、日志、风控等）。支持优先级、阻断和事件类型过滤。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnMessage {
    /** 关注的事件类型（如 "message/group"、"message/private"），空=所有消息 */
    String[] type() default {};
    /** 优先��� */
    int priority() default 50;
    /** 处理后是否阻断后续处理链 */
    boolean block() default false;
    /** 是否仅群聊 */
    boolean groupOnly() default false;
    /** 是否仅私聊 */
    boolean privateOnly() default false;
}
