package dev.xuanji.starter;

import dev.xuanji.core.plugin.XuanjiPluginManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 插件自动加载器 —— ApplicationReady 后扫描 plugins/ 目录并加载所有 PF4J 插件。
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
