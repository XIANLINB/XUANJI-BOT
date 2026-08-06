/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.core.plugin.XuanjiPluginManager
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.boot.context.event.ApplicationReadyEvent
 *  org.springframework.context.event.EventListener
 *  org.springframework.stereotype.Component
 */
package dev.xuanji.starter;

import dev.xuanji.core.plugin.XuanjiPluginManager;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PluginAutoLoader {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(PluginAutoLoader.class);
    private final XuanjiPluginManager pluginManager;

    @EventListener(value={ApplicationReadyEvent.class})
    public void onReady() {
        log.info("[Plugin] \u5f00\u59cb\u626b\u63cf\u63d2\u4ef6\u76ee\u5f55...");
        this.pluginManager.loadAndStartAll();
    }

    @Generated
    public PluginAutoLoader(XuanjiPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
}

