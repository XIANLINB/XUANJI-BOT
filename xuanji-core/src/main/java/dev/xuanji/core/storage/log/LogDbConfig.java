package dev.xuanji.core.storage.log;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置：
 * <ul>
 *   <li><b>业务库（@Primary）</b>：承接 {@code spring.datasource} → {@code data/xuanji/data/xuanji.mv.db}
 *       （框架域 + 平台域表：xuanji_bot / binding / kv / dedup / blacklist / qqbot_user / qqbot_group ...）。</li>
 *   <li><b>日志库</b>：{@code xuanji.log-datasource} → {@code data/xuanji/xuanji.log.mv.db}
 *       （消息/事件流水等大表，独立文件防膨胀）。Bean 名 {@code logDataSource} / {@code logJdbcTemplate}，
 *       需用 {@code @Qualifier("logJdbcTemplate")} 显式注入。</li>
 * </ul>
 *
 * <p>⚠️ 历史坑：此前未给业务 DataSource 标 {@code @Primary}，导致 Spring Boot 的
 * {@code DataSourceAutoConfiguration}/{@code JdbcTemplateAutoConfiguration} 因 {@code @ConditionalOnMissingBean}
 * 双双失效，业务库文件从未生成、全部 17 张表挤进日志库。现业务库显式 {@code @Primary} 修复。
 */
@Configuration(proxyBeanMethods = false)
public class LogDbConfig {

    @Value("${xuanji.log-datasource.url}")
    private String logUrl;

    @Value("${xuanji.log-datasource.username:sa}")
    private String logUsername;

    @Value("${xuanji.log-datasource.password:}")
    private String logPassword;

    @Value("${spring.datasource.url}")
    private String bizUrl;

    @Value("${spring.datasource.username:sa}")
    private String bizUsername;

    @Value("${spring.datasource.password:}")
    private String bizPassword;

    // ===================== 日志库（独立文件，显式限定名） =====================

    @Bean
    @Qualifier("logDataSource")
    public DataSource logDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(logUrl);
        ds.setUsername(logUsername);
        ds.setPassword(logPassword);
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(3);
        return ds;
    }

    @Bean
    @Qualifier("logJdbcTemplate")
    public JdbcTemplate logJdbcTemplate(@Qualifier("logDataSource") DataSource logDataSource) {
        return new JdbcTemplate(logDataSource);
    }

    // ===================== 业务库（主库，@Primary，承接 spring.datasource） =====================

    @Bean
    @Primary
    public DataSource businessDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(bizUrl);
        ds.setUsername(bizUsername);
        ds.setPassword(bizPassword);
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Bean
    @Primary
    public JdbcTemplate businessJdbcTemplate(@Qualifier("businessDataSource") DataSource businessDataSource) {
        return new JdbcTemplate(businessDataSource);
    }
}
