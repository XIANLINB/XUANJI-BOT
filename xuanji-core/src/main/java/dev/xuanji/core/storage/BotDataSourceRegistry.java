package dev.xuanji.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.xuanji.core.concurrent.ThreadPoolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot 数据源注册表 — 按需打开平台共享库 / per-bot 实例库，缓存 JdbcTemplate（HikariCP 连接池）。
 *
 * <pre>
 * 平台共享库：data/{platform}/{platform}.mv.db（全 bot 一张表，id 全局唯一）
 * 实例库：    data/{platform}/{appid}/data/{appid}.mv.db（5 张业务表，bot_id 逻辑关联平台库）
 * </pre>
 *
 * <p><b>性能（dispatch 慢根因修复）</b>：原用 DriverManagerDataSource（无池），JdbcTemplate 每次
 * query/update 都新建物理 H2 连接（打开文件、读头），单条写几十 ms——handler 内 5~6 步落库叠加
 * 成 700-900ms。改用 HikariCP 池化（per 库独立小池），连接复用，消息处理耗时可大幅下降。
 *
 * <p>删机器人时必须 {@link #closeInstance} 释放 H2 文件锁，否则后续操作（目录删除）会失败。
 */
@Slf4j
@Component
public class BotDataSourceRegistry implements DisposableBean {

    private final Map<String, JdbcTemplate> cache = new ConcurrentHashMap<>();
    /** 全局配置（tune.hikari_instance_max 读取实例库连接池上限，未配置默认 3）。 */
    private final dev.xuanji.core.config.ConfigService configService;

    public BotDataSourceRegistry(dev.xuanji.core.config.ConfigService configService) {
        this.configService = configService;
    }

    /** 实例库连接池最大连接数：读 tune.hikari_instance_max，默认 3。 */
    private int instancePoolMax() {
        try {
            var g = configService.getGlobalConfig();
            String v = g.get("tune.hikari_instance_max");
            if (v != null && !v.isBlank()) {
                int n = Integer.parseInt(v.trim());
                if (n > 0) return n;
            }
        } catch (Exception ignored) { /* 默认 3 */ }
        return 3;
    }

    /** 平台共享库 JdbcTemplate。 */
    public JdbcTemplate forPlatform(String platform) {
        return compute("platform:" + platform, "./data/" + platform + "/" + platform);
    }

    /** per-bot 实例库 JdbcTemplate。 */
    public JdbcTemplate forInstance(String platform, String instanceId) {
        return compute("instance:" + platform + ":" + instanceId,
                "./data/" + platform + "/" + instanceId + "/data/" + instanceId);
    }

    /** 释放某实例库连接（删机器人前置步骤），失败可忽略。 */
    public void closeInstance(String platform, String instanceId) {
        String key = "instance:" + platform + ":" + instanceId;
        JdbcTemplate tpl = cache.remove(key);
        if (tpl != null) {
            closeQuietly(tpl.getDataSource());
        }
    }

    private JdbcTemplate compute(String key, String basePath) {
        return cache.computeIfAbsent(key, k -> {
            // AUTO_SERVER=TRUE：同一 JVM 内多连接共享 H2 文件（池化必需，否则并发写 lock timeout）
            String url = "jdbc:h2:file:" + basePath + ";AUTO_SERVER=TRUE";
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername("sa");
            cfg.setPassword("");
            cfg.setPoolName("h2-" + k.replaceAll("[^a-zA-Z0-9]", "-"));
            cfg.setMaximumPoolSize(instancePoolMax());
            cfg.setMinimumIdle(1);
            cfg.setIdleTimeout(60_000);
            cfg.setConnectionTimeout(5_000);
            // H2 单连接文件锁：禁止池内并发连接同一文件导致 lock timeout
            cfg.setAutoCommit(true);
            HikariDataSource ds = new HikariDataSource(cfg);
            // 注册到监控：连接池实时状态（active/idle/total）
            String poolKey = key.replaceFirst("^(platform|instance):", "");
            ThreadPoolRegistry.register("H2连接池(" + poolKey + ")", () -> {
                HikariPoolMXBean mx = ds.getHikariPoolMXBean();
                return new ThreadPoolRegistry.PoolInfo(
                        "H2连接池(" + poolKey + ")", "HikariCP",
                        cfg.getMinimumIdle(), cfg.getMaximumPoolSize(),
                        mx.getActiveConnections(), mx.getTotalConnections(), mx.getThreadsAwaitingConnection(), -1,
                        "H2 单文件锁限制，per 库独立小池");
            });
            log.info("[DataSource] 打开 H2(池化): {}", url);
            return new JdbcTemplate(ds);
        });
    }

    private void closeQuietly(DataSource ds) {
        try {
            if (ds instanceof AutoCloseable ac) {
                ac.close();
            }
        } catch (Exception e) {
            log.debug("[DataSource] 关闭失败（可忽略）: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        cache.values().forEach(t -> closeQuietly(t.getDataSource()));
        cache.clear();
    }
}
