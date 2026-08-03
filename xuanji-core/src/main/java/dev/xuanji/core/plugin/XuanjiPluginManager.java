package dev.xuanji.core.plugin;

import dev.xuanji.api.annotation.XuanjiPlugin;
import dev.xuanji.api.plugin.XuanjiPluginBase;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import dev.xuanji.core.command.CommandRegistry;

import java.nio.file.Path;
import java.util.*;

/**
 * 璇玑插件管理器 — PF4J + Spring 子容器隔离，并承载「插件真生命周期」。
 *
 * <h3>架构</h3>
 * <pre>
 * 主 ApplicationContext（starter）
 *   └── xuanji-core Bean (Pipeline, CommandRegistry, ...)
 *        └── 插件子 ApplicationContext（每个插件独立）
 *             ├── 插件 Bean
 *             └── ClassLoader = PF4J PluginClassLoader
 * </pre>
 *
 * <h3>生命周期</h3>
 * 加载后按持久态（{@link PluginStateStore}）应用启用/停用：
 * 启用 → 调用 {@code onEnable()} 并注册指令；停用 → 调用 {@code onDisable()} 并反注册指令。
 * 运行时可通过 {@link #enablePlugin}/{@link #disablePlugin}/{@link #reloadPlugin} 热启停。
 */
@Slf4j(topic = "xuanji.plugin")
@Component
public class XuanjiPluginManager extends DefaultPluginManager {

    private final ApplicationContext parentContext;
    private final PluginStateStore stateStore;
    private final CommandRegistry commandRegistry;

    /** pluginId → 该插件注册的所有指令实例（用于启用时重注册 / 停用反注册） */
    private final Map<String, List<Object>> pluginCommands = new HashMap<>();
    /** pluginId → 当前业务生命周期状态 */
    private final Map<String, XuanjiPluginState> states = new HashMap<>();
    /** pluginId → 插件 Spring 子容器 */
    private final Map<String, AnnotationConfigApplicationContext> pluginContexts = new HashMap<>();

    public XuanjiPluginManager(ApplicationContext parentContext,
                               PluginStateStore stateStore,
                               CommandRegistry commandRegistry) {
        this.parentContext = parentContext;
        this.stateStore = stateStore;
        this.commandRegistry = commandRegistry;
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new CompoundPluginLoader()
                .add(new DevelopmentPluginLoader(this), this::isDevelopment)
                .add(new JarPluginLoader(this), this::isNotDevelopment)
                .add(new DefaultPluginLoader(this), this::isNotDevelopment);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder()
                .add(new PropertiesPluginDescriptorFinder())
                .add(new ManifestPluginDescriptorFinder());
    }

    /** 加载并启动所有插件，为每个插件创建独立 Spring 子容器，再按持久态应用启用/停用 */
    public void loadAndStartAll() {
        Path pluginsDir = getPluginsRoot();
        if (!pluginsDir.toFile().exists()) {
            pluginsDir.toFile().mkdirs();
            log.info("[Plugin] 插件目录已创建: {}", pluginsDir.toAbsolutePath());
        }

        loadPlugins();
        startPlugins();

        for (PluginWrapper wrapper : getStartedPlugins()) {
            String id = wrapper.getPluginId();
            try {
                createPluginContext(wrapper);
                // 指令实例已收集到 pluginCommands；按持久态应用启用/停用
                applyPersistedState(wrapper);
                log.info("[Plugin] 已加载: {} v{} (state={})", id, wrapper.getDescriptor().getVersion(), states.get(id));
            } catch (Exception e) {
                log.error("[Plugin] 加载失败: {}, error={}", id, e.getMessage());
                states.put(id, XuanjiPluginState.ERROR);
            }
        }

        log.info("[Plugin] 插件加载完成: {} 个运行中", pluginContexts.size());
    }

    /** 按持久态应用启用/停用 */
    private void applyPersistedState(PluginWrapper wrapper) {
        String id = wrapper.getPluginId();
        boolean enabled = stateStore.isEnabled(id);
        if (enabled) {
            callHook(wrapper, true);
            commandRegistry.setPluginEnabled(id, true);
            states.put(id, XuanjiPluginState.ENABLED);
        } else {
            callHook(wrapper, false);
            unregisterCommands(id);
            commandRegistry.setPluginEnabled(id, false);
            states.put(id, XuanjiPluginState.DISABLED);
        }
    }

