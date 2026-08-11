package XuanJi.api.llm;

import java.lang.annotation.*;

/**
 * LLM 工具参数注解 —— 标注 {@link LlmTool} 方法的参数，为模型提供参数名与语义描述。
 *
 * <p>参数类型从方法签名自动推断（String→string / int-long→integer / double-float→number /
 * boolean→boolean / List→array）。参数名优先取 {@link #name()}（推荐显式指定，避免依赖
 * {@code -parameters} 编译选项），否则用反射参数名。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LlmToolParam {

    /** 参数说明，模型据此理解该传什么值。 */
    String value() default "";

    /** 显式参数名（工具 JSON 里的 key），推荐填写；为空则用反射参数名。 */
    String name() default "";

    /** 是否必填，默认 true。 */
    boolean required() default true;
}
