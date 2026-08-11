package XuanJi.api.llm;

import java.lang.annotation.*;

/**
 * LLM 工具注解 —— 把任意 Spring Bean 方法暴露给大模型作为 Function Calling 工具。
 *
 * <p>P0 仅落注解壳（元数据定义），P2 由 {@code ToolRegistry} 扫描启用并参与
 * {@code LlmProvider.chat} 的工具调用循环。插件方法标了本注解后同样可被 AI 调用
 * （SPI 在 api 层、插件可见的原因）。
 *
 * <pre>{@code
 * @LlmTool(name = "查询群统计", description = "查询指定群最近 N 天消息统计", confirm = false)
 * public String groupStats(@ToolParam("群ID") String groupId,
 *                          @ToolParam("天数") int days) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LlmTool {

    /** 工具名（给模型看的标识，建议简短），默认取方法名。 */
    String name() default "";

    /** 工具用途描述，模型据此决定何时调用；描述越具体调用越准。 */
    String description() default "";

    /** 中文释义（仅控制台工具清单展示用，不发给模型），如「建定时任务 / 生成图片」。 */
    String descriptionZh() default "";

    /** 是否要求用户显式确认后执行。危险操作（发消息/改配置/建任务）应置 true。 */
    boolean confirm() default true;
}
