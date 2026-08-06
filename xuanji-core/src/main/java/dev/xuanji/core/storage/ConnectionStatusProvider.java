package dev.xuanji.core.storage;

import java.util.Map;

/**
 * 平台连接状态 SPI — 控制台「运行健康」页展示各平台在线连接。
 */
public interface ConnectionStatusProvider {

    /** 平台标识：qqbot / onebot。 */
    String platform();

    /** 该平台连接状态（在线实例、WebSocket 会话数等），键值自定。 */
    Map<String, Object> connections();
}