    /** 为指定插件创建独立的 Spring 子容器 */
    private void createPluginContext(PluginWrapper wrapper) {
        ClassLoader pluginClassLoader = wrapper.getPluginClassLoader();
        Class<?> pluginClass;
        try {
            pluginClass = pluginClassLoader.loadClass(wrapper.getDescriptor().getPluginClass());
        } catch (ClassNotFoundException e) {
            log.warn("[Plugin] {} 插件类未指定，跳过 Spring 容器", wrapper.getPluginId());
            return;
        }

        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(pluginClassLoader);
        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.setClassLoader(pluginClassLoader);
            ctx.setParent(parentContext);

            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            beanFactory.setParentBeanFactory(parentContext.getAutowireCapableBeanFactory());
            String basePackage = pluginClass.getPackageName();
            ctx.scan(basePackage);
            ctx.refresh();

            pluginContexts.put(wrapper.getPluginId(), ctx);

            try {
                CommandRegistry registry = parentContext.getBean(CommandRegistry.class);
                scanPluginClasses(wrapper, registry);
            } catch (Exception e) {
                log.debug("[Plugin] CommandRegistry 不可用，跳过指令注册: {}", e.getMessage());
            }
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    /**
     * 从插件主类中找所有 @XuanjiPlugin 注解的类（含内部类），注册到 CommandRegistry，
     * 并收集实例以便后续反注册。
     */
    private void scanPluginClasses(PluginWrapper wrapper, CommandRegistry registry) {
        String pluginClassName = wrapper.getDescriptor().getPluginClass();
        if (pluginClassName == null || pluginClassName.isBlank()) return;

        ClassLoader cl = wrapper.getPluginClassLoader();
        try {
            Class<?> mainClass = cl.loadClass(pluginClassName);
            if (mainClass.isAnnotationPresent(XuanjiPlugin.class)) {
                registerIfPossible(mainClass, registry, cl, wrapper.getPluginId());
            }
            for (Class<?> inner : mainClass.getDeclaredClasses()) {
                if (inner.isAnnotationPresent(XuanjiPlugin.class)) {
                    registerIfPossible(inner, registry, cl, wrapper.getPluginId());
                }
            }
        } catch (Exception e) {
            log.warn("[Plugin] 扫描插件类失败: {}", pluginClassName, e.getMessage());
        }
    }

    private void registerIfPossible(Class<?> cls, CommandRegistry registry, ClassLoader cl, String pluginId) {
        try {
            Object instance = cls.getDeclaredConstructor().newInstance();
            registry.register(instance, pluginId);
            pluginCommands.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(instance);
            log.info("[Plugin] 注册指令类: {}", cls.getSimpleName());
        } catch (Exception e) {
            log.debug("[Plugin] 无法实例化 {}: {}", cls.getSimpleName(), e.getMessage());
        }
    }

    // ==================== 运行时生命周期控制 ====================

    /** 启用插件：重注册指令 → 调用 onEnable → 持久化启用 */
    public synchronized boolean enablePlugin(String id) {
        if (states.get(id) == XuanjiPluginState.ENABLED) return true;
        PluginWrapper w = getPlugin(id);
        if (w == null) return false;

        registerCommands(id);
        callHook(w, true);
        commandRegistry.setPluginEnabled(id, true);
        stateStore.setEnabled(id, true);
        states.put(id, XuanjiPluginState.ENABLED);
        log.info("[Plugin] 已启用: {}", id);
        return true;
    }

    /** 停用插件：调用 onDisable → 反注册指令 → 持久化停用 */
    public synchronized boolean disablePlugin(String id) {
        if (states.get(id) != XuanjiPluginState.ENABLED) return false;
        PluginWrapper w = getPlugin(id);
        if (w == null) return false;

        callHook(w, false);
        unregisterCommands(id);
        commandRegistry.setPluginEnabled(id, false);
        stateStore.setEnabled(id, false);
        states.put(id, XuanjiPluginState.DISABLED);
        log.info("[Plugin] 已停用: {}", id);
        return true;
    }

    /** 重载插件：先停用再启用（重跑 onDisable/onEnable 钩子，指令重注册） */
    public synchronized boolean reloadPlugin(String id) {
        XuanjiPluginState s = states.get(id);
        if (s == null) return false;
        if (s == XuanjiPluginState.ENABLED) disablePlugin(id);
        return enablePlugin(id);
    }

    private void registerCommands(String id) {
        for (Object inst : pluginCommands.getOrDefault(id, List.of())) {
            commandRegistry.register(inst, id);
        }
    }

    private void unregisterCommands(String id) {
        for (Object inst : pluginCommands.getOrDefault(id, List.of())) {
            commandRegistry.unregister(inst);
        }
    }

    /** 调用插件生命周期钩子（onEnable/onDisable） */
    private void callHook(PluginWrapper w, boolean enable) {
        try {
            Object p = w.getPlugin();
            if (p instanceof XuanjiPluginBase xp) {
                if (enable) xp.onEnable();
                else xp.onDisable();
            }
        } catch (Exception e) {
            log.warn("[Plugin] 生命周期钩子执行异常 ({}): {}", w.getPluginId(), e.getMessage());
        }
    }

    /** 插件列表（含 id/version/state/enabled） */
    public List<PluginInfo> listPlugins() {
        List<PluginInfo> list = new ArrayList<>();
        for (PluginWrapper w : getPlugins()) {
            String id = w.getPluginId();
            String version = w.getDescriptor() != null ? w.getDescriptor().getVersion() : "?";
            XuanjiPluginState st = states.getOrDefault(id, XuanjiPluginState.LOADED);
            list.add(new PluginInfo(id, version, st.name(), st == XuanjiPluginState.ENABLED));
        }
        return list;
    }

    public record PluginInfo(String id, String version, String state, boolean enabled) {}

    /** 卸载指定插件（关闭其 Spring 子容器 + 反注册指令 + 清理状态） */
    @Override
    public boolean unloadPlugin(String pluginId) {
        AnnotationConfigApplicationContext ctx = pluginContexts.remove(pluginId);
        if (ctx != null) ctx.close();
        unregisterCommands(pluginId);
        pluginCommands.remove(pluginId);
        states.remove(pluginId);
        stateStore.delete(pluginId);
        return stopPlugin(pluginId) == PluginState.STOPPED;
    }

    /** 停止所有插件并关闭容器 */
    @Override
    public void stopPlugins() {
        for (String id : new ArrayList<>(pluginContexts.keySet())) {
            unloadPlugin(id);
        }
        super.stopPlugins();
    }
}
