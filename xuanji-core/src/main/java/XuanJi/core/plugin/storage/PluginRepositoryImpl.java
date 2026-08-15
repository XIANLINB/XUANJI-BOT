package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.Page;
import XuanJi.api.plugin.PageReq;
import XuanJi.api.plugin.PluginRepository;
import XuanJi.api.plugin.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link PluginRepository} 的框架实现。SQL 全部由 {@link EntityMetadata} 推导，
 * 物理表名来自元数据（含插件前缀），字段名经元数据白名单校验，值一律 {@code ?} 参数化。
 *
 * <p>写操作受 {@link PluginSchemaGenerator#MAX_ROWS} 行数配额约束；越界抛
 * {@link QuotaExceededException}。
 */
@Slf4j(topic = "xuanji.plugin.storage")
public class PluginRepositoryImpl<T> implements PluginRepository<T> {

    private final EntityMetadata meta;
    private final JdbcTemplate jdbc;
    private final PluginSchemaGenerator schema;
    private final RowMapper<T> rowMapper;
    private final List<Field> fields = new ArrayList<>();

    public PluginRepositoryImpl(EntityMetadata meta, JdbcTemplate jdbc, PluginSchemaGenerator schema) {
        this.meta = meta;
        this.jdbc = jdbc;
        this.schema = schema;
        for (EntityMetadata.ColumnMeta c : meta.columns) {
            try {
                Field f = meta.entityClass.getDeclaredField(c.fieldName);
                f.setAccessible(true);
                fields.add(f);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("实体 " + meta.entityClass.getName()
                        + " 缺少字段 " + c.fieldName, e);
            }
        }
        this.rowMapper = new EntityRowMapper();
    }

    private EntityMetadata.ColumnMeta autoId() {
        if (meta.autoIdColumn == null) return null;
        return meta.columnBySqlName(meta.autoIdColumn);
    }

    /* ===================== 写 ===================== */

    @Override
    public T save(T entity) {
        List<EntityMetadata.ColumnMeta> cols = persistColumns(); // 排除自增列
        Object[] vals = readValues(entity, cols);

        if (!meta.primaryKeyColumns.isEmpty()) {
            // MERGE upsert by business key
            Object[] ids = readValues(entity, pkCols());
            boolean exists = findByIdOrNull(ids) != null;
            if (!exists && !schema.withinQuota(jdbc, meta.pluginId, meta.physicalTable, 1)) {
                throw new QuotaExceededException(meta);
            }
            StringBuilder sql = new StringBuilder("MERGE INTO ").append(meta.physicalTable)
                    .append(" (").append(joinColNames(cols)).append(") KEY (")
                    .append(String.join(", ", meta.primaryKeyColumns)).append(") VALUES (")
                    .append(placeholders(cols.size())).append(')');
            jdbc.update(sql.toString(), vals);
            if (!exists) schema.adjustRows(jdbc, meta.pluginId, meta.physicalTable, 1);
            // 用业务主键回填（含自增/其他列）
            T refreshed = findByIdOrNull(ids);
            return refreshed != null ? refreshed : entity;
        } else {
            // 无业务主键：纯插入
            if (!schema.withinQuota(jdbc, meta.pluginId, meta.physicalTable, 1)) {
                throw new QuotaExceededException(meta);
            }
            EntityMetadata.ColumnMeta aid = autoId();
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(meta.physicalTable)
                    .append(" (").append(joinColNames(cols)).append(") VALUES (")
                    .append(placeholders(cols.size())).append(')');
            if (aid != null) {
                KeyHolder kh = new GeneratedKeyHolder();
                jdbc.update(con -> {
                    PreparedStatement ps = con.prepareStatement(sql.toString(), new String[]{aid.columnName});
                    bindParams(ps, cols, vals);
                    return ps;
                }, kh);
                Number key = kh.getKey();
                if (key != null) writeValue(entity, aid, key.longValue());
                schema.adjustRows(jdbc, meta.pluginId, meta.physicalTable, 1);
            } else {
                jdbc.update(sql.toString(), vals);
                schema.adjustRows(jdbc, meta.pluginId, meta.physicalTable, 1);
            }
            return entity;
        }
    }

    @Override
    public int update(T entity) {
        if (meta.primaryKeyColumns.isEmpty()) {
            throw new IllegalStateException("实体 " + meta.entityClass.getSimpleName()
                    + " 无业务主键，不支持 update（请改用 save 或 delete+save）");
        }
        List<EntityMetadata.ColumnMeta> setCols = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (EntityMetadata.ColumnMeta c : meta.columns) {
            if (meta.primaryKeyColumns.contains(c.columnName)) continue;
            if (c.autoId) continue;
            Object v = readValue(entity, c);
            if (v == null) continue; // 仅更新非空字段
            setCols.add(c);
            params.add(v);
        }
        if (setCols.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("UPDATE ").append(meta.physicalTable).append(" SET ");
        for (int i = 0; i < setCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(setCols.get(i).columnName).append(" = ?");
        }
        sql.append(" WHERE ");
        boolean first = true;
        for (String pk : meta.primaryKeyColumns) {
            EntityMetadata.ColumnMeta pkc = meta.columnBySqlName(pk);
            if (!first) sql.append(" AND ");
            sql.append(pk).append(" = ?");
            params.add(readValue(entity, pkc));
            first = false;
        }
        return jdbc.update(sql.toString(), params.toArray());
    }

    @Override
    public int deleteById(Object... ids) {
        if (meta.primaryKeyColumns.isEmpty()) {
            throw new IllegalStateException("实体 " + meta.entityClass.getSimpleName() + " 无业务主键，不支持 deleteById");
        }
        if (ids.length != meta.primaryKeyColumns.size()) {
            throw new IllegalArgumentException("deleteById 需要 " + meta.primaryKeyColumns.size() + " 个主键值");
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(meta.physicalTable).append(" WHERE ");
        for (int i = 0; i < meta.primaryKeyColumns.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(meta.primaryKeyColumns.get(i)).append(" = ?");
        }
        int n = jdbc.update(sql.toString(), ids);
        if (n > 0) schema.adjustRows(jdbc, meta.pluginId, meta.physicalTable, -n);
        return n;
    }

    @Override
    public int deleteBy(Query q) {
        SqlAndParams sp = buildWhere(q, true);
        int n = jdbc.update("DELETE FROM " + meta.physicalTable + sp.sql, sp.params.toArray());
        if (n > 0) schema.adjustRows(jdbc, meta.pluginId, meta.physicalTable, -n);
        return n;
    }

    /* ===================== 读 ===================== */

    @Override
    public Optional<T> findById(Object... ids) {
        return Optional.ofNullable(findByIdOrNull(ids));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T findByIdOrNull(Object... ids) {
        if (meta.primaryKeyColumns.isEmpty()) return null;
        if (ids.length != meta.primaryKeyColumns.size()) {
            throw new IllegalArgumentException("findById 需要 " + meta.primaryKeyColumns.size() + " 个主键值");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(selectColumns()).append(" FROM ")
                .append(meta.physicalTable).append(" WHERE ");
        for (int i = 0; i < meta.primaryKeyColumns.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(meta.primaryKeyColumns.get(i)).append(" = ?");
        }
        List<T> list = jdbc.query(sql.toString(), rowMapper, ids);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<T> findAll() {
        return jdbc.query("SELECT " + selectColumns() + " FROM " + meta.physicalTable
                + " LIMIT " + PluginSchemaGenerator.MAX_ROWS, rowMapper);
    }

    @Override
    public List<T> findBy(String field, Object value) {
        EntityMetadata.ColumnMeta c = validateColumn(field);
        return jdbc.query("SELECT " + selectColumns() + " FROM " + meta.physicalTable
                + " WHERE " + c.columnName + " = ? LIMIT " + PluginSchemaGenerator.MAX_ROWS,
                rowMapper, value);
    }

    @Override
    public List<T> query(Query q) {
        SqlAndParams sp = buildWhere(q, true);
        String sql = "SELECT " + selectColumns() + " FROM " + meta.physicalTable + sp.sql;
        return jdbc.query(sql, rowMapper, sp.params.toArray());
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + meta.physicalTable, Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public long countBy(Query q) {
        SqlAndParams sp = buildWhere(q, false);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + meta.physicalTable + sp.sql,
                Long.class, sp.params.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public Page<T> page(PageReq req) {
        String orderBy = "";
        if (req.getOrderBy() != null && !req.getOrderBy().isBlank()) {
            EntityMetadata.ColumnMeta c = validateColumn(req.getOrderBy());
            orderBy = " ORDER BY " + c.columnName + (req.isDesc() ? " DESC" : " ASC");
        }
        int limit = Math.max(1, Math.min(req.getSize(), (int) PluginSchemaGenerator.MAX_ROWS));
        int offset = req.offset();
        List<T> content = jdbc.query(
                "SELECT " + selectColumns() + " FROM " + meta.physicalTable
                        + orderBy + " LIMIT ? OFFSET ?",
                rowMapper, limit, offset);
        long total = count();
        Page<T> page = new Page<>();
        page.setContent(content);
        page.setTotal(total);
        page.setPage(Math.max(1, req.getPage()));
        page.setSize(limit);
        return page;
    }

    /* ===================== SQL 构造辅助 ===================== */

    private static final int DEFAULT_QUERY_CAP = 1000;

    /** 把 Query 转成 WHERE 子句（含 ORDER BY / LIMIT），参数全部 {@code ?}。 */
    private SqlAndParams buildWhere(Query q, boolean withLimit) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isEmpty()) {
            boolean first = true;
            for (Query.Condition cond : q.getConditions()) {
                String op = switch (cond.getOperator()) {
                    case EQ -> "=";
                    case NE -> "<>";
                    case GT -> ">";
                    case GE -> ">=";
                    case LT -> "<";
                    case LE -> "<=";
                    case LIKE -> "LIKE";
                    case IS_NULL -> "IS NULL";
                    case IS_NOT_NULL -> "IS NOT NULL";
                    case IN, BETWEEN -> null; // 单独处理
                };
                EntityMetadata.ColumnMeta c = validateColumn(cond.getField());
                if (!first) where.append(' ').append(cond.getConnector() == Query.Connector.OR ? "OR" : "AND").append(' ');
                first = false;
                switch (cond.getOperator()) {
                    case IN -> {
                        @SuppressWarnings("unchecked")
                        List<Object> list = cond.getValue() instanceof List
                                ? (List<Object>) cond.getValue()
                                : java.util.List.of(cond.getValue());
                        StringBuilder in = new StringBuilder(c.columnName).append(" IN (");
                        for (int i = 0; i < list.size(); i++) {
                            if (i > 0) in.append(", ");
                            in.append('?');
                            params.add(list.get(i));
                        }
                        in.append(')');
                        where.append(in);
                    }
                    case BETWEEN -> {
                        where.append(c.columnName).append(" BETWEEN ? AND ?");
                        params.add(cond.getValue());
                        params.add(cond.getValue2());
                    }
                    default -> {
                        where.append(c.columnName).append(' ').append(op);
                        if (cond.getOperator() != Query.Operator.IS_NULL
                                && cond.getOperator() != Query.Operator.IS_NOT_NULL) {
                            where.append(" ?");
                            params.add(cond.getValue());
                        }
                    }
                }
            }
            if (!q.getOrders().isEmpty()) {
                where.append(" ORDER BY ");
                for (int i = 0; i < q.getOrders().size(); i++) {
                    Query.Order o = q.getOrders().get(i);
                    EntityMetadata.ColumnMeta c = validateColumn(o.getField());
                    if (i > 0) where.append(", ");
                    where.append(c.columnName).append(o.isDesc() ? " DESC" : " ASC");
                }
            }
        }
        if (withLimit) {
            int lim = q != null && q.getLimit() != null
                    ? Math.min(q.getLimit(), DEFAULT_QUERY_CAP) : DEFAULT_QUERY_CAP;
            where.append(" LIMIT ").append(lim);
            if (q != null && q.getOffset() != null) {
                where.append(" OFFSET ").append(q.getOffset());
            }
        }
        return new SqlAndParams(where.toString(), params);
    }

    private EntityMetadata.ColumnMeta validateColumn(String field) {
        EntityMetadata.ColumnMeta c = meta.columnBySqlName(field);
        if (c == null) {
            throw new IllegalArgumentException("插件 " + meta.pluginId + " 实体 "
                    + meta.entityClass.getSimpleName() + " 不存在列 '" + field + "'（字段名须为已知列）");
        }
        return c;
    }

    /** 参与持久化的列（排除自增列，交由 DB 生成）。 */
    private List<EntityMetadata.ColumnMeta> persistColumns() {
        List<EntityMetadata.ColumnMeta> cols = new ArrayList<>();
        for (EntityMetadata.ColumnMeta c : meta.columns) {
            if (c.autoId) continue;
            cols.add(c);
        }
        return cols;
    }

    private List<EntityMetadata.ColumnMeta> pkCols() {
        List<EntityMetadata.ColumnMeta> cols = new ArrayList<>();
        for (String pk : meta.primaryKeyColumns) cols.add(meta.columnBySqlName(pk));
        return cols;
    }

    private String joinColNames(List<EntityMetadata.ColumnMeta> cols) {
        List<String> names = new ArrayList<>();
        for (EntityMetadata.ColumnMeta c : cols) names.add(c.columnName);
        return String.join(", ", names);
    }

    private String selectColumns() {
        return joinColNames(meta.columns);
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append('?');
        }
        return sb.toString();
    }

    /* ===================== 反射读写 ===================== */

    private Object[] readValues(T entity, List<EntityMetadata.ColumnMeta> cols) {
        Object[] vals = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            vals[i] = readValue(entity, cols.get(i));
        }
        return vals;
    }

    private Object readValue(T entity, EntityMetadata.ColumnMeta c) {
        try {
            Field f = fields.get(meta.columns.indexOf(c));
            return f.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取字段 " + c.fieldName + " 失败", e);
        }
    }

    private void writeValue(T entity, EntityMetadata.ColumnMeta c, Object v) {
        try {
            Field f = fields.get(meta.columns.indexOf(c));
            if (v != null && f.getType() == Long.class && v instanceof Number n) v = n.longValue();
            f.set(entity, v);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("写入字段 " + c.fieldName + " 失败", e);
        }
    }

    private void bindParams(PreparedStatement ps, List<EntityMetadata.ColumnMeta> cols, Object[] vals) throws SQLException {
        for (int i = 0; i < cols.size(); i++) {
            setParam(ps, i + 1, cols.get(i), vals[i]);
        }
    }

    private static void setParam(PreparedStatement ps, int idx, EntityMetadata.ColumnMeta c, Object v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, sqlTypeFor(c));
            return;
        }
        Class<?> t = c.javaType;
        if (t == String.class) ps.setString(idx, (String) v);
        else if (t == long.class || t == Long.class) ps.setLong(idx, ((Number) v).longValue());
        else if (t == int.class || t == Integer.class) ps.setInt(idx, ((Number) v).intValue());
        else if (t == short.class || t == Short.class) ps.setShort(idx, ((Number) v).shortValue());
        else if (t == boolean.class || t == Boolean.class) ps.setBoolean(idx, (Boolean) v);
        else if (t == double.class || t == Double.class) ps.setDouble(idx, ((Number) v).doubleValue());
        else if (t == float.class || t == Float.class) ps.setFloat(idx, ((Number) v).floatValue());
        else if (BigDecimal.class.isAssignableFrom(t)) ps.setBigDecimal(idx, (BigDecimal) v);
        else if (LocalDateTime.class.isAssignableFrom(t)) ps.setTimestamp(idx, Timestamp.valueOf((LocalDateTime) v));
        else if (LocalDate.class.isAssignableFrom(t)) ps.setDate(idx, java.sql.Date.valueOf((LocalDate) v));
        else ps.setObject(idx, v);
    }

    private static int sqlTypeFor(EntityMetadata.ColumnMeta c) {
        Class<?> t = c.javaType;
        if (t == String.class) return Types.VARCHAR;
        if (t == boolean.class || t == Boolean.class) return Types.BOOLEAN;
        if (t == LocalDateTime.class) return Types.TIMESTAMP;
        if (t == LocalDate.class) return Types.DATE;
        if (t == double.class || t == Double.class || t == float.class || t == Float.class) return Types.DOUBLE;
        if (t == BigDecimal.class) return Types.DECIMAL;
        return Types.BIGINT;
    }

    private Object fromSqlValue(ResultSet rs, EntityMetadata.ColumnMeta c) throws SQLException {
        Class<?> t = c.javaType;
        if (t == String.class) {
            return rs.getString(c.columnName);
        } else if (t == long.class || t == Long.class) {
            long v = rs.getLong(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (t == int.class || t == Integer.class) {
            int v = rs.getInt(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (t == short.class || t == Short.class) {
            short v = rs.getShort(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (t == boolean.class || t == Boolean.class) {
            boolean v = rs.getBoolean(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (t == double.class || t == Double.class) {
            double v = rs.getDouble(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (t == float.class || t == Float.class) {
            float v = rs.getFloat(c.columnName);
            return rs.wasNull() ? null : v;
        } else if (BigDecimal.class.isAssignableFrom(t)) {
            return rs.getBigDecimal(c.columnName);
        } else if (LocalDateTime.class.isAssignableFrom(t)) {
            Timestamp ts = rs.getTimestamp(c.columnName);
            return ts == null ? null : ts.toLocalDateTime();
        } else if (LocalDate.class.isAssignableFrom(t)) {
            java.sql.Date d = rs.getDate(c.columnName);
            return d == null ? null : d.toLocalDate();
        } else {
            return rs.getObject(c.columnName);
        }
    }

    private class EntityRowMapper implements RowMapper<T> {
        @Override
        @SuppressWarnings("unchecked")
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                T entity = (T) meta.entityClass.getDeclaredConstructor().newInstance();
                for (int i = 0; i < meta.columns.size(); i++) {
                    EntityMetadata.ColumnMeta c = meta.columns.get(i);
                    Object v = fromSqlValue(rs, c);
                    Field f = fields.get(i);
                    if (v != null && f.getType().isPrimitive() && v instanceof Number) {
                        // 原始类型不可赋 null，保持原值
                    }
                    f.set(entity, v);
                }
                return entity;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("实例化实体 " + meta.entityClass.getName() + " 失败", e);
            }
        }
    }

    private record SqlAndParams(String sql, List<Object> params) {}

    /** 行数配额超限时抛出（插件侧可捕获并提示）。 */
    public static class QuotaExceededException extends RuntimeException {
        QuotaExceededException(EntityMetadata meta) {
            super("插件 " + meta.pluginId + " 的表 " + meta.physicalTable
                    + " 行数已达上限 " + PluginSchemaGenerator.MAX_ROWS);
        }
    }
}
