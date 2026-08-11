package XuanJi.api.annotation;
import java.lang.annotation.*;
/** 标记方法接收群事件（成员加群/退群等）。 */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface GroupEvent { int order() default 0; /** 限定平台（空=全部平台） */ String[] platforms() default {}; }
