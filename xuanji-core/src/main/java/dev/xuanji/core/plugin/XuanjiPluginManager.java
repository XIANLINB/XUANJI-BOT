package dev.xuanji.core.plugin;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * 璇玑插件管理器 — PF4J + Spring 子容器隔离。
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
 * <p>插件退出时子容器关闭 → 所有 Bean 销毁 → ClassLoader 卸载 → 无内存泄漏。
 */
@Slf4j(topic = "xuanji.plugin")
@Component
public class XuanjiPluginManager extends DefaultPluginManager {

    private final ApplicationContext parentContext;
    private final Map<String, AnnotationConfigApplicationContext> pluginContexts = new HashMap<>();
    private final Map<String, java.util.List<Object>> pluginInstances = new HashMap<>();

    public XuanjiPluginManager(ApplicationContext parentContext) {
        this.parentContext = parentContext;
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

    /** 加载并启动所有插件，为每个插件创建独立 Spring 子容器 */
    public void loadAndStartAll() {
        Path pluginsDir = getPluginsRoot();
        if (!pluginsDir.toFile().exists()) {
            pluginsDir.toFile().mkdirs();
            log.info("[Plugin] 插件目录已创建: {}", pluginsDir.toAbsolutePath());
        }

        loadPlugins();
        startPlugins();

        for (PluginWrapper wrapper : getStartedPlugins()) {
            try {
                createPluginContext(wrapper);
                log.info("[Plugin] 已加载: {} v{}", wrapper.getPluginId(), wrapper.getDescriptor().getVersion());
            } catch (Exception e) {
                log.error("[Plugin] 加载失败: {}, error={}", wrapper.getPluginId(), e.getMessage());
            }
        }

        log.info("[Plugin] 插件加载完成: {} 个运行中", pluginContexts.size());
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

        // 用插件 ClassLoader 创建子容器
        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(pluginClassLoader);
        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.setClassLoader(pluginClassLoader);
            ctx.setParent(parentContext);

            // 创建共享 BeanFactory，让插件能访问父容器 Bean
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            beanFactory.setParentBeanFactory(parentContext.getAutowireCapableBeanFactory());
            // 扫描插件类所在的包
            String basePackage = pluginClass.getPackageName();
            ctx.scan(basePackage);
            ctx.refresh();

            pluginContexts.put(wrapper.getPluginId(), ctx);

            // 扫描插件 classpath，找出所有 @XuanjiPlugin 类并注册 @Command
            try {
                dev.xuanji.core.command.CommandRegistry registry =
                        parentContext.getBean(dev.xuanji.core.command.CommandRegistry.class);
                // 遍历插件 jar 中的类（通过 ClassLoader 扫描）
                scanPluginClasses(wrapper, registry);
            } catch (Exception e) {
                log.debug("[Plugin] CommandRegistry 不可用，跳过指令注册: {}", e.getMessage());
            }

        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    /**
     * 从插件主类中找所有 @XuanjiPlugin 注解的类（含内部类），注册到 CommandRegistry。
     */
    private void scanPluginClasses(PluginWrapper wrapper, dev.xuanji.core.command.CommandRegistry registry) {
        String pluginClassName = wrapper.getDescriptor().getPluginClass();
        if (pluginClassName == null || pluginClassName.isBlank()) return;

        ClassLoader cl = wrapper.getPluginClassLoader();
        try {
            Class<?> mainClass = cl.loadClass(pluginClassName);
            // 检查主类本身
            if (mainClass.isAnnotationPresent(dev.xuanji.api.annotation.XuanjiPlugin.class)) {
                registerIfPossible(mainClass, registry, cl, wrapper.getPluginId());
            }
            // 检查内部静态类（如 DemoPlugin.Commands）
            for (Class<?> inner : mainClass.getDeclaredClasses()) {
                if (inner.isAnnotationPresent(dev.xuanji.api.annotation.XuanjiPlugin.class)) {
                    registerIfPossible(inner, registry, cl, wrapper.getPluginId());
                }
            }
        } catch (Exception e) {
            log.warn("[Plugin] 扫描插件类失败: {}", pluginClassName, e.getMessage());
        }
    }

    private void registerIfPossible(Class<?> cls, dev.xuanji.core.command.CommandRegistry registry, ClassLoader cl, String pluginId) {
        try {
            Object instance = cls.getDeclaredConstructor().newInstance();
            registry.register(instance, pluginId);
            // 找到这个调用来自哪个 pluginId（从 scanPluginClasses 传入，通过方法签名无法获取，暂时用 class 名推测）
            // 这里用简单方案：把所有注册的 instance 放到一个全局列表
            log.info("[Plugin] 注册指令类: {}", cls.getSimpleName());
        } catch (Exception e) {
            log.debug("[Plugin] 无法实例化 {}: {}", cls.getSimpleName(), e.getMessage());
        }
    }

    /** 卸载指定插件（关闭其 Spring 子容器） */
    public boolean unloadPlugin(String pluginId) {
        AnnotationConfigApplicationContext ctx = pluginContexts.remove(pluginId);
        if (ctx != null) {
            ctx.close();
            log.info("[Plugin] 已卸载: {}", pluginId);
        }
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
