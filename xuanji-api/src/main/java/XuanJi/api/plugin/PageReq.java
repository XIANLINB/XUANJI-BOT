package XuanJi.api.plugin;

/**
 * 分页请求。配合 {@link PluginRepository#page(PageReq)} 使用。
 *
 * <p>页码从 1 开始；未指定排序时由框架按主键/自增列兜底排序。排序字段名须为实体已知列。
 *
 * <p>示例：
 * <pre>{@code
 * PageReq.of(1, 20).orderBy("coins", true);   // 第 1 页、每页 20、按 coins 降序
 * }</pre>
 */
public class PageReq {

    private int page = 1;
    private int size = 10;
    private String orderBy;
    private boolean desc;

    public PageReq() {}

    /** 便捷工厂：第 page 页、每页 size 条。 */
    public static PageReq of(int page, int size) {
        PageReq r = new PageReq();
        r.page = page;
        r.size = size;
        return r;
    }

    /** 设置排序字段与方向（desc=true 降序）。 */
    public PageReq orderBy(String field, boolean desc) {
        this.orderBy = field;
        this.desc = desc;
        return this;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public boolean isDesc() { return desc; }
    public void setDesc(boolean desc) { this.desc = desc; }

    /** 计算 SQL 偏移量（页码越界安全处理）。 */
    public int offset() {
        int p = Math.max(1, page);
        return (p - 1) * Math.max(1, size);
    }
}
