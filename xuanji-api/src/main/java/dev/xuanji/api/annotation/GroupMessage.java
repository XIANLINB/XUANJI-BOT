package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/** 标记方法接收群聊消息事件。不写此注解则收不到群聊消息。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GroupMessage {
    int order() default 0;
}
