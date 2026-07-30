package dev.xuanji.core.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * H2 控制台 — 手动注册 Servlet（绕过 SB4 自动装配兼容性问题）。
 */
@Configuration
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2Console() {
        ServletRegistrationBean<JakartaWebServlet> bean = new ServletRegistrationBean<>(
                new JakartaWebServlet(), "/h2-console/*");
        bean.addInitParameter("webAllowOthers", "true");
        bean.setLoadOnStartup(1);
        return bean;
    }
}
