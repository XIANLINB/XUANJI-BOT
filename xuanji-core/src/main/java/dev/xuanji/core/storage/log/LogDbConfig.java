package dev.xuanji.core.storage.log;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 日志库独立 DataSource —— data/xuanji/xuanji.log.db
 *
 * <p>与业务库（xuanji.mv.db）物理隔离，日志膨胀不影响业务查询性能。
 */
@Configuration(proxyBeanMethods = false)
public class LogDbConfig {

    @Bean
    @ConfigurationProperties("xuanji.log-datasource")
    public DataSourceProperties logDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource logDataSource(DataSourceProperties logDataSourceProperties) {
        return logDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public JdbcTemplate logJdbcTemplate(DataSource logDataSource) {
        return new JdbcTemplate(logDataSource);
    }
}
