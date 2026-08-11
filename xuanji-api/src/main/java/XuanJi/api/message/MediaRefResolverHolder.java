package XuanJi.api.message;

import XuanJi.api.annotation.MediaType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 媒体引用解析器注册表 — 平台适配器按 platform 注册专属 {@link MediaRefResolver}。
 *
 * <p>对称复刻 {@link MessageConverterHolder} 的静态 Map 范式：
 * 适配器启动时注册，解析失败或平台未注册时兜底使用 {@link DefaultMediaRefResolver}，
 * 绝不抛异常拖垮插件。
 */
public final class MediaRefResolverHolder {

    private static final Map<String, MediaRefResolver> RESOLVERS = new ConcurrentHashMap<>();
    private static final MediaRefResolver DEFAULT = new DefaultMediaRefResolver();

    private MediaRefResolverHolder() {}

    /** 适配器启动时注册媒体解析器（key=platform，如 "qqbot" / "onebot"）。 */
    public static void register(String platform, MediaRefResolver resolver) {
        RESOLVERS.put(platform, resolver);
    }

    /** 按平台归一化；平台未注册或解析失败兜底默认实现。 */
    public static XuanJiMediaRef resolve(String platform, String rawRef, MediaType type) {
        MediaRefResolver r = platform != null ? RESOLVERS.get(platform) : null;
        if (r == null) r = DEFAULT;
        try {
            return r.resolve(rawRef, type);
        } catch (Exception e) {
            return DEFAULT.resolve(rawRef, type);
        }
    }

    /** 平台无关归一化（使用默认解析器）。 */
    public static XuanJiMediaRef resolve(String rawRef, MediaType type) {
        return resolve(null, rawRef, type);
    }

    /** 测试 / 热卸载用：移除某平台的解析器。 */
    public static void unregister(String platform) {
        RESOLVERS.remove(platform);
    }

    /**
     * 解析并<b>按需下载落盘</b>（P1-D convertToFilePath）：URL 形态且下载器已启用时，
     * 下载到本地并返回 FILE_PATH 形态的 XuanJiMediaRef；未启用 / 非 URL / 下载失败回退普通解析。
     * 开关判定：bot 级 &gt; 全局。
     */
    public static XuanJiMediaRef resolveFile(String platform, String rawRef, MediaType type, String botKey) {
        XuanJiMediaRef ref = resolve(platform, rawRef, type);
        if (ref.form() != XuanJiMediaForm.URL) return ref; // 非 URL 无需下载
        var path = XuanJi.api.media.MediaFileDownloader.download(ref.raw(), type, botKey);
        if (path == null) return ref; // 未启用 / 超限 / 失败 → 保持 URL 形态
        return XuanJiMediaRef.of(XuanJiMediaForm.FILE_PATH, path.toString(), type);
    }

    /** 无 bot 上下文时按全局开关下载。 */
    public static XuanJiMediaRef resolveFile(String platform, String rawRef, MediaType type) {
        return resolveFile(platform, rawRef, type, null);
    }
}
