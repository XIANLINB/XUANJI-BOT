package dev.xuanji.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.HashMap;
import java.util.Map;

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
@PropertySource(value = "classpath:xuanji-robots.yml", factory = YamlPropertySourceFactory.class)
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
    }

    /** 根据 appId 反查 YAML 中的 botKey */
    public String findBotKeyByRobotId(long robotId) {
        if (robots == null) return null;
        for (var e : robots.entrySet()) {
            String a = e.getValue().getAppId();
            if (a != null && ((long) a.hashCode()) == robotId) return e.getKey();
        }
        return null;
    }
}
