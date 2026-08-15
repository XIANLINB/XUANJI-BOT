package XuanJi.api.plugin;

/**
 * 插件实体字段 → H2 列类型映射枚举。
 *
 * <p>多数情况下用 {@link #AUTO}：框架按 Java 字段类型自动推断（见下表）。
 * 若需精确控制（如把 {@code String} 显式存成 {@code TEXT} 而非 {@code VARCHAR}），
 * 可在 {@link PluginColumn#type()} 指定。
 *
 * <h3>AUTO 推断规则</h3>
 * <table border="1">
 *   <tr><th>Java 类型</th><th>H2 列类型</th></tr>
 *   <tr><td>long / Long</td><td>BIGINT</td></tr>
 *   <tr><td>int / Integer / short / Short</td><td>INTEGER / SMALLINT</td></tr>
 *   <tr><td>boolean / Boolean</td><td>BOOLEAN</td></tr>
 *   <tr><td>String</td><td>VARCHAR(length)（显式 TEXT 时用 TEXT）</td></tr>
 *   <tr><td>double / float / Double / Float</td><td>DOUBLE PRECISION</td></tr>
 *   <tr><td>BigDecimal</td><td>DECIMAL</td></tr>
 *   <tr><td>LocalDateTime</td><td>TIMESTAMP</td></tr>
 *   <tr><td>LocalDate</td><td>DATE</td></tr>
 *   <tr><td>byte[]</td><td>BLOB（插件建表时禁用，见安全约束）</td></tr>
 * </table>
 *
 * <p><b>安全约束</b>：插件建表时 {@link #BLOB} 被框架拒绝（避免大对象撑爆库）；
 * 字段名/类型必须经框架加载期白名单校验。
 */
public enum ColumnType {
    /** 按 Java 字段类型自动推断 */
    AUTO,
    BIGINT,
    INT,
    SMALLINT,
    BOOLEAN,
    VARCHAR,
    TEXT,
    DOUBLE,
    DECIMAL,
    TIMESTAMP,
    DATE,
    /** 插件建表时禁用（仅保留枚举位以免历史映射断裂） */
    BLOB
}
