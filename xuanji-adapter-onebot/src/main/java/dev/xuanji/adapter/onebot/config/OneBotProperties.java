package dev.xuanji.adapter.onebot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OneBot v11 适配器配置。
 *
 * <p>参考 Shiro 连接 Napcat 的范式：框架本身不认识 Napcat / go-cqhttp / Lagrange，
 * 只认 OneBot v11 标准协议，任何兼容实现都能接入。两种连接方向都支持：
 *
 * <h3>反向 WS（推荐，框架作服务端）</h3>
 * <pre>
 * xuanji:
 *   onebot:
 *     enabled: true
 *     reverse:
 *       enabled: true
 *       path: /onebot/ws          # Napcat 侧填 ws://框架IP:端口/onebot/ws
 *       access-token: ${ONEBOT_TOKEN:}
 * </pre>
 * Napcat 侧配置 {@code ws-reverse.universal = ws://host:port/onebot/ws} 并填相同 token。
 *
 * <h3>正向 WS（框架作客户端，主动连 Napcat）</h3>
 * <pre>
 * xuanji:
 *   onebot:
 *     enabled: true
 *     forward:
 *       enabled: true
 *       url: ws://127.0.0.1:3001
 *       access-token: ${ONEBOT_TOKEN:}
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "xuanji.onebot")
public class OneBotProperties {

    /** 总开关：false 时整个 OneBot 适配器不装配 */
    private boolean enabled = false;

    /** API 调用超时（毫秒） */
    private long apiTimeoutMs = 10_000L;

    /** 是否忽略 self 发出的消息事件（post_type=message_sent） */
    private boolean ignoreSelfMessage = true;

    private Reverse reverse = new Reverse();
    private Forward forward = new Forward();

    /** 反向 WS：框架起 WebSocket 服务端，等待 OneBot 实现来连 */
    @Data
    public static class Reverse {
        private boolean enabled = true;
        /** 端点路径，Napcat 的 ws-reverse.universal 需指向此路径 */
        private String path = "/onebot/ws";
        /** 鉴权令牌，为空则不校验（仅建议本机回环使用） */
        private String accessToken = "";
    }

    /** 正向 WS：框架作客户端主动连接 OneBot 实现暴露的 WS 端口 */
    @Data
    public static class Forward {
        private boolean enabled = false;
        /** OneBot 实现的 WS 地址，如 ws://127.0.0.1:3001 */
        private String url = "";
        /** 鉴权令牌，随 Authorization: Bearer 头发送 */
        private String accessToken = "";
        /** 断线重连间隔（毫秒），0 表示不重连 */
        private long reconnectIntervalMs = 5_000L;
    }
}
