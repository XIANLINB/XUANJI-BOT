/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.xuanji.api.message.DefaultMediaRefResolver
 *  dev.xuanji.api.message.MediaRefResolver
 *  dev.xuanji.api.message.MediaRefResolverHolder
 *  dev.xuanji.core.command.CommandRegistry
 *  dev.xuanji.core.concurrent.BotOutboundExecutor
 *  dev.xuanji.core.pipeline.BotPipeline
 *  dev.xuanji.core.storage.BotDataSourceRegistry
 *  dev.xuanji.core.storage.FrameworkBotRepository
 *  dev.xuanji.core.storage.MessageEventRecorder
 *  jakarta.annotation.PostConstruct
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.context.properties.EnableConfigurationProperties
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 */
package dev.xuanji.adapter.onebot.config;

import dev.xuanji.adapter.onebot.adapter.OneBotAdapter;
import dev.xuanji.adapter.onebot.adapter.OneBotBotManager;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.event.handler.OneBotMessageHandler;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.adapter.onebot.storage.OneBotPlatformDataProvider;
import dev.xuanji.adapter.onebot.storage.OneBotRepository;
import dev.xuanji.adapter.onebot.storage.OneBotSchemaProvider;
import dev.xuanji.adapter.onebot.websocket.OneBotEventDispatcher;
import dev.xuanji.adapter.onebot.websocket.OneBotWsClient;
import dev.xuanji.api.message.DefaultMediaRefResolver;
import dev.xuanji.api.message.MediaRefResolver;
import dev.xuanji.api.message.MediaRefResolverHolder;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import dev.xuanji.core.pipeline.BotPipeline;
import dev.xuanji.core.storage.BotDataSourceRegistry;
import dev.xuanji.core.storage.FrameworkBotRepository;
import dev.xuanji.core.storage.MessageEventRecorder;
import jakarta.annotation.PostConstruct;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value={OneBotProperties.class})
@ConditionalOnProperty(prefix="xuanji.onebot", name={"enabled"}, havingValue="true")
public class OneBotAutoConfiguration {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotAutoConfiguration.class);

    public OneBotAutoConfiguration() {
        log.info("[OneBot] \u9002\u914d\u5668\u5df2\u542f\u7528\uff08OneBot v11 \u6807\u51c6\u534f\u8bae\uff0c\u517c\u5bb9 Napcat / Lagrange / go-cqhttp \u7b49\u5b9e\u73b0\uff09");
    }

    @PostConstruct
    public void registerMediaResolver() {
        MediaRefResolverHolder.register((String)"onebot", (MediaRefResolver)new DefaultMediaRefResolver());
        log.info("[OneBot] \u5df2\u6ce8\u518c\u5a92\u4f53\u89e3\u6790\u5668");
    }

    @Bean
    public OneBotSessionRegistry oneBotSessionRegistry(FrameworkBotRepository frameworkBotRepository) {
        return new OneBotSessionRegistry(frameworkBotRepository);
    }

    @Bean
    public OneBotBotManager oneBotBotManager() {
        return new OneBotBotManager();
    }

    @Bean
    public OneBotApiService oneBotApiService(OneBotSessionRegistry registry, OneBotProperties props) {
        return new OneBotApiService(registry, props);
    }

    @Bean
    public OneBotMessageSenderImpl oneBotMessageSender(OneBotApiService api) {
        return new OneBotMessageSenderImpl(api);
    }

    @Bean
    public OneBotAdapter oneBotAdapter(OneBotBotManager botManager, OneBotSessionRegistry registry) {
        return new OneBotAdapter(botManager, registry);
    }

    @Bean
    public OneBotEventDispatcher oneBotEventDispatcher(OneBotApiService api, OneBotBotManager botManager, BotPipeline pipeline, OneBotProperties props, OneBotSessionRegistry registry) {
        return new OneBotEventDispatcher(api, botManager, pipeline, props, registry);
    }

    @Bean
    public OneBotSchemaProvider oneBotSchemaProvider() {
        return new OneBotSchemaProvider();
    }

    @Bean
    public OneBotRepository oneBotRepository(BotDataSourceRegistry dataSourceRegistry, OneBotSchemaProvider oneBotSchemaProvider) {
        return new OneBotRepository(dataSourceRegistry, oneBotSchemaProvider);
    }

    @Bean
    public OneBotPlatformDataProvider oneBotPlatformDataProvider(OneBotRepository repository, OneBotSessionRegistry sessionRegistry, FrameworkBotRepository frameworkBotRepository) {
        return new OneBotPlatformDataProvider(repository, sessionRegistry, frameworkBotRepository);
    }

    @Bean
    public OneBotMessageHandler oneBotMessageHandler(CommandRegistry commandRegistry, OneBotApiService api, OneBotMessageSenderImpl sender, OneBotRepository repository, MessageEventRecorder eventRecorder, BotOutboundExecutor outbound) {
        return new OneBotMessageHandler(commandRegistry, api, sender, repository, eventRecorder, outbound);
    }

    @Bean(destroyMethod="stop")
    @ConditionalOnProperty(prefix="xuanji.onebot.forward", name={"enabled"}, havingValue="true")
    public OneBotWsClient oneBotWsClient(OneBotProperties props, OneBotEventDispatcher dispatcher, OneBotSessionRegistry registry, OneBotApiService api) {
        return new OneBotWsClient(props, dispatcher, registry, api);
    }
}

