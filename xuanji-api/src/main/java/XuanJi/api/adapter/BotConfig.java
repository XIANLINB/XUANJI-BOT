package XuanJi.api.adapter;

import java.util.Map;

/**
 * XuanJiBot 实例配置 — 一条 {@code xuanji.bots[]} 配置项的编程模型。
 *
 * <p>由框架从 application.yml 解析后传给适配器。
 */
public record BotConfig(
        /** YAML 中的配置别名（即 botKey，如表名为 xuanji_qqbot_a_group 中的 a） */
        String key,

        /** 平台标识 */
        String adapter,

        /** 平台 AppID */
        String appId,

        /** 平台凭证（Secret / Token 等） */
        String secret,

        /** 连接方式：websocket / webhook / polling */
        String connectionMethod,

        /** 是否沙箱模式 */
        boolean sandbox,

        /** 平台特定扩展配置（适配器自定义键值） */
        Map<String, String> extras
) {}
