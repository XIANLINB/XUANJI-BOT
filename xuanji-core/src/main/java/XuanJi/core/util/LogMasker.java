package XuanJi.core.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具：只打码「真正的秘密凭据」的值，其余内容（群号、用户标识、手机号、邮箱等）一律原样，
 * 保证日志可读性。
 *
 * <p>用于把原始报文送入日志前脱敏。脱敏范围刻意保持最小：仅供应商 api_key、PIN 及等价的凭据类字段
 * （token / password / secret / authorization / credential）。群号、openid、user_id、手机号、邮箱等
 * 属于业务标识/联系方式，不属于需保护的秘密，不做打码，以免日志不可读。
 */
public final class LogMasker {

    /** 需要脱敏的敏感字段名（小写匹配）。仅这些 key 的值会被替换为 {@code ***}。 */
    private static final Set<String> SENSITIVE = Set.of(
            "api_key", "apikey", "token", "password", "passwd",
            "secret", "authorization", "credential", "pin");

    /** JSON 字符串中敏感 key 的字符串值脱敏：保留引号，值替换为 ***。 */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\"(?:api_key|apikey|token|password|passwd|secret|authorization|credential|pin)\"\\s*:\\s*\")([^\"]*)(\")");

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
