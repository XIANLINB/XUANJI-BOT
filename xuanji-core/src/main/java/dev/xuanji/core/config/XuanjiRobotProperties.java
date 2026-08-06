package dev.xuanji.core.config;

import lombok.Data;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 机器人配置属性
 *
 * <p>从 xuanji-robots.yml 读取机器人配置，包括全局配置和各机器人的详细配置。
 *
 * <h3>配置文件结构</h3>
 * <pre>
 * xuanji:
 *   webhook-url: "example.com"        # 全局 Webhook 域名
 *   is-new-openbot: true               # 全局：是否使用新开放平台
 *   robots:                            # 机器人列表
 *     bot1:
 *       app-id: "xxx"
 *       client-secret: "xxx"
 *       is-sandbox: false
 *       connection-method: websocket
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "xuanji")
@PropertySource(value = "file:data/xuanji-robots.yml", ignoreResourceNotFound = true, factory = YamlPropertySourceFactory.class)
public class XuanjiRobotProperties {

    /** 全局 Webhook 回调域名（使用 webhook 连接方式时必填） */
    private String webhookUrl;

    /** 是否使用新开放平台（全局配置，影响所有机器人） */
    private boolean isNewOpenBot;

    /** 是否忽略机器人发送的消息（true=忽略，false=正常处理） */
    private boolean ignoreBotMessages;

    /** 机器人配置列表，key = 机器人标识（如 bot1、bot2） */
    private Map<String, RobotProperties> robots = new HashMap<>();

    /** 机器人主人 member_openid（按 botKey 配置） */
    private Map<String, String> master = new HashMap<>();

    /** 是否已登记该 appId（供启动时从库合并去重）。 */
    public boolean containsAppId(String appId) {
        if (robots == null || appId == null) return false;
        for (RobotProperties r : robots.values()) {
            if (appId.equals(r.getAppId())) return true;
        }
        return false;
    }

    /** 运行时注入一个机器人配置（botKey 缺省用 appId；不覆盖已有同 botKey 项）。 */
    public void registerRobot(String botKey, String appId, String clientSecret,
                              boolean sandbox, String connectionMethod, String status) {
        if (robots == null) robots = new HashMap<>();
        String key = botKey != null && !botKey.isBlank() ? botKey : appId;
        if (robots.containsKey(key)) return;
        RobotProperties rp = new RobotProperties();
        rp.setAdapter("qqbot");
        rp.setAppId(appId);
        rp.setClientSecret(clientSecret);
        rp.setSandbox(sandbox);
        rp.setConnectionMethod(connectionMethod);
        rp.setStatus(status);
        robots.put(key, rp);
    }

    /**
     * 单个机器人的配置属性
     */
    @Data
    public static class RobotProperties {
        /** 适配器类型（qqbot / onebot / feishu），默认 qqbot */
        private String adapter;

        /** QQ 开放平台 AppID */
        private String appId;

        /** QQ 开放平台 AppSecret（明文） */
        private String clientSecret;

        /** 是否使用沙箱环境（true=沙箱，false=正式环境） */
        private boolean sandbox;

        /** 连接方式：websocket 或 webhook */
        private String connectionMethod;

        /** 启停状态（ONLINE / OFFLINE），控制台可写，启动器据此决定是否拉起 */
        private String status;

        /** Webhook 回调地址（connectionMethod=webhook 时使用） */
        private String webhookUrl;
    }

    /**
     * 运行时热重载：从磁盘重新读取 {@code data/xuanji-robots.yml} 并刷新本 bean 的字段。
     *
     * <p>原因：{@code @PropertySource} + {@code @ConfigurationProperties} 仅在上下文刷新时
     * 静态绑定一次。向导（BotConfigController）保存/删除配置并热重载后，本 bean 若不被刷新，
     * 后续读取 {@link #getRobots()} / {@link #findBotKeyByRobotId(String)} / {@link #isIgnoreBotMessages()}
     * 的代码会拿到过期数据。本方法用 Spring {@link Binder} 重建一次绑定，效果等价于重启时的绑定。
     *
     * <p>路径必须与 {@code RobotsFile.PATH} 一致（{@code data/xuanji-robots.yml}）。
     */
    /** 重载配置（starter 控制台保存后调用；历史方法名 reload）。 */
    public void reload() {
        reloadFromYaml();
    }

    public void reloadFromYaml() {
        Path path = Paths.get("data", "xuanji-robots.yml");        if (!Files.exists(path)) {
            // 与 @PropertySource(ignoreResourceNotFound=true) 行为一致：文件缺失时清空
            this.robots.clear();
            this.master.clear();
            return;
        }
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(path));
        Properties props = factory.getObject();
        if (props == null || props.isEmpty()) return;

        Map<String, Object> map = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(map);
        XuanjiRobotProperties fresh = new Binder(source)
                .bind("xuanji", Bindable.of(XuanjiRobotProperties.class))
                .orElse(null);
        if (fresh == null) return;

        this.webhookUrl = fresh.webhookUrl;
        this.isNewOpenBot = fresh.isNewOpenBot;
        this.ignoreBotMessages = fresh.ignoreBotMessages;
        this.robots = fresh.robots;
        this.master = fresh.master;
    }

    /** 根据 appId 反查 YAML 中的 botKey */
    public String findBotKeyByRobotId(String robotId) {
        if (robots == null) return null;
        for (var e : robots.entrySet()) {
            String a = e.getValue().getAppId();
            if (a != null && a.equals(robotId)) return e.getKey();
        }
        return null;
    }
}
