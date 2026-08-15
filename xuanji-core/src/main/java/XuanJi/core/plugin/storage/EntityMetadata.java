package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.ColumnType;

import java.util.List;

/**
 * 插件实体在加载期解析出的元数据（表名/列/主键/索引/类型映射）。
 *
 * <p>由 {@link PluginEntityScanner} 在插件加载时一次性算出并缓存；后续建表、
 * {@code RowMapper}、SQL 生成全部复用本对象，运行期不再反射。
 *
 * <p>物理表名已含插件前缀 {@code plugin_{pluginId}_{table}}，且经标识符白名单清洗，
 * 故可被安全地拼入 DDL（值仍走 {@code ?} 参数化，杜绝注入）。
 */
public class EntityMetadata {

    /** 插件 id（信任边界，来自 wrapper.getPluginId()）。 */
    public final String pluginId;
    /** 实体 Class（由插件 classloader 加载）。 */
    public final Class<?> entityClass;
    /** 逻辑表名（@PluginEntity.table()）。 */
    public final String logicalTable;
    /** 物理表名 plugin_{pluginId}_{table}（已标识符清洗）。 */
    public final String physicalTable;
    /** 实体版本 @PluginEntity.version()。 */
    public final int version;
    /** 全部参与持久化的列（含主键/自增列），按顺序。 */
    public final List<ColumnMeta> columns;
    /** 业务主键列的物理列名（@PluginId 标注，可复合）。 */
    public final List<String> primaryKeyColumns;
    /** 框架托管自增列物理名（@PluginAutoId，可空）。 */
    public final String autoIdColumn;
    /** 结构指纹（用于迁移检测；与 autoId/row_id 等框架列无关）。 */
    public final String columnsHash;

    public EntityMetadata(String pluginId, Class<?> entityClass, String logicalTable,
                          String physicalTable, int version, List<ColumnMeta> columns,
                          List<String> primaryKeyColumns, String autoIdColumn, String columnsHash) {
        this.pluginId = pluginId;
        this.entityClass = entityClass;
        this.logicalTable = logicalTable;
        this.physicalTable = physicalTable;
        this.version = version;
        this.columns = List.copyOf(columns);
        this.primaryKeyColumns = List.copyOf(primaryKeyColumns);
        this.autoIdColumn = autoIdColumn;
        this.columnsHash = columnsHash;
    }

    /** 按物理列名取列元数据（找不到返回 null）。 */
    public ColumnMeta columnBySqlName(String sqlName) {
        for (ColumnMeta c : columns) {
            if (c.columnName.equals(sqlName)) return c;
        }
        return null;
    }

    /** 校验某字段名是否为已知列（Query 防注入用）。 */
    public boolean hasColumn(String sqlName) {
        return columnBySqlName(sqlName) != null;
    }

    /** 单个列的描述。 */
    public static class ColumnMeta {
        /** Java 字段名。 */
        public final String fieldName;
        /** 物理列名（已标识符清洗）。 */
        public final String columnName;
        /** Java 字段类型。 */
        public final Class<?> javaType;
        /** 映射后的 H2 列类型（如 BIGINT / VARCHAR(255)）。 */
        public final String sqlType;
        /** 原始声明类型风格（用于校验/文档）。 */
        public final ColumnType declaredType;
        public final boolean nullable;
        public final boolean index;
        public final boolean unique;
        public final int length;
        public final boolean primaryKey;
        public final boolean autoId;

        public ColumnMeta(String fieldName, String columnName, Class<?> javaType, String sqlType,
                          ColumnType declaredType, boolean nullable, boolean index, boolean unique,
                          int length, boolean primaryKey, boolean autoId) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.javaType = javaType;
            this.sqlType = sqlType;
            this.declaredType = declaredType;
            this.nullable = nullable;
            this.index = index;
            this.unique = unique;
            this.length = length;
            this.primaryKey = primaryKey;
            this.autoId = autoId;
        }
    }
}
