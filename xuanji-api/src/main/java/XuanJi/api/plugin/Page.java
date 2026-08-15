package XuanJi.api.plugin;

import java.util.List;

/**
 * 分页查询结果。由 {@link PluginRepository#page(PageReq)} 返回。
 *
 * @param <T> 实体类型
 */
public class Page<T> {

    /** 本页数据。 */
    private List<T> content = List.of();
    /** 满足查询条件的总行数（非本页条数）。 */
    private long total;
    /** 页码（1 起）。 */
    private int page;
    /** 每页大小。 */
    private int size;

    public Page() {}

    public Page(List<T> content, long total, int page, int size) {
        this.content = content;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    /** 总页数（向上取整，size<=0 时返回 0）。 */
    public int getTotalPages() {
        if (size <= 0) return 0;
        return (int) ((total + size - 1) / size);
    }

    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
}
