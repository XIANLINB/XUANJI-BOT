package XuanJi.api.plugin;

import java.lang.annotation.*;

/**
 * 标记实体的业务主键字段。可标注多个字段组成<b>复合主键</b>。
 *
 * <p>标注本注解的字段用于 {@code save}/{@code findById}/{@code deleteById} 的 upsert 与定位。
 * 框架还会额外维护一个托管自增列 {@code row_id BIGINT AUTO_INCREMENT PRIMARY KEY}
 * （便于排序/引用），与业务主键互不冲突。
 *
 * <p>与 {@link PluginAutoId} 的区别：{@code @PluginId} 是<b>业务键</b>（由插件赋值、可复合）；
 * {@code @PluginAutoId} 是<b>框架托管自增列</b>（单列、自动生成）。二者可并存。
 *
 * @see PluginEntity
 * @see PluginAutoId
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginId {
}
