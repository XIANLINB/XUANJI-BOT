package dev.xuanji.adapter.onebot.config;

import dev.xuanji.adapter.onebot.adapter.OneBotAdapter;
import dev.xuanji.adapter.onebot.adapter.OneBotBotManager;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.adapter.onebot.websocket.OneBotEventDispatcher;
import dev.xuanji.adapter.onebot.websocket.OneBotWsClient;
import dev.xuanji.core.pipeline.BotPipeline;
import dev.xuanji.adapter.onebot.event.handler.OneBotMessageHandler;
import dev.xuanji.core.command.CommandRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OneBot 适配器装配入口 — 整个模块的唯一开关。
 *
 * <p>设计原则：<b>用户加依赖即可，不配置就完全不装配</b>。
 * 所有 OneBot 组件都在本类以 {@code @Bean} 声明（而非 {@code @Component} 被扫描），
 * 因此 {@code xuanji.onebot.enabled=false}（默认）时，容器里一个 OneBot Bean 都不会出现，
 * 不占内存、不开端口、不影响 QQ 适配器。
 *
 * <p>启用方式：
 * <pre>
 * xuanji:
 *   onebot:
 *     enabled: true
 * </pre>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OneBotProperties.class)
@ConditionalOnProperty(prefix = "xuanji.onebot", name = "enabled", havingValue = "true")
public class OneBotAutoConfiguration {

    public OneBotAutoConfiguration() {
        log.info("[OneBot] 适配器已启用（OneBot v11 标准协议，兼容 Napcat / Lagrange / go-cqhttp 等实现）");
    }

    @Bean
    public OneBotSessionRegistry oneBotSessionRegistry(JdbcTemplate jdbc) {
        return new OneBotSessionRegistry(jdbc);
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
    public OneBotEventDispatcher oneBotEventDispatcher(OneBotApiService api,
                                                       OneBotBotManager botManager,
                                                       BotPipeline pipeline,
                                                       OneBotProperties props) {
        return new OneBotEventDispatcher(api, botManager, pipeline, props);
    }

    @Bean
    public OneBotMessageHandler oneBotMessageHandler(CommandRegistry commandRegistry,
                                                     OneBotApiService api,
                                                     OneBotMessageSenderImpl sender,
                                                     JdbcTemplate jdbc) {
        return new OneBotMessageHandler(commandRegistry, api, sender, jdbc);
    }

    /**
     * 注意：OneBotController 自身带 {@code @ConditionalOnProperty(enabled=true)} 且为
     * {@code @RestController}，由 Spring 组件扫描在启用时直接创建并自动装配依赖，
     * 此处<b>不再</b>以 {@code @Bean} 重复声明，避免启用时两个同名 Bean 冲突。
     */
    /**
     * 正向 WS 客户端 —— 双重开关：模块启用 + {@code forward.enabled=true}。
     * 反向 WS 服务端在 {@code OneBotWsServer} 中自行声明（需要 WebSocketConfigurer 身份）。
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "xuanji.onebot.forward", name = "enabled", havingValue = "true")
    public OneBotWsClient oneBotWsClient(OneBotProperties props,
                                         OneBotEventDispatcher dispatcher,
                                         OneBotSessionRegistry registry,
                                         OneBotApiService api) {
        return new OneBotWsClient(props, dispatcher, registry, api);
    }
}
