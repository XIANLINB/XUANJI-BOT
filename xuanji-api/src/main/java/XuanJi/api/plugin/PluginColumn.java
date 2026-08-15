package XuanJi.api.plugin;

import java.lang.annotation.*;

/**
 * 标注实体字段如何映射为 H2 列。未标注 {@link PluginColumn} 的字段<b>不参与持久化</b>
 * （视为瞬态/辅助字段）。
 *
 * <p>物理列名缺省取字段名；可经 {@link #name()} 覆盖。列名/类型须经框架加载期白名单校验。
 *
 * <h3>限制（安全基线，由框架强制）</h3>
 * <ul>
 *   <li>实体字段总数 ≤ 64；声明 {@link #index()} 的字段 ≤ 4。</li>
 *   <li>{@code VARCHAR} 长度 ≤ 8192（超长请用 {@link ColumnType#TEXT}）。</li>
 *   <li>{@link ColumnType#BLOB} 禁用。</li>
 * </ul>
 *
 * @see PluginEntity
 * @see ColumnType
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginColumn {
    /** 物理列名，缺省取字段名。须匹配 {@code ^[a-z][a-z0-9_]{0,63}$} */
    String name() default "";

    /** 显式列类型；缺省 {@link ColumnType#AUTO} 按 Java 类型推断 */
    ColumnType type() default ColumnType.AUTO;

    /** VARCHAR 长度（仅 type=VARCHAR/AUTO(String) 时生效，≤ 8192） */
    int length() default 255;

    /** 是否可空（默认 true） */
    boolean nullable() default true;

    /** 是否为该列建二级索引（≤ 4 个，由框架强制） */
    boolean index() default false;

    /** 是否唯一约束 */
    boolean unique() default false;

    /** 列注释（仅元信息用途，不影响 DDL 正确性） */
    String comment() default "";
}
