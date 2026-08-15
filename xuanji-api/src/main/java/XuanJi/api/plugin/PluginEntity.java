package XuanJi.api.plugin;

import java.lang.annotation.*;

/**
 * 标记一个插件实体类（POJO）。框架在插件加载时扫描 classpath 上带本注解的类，
 * 自动生成并维护 H2 表 {@code plugin_{pluginId}_{table}}，并提供类型安全的 CRUD。
 *
 * <p>实体需为<b>无参构造 + 字段可读写</b>的普通 POJO（字段用 {@link PluginColumn} 标注，
 * 主键用 {@link PluginId} 标注）。框架仅做字段映射，不会调用实体的任意方法。
 *
 * <p><b>硬约束</b>：一个插件<b>最多声明 1 个</b> {@code @PluginEntity}，否则框架拒绝加载该插件。
 *
 * <p>示例：
 * <pre>{@code
 * @PluginEntity(table = "signin")
 * public class SigninRecord {
 *     @PluginId long groupId;
 *     @PluginId long userId;
 *     @PluginColumn int coins;
 *     @PluginColumn int points;
 *     @PluginColumn(length = 32) String date;
 * }
 * }</pre>
 *
 * @see PluginColumn
 * @see PluginId
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginEntity {
    /** 逻辑表名；物理表名 = {@code plugin_{pluginId}_{table}}。须匹配 {@code ^[a-z][a-z0-9_]{0,63}$} */
    String table();

    /** 实体结构版本，仅在迁移登记时使用（升级结构请用新增列而非改名/删列）。 */
    int version() default 1;
}
