package XuanJi.core.plugin;

import XuanJi.api.annotation.XuanJiPlugin;
import XuanJi.api.plugin.XuanJiPluginBase;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import XuanJi.core.command.CommandRegistry;

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
public class XuanJiPluginManager extends DefaultPluginManager {

    private final ApplicationContext parentContext;
    private final PluginStateStore stateStore;
    private final CommandRegistry commandRegistry;

    /** 插件配置服务（schema 注册/注销） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PluginConfigService pluginConfigService;

    /** pluginId → 该插件注册的所有指令实例（用于启用时重注册 / 停用反注册） */
    private final Map<String, List<Object>> pluginCommands = new HashMap<>();
    /** pluginId → 当前业务生命周期状态 */
    private final Map<String, XuanJiPluginState> states = new HashMap<>();
    /** pluginId → 插件 Spring 子容器 */
    private final Map<String, AnnotationConfigApplicationContext> pluginContexts = new HashMap<>();
    /** copy 式加载器实例（热加载时回查用户原始 jar 路径，且承载唯一副本名生成） */
    private CopyingJarPluginLoader copyingLoader;

    public XuanJiPluginManager(ApplicationContext parentContext,
                               PluginStateStore stateStore,
                               CommandRegistry commandRegistry) {
        this.parentContext = parentContext;
        this.stateStore = stateStore;
        this.commandRegistry = commandRegistry;
    }

    @Override
    protected PluginLoader createPluginLoader() {
        CopyingJarPluginLoader cpl = new CopyingJarPluginLoader(this);
        this.copyingLoader = cpl;
        return new CompoundPluginLoader()
                .add(new DevelopmentPluginLoader(this), this::isDevelopment)
                .add(cpl, this::isNotDevelopment)
                .add(new DefaultPluginLoader(this), this::isNotDevelopment);
    }

    /**
     * 委托 pf4j 默认插件仓库做路径探测，仅过滤结果：只保留 *.jar 文件、排除 copy- 副本
     * （防 .work 目录/副本被当插件扫描）。
     */
    @Override
    protected PluginRepository createPluginRepository() {
        PluginRepository delegate = super.createPluginRepository();
        return new PluginRepository() {
            @Override
            public List<Path> getPluginPaths() {
                try {
                    return delegate.getPluginPaths().stream()
                            .filter(p -> p.toString().endsWith(".jar")
                                    && !p.getFileName().toString().startsWith("copy-"))
                            .toList();
                } catch (Exception e) {
                    return List.of();
                }
            }

            @Override
            public boolean deletePluginPath(Path pluginPath) {
                return delegate.deletePluginPath(pluginPath);
            }
        };
    }

    /**
     * 复制式 Jar 加载器 — 先把插件 jar 复制到 {@code plugins/.work/} 再加载副本。
     *
     * <p>Windows 下 pf4j 的 JarPluginLoader 打开 jar 后会持有文件锁直到 ClassLoader 关闭，
     * 若直接加载 plugins/ 下的原 jar，框架运行期间用户无法覆盖它。复制加载后<b>原 jar 只被读取、
     * 永不被 JVM 长期持有</b>，用户可随时覆盖；热加载时重新复制新 jar 即加载新内容，不重启框架。
     *
     * <p>关键修正（修复原 jar 被锁死）：每次加载都生成<b>唯一</b>副本名（{@code copy-<原名>-<n>.jar}），
     * 绝不覆盖仍被旧 ClassLoader 锁定的旧副本；且复制失败<b>绝不回退直载原 jar</b>（否则原 jar 会被锁死）。
     * 这样无论热重载多少次，原 jar 始终处于「仅被读取」状态，可被用户随时覆盖。
     */
    static final class CopyingJarPluginLoader extends JarPluginLoader {

        private final Path workDir;
        /** 插件根目录（plugins/），构造时保存，供静态内部类回查原始 jar */
        private final Path pluginsRoot;
        /** pluginId → 用户原始 jar 路径（热加载时回读最新 jar） */
        private final Map<String, Path> originalJarPaths = new HashMap<>();

