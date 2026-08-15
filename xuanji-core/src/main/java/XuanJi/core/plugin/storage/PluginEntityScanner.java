package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.ColumnType;
import XuanJi.api.plugin.PluginAutoId;
import XuanJi.api.plugin.PluginColumn;
import XuanJi.api.plugin.PluginEntity;
import XuanJi.api.plugin.PluginId;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件实体扫描器 — 在插件加载期用 classgraph 扫插件 classpath 上带 {@link PluginEntity}
 * 的类，反射出 {@link EntityMetadata}（表名/列/主键/索引/类型映射）。
 *
 * <h3>硬约束（安全基线，违反即抛 {@link PluginStructureException}）</h3>
 * <ul>
 *   <li>标识符白名单 {@code ^[a-z][a-z0-9_]{0,63}$}：实体表名 / 列名 / 清洗后的 pluginId 前缀。</li>
 *   <li>字段总数 ≤ {@link #MAX_COLUMNS}；声明 {@code index=true} 的字段 ≤ {@link #MAX_INDEXES}。</li>
 *   <li>{@code VARCHAR} 长度 ≤ {@link #MAX_VARCHAR}；{@link ColumnType#BLOB} 禁用。</li>
 *   <li>单表约束由调用方（{@code PluginDataProvider}）在得到 >1 个实体时拒绝加载。</li>
 * </ul>
 */
@Slf4j(topic = "xuanji.plugin.storage")
@Component
public class PluginEntityScanner {

    /** 单实体字段数上限。 */
    public static final int MAX_COLUMNS = 64;
    /** 单实体二级索引数上限。 */
    public static final int MAX_INDEXES = 4;
    /** VARCHAR 长度上限（超长用 TEXT）。 */
    public static final int MAX_VARCHAR = 8192;
    /** 标识符白名单。 */
    public static final String IDENTIFIER_RE = "^[a-z][a-z0-9_]{0,63}$";

    /** 扫描插件 classpath 上全部 @PluginEntity 实体，返回元数据列表（未做单表数量限制）。 */
    public List<EntityMetadata> scan(String pluginId, ClassLoader pluginClassLoader) {
        List<EntityMetadata> result = new ArrayList<>();
        try (ScanResult scan = new ClassGraph()
                .overrideClassLoaders(pluginClassLoader)
                .ignoreParentClassLoaders()     // 只扫插件自身 jar，不扫框架/三方
                .enableAnnotationInfo()
                .scan()) {
            ClassInfoList entities = scan.getClassesWithAnnotation(PluginEntity.class.getName());
            for (ClassInfo ci : entities) {
                result.add(build(pluginId, pluginClassLoader, ci.getName()));
            }
        }
        return result;
    }

    private EntityMetadata build(String pluginId, ClassLoader cl, String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            throw new PluginStructureException("插件 " + pluginId + " 实体类无法加载: " + className);
        }
        PluginEntity ann = clazz.getAnnotation(PluginEntity.class);
        if (ann == null) {
            throw new PluginStructureException("插件 " + pluginId + " 类 " + className + " 缺少 @PluginEntity");
        }

        String logicalTable = ann.table();
        if (logicalTable == null || logicalTable.isBlank()) {
            throw new PluginStructureException("插件 " + pluginId + " 实体 " + className + " 的 table() 不能为空");
        }
        if (!logicalTable.matches(IDENTIFIER_RE)) {
            throw new PluginStructureException(
                    "插件 " + pluginId + " 实体表名 '" + logicalTable + "' 非法（须匹配 " + IDENTIFIER_RE + "）");
        }
        String safePlugin = sanitizePluginPrefix(pluginId);
        String physicalTable = "plugin_" + safePlugin + "_" + logicalTable;

        List<EntityMetadata.ColumnMeta> columns = new ArrayList<>();
        List<String> pkCols = new ArrayList<>();
        String autoIdCol = null;
        int indexCount = 0;

        for (Field f : clazz.getDeclaredFields()) {
            PluginColumn col = f.getAnnotation(PluginColumn.class);
            PluginId pid = f.getAnnotation(PluginId.class);
            PluginAutoId paid = f.getAnnotation(PluginAutoId.class);
            if (col == null && pid == null && paid == null) {
                continue; // 非持久化字段
            }

            String colName = (col != null && !col.name().isBlank()) ? col.name() : f.getName();
            if (!colName.matches(IDENTIFIER_RE)) {
                throw new PluginStructureException(
                        "插件 " + pluginId + " 实体 " + className + " 列名 '" + colName + "' 非法（须匹配 " + IDENTIFIER_RE + "）");
            }

            // 类型/约束来源：显式 @PluginColumn > 业务主键(@PluginId)/自增(@PluginAutoId) 默认非空
            ColumnType declared = ColumnType.AUTO;
            int length = 255;
            boolean nullable = true;
            boolean index = false;
            boolean unique = false;
            if (col != null) {
                declared = col.type();
                length = col.length();
                nullable = col.nullable();
                index = col.index();
                unique = col.unique();
            } else {
                nullable = false; // @PluginId / @PluginAutoId 视为不可空
            }

            if (declared == ColumnType.BLOB) {
                throw new PluginStructureException(
                        "插件 " + pluginId + " 实体 " + className + " 列 " + colName + " 禁止使用 BLOB 类型");
            }
            String sqlType = resolveSqlType(f.getType(), declared, length, pluginId, className, colName);
            boolean isPk = pid != null;
            boolean isAuto = paid != null;

            if (isPk) pkCols.add(colName);
            if (isAuto) {
                if (autoIdCol != null) {
                    throw new PluginStructureException(
                            "插件 " + pluginId + " 实体 " + className + " 至多只能有一个 @PluginAutoId");
                }
                autoIdCol = colName;
            }
            if (index) {
                if (++indexCount > MAX_INDEXES) {
                    throw new PluginStructureException(
                            "插件 " + pluginId + " 实体 " + className + " 索引数超过上限 " + MAX_INDEXES);
                }
            }
            columns.add(new EntityMetadata.ColumnMeta(f.getName(), colName, f.getType(), sqlType, declared,
                    nullable, index, unique, length, isPk, isAuto));
        }

        if (columns.isEmpty()) {
            throw new PluginStructureException("插件 " + pluginId + " 实体 " + className + " 没有任何持久化字段");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new PluginStructureException(
                    "插件 " + pluginId + " 实体 " + className + " 字段数 " + columns.size() + " 超过上限 " + MAX_COLUMNS);
        }

        String hash = columnsHash(columns);
        return new EntityMetadata(pluginId, clazz, logicalTable, physicalTable, ann.version(),
                columns, pkCols, autoIdCol, hash);
    }

    /**
     * 把 pluginId 清洗为标识符安全前缀：小写、非 {@code [a-z0-9_]} 字符替换为 {@code _}。
     * 注意：不同 pluginId 可能映射到同一前缀（碰撞风险低，属已知限制）。
     */
    static String sanitizePluginPrefix(String pluginId) {
        if (pluginId == null) return "p";
        StringBuilder sb = new StringBuilder();
        for (char c : pluginId.toLowerCase().toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
        }
        String s = sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "p" : s;
    }

    /** Java 字段类型 + 声明类型 → H2 列类型（含校验）。 */
    private String resolveSqlType(Class<?> javaType, ColumnType declared, int length,
                                  String pluginId, String className, String colName) {
        ColumnType t = declared;
        if (t == ColumnType.AUTO) {
            t = inferType(javaType, pluginId, className, colName);
        }
        return switch (t) {
            case BIGINT -> "BIGINT";
            case INT -> "INTEGER";
            case SMALLINT -> "SMALLINT";
            case BOOLEAN -> "BOOLEAN";
            case DOUBLE -> "DOUBLE PRECISION";
            case DECIMAL -> "DECIMAL";
            case TIMESTAMP -> "TIMESTAMP";
            case DATE -> "DATE";
            case TEXT -> "TEXT";
            case VARCHAR -> {
                int len = Math.max(1, Math.min(length, MAX_VARCHAR));
                yield "VARCHAR(" + len + ")";
            }
            case BLOB -> throw new PluginStructureException(
                    "插件 " + pluginId + " 实体 " + className + " 列 " + colName + " 禁止使用 BLOB 类型");
            case AUTO -> throw new PluginStructureException(
                    "插件 " + pluginId + " 实体 " + className + " 列 " + colName + " 无法推断类型");
        };
    }

    private ColumnType inferType(Class<?> javaType, String pluginId, String className, String colName) {
        if (javaType == long.class || javaType == Long.class) return ColumnType.BIGINT;
        if (javaType == int.class || javaType == Integer.class) return ColumnType.INT;
        if (javaType == short.class || javaType == Short.class) return ColumnType.SMALLINT;
        if (javaType == boolean.class || javaType == Boolean.class) return ColumnType.BOOLEAN;
        if (javaType == String.class) return ColumnType.VARCHAR;
        if (javaType == double.class || javaType == Double.class
                || javaType == float.class || javaType == Float.class) return ColumnType.DOUBLE;
        if (BigDecimal.class.isAssignableFrom(javaType)) return ColumnType.DECIMAL;
        if (LocalDateTime.class.isAssignableFrom(javaType)) return ColumnType.TIMESTAMP;
        if (LocalDate.class.isAssignableFrom(javaType)) return ColumnType.DATE;
        if (javaType == byte[].class) return ColumnType.BLOB; // 触发上方 BLOB 拒绝
        throw new PluginStructureException(
                "插件 " + pluginId + " 实体 " + className + " 列 " + colName
                        + " 类型 " + javaType.getSimpleName() + " 无法映射（请用 @PluginColumn(type=...) 显式指定）");
    }

    /** 结构指纹（仅业务列，不含框架列）。 */
    private String columnsHash(List<EntityMetadata.ColumnMeta> columns) {
        StringBuilder sb = new StringBuilder();
        for (EntityMetadata.ColumnMeta c : columns) {
            sb.append(c.columnName).append(':').append(c.sqlType)
              .append(':').append(c.nullable ? 1 : 0).append(':')
              .append(c.primaryKey ? 1 : 0).append('|');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder h = new StringBuilder();
            for (byte b : dig) h.append(String.format("%02x", b));
            return h.substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /** 加载期结构非法时抛出（调用方据此拒绝加载插件）。 */
    public static class PluginStructureException extends RuntimeException {
        public PluginStructureException(String msg) { super(msg); }
    }
}
