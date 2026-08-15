package XuanJi.core.plugin.storage;

import XuanJi.api.plugin.Page;
import XuanJi.api.plugin.PageReq;
import XuanJi.api.plugin.PluginDataViewer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link PluginDataViewer} 默认实现：框架内置数据面板用，按 {@code pluginId} 隔离地
 * 读取任意插件的结构化数据（非类型化 {@code Map} 行）。
 *
 * <p>隔离：每个读取前都校验「该表确实登记在该 pluginId 名下」，杜绝越权读别的插件表。
 * 表名/列名均来自框架登记元数据或 {@code ResultSetMetaData}，不接收外部标识符拼接。
 */
@Slf4j(topic = "xuanji.plugin.storage")
@Component
public class PluginDataViewerImpl implements PluginDataViewer {

    private final JdbcTemplate jdbc;
    private final PluginSchemaGenerator schema;

    public PluginDataViewerImpl(JdbcTemplate jdbc, PluginSchemaGenerator schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @Override
    public List<EntityTable> listEntities(String pluginId) {
        schema.createFrameworkTables(jdbc);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT entity, table_name FROM " + PluginSchemaGenerator.REGISTRY_TABLE
                        + " WHERE plugin_id=?", pluginId);
        List<EntityTable> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new EntityTable(String.valueOf(r.get("TABLE_NAME")), String.valueOf(r.get("ENTITY"))));
        }
        return list;
    }

    @Override
    public List<ColumnMeta> describe(String pluginId, String table) {
        assertOwnership(pluginId, table);
        List<ColumnMeta> metas = new ArrayList<>();
        try (Connection conn = dataSource().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE 1=0");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                metas.add(new ColumnMeta(
                        md.getColumnLabel(i),
                        md.getColumnTypeName(i),
                        false,
                        md.isNullable(i) != ResultSetMetaData.columnNoNulls));
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("读取表结构失败: " + table, e);
        }
        return metas;
    }

    @Override
    public Page<Map<String, Object>> read(String pluginId, String table, PageReq req) {
        assertOwnership(pluginId, table);

        int limit = Math.max(1, Math.min(req.getSize(), (int) PluginSchemaGenerator.MAX_ROWS));
        int offset = req.offset();
        String orderBy = "";
        if (req.getOrderBy() != null && !req.getOrderBy().isBlank()) {
            Set<String> cols = columnNames(table);
            if (cols.contains(req.getOrderBy())) {
                orderBy = " ORDER BY " + req.getOrderBy() + (req.isDesc() ? " DESC" : " ASC");
            }
        }

        List<Map<String, Object>> content = jdbc.queryForList(
                "SELECT * FROM " + table + orderBy + " LIMIT ? OFFSET ?", limit, offset);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);

        Page<Map<String, Object>> page = new Page<>();
        page.setContent(content);
        page.setTotal(total == null ? 0 : total);
        page.setPage(Math.max(1, req.getPage()));
        page.setSize(limit);
        return page;
    }

    /** 校验 table 确实属于 pluginId（隔离边界）。 */
    private void assertOwnership(String pluginId, String table) {
        if (pluginId == null || table == null) throw new IllegalArgumentException("pluginId/table 不能为空");
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + PluginSchemaGenerator.REGISTRY_TABLE
                        + " WHERE plugin_id=? AND table_name=?", Integer.class, pluginId, table);
        if (cnt == null || cnt == 0) {
            throw new IllegalArgumentException("插件 " + pluginId + " 不存在数据表 " + table + "（无权访问）");
        }
    }

    private Set<String> columnNames(String table) {
        Set<String> cols = new HashSet<>();
        try (Connection conn = dataSource().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE 1=0");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i));
        } catch (SQLException ignored) {
        }
        return cols;
    }

    private DataSource dataSource() {
        return jdbc.getDataSource();
    }
}
