package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 指令参数 — 标注 @Command 方法的参数。
 *
 * <p>框架自动从用户消息中解析参数，支持类型转换（String/int/Long/LocalDate 等）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Arg {
    /** 参数名（显示在帮助信息中） */
    String value();
    /** 是否必填，默认 true */
    boolean required() default true;
    /** 缺少参数时的提示 */
    String missing() default "";
    /** 取剩余全部 token（含空格）作为整体参数，如提示语内容；仅支持最后一个 @Arg */
    boolean rest() default false;
}
