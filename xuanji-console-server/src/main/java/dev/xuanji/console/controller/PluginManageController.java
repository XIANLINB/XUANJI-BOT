package dev.xuanji.console.controller;

import dev.xuanji.api.result.R;
import dev.xuanji.core.plugin.XuanjiPluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 插件管理 REST API — 控制台调用，支持启停/重载与状态查询。
 */
@Slf4j(topic = "xuanji.plugin")
@RestController
@RequestMapping("/api/plugins")
public class PluginManageController {

    private final XuanjiPluginManager pluginManager;

    public PluginManageController(XuanjiPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /** 插件列表（含状态） */
    @GetMapping
    public R<?> list() {
        return R.ok(pluginManager.listPlugins());
    }

    /** 启用插件 */
    @PostMapping("/{id}/enable")
    public R<?> enable(@PathVariable String id) {
        if (pluginManager.enablePlugin(id)) {
            return R.ok("已启用: " + id);
        }
        return R.fail("启用失败（插件不存在或已启用）");
    }

    /** 停用插件 */
    @PostMapping("/{id}/disable")
    public R<?> disable(@PathVariable String id) {
        if (pluginManager.disablePlugin(id)) {
            return R.ok("已停用: " + id);
        }
        return R.fail("停用失败（插件不存在或未启用）");
    }

    /** 重载插件（先停用再启用，重跑生命周期钩子） */
    @PostMapping("/{id}/reload")
    public R<?> reload(@PathVariable String id) {
        if (pluginManager.reloadPlugin(id)) {
            return R.ok("已重载: " + id);
        }
        return R.fail("重载失败（插件不存在）");
    }
}
