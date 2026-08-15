package XuanJi.api.plugin;

import java.lang.annotation.*;

/**
 * 标记一个<b>框架托管自增主键</b>字段（单列 {@code BIGINT AUTO_INCREMENT}）。
 *
 * <p>与 {@link PluginId}（业务复合主键）不同，{@code @PluginAutoId} 由数据库自动生成，
 * 插件无需（也不应）手动赋值；常用于「只需要一条自增 id 即可」的简单实体。
 *
 * <p>一个实体至多标注一个 {@code @PluginAutoId}。框架建表时也会自动追加 {@code row_id}
 * 作为内部排序/引用键，二者可共存——若你既想要业务主键又想要自增 id，请优先用
 * {@code @PluginId}（业务键）+ 自动 {@code row_id}（内部键）的组合，而非本注解。
 *
 * @see PluginId
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginAutoId {
}
