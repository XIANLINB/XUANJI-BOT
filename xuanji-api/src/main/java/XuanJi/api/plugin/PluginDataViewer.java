package XuanJi.api.plugin;

import java.util.List;
import java.util.Map;

/**
 * 框架内置数据面板用的<b>非类型化</b>读取接口。由 core 实现，供控制台/后台按
 * {@code pluginId} 查看任意插件的结构化数据——插件无需写任何代码即可获得后台表格视图。
 *
 * <p><b>隔离</b>：所有方法都以 {@code pluginId} 为信任边界，只能读到该插件自己的表；
 * 调用方须置于既有管理员/主人权限之后。
 *
 * <p>与插件侧 {@link PluginRepository} 的区别：本接口返回 {@code Map<String,Object>} 行，
 * 不做 POJO 映射（框架侧拿不到插件的实体 Class）。
 *
 * @see PluginRepository
 */
public interface PluginDataViewer {

    /** 单张表的描述（逻辑表名 + 实体类全限定名，未声明实体时为空）。 */
    class EntityTable {
        private String table;
        private String entityClass;

        public EntityTable() {}
        public EntityTable(String table, String entityClass) {
            this.table = table;
            this.entityClass = entityClass;
        }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public String getEntityClass() { return entityClass; }
        public void setEntityClass(String entityClass) { this.entityClass = entityClass; }
    }

    /** 列元信息。 */
    class ColumnMeta {
        private String name;
        private String type;
        private boolean primaryKey;
        private boolean nullable;

        public ColumnMeta() {}
        public ColumnMeta(String name, String type, boolean primaryKey, boolean nullable) {
            this.name = name;
            this.type = type;
            this.primaryKey = primaryKey;
            this.nullable = nullable;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
    }

    /** 列出某插件声明的所有实体表。 */
    List<EntityTable> listEntities(String pluginId);

    /** 描述某表结构（列名/类型/主键/可空）。 */
    List<ColumnMeta> describe(String pluginId, String table);

    /**
     * 分页读取某表数据（非类型化，列为 key→value）。
     * 排序字段名须为已知列；大表务必分页，框架会强制封顶返回行数。
     */
    Page<Map<String, Object>> read(String pluginId, String table, PageReq req);
}
