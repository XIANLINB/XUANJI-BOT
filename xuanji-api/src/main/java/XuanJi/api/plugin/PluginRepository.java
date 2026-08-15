package XuanJi.api.plugin;

import java.util.List;
import java.util.Optional;

/**
 * 插件结构化数据的类型安全 CRUD 仓储。由框架按插件 id + 实体类自动创建，
 * 插件通过 {@link PluginData#repo(Class)} 获取，<b>全程零 SQL</b>。
 *
 * <p>所有读写都限定在本插件自己的表 {@code plugin_{pluginId}_{table}} 内，
 * 插件无法触碰框架表或其它插件表。值一律参数化，杜绝注入。
 *
 * <p>示例：
 * <pre>{@code
 * var repo = data.repo(SigninRecord.class);
 * repo.save(record);                                  // 按业务主键 upsert
 * Optional<SigninRecord> r = repo.findById(gid, uid);
 * List<SigninRecord> top = repo.query(
 *     Query.where("coins").ge(100).and("group_id").eq(gid)
 *          .orderBy("coins").desc().limit(10));
 * Page<SigninRecord> p = repo.page(PageReq.of(1, 20).orderBy("signtime", true));
 * }</pre>
 *
 * @param <T> 实体类型（带 {@link PluginEntity} 注解的 POJO）
 */
public interface PluginRepository<T> {

    /* ===================== 写 ===================== */

    /**
     * 按 {@link PluginId} 业务主键 upsert（存在则更新、不存在则插入）。
     * 返回被保存的实体（自增列/托管字段会被回填）。
     */
    T save(T entity);

    /** 按主键更新非空字段，返回受影响行数。 */
    int update(T entity);

    /** 按业务主键删除（复合主键传多个值，顺序与实体 {@code @PluginId} 字段声明一致）。 */
    int deleteById(Object... ids);

    /** 按查询条件删除，返回删除行数。 */
    int deleteBy(Query q);

    /* ===================== 读 ===================== */

    /** 按主键查询（复合主键传多个值）。 */
    Optional<T> findById(Object... ids);

    /** {@link #findById} 的空指针友好版，查不到返回 null。 */
    T findByIdOrNull(Object... ids);

    /** 读取全部行（大表慎用，框架会封顶返回行数）。 */
    List<T> findAll();

    /** 按单字段等值查询（字段名须为实体已知列）。 */
    List<T> findBy(String field, Object value);

    /** 按构造好的 {@link Query} 条件/排序/limit 查询。 */
    List<T> query(Query q);

    /** 总行数。 */
    long count();

    /** 按条件计数。 */
    long countBy(Query q);

    /** 分页 + 排序 + 条件查询。 */
    Page<T> page(PageReq req);
}
