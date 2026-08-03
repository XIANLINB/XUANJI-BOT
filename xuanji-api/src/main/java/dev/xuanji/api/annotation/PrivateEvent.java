package dev.xuanji.api.annotation;
import java.lang.annotation.*;
/** 标记方法接收私聊事件。 */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface PrivateEvent { int order() default 0; /** 限定平台（空=全部平台） */ String[] platforms() default {}; }
