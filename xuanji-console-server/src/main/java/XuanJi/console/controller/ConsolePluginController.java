package XuanJi.console.controller;

import XuanJi.api.plugin.PluginConfigField;
import XuanJi.console.service.AuditService;
import XuanJi.core.command.CommandRegistry;
import XuanJi.core.plugin.PluginBotBinding;
import XuanJi.core.plugin.PluginBotBindingService;
import XuanJi.core.plugin.PluginConfigService;
import XuanJi.core.plugin.XuanJiPluginManager;
import XuanJi.core.storage.PluginKvStore;
import XuanJi.core.web.XuanJiApi;
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
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsolePluginController {

    private final XuanJiPluginManager pluginManager;
    private final CommandRegistry commandRegistry;
    private final PluginBotBindingService bindingService;
    private final PluginConfigService pluginConfigService;
    private final PluginKvStore pluginKvStore;
    private final AuditService auditService;

    public ConsolePluginController(XuanJiPluginManager pluginManager, CommandRegistry commandRegistry,
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
        p.put("name", pluginDisplayName(pw, d)); // 优先 @XuanJiPlugin.name（如「演示插件」），兜底 pluginId
        p.put("version", d.getVersion());
        p.put("provider", d.getProvider());
        p.put("description", d.getPluginDescription());
        p.put("state", pw.getPluginState().toString());
        p.put("running", running);
        // 插件来源/类型（从 jar MANIFEST 自定义头读取；缺失时按社区/其他兜底）
        Map<String, String> meta = pluginManifestMeta(pw);
        p.put("origin", meta.getOrDefault("origin", "community"));
        p.put("category", meta.getOrDefault("category", "other"));
        return p;
    }

    /** 读取插件 jar MANIFEST 的自定义头（Plugin-Origin / Plugin-Category）。 */
    private Map<String, String> pluginManifestMeta(PluginWrapper pw) {
        Map<String, String> meta = new LinkedHashMap<>();
        try {
            java.nio.file.Path jar = pw.getPluginPath();
            if (jar == null || !java.nio.file.Files.exists(jar)) return meta;
            try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
                java.util.jar.Manifest mf = jf.getManifest();
                if (mf == null) return meta;
                var attrs = mf.getMainAttributes();
                String origin = attrs.getValue("Plugin-Origin");
                String category = attrs.getValue("Plugin-Category");
                if (origin != null && !origin.isBlank()) meta.put("origin", origin.trim());
                if (category != null && !category.isBlank()) meta.put("category", category.trim());
            }
        } catch (Exception e) {
            log.debug("[Plugin] 读取 MANIFEST 元数据失败 {}: {}", pw.getPluginId(), e.getMessage());
        }
        return meta;
    }

    /** 从插件类 @XuanJiPlugin(name=...) 读取显示名；主类或内部类均可（注解常标在 Commands 内部类）。 */
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
        var plg = cls.getAnnotation(XuanJi.api.annotation.XuanJiPlugin.class);
        return (plg != null && plg.name() != null && !plg.name().isBlank()) ? plg.name() : null;
    }

    @PostMapping("/plugins/{pluginId}/stop")
    public Map<String, Object> stopPlugin(@PathVariable String pluginId, jakarta.servlet.http.HttpServletRequest req) {
        boolean ok = pluginManager.disablePlugin(pluginId);
        if (!ok) commandRegistry.setPluginEnabled(pluginId, false); // 兜底
        auditService.record("PLUGIN_DISABLE", "禁用插件 " + pluginId, req);
        return Map.of("status", ok ? "ok" : "noop");
    }

    @PostMapping("/plugins/{pluginId}/start")
    public Map<String, Object> startPlugin(@PathVariable String pluginId, jakarta.servlet.http.HttpServletRequest req) {
        boolean ok = pluginManager.enablePlugin(pluginId);
        if (!ok) commandRegistry.setPluginEnabled(pluginId, true); // 兜底
        auditService.record("PLUGIN_ENABLE", "启用插件 " + pluginId, req);
        return Map.of("status", ok ? "ok" : "noop");
    }

    /** P3-G：jar 热加载 — 重新加载插件 jar（改插件后不重启框架）。 */
    @PostMapping("/plugins/{pluginId}/reload")
    public Map<String, Object> reloadPlugin(@PathVariable String pluginId, jakarta.servlet.http.HttpServletRequest req) {
        boolean ok = pluginManager.reloadJar(pluginId);
        auditService.record("PLUGIN_RELOAD", "热重载插件 jar " + pluginId, req);
        return Map.of("status", ok ? "ok" : "error");
    }

    /** 运行时扫描 plugins 目录，加载新添加的插件 jar（不重启框架）。 */
    @PostMapping("/plugins/scan")
    public Map<String, Object> scanPlugins(jakarta.servlet.http.HttpServletRequest req) {
        java.util.List<String> loaded = pluginManager.scanNewPlugins();
        auditService.record("PLUGIN_LOAD", "扫描并加载新插件: " + (loaded.isEmpty() ? "无" : String.join(", ", loaded)), req);
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
        // 清除该插件的持久化数据（KV + 机器人绑定；命令/配置注销已由 pluginManager.unloadPlugin 完成）
        pluginKvStore.clear(pluginId);
        bindingService.deleteAll(pluginId);
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
    public Map<String, Object> bind(@PathVariable String pluginId, @RequestBody BindRequest req,
                                    jakarta.servlet.http.HttpServletRequest http) {
        bindingService.bind(pluginId, req.platform(), req.botKey());
        auditService.record("PLUGIN_BIND", "插件 " + pluginId + " 绑定 " + req.platform() + "/" + req.botKey(), http);
        return Map.of("status", "ok");
    }

    /** 解绑（恢复全局生效需解绑该插件所有记录）。 */
    @DeleteMapping("/plugins/{pluginId}/bindings")
    public Map<String, Object> unbind(@PathVariable String pluginId,
                                      @RequestParam String platform, @RequestParam String botKey,
                                      jakarta.servlet.http.HttpServletRequest http) {
        bindingService.unbind(pluginId, platform, botKey);
        auditService.record("PLUGIN_UNBIND", "插件 " + pluginId + " 解绑 " + platform + "/" + botKey, http);
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
    public Map<String, Object> saveConfig(@PathVariable String pluginId, @RequestBody Map<String, String> body,
                                          jakarta.servlet.http.HttpServletRequest http) {
        pluginConfigService.saveConfig(pluginId, body);
        auditService.record("PLUGIN_CONFIG_UPDATE", "插件 " + pluginId + " 配置更新 " + body.size() + " 项", http);
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
    public Map<String, Object> clearPluginKv(@PathVariable String pluginId,
                                             jakarta.servlet.http.HttpServletRequest http) {
        pluginKvStore.clear(pluginId);
        auditService.record("PLUGIN_KV_CLEAR", "清空插件持久化数据 " + pluginId, http);
        log.info("[Plugin] 已清空插件持久化数据: {}", pluginId);
        return Map.of("status", "ok", "pluginId", pluginId);
    }
}
