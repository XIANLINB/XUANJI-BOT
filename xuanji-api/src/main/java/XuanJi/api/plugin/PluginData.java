package XuanJi.api.plugin;

/**
 * 插件获取结构化存储能力的统一入口。在命令/定时任务方法声明本类型参数，
 * 由框架按当前插件 id 自动注入（与 {@link PluginStorage} 注入机制一致）。
 *
 * <p>通过 {@link #repo(Class)} 拿到类型安全的 {@link PluginRepository}；
 * 仍可通过 {@link #storage()} 访问既有的 KV 存储（兼容老用法）。
 *
 * <p>示例：
 * <pre>{@code
 * @Command("签到榜")
 * void leaderboard(GroupMessageEvent e, PluginData data, @Arg("群") long gid) {
 *     var repo = data.repo(SigninRecord.class);
 *     List<SigninRecord> top = repo.query(
 *         Query.where("group_id").eq(gid).orderBy("coins").desc().limit(10));
 *     // 渲染 top ...
 * }
 * }</pre>
 *
 * @see PluginRepository
 * @see PluginStorage
 */
public interface PluginData {

    /**
     * 获取某实体类的类型安全仓储。同一插件多次调用返回同一实例（按实体类缓存）。
     *
     * @param entityClass 带 {@link PluginEntity} 注解的 POJO 类型
     * @param <T> 实体类型
     */
    <T> PluginRepository<T> repo(Class<T> entityClass);

    /** 既有 KV 存储（与结构化存储并存）。 */
    PluginStorage storage();
}