        CopyingJarPluginLoader(PluginManager pm) {
            super(pm);
            pluginsRoot = pm.getPluginsRoot();
            workDir = pluginsRoot.resolve(".work");
            try {
                java.nio.file.Files.createDirectories(workDir);
            } catch (Exception ignored) { /* 目录创建失败由复制时处理 */ }
        }

        /** 返回插件对应的「用户原始 jar」路径（热加载回读用）。 */
        Path originalJarOf(String pluginId) {
            Path p = originalJarPaths.get(pluginId);
            return (p != null && java.nio.file.Files.isRegularFile(p)) ? p : null;
        }

        /** 插件卸载后清除陈旧映射，避免内存泄漏与误回读。 */
        void forget(String pluginId) {
            originalJarPaths.remove(pluginId);
        }

        /** 卸载后立即清扫该插件的 copy 副本（须在 ClassLoader 关闭、文件句柄释放后调用，映射删除前）。 */
        void sweepCopiesFor(String pluginId) {
            Path orig = originalJarPaths.get(pluginId);
            if (orig != null) {
                sweepOldCopies(orig.getFileName().toString());
            }
        }

        @Override
        public synchronized ClassLoader loadPlugin(Path pluginPath, PluginDescriptor descriptor) {
            // 记录用户原始 jar：若传入的是旧副本，反推回 plugins/<原名>.jar
            Path original = pluginPath;
            String fn = original.getFileName().toString();
            if (fn.startsWith("copy-")) {
                Path cand = pluginsRoot.resolve(stripCopyPrefix(fn));
                if (java.nio.file.Files.isRegularFile(cand)) original = cand;
            }
            originalJarPaths.put(descriptor.getPluginId(), original);

            String base = original.getFileName().toString();
            // best-effort 清理同名旧副本（仍被旧 ClassLoader 占用的会删除失败，忽略即可，下方用唯一名避开）
            sweepOldCopies(base);
            // 生成唯一副本名，绝不替换仍被占用的旧副本
            Path copy = uniqueCopy(base);
            try {
                java.nio.file.Files.copy(original, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // 宁可失败也不回退直载原 jar——回退会让原 jar 被 JVM 锁死，用户再也无法覆盖
                throw new RuntimeException("[Plugin] 复制插件到工作目录失败: " + e.getMessage(), e);
            }
            return super.loadPlugin(copy, descriptor);
        }

        private static String stripCopyPrefix(String fn) {
            return fn.startsWith("copy-") ? fn.substring("copy-".length()) : fn;
        }

        /** 删除 copy-<base>-* 的旧副本（best-effort）。 */
        private void sweepOldCopies(String base) {
            try (var s = java.nio.file.Files.list(workDir)) {
                s.filter(p -> p.getFileName().toString().startsWith("copy-" + base + "-"))
                 .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) { /* 占用中，跳过 */ } });
            } catch (Exception ignored) { /* 目录不可列，忽略 */ }
        }

        /** 生成形如 copy-<base>-<n>.jar 的唯一路径，自动跳过已存在（可能仍被锁）的名字。 */
        private Path uniqueCopy(String base) {
            int n = 0;
            Path p;
            do {
                p = workDir.resolve("copy-" + base + "-" + (n++));
            } while (java.nio.file.Files.exists(p));
            return p;
        }
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
        // 启动清理 .work 工作目录的旧副本（热加载副本每次 REPLACE，正常无累积；此处兜底清残留）
        cleanupWorkDir(pluginsDir);

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
                states.put(id, XuanJiPluginState.ERROR);
            }
        }

        log.info("[Plugin] 插件加载完成: {} 个运行中", pluginContexts.size());
    }

    /** 启动时清空 .work 工作目录的 copy 副本（CopyingJarPluginLoader 加载时会重新复制）。 */
    private void cleanupWorkDir(Path pluginsDir) {
        Path work = pluginsDir.resolve(".work");
        try {
            if (!java.nio.file.Files.isDirectory(work)) return;
            try (var s = java.nio.file.Files.list(work)) {
                long removed = s.filter(p -> p.getFileName().toString().startsWith("copy-"))
                        .map(p -> {
                            try { java.nio.file.Files.deleteIfExists(p); return 1L; }
                            catch (Exception e) { return 0L; }
                        })
                        .reduce(0L, Long::sum);
                if (removed > 0) {
                    log.info("[Plugin] 启动清理 .work 旧副本 {} 个", removed);
                }
            }
        } catch (Exception e) {
            log.debug("[Plugin] 清理 .work 失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 按持久态应用启用/停用 */
    private void applyPersistedState(PluginWrapper wrapper) {
        String id = wrapper.getPluginId();
        boolean enabled = stateStore.isEnabled(id);
        log.info("[Plugin] 恢复持久化状态: {} → {}", id, enabled ? "启用(ENABLED)" : "停用(DISABLED)");
        if (enabled) {
            callHook(wrapper, true);
            commandRegistry.setPluginEnabled(id, true);
            states.put(id, XuanJiPluginState.ENABLED);
        } else {
            callHook(wrapper, false);
            unregisterCommands(id);
            commandRegistry.setPluginEnabled(id, false);
            states.put(id, XuanJiPluginState.DISABLED);
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
     * 从插件主类中找所有 @XuanJiPlugin 注解的类（含内部类），注册到 CommandRegistry，
     * 并收集实例以便后续反注册。
     */
    private void scanPluginClasses(PluginWrapper wrapper, CommandRegistry registry) {
        String pluginClassName = wrapper.getDescriptor().getPluginClass();
        if (pluginClassName == null || pluginClassName.isBlank()) return;

        ClassLoader cl = wrapper.getPluginClassLoader();
        try {
            Class<?> mainClass = cl.loadClass(pluginClassName);
            if (mainClass.isAnnotationPresent(XuanJiPlugin.class)) {
                registerIfPossible(mainClass, registry, cl, wrapper.getPluginId());
            }
            for (Class<?> inner : mainClass.getDeclaredClasses()) {
                if (inner.isAnnotationPresent(XuanJiPlugin.class)) {
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
            // 插件实现了 PluginConfigProvider → 注册配置 schema（控制台动态面板数据源）
            if (instance instanceof XuanJi.api.plugin.PluginConfigProvider pcp && pluginConfigService != null) {
                pluginConfigService.register(pluginId, pcp);
            }
            pluginCommands.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(instance);
            log.info("[Plugin] 注册指令类: {}", cls.getSimpleName());
        } catch (Exception e) {
            log.debug("[Plugin] 无法实例化 {}: {}", cls.getSimpleName(), e.getMessage());
        }
    }

    // ==================== 运行时生命周期控制 ====================

    /** 启用插件：重注册指令 → 调用 onEnable → 持久化启用 */
    public synchronized boolean enablePlugin(String id) {
        if (states.get(id) == XuanJiPluginState.ENABLED) return true;
        PluginWrapper w = getPlugin(id);
        if (w == null) return false;

        registerCommands(id);
        callHook(w, true);
        commandRegistry.setPluginEnabled(id, true);
        stateStore.setEnabled(id, true);
        states.put(id, XuanJiPluginState.ENABLED);
        log.info("[Plugin] 已启用: {}", id);
        return true;
    }

    /** 停用插件：调用 onDisable → 反注册指令 → 持久化停用 */
    public synchronized boolean disablePlugin(String id) {
        if (states.get(id) != XuanJiPluginState.ENABLED) return false;
        PluginWrapper w = getPlugin(id);
        if (w == null) return false;

        callHook(w, false);
        unregisterCommands(id);
        commandRegistry.setPluginEnabled(id, false);
        stateStore.setEnabled(id, false);
        states.put(id, XuanJiPluginState.DISABLED);
        log.info("[Plugin] 已停用: {}", id);
        return true;
    }

    /** 重载插件：先停用再启用（重跑 onDisable/onEnable 钩子，指令重注册） */
    public synchronized boolean reloadPlugin(String id) {
        XuanJiPluginState s = states.get(id);
        if (s == null) return false;
        if (s == XuanJiPluginState.ENABLED) disablePlugin(id);
        return enablePlugin(id);
    }

    /**
     * P3-G：<b>jar 热加载</b> — 重新加载插件 jar（改插件后不重启框架）。
     *
     * <p>流程：卸载旧实例（关 Spring 子容器 + 反注册指令 + pf4j stop）→
     * 重新 loadPlugin(jarPath) → 新建子容器 → 恢复原启用状态。
     * 持久态保留（不因热加载丢失启用/停用设置）。
     *
     * @return true 表示重载成功
     */
    public synchronized boolean reloadJar(String id) {
        PluginWrapper old = getPlugin(id);
        if (old == null) return false;
        Path jarPath = resolveReloadJar(old);
        XuanJiPluginState prev = states.getOrDefault(id, XuanJiPluginState.LOADED);
        boolean wasEnabled = prev == XuanJiPluginState.ENABLED;

        // 1. 卸载旧实例（容器 / 指令 / pf4j），保留持久态
        AnnotationConfigApplicationContext ctx = pluginContexts.remove(id);
        if (ctx != null) {
            try { ctx.close(); } catch (Exception e) { log.debug("[Plugin] 子容器关闭异常: {}", e.getMessage()); }
        }
        unregisterCommands(id);
        pluginCommands.remove(id);
        states.remove(id);
        // pf4j 原生卸载：停插件 + 从 plugins map 移除路径条目（stopPlugin 不移除，同路径二次加载会报 already loaded）；
        // 不走本类覆写的 unloadPlugin（那会删 stateStore 持久态）
        super.unloadPlugin(id);
        // 关键修复：PF4J 卸载不会关闭插件 URLClassLoader，导致 .work/copy-*.jar 在 Windows 下持续被占用，
        // 下次热加载复制失败并回退直载原 jar（从而锁死原 jar，无法再覆盖更新）。此处主动关闭 ClassLoader 释放文件锁。
        closePluginClassLoader(old);
        log.info("[Plugin] 热加载: 已卸载旧实例 {}", id);

        // 2. 重新加载新 jar
        try {
            loadPlugin(jarPath);
        } catch (Exception e) {
            log.error("[Plugin] 热加载: 加载新 jar 失败: {}, error={}", id, e.getMessage());
            return false;
        }
        startPlugin(id);
        PluginWrapper w = getPlugin(id);
        if (w == null) {
            log.error("[Plugin] 热加载: 重新加载后插件不可见: {}", id);
            return false;
        }

        // 3. 新建子容器 + 恢复原启用状态
        createPluginContext(w);
        stateStore.setEnabled(id, wasEnabled);
        applyPersistedState(w);
        log.info("[Plugin] 热加载完成: {} v{} (state={})",
                id, w.getDescriptor().getVersion(), states.get(id));
        return true;
    }

    /** 解析热加载应使用的「原始」jar 路径：优先用 copy 加载器记录的「用户原始 jar」，
     *  确保热加载加载的是用户更新后的内容，而非仍被锁定的旧副本。 */
    private Path resolveReloadJar(PluginWrapper old) {
        Path p = copyingLoader != null ? copyingLoader.originalJarOf(old.getPluginId()) : null;
        if (p != null) return p;
        return old.getPluginPath();
    }

    /** 关闭插件 ClassLoader（释放底层 jar 文件句柄）。Windows 下 PF4J 卸载不关闭 URLClassLoader，
     *  会令 .work/copy-*.jar 持续被占用，下次热加载复制失败并回退直载原 jar（锁死原 jar）。 */
    private void closePluginClassLoader(PluginWrapper wrapper) {
        ClassLoader cl = wrapper.getPluginClassLoader();
        if (cl instanceof java.io.Closeable c) {
            try { c.close(); } catch (Exception e) { log.debug("[Plugin] 关闭插件 ClassLoader 失败(可忽略): {}", e.getMessage()); }
        }
    }

    private void registerCommands(String id) {
        for (Object inst : pluginCommands.getOrDefault(id, List.of())) {
            commandRegistry.register(inst, id);
        }
    }

    /**
     * 运行时扫描 plugins 目录，加载新增的插件 jar（不重启框架）。
     *
     * <p>框架启动时只加载一次（{@link #loadAndStartAll()}）；运行中往 plugins/ 放入新 jar
     * 后调用本方法即可发现并加载：解析 descriptor → 跳过已加载 → loadPlugin + startPlugin
     * + 建子容器 + 按持久态应用启停。
     *
     * @return 本次新加载的插件 id 列表（空 = 无新增）
     */
    public synchronized java.util.List<String> scanNewPlugins() {
        java.util.List<String> loaded = new java.util.ArrayList<>();
        Path pluginsDir = getPluginsRoot();
        if (!pluginsDir.toFile().exists()) return loaded;
        java.util.Set<String> known = new java.util.HashSet<>();
        for (PluginWrapper w : getPlugins()) known.add(w.getPluginId());

        org.pf4j.ManifestPluginDescriptorFinder finder = new org.pf4j.ManifestPluginDescriptorFinder();
        try (var stream = java.nio.file.Files.list(pluginsDir)) {
            for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().startsWith("copy-"))
                    .toList()) {
                String id;
                try {
                    org.pf4j.PluginDescriptor pd = finder.find(jar);
                    if (pd == null || pd.getPluginId() == null || pd.getPluginId().isBlank()) continue;
                    id = pd.getPluginId();
                } catch (Exception e) {
                    log.debug("[Plugin] 扫描跳过非插件 jar: {} ({})", jar.getFileName(), e.getMessage());
                    continue;
                }
                if (known.contains(id)) continue;
                try {
                    loadPlugin(jar);
                    startPlugin(id);
                    PluginWrapper w = getPlugin(id);
                    if (w == null) continue;
                    createPluginContext(w);
                    applyPersistedState(w);
                    loaded.add(id);
                    log.info("[Plugin] 扫描发现并加载新插件: {} v{}", id, w.getDescriptor().getVersion());
                } catch (Exception e) {
                    log.error("[Plugin] 扫描加载失败: {} error={}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Plugin] 扫描插件目录失败: {}", e.getMessage());
        }
        return loaded;
    }

    private void unregisterCommands(String id) {
        for (Object inst : pluginCommands.getOrDefault(id, List.of())) {
            commandRegistry.unregister(inst);
        }
        if (pluginConfigService != null) {
            pluginConfigService.unregister(id);
        }
    }

    /** 调用插件生命周期钩子（onEnable/onDisable） */
    private void callHook(PluginWrapper w, boolean enable) {
        try {
            Object p = w.getPlugin();
            if (p instanceof XuanJiPluginBase xp) {
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
            XuanJiPluginState st = states.getOrDefault(id, XuanJiPluginState.LOADED);
            list.add(new PluginInfo(id, version, st.name(), st == XuanJiPluginState.ENABLED));
        }
        return list;
    }

    public record PluginInfo(String id, String version, String state, boolean enabled) {}

    /** 卸载指定插件（关闭其 Spring 子容器 + 反注册指令 + 清理状态） */
    @Override
    public boolean unloadPlugin(String pluginId) {
        PluginWrapper w = getPlugin(pluginId);
        AnnotationConfigApplicationContext ctx = pluginContexts.remove(pluginId);
        if (ctx != null) ctx.close();
        unregisterCommands(pluginId);
        pluginCommands.remove(pluginId);
        states.remove(pluginId);
        boolean stopped = stopPlugin(pluginId) == PluginState.STOPPED;
        if (w != null) closePluginClassLoader(w);
        if (copyingLoader != null) {
            // 先清扫副本（需用 originalJarPaths 映射）再 forget，避免卸载后磁盘残留到下次启动
            copyingLoader.sweepCopiesFor(pluginId);
            copyingLoader.forget(pluginId);
        }
        stateStore.delete(pluginId);
        return stopped;
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
