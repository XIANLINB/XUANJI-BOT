package XuanJi.adapter.qqbot.util;

import XuanJi.api.annotation.MediaType;
import XuanJi.api.message.DefaultMediaRefResolver;
import XuanJi.api.message.XuanJiMediaForm;
import XuanJi.api.message.XuanJiMediaRef;
import XuanJi.api.message.MediaRefResolver;

/**
 * QQ 媒体引用解析器 — 处理 QQ 临时链接裸域名（如 gchat.qpic.cn/... 无 scheme），
 * 补 https:// 后归一化；其余委托默认解析器。
 */
public class QqMediaRefResolver implements MediaRefResolver {
    private final DefaultMediaRefResolver delegate = new DefaultMediaRefResolver();

    @Override
    public XuanJiMediaRef resolve(String rawRef, MediaType type) {
        if (rawRef == null) {
            return new XuanJiMediaRef(XuanJiMediaForm.PLATFORM_ID, "", type);
        }
        String s = rawRef.trim();
        // QQ 群/C2C 富媒体入站常给裸域名无 scheme 的临时链接，补 scheme 后归为 URL
        if (!s.startsWith("http://") && !s.startsWith("https://")
                && !s.startsWith("file://") && !s.startsWith("base64://")
                && s.contains(".") && s.contains("/")) {
            s = "https://" + s;
        }
        return delegate.resolve(s, type);
    }
}
