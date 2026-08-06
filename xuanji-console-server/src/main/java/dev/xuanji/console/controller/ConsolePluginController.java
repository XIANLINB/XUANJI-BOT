package dev.xuanji.console.controller;

import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.plugin.PluginBotBinding;
import dev.xuanji.core.plugin.PluginBotBindingService;
import dev.xuanji.core.plugin.XuanjiPluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 插件管理（列表 / 启停 / 机器人绑定）。
 */
@RestController
@RequestMapping("/xuanji/api/console")
public class ConsolePluginController {

    private final XuanjiPluginManager pluginManager;
    private final CommandRegistry commandRegistry;
    private final PluginBotBindingService bindingService;

    public ConsolePluginController(XuanjiPluginManager pluginManager, CommandRegistry commandRegistry,
                                   PluginBotBindingService bindingService) {
        this.pluginManager = pluginManager;
        this.commandRegistry = commandRegistry;
        this.bindingService = bindingService;
    }

    @GetMapping("/plugins")
    public List<Map<String, Object>> plugins() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PluginWrapper pw : pluginManager.getPlugins()) {
            list.add(pluginRow(pw, commandRegistry.isPluginEnabled(pw.getPluginId())));
        }
        for (PluginWrapper pw : pluginManager.getResolvedPlugins()) {
            if (pluginManager.getPlugins().stream().noneMatch(pl -> pl.getPluginId().equals(pw.getPluginId()))) {
                list.add(pluginRow(pw, false));
            }
        }
        return list;
    }

    private Map<String, Object> pluginRow(PluginWrapper pw, boolean running) {
        var d = pw.getDescriptor();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", pw.getPluginId());
        p.put("name", d.getPluginId()); // PF4J 用 id 作为名称
        p.put("version", d.getVersion());
        p.put("provider", d.getProvider());
        p.put("description", d.getPluginDescription());
        p.put("state", pw.getPluginState().toString());
        p.put("running", running);
        return p;
    }

    @PostMapping("/plugins/{pluginId}/stop")
    public Map<String, Object> stopPlugin(@PathVariable String pluginId) {
        boolean ok = pluginManager.disablePlugin(pluginId);
        if (!ok) commandRegistry.setPluginEnabled(pluginId, false); // 兜底
        return Map.of("status", ok ? "ok" : "noop");
    }

    @PostMapping("/plugins/{pluginId}/start")
    public Map<String, Object> startPlugin(@PathVariable String pluginId) {
        boolean ok = pluginManager.enablePlugin(pluginId);
        if (!ok) commandRegistry.setPluginEnabled(pluginId, true); // 兜底
        return Map.of("status", ok ? "ok" : "noop");
    }

    /** P3-G：jar 热加载 — 重新加载插件 jar（改插件后不重启框架）。 */
    @PostMapping("/plugins/{pluginId}/reload")
    public Map<String, Object> reloadPlugin(@PathVariable String pluginId) {
        boolean ok = pluginManager.reloadJar(pluginId);
        return Map.of("status", ok ? "ok" : "error");
    }

    // ===== 插件-机器人绑定（P1-C）=====

    /** 列出某插件的全部绑定；空列表=全局插件（对所有 bot 生效）。 */
    @GetMapping("/plugins/{pluginId}/bindings")
    public List<PluginBotBinding> bindings(@PathVariable String pluginId) {
        return bindingService.list(pluginId);
    }

    /** 绑定插件到指定 (platform, botKey)。 */
    @PostMapping("/plugins/{pluginId}/bindings")
    public Map<String, Object> bind(@PathVariable String pluginId, @RequestBody BindRequest req) {
        bindingService.bind(pluginId, req.platform(), req.botKey());
        return Map.of("status", "ok");
    }

    /** 解绑（恢复全局生效需解绑该插件所有记录）。 */
    @DeleteMapping("/plugins/{pluginId}/bindings")
    public Map<String, Object> unbind(@PathVariable String pluginId,
                                      @RequestParam String platform, @RequestParam String botKey) {
        bindingService.unbind(pluginId, platform, botKey);
        return Map.of("status", "ok");
    }

    /** 绑定请求体。 */
    private record BindRequest(String platform, String botKey) {
    }
}
