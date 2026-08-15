package XuanJi.api.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 类型安全、零 SQL 的查询构造器。由插件通过静态工厂 {@link #where(String)} 创建，
 * 链式拼装条件 / 排序 / 分页后交给 {@code PluginRepository.query / page / countBy} 执行。
 *
 * <p><b>防注入</b>：条件字段名必须在实体元数据允许范围内（由框架加载期校验），
 * 值一律以 {@code ?} 参数化传入，本类不拼接任何 SQL 片段。
 *
 * <p>示例：
 * <pre>{@code
 * Query.where("coins").ge(100)
 *      .and("group_id").eq(gid)
 *      .or("points").ge(500)
 *      .orderBy("signtime").desc()
 *      .limit(20);
 * }</pre>
 *
 * <p>内部表示（供框架实现读取）：{@link #getConditions()} / {@link #getOrders()} /
 * {@link #getLimit()} / {@link #getOffset()}。字段名一律取自实体已知列。
 */
public class Query {

    /** 多条件间连接方式（首个条件忽略，恒为 AND 语义起点）。 */
    public enum Connector { AND, OR }

    /** 比较算子。 */
    public enum Operator {
        EQ, NE, GT, GE, LT, LE, LIKE, IN, BETWEEN, IS_NULL, IS_NOT_NULL
    }

    /** 单条条件。 */
    public static class Condition {
        private final Connector connector;
        private final String field;
        private final Operator operator;
        private final Object value;
        private final Object value2; // BETWEEN 上界 / IN 集合（可选）

        Condition(Connector connector, String field, Operator operator, Object value, Object value2) {
            this.connector = connector;
            this.field = field;
            this.operator = operator;
            this.value = value;
            this.value2 = value2;
        }

        public Connector getConnector() { return connector; }
        public String getField() { return field; }
        public Operator getOperator() { return operator; }
        public Object getValue() { return value; }
        public Object getValue2() { return value2; }
    }

    /** 排序项。 */
    public static class Order {
        private final String field;
        private final boolean desc;

        Order(String field, boolean desc) {
            this.field = field;
            this.desc = desc;
        }

        public String getField() { return field; }
        public boolean isDesc() { return desc; }
    }

    private final List<Condition> conditions = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    private Query() {}

    /**
     * 以第一个条件字段起手，返回字段步骤用于接比较算子
     * （等价于 {@code new Query().and(field)}）。
     *
     * <p>例：{@code Query.where("coins").ge(100).and("group_id").eq(gid)...}
     */
    public static FieldStep where(String field) {
        return new Query().and(field);
    }

    /** 追加一个 AND 条件字段；返回字段步骤用于接比较算子。 */
    public FieldStep and(String field) {
        return new FieldStep(this, field, Connector.AND);
    }

    /** 追加一个 OR 条件字段；返回字段步骤用于接比较算子。 */
    public FieldStep or(String field) {
        return new FieldStep(this, field, Connector.OR);
    }

    /** 追加排序（默认升序，可用返回步骤切到降序）。 */
    public OrderStep orderBy(String field) {
        return new OrderStep(this, field);
    }

    /** 限制返回行数（大表务必设置，框架也会强制封顶）。 */
    public Query limit(int n) {
        this.limit = n;
        return this;
    }

    /** 偏移（与 {@link #limit(int)} 配合做分页；通常直接用 {@link PageReq}）。 */
    public Query offset(int n) {
        this.offset = n;
        return this;
    }

    void add(Connector connector, String field, Operator op, Object value, Object value2) {
        conditions.add(new Condition(connector, field, op, value, value2));
    }

    void addOrder(String field, boolean desc) {
        orders.add(new Order(field, desc));
    }

    public List<Condition> getConditions() { return conditions; }
    public List<Order> getOrders() { return orders; }
    public Integer getLimit() { return limit; }
    public Integer getOffset() { return offset; }
    public boolean isEmpty() { return conditions.isEmpty(); }

    /** 字段步骤：承接 {@link #and(String)}/{@link #or(String)} 后接比较算子，返回 {@link Query}。 */
    public static class FieldStep {
        private final Query query;
        private final String field;
        private final Connector connector;

        FieldStep(Query query, String field, Connector connector) {
            this.query = query;
            this.field = field;
            this.connector = connector;
        }

        public Query eq(Object v)            { query.add(connector, field, Operator.EQ, v, null); return query; }
        public Query ne(Object v)            { query.add(connector, field, Operator.NE, v, null); return query; }
        public Query gt(Object v)            { query.add(connector, field, Operator.GT, v, null); return query; }
        public Query ge(Object v)            { query.add(connector, field, Operator.GE, v, null); return query; }
        public Query lt(Object v)            { query.add(connector, field, Operator.LT, v, null); return query; }
        public Query le(Object v)            { query.add(connector, field, Operator.LE, v, null); return query; }
        public Query like(Object pattern)    { query.add(connector, field, Operator.LIKE, pattern, null); return query; }
        public Query isNull()                { query.add(connector, field, Operator.IS_NULL, null, null); return query; }
        public Query isNotNull()             { query.add(connector, field, Operator.IS_NOT_NULL, null, null); return query; }

        public Query in(Collection<?> values)    { query.add(connector, field, Operator.IN, values, null); return query; }
        public Query in(Object... values)        { query.add(connector, field, Operator.IN, List.of(values), null); return query; }

        public Query between(Object lo, Object hi){ query.add(connector, field, Operator.BETWEEN, lo, hi); return query; }
    }

    /** 排序步骤：承接 {@link #orderBy(String)} 后切升/降序，返回 {@link Query}。 */
    public static class OrderStep {
        private final Query query;
        private final String field;

        OrderStep(Query query, String field) {
            this.query = query;
            this.field = field;
        }

        public Query asc()  { query.addOrder(field, false); return query; }
        public Query desc() { query.addOrder(field, true);  return query; }
    }
}
