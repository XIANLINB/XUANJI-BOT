package dev.xuanji.starter;

import dev.xuanji.core.plugin.XuanjiPluginManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 插件自动装载器 — 容器就绪后扫描 {@code plugins/} 目录并启动全部插件。
 *
 * <p>刻意挂在 {@link ApplicationReadyEvent} 而非 {@code @PostConstruct}：插件的 Spring 子容器以主容器为
 * parent，必须等主容器完全刷新完毕才能安全地解析依赖，否则会拿到半初始化的 Bean。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginAutoLoader {

    private final XuanjiPluginManager pluginManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("[Plugin] 开始扫描插件目录...");
        pluginManager.loadAndStartAll();
    }
}
