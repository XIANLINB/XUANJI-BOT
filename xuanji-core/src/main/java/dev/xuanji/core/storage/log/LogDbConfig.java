package dev.xuanji.core.storage.log;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 日志库独立 DataSource —— data/xuanji/xuanji.log.mv.db
 */
@Configuration(proxyBeanMethods = false)
public class LogDbConfig {

    @Value("${xuanji.log-datasource.url}")
    private String url;

    @Value("${xuanji.log-datasource.username:sa}")
    private String username;

    @Value("${xuanji.log-datasource.password:}")
    private String password;

    @Bean
    public DataSource logDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(3);
        return ds;
    }

    @Bean
    public JdbcTemplate logJdbcTemplate(DataSource logDataSource) {
        return new JdbcTemplate(logDataSource);
    }
}
