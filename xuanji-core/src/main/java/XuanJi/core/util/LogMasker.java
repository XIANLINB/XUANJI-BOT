package XuanJi.core.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具：只打码「敏感 key」的值，不动其它内容（保证日志可读性）。
 *
 * <p>用于把原始报文 / 用户标识等送入日志前脱敏，避免 openid、api_key、token 等明文落盘。
 * 与 {@code TraceContext} 配合：调试时若需看原始报文，开 DEBUG 级别，且此处已脱敏敏感字段。
 */
public final class LogMasker {

    /** 需要脱敏的敏感字段名（小写匹配）。仅这些 key 的值会被替换为 {@code ***}。 */
    private static final Set<String> SENSITIVE = Set.of(
            "openid", "member_openid", "user_id", "userid",
            "api_key", "apikey", "token", "password", "passwd",
            "secret", "session", "authorization", "credential",
            "phone", "email", "cookie");

    /** JSON 字符串中敏感 key 的字符串值脱敏：保留引号，值替换为 ***。 */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\"(?:openid|member_openid|user_id|userid|api_key|apikey|token|password|passwd|secret|session|authorization|credential|phone|email|cookie)\"\\s*:\\s*\")([^\"]*)(\")");

    private LogMasker() {}

    /** 单个值脱敏：key 命中敏感集合则返回 "***"，否则原样返回。 */
    public static String maskValue(String key, String value) {
        if (key == null || value == null) return value;
        return SENSITIVE.contains(key.toLowerCase()) ? "***" : value;
    }

    /** Map 脱敏：返回新 Map，敏感 key 的值替换为 "***"，其余原样保留。 */
    public static Map<String, Object> mask(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (m == null) return out;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            if (SENSITIVE.contains(k.toLowerCase())) {
                out.put(k, "***");
            } else {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    /** JSON 字符串脱敏：敏感 key 的字符串值替换为 ***。 */
    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) return json;
        return SENSITIVE_JSON.matcher(json).replaceAll("$1***$3");
    }
}
