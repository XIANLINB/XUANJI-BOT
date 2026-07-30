package com.qunxing.qq_bot_xuanji.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 公共 HttpClient 配置
 *
 * <p>将 Java 11+ 的 {@link HttpClient} 注册为 Spring Bean，所有需要 HTTP 调用的服务统一注入使用。
 * 避免每个 Service 各自创建 HttpClient 实例导致连接池浪费和资源泄漏。
 *
 * <h3>配置说明</h3>
 * <ul>
 *   <li><b>connectTimeout</b>：10 秒（与 QQ API 建议一致）</li>
 *   <li><b>followRedirects</b>：NORMAL（自动跟随重定向）</li>
 *   <li><b>连接池</b>：使用 Java 11+ HttpClient 内置的连接池，自动管理</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final HttpClient httpClient;
 *     public MyService(HttpClient httpClient) {
 *         this.httpClient = httpClient; // 注入共享的 HttpClient
 *     }
 * }
 * }</pre>
 *
 * <h3>使用者</h3>
 * <ul>
 *   <li>{@link com.qunxing.qq_bot_xuanji.core.api.QqApiService} — QQ 平台 API 调用</li>
 *   <li>{@link com.qunxing.qq_bot_xuanji.core.websocket.GatewayService} — 网关地址获取</li>
 * </ul>
 */
@Configuration
public class HttpClientConfig {

    /**
     * 创建并注册公共 HttpClient Bean
     *
     * @return 配置好的 HttpClient 实例（单例，全局共享）
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
