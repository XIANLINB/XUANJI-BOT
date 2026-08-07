package dev.xuanji.console.controller;

import dev.xuanji.api.plugin.PluginConfigField;
import dev.xuanji.console.service.AuditService;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.plugin.PluginBotBinding;
import dev.xuanji.core.plugin.PluginBotBindingService;
import dev.xuanji.core.plugin.PluginConfigService;
import dev.xuanji.core.plugin.XuanjiPluginManager;
import dev.xuanji.core.storage.PluginKvStore;
import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 插件管理（列表 / 启停 / 机器人绑定 / 配置面板 / 运行时扫描 / 卸载）。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/console")
public class ConsolePluginController {

    private final XuanjiPluginManager pluginManager;
    private final CommandRegistry commandRegistry;
    private final PluginBotBindingService bindingService;
    private final PluginConfigService pluginConfigService;
    private final PluginKvStore pluginKvStore;
    private final AuditService auditService;

    public ConsolePluginController(XuanjiPluginManager pluginManager, CommandRegistry commandRegistry,
                                   PluginBotBindingService bindingService, PluginConfigService pluginConfigService,
                                   PluginKvStore pluginKvStore, AuditService auditService) {
        this.pluginManager = pluginManager;
        this.commandRegistry = commandRegistry;
        this.bindingService = bindingService;
        this.pluginConfigService = pluginConfigService;
        this.pluginKvStore = pluginKvStore;
        this.auditService = auditService;
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
        p.put("name", pluginDisplayName(pw, d)); // 优先 @XuanjiPlugin.name（如「演示插件」），兜底 pluginId
        p.put("version", d.getVersion());
        p.put("provider", d.getProvider());
        p.put("description", d.getPluginDescription());
        p.put("state", pw.getPluginState().toString());
        p.put("running", running);
        return p;
    }

    /** 从插件类 @XuanjiPlugin(name=...) 读取显示名；主类或内部类均可（注解常标在 Commands 内部类）。 */
    private String pluginDisplayName(PluginWrapper pw, org.pf4j.PluginDescriptor d) {
        try {
            if (d.getPluginClass() != null && !d.getPluginClass().isBlank()) {
                Class<?> cls = pw.getPluginClassLoader().loadClass(d.getPluginClass());
                String n = pluginNameOf(cls);
                if (n != null) return n;
                for (Class<?> inner : cls.getDeclaredClasses()) {
                    n = pluginNameOf(inner);
                    if (n != null) return n;
                }
            }
        } catch (Exception ignored) {
            // 插件类不可加载：回退 id
        }
        return d.getPluginId();
    }

    private static String pluginNameOf(Class<?> cls) {
        var plg = cls.getAnnotation(dev.xuanji.api.annotation.XuanjiPlugin.class);
        return (plg != null && plg.name() != null && !plg.name().isBlank()) ? plg.name() : null;
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

    /** 运行时扫描 plugins 目录，加载新添加的插件 jar（不重启框架）。 */
    @PostMapping("/plugins/scan")
    public Map<String, Object> scanPlugins() {
        java.util.List<String> loaded = pluginManager.scanNewPlugins();
        return Map.of("status", "ok", "loaded", loaded);
    }

    /** 卸载插件：关闭容器 + 反注册指令 + 删除 jar 文件 + 清除持久化数据（不可恢复）。 */
    @PostMapping("/plugins/{pluginId}/unload")
    public Map<String, Object> unloadPlugin(@PathVariable String pluginId,
                                            jakarta.servlet.http.HttpServletRequest request) {
        java.nio.file.Path jarPath = null;
        for (PluginWrapper w : pluginManager.getPlugins()) {
            if (w.getPluginId().equals(pluginId)) {
                jarPath = w.getPluginPath();
                break;
            }
        }
        boolean ok = pluginManager.unloadPlugin(pluginId);
        if (ok && jarPath != null) {
            try {
                java.nio.file.Files.deleteIfExists(jarPath);
            } catch (Exception e) {
                log.warn("[Plugin] 删除 jar 失败（可手动清理）: {} error={}", jarPath, e.getMessage());
            }
        }
        // 清除该插件的持久化数据（xuanji_plugin_kv）
        pluginKvStore.clear(pluginId);
        auditService.record("PLUGIN_UNLOAD", pluginId + (jarPath != null ? " (" + jarPath.getFileName() + ")" : ""),
                request.getRemoteAddr());
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

    // ===== 插件配置面板（PluginConfigProvider schema）=====

    /** 插件声明的配置字段定义（动态表单数据源）；未声明返回空列表。 */
    @GetMapping("/plugins/{pluginId}/config-schema")
    public List<Map<String, Object>> configSchema(@PathVariable String pluginId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PluginConfigField f : pluginConfigService.schema(pluginId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", f.key());
            m.put("label", f.label());
            m.put("type", f.type().name());
            m.put("defaultValue", f.defaultValue());
            m.put("options", f.options() == null ? List.of() : f.options());
            m.put("description", f.description() == null ? "" : f.description());
            out.add(m);
        }
        return out;
    }

    /** 插件当前配置（schema 默认值 + DB 已配置值合并，供表单回显）。 */
    @GetMapping("/plugins/{pluginId}/config")
    public Map<String, Object> pluginConfig(@PathVariable String pluginId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pluginId", pluginId);
        m.put("values", pluginConfigService.configMap(pluginId));
        return m;
    }

    /** 保存插件配置（只接受 schema 声明的 key）。 */
    @PutMapping("/plugins/{pluginId}/config")
    public Map<String, Object> saveConfig(@PathVariable String pluginId, @RequestBody Map<String, String> body) {
        pluginConfigService.saveConfig(pluginId, body);
        return Map.of("status", "ok");
    }

    // ===== 插件数据存储浏览（PluginStorage KV）=====

    /** 插件持久化 KV 全部数据（插件页「数据存储」区块，只读浏览）。 */
    @GetMapping("/plugins/{pluginId}/kv")
    public Map<String, Object> pluginKv(@PathVariable String pluginId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pluginId", pluginId);
        m.put("values", pluginKvStore.list(pluginId));
        return m;
    }

    /** 一键清空插件持久化 KV（测试/重置场景，只清当前插件命名空间，不动配置表）。 */
    @PostMapping("/plugins/{pluginId}/kv/clear")
    public Map<String, Object> clearPluginKv(@PathVariable String pluginId) {
        pluginKvStore.clear(pluginId);
        log.info("[Plugin] 已清空插件持久化数据: {}", pluginId);
        return Map.of("status", "ok", "pluginId", pluginId);
    }
}
