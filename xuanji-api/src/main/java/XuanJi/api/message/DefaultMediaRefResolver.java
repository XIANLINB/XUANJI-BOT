package XuanJi.api.message;

import XuanJi.api.annotation.MediaType;

/**
 * 默认媒体引用解析器 — 纯函数形态识别，无平台副作用。
 *
 * <p>识别顺序：http(s):// → URL；data: → DATA_URI；base64:// → BASE64；
 * file:// 或本地路径 → FILE_PATH；裸 base64 长串 → BASE64；
 * 裸域名（含 . 与 /，如 QQ 临时链接）→ URL；其余 → PLATFORM_ID。
 *
 * <p>平台专属 resolver 未注册时由 {@link MediaRefResolverHolder} 兜底使用。
 */
public class DefaultMediaRefResolver implements MediaRefResolver {

    @Override
    public XuanJiMediaRef resolve(String rawRef, MediaType type) {
        String s = rawRef == null ? "" : rawRef.trim();
        XuanJiMediaForm form = detect(s);
        return XuanJiMediaRef.of(form, s, type);
    }

    static XuanJiMediaForm detect(String s) {
        if (s.isEmpty()) {
            return XuanJiMediaForm.PLATFORM_ID;
        }
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return XuanJiMediaForm.URL;
        }
        if (s.startsWith("data:")) {
            return XuanJiMediaForm.DATA_URI;
        }
        if (s.startsWith("base64://")) {
            return XuanJiMediaForm.BASE64;
        }
        if (s.startsWith("file://")) {
            return XuanJiMediaForm.FILE_PATH;
        }
        // 本地绝对路径：/ 开头或 X:\ 盘符
        if (s.startsWith("/") || (s.length() > 2 && s.charAt(1) == ':' && Character.isLetter(s.charAt(0)))) {
            return XuanJiMediaForm.FILE_PATH;
        }
        // 裸 base64：长串且全为 base64 字符集
        if (s.length() >= 20 && isBase64Chars(s)) {
            return XuanJiMediaForm.BASE64;
        }
        // 裸域名（含 . 与 /，QQ 临时链接 gchat.qpic.cn/... 形态）：当作 URL
        if (s.contains(".") && s.contains("/")) {
            return XuanJiMediaForm.URL;
        }
        // 其余：平台文件标识（QQ file_info / OneBot file_id 哈希等）
        return XuanJiMediaForm.PLATFORM_ID;
    }

    private static boolean isBase64Chars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!ok) return false;
        }
        return true;
    }
}
