package dev.xuanji.api.message;

import dev.xuanji.api.annotation.MediaType;

/**
 * 媒体引用解析器 — 把平台专属的媒体引用字符串归一化为 {@link MediaRef}。
 *
 * <p>SPI 形态：各平台适配器在启动时向 {@link MediaRefResolverHolder} 注册自己的实现，
 * 覆盖默认的形态识别逻辑（如 QQ 临时链接裸域名补 scheme、OneBot file_id 识别）。
 */
public interface MediaRefResolver {
    MediaRef resolve(String rawRef, MediaType type);
}
