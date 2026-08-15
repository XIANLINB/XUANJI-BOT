package XuanJi.console.controller;

import XuanJi.api.plugin.Page;
import XuanJi.api.plugin.PageReq;
import XuanJi.api.plugin.PluginDataViewer;
import XuanJi.api.result.R;
import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 控制台 · 插件结构化数据浏览面板（方案 A：注解声明实体 + 框架自动建表）。
 *
 * <p>对任意插件按 {@code pluginId} 隔离地查看其声明的实体表、表结构与分页数据，
 * 插件侧零代码即可获得后台表格视图。
 *
 * <p><b>返回约定</b>：成功直接返回裸数据（{@code List} / {@code Page}），不加 {@link R} 包裹，
 * 与 {@code ConsolePluginController} 保持一致，前端 {@code http.get} 直接拿到业务数据；
 * 异常交由 {@link XuanJi.console.exception.GlobalExceptionHandler} 统一处理。
 * 仅当入参非法（未知/越权表）时主动以 400 + R 错误体返回，便于前端取到 message。
 *
 * <p><b>鉴权</b>：所有端点落在 {@code /xuanji/api/v1/console/**} 下，已被
 * {@link XuanJi.console.security.AuthFilter} 统一要求有效会话 cookie（控制台单 PIN 登录即管理员）。
 * 读取实现内部的 {@code PluginDataViewerImpl.assertOwnership} 还会二次校验「表确属该 pluginId」。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console")
public class PluginDataViewController {

    private final PluginDataViewer viewer;

    public PluginDataViewController(PluginDataViewer viewer) {
        this.viewer = viewer;
    }

    /**
     * 列出某插件声明的全部实体表。
     * GET /xuanji/api/v1/console/plugins/{pluginId}/entities
     */
    @GetMapping("/plugins/{pluginId}/entities")
    public ResponseEntity<?> listEntities(@PathVariable String pluginId) {
        try {
            return ResponseEntity.ok(viewer.listEntities(pluginId));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * 描述某表结构（列名 / 类型 / 主键 / 可空）。
     * GET /xuanji/api/v1/console/plugins/{pluginId}/entities/{table}/describe
     */
    @GetMapping("/plugins/{pluginId}/entities/{table}/describe")
    public ResponseEntity<?> describe(@PathVariable String pluginId, @PathVariable String table) {
        try {
            return ResponseEntity.ok(viewer.describe(pluginId, table));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * 分页读取某表数据（非类型化，列为 key→value）。大表务必分页，框架会强制封顶返回行数。
     * GET /xuanji/api/v1/console/plugins/{pluginId}/entities/{table}/read
     *      ?page=1&size=20&orderBy=coins&desc=true
     */
    @GetMapping("/plugins/{pluginId}/entities/{table}/read")
    public ResponseEntity<?> read(@PathVariable String pluginId,
                                 @PathVariable String table,
                                 @RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "20") int size,
                                 @RequestParam(required = false) String orderBy,
                                 @RequestParam(required = false, defaultValue = "false") boolean desc) {
        try {
            PageReq req = PageReq.of(page, size);
            if (orderBy != null && !orderBy.isBlank()) {
                req.orderBy(orderBy, desc);
            }
            return ResponseEntity.ok(viewer.read(pluginId, table, req));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /** 非法入参（未知/越权表等）→ 400 + R 错误体，供前端 toApiError 解析 message。 */
    private static ResponseEntity<R<Void>> bad(String msg) {
        return ResponseEntity.status(400).body(R.fail(400, msg));
    }
}
