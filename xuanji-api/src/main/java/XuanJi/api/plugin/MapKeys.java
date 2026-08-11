package XuanJi.api.plugin;

import java.util.List;
import java.util.Map;

/**
 * 类型化记录共用的 Map 取值工具（包内使用）。
 *
 * <p>统一兼容三种 key 风格：平台远程 JSON（小写 snake_case）、本地库行（H2 大写）、
 * 框架注入对象（camelCase）。
 */
final class MapKeys {

    private MapKeys() { }

    /** 取字符串；按候选 key 依次查找，首个非空命中返回。 */
    static String str(Map<?, ?> m, String... keys) {
        if (m == null) return "";
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    /** 取 int；兼容 Number 与数字字符串，取不到返回 null。 */
    static Integer intVal(Map<?, ?> m, String... keys) {
        Long v = longVal(m, keys);
        return v == null ? null : v.intValue();
    }

    /** 取 long；兼容 Number 与数字字符串，取不到返回 null。 */
    static Long longVal(Map<?, ?> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null) return null;
            if (v instanceof Number n) return n.longValue();
            try {
                String s = String.valueOf(v).trim();
                if (s.isEmpty()) return null;
                return Long.parseLong(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** 取 boolean；兼容 Boolean 与 "true"/"1" 字符串。 */
    static boolean bool(Map<?, ?> m, String... keys) {
        if (m == null) return false;
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null) continue;
            if (v instanceof Boolean b) return b;
            String s = String.valueOf(v).trim();
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        }
        return false;
    }

    /** 取嵌套 Map；按候选 key 依次查找。 */
    static Map<String, Object> map(Map<?, ?> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            if (m.get(k) instanceof Map<?, ?> mm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> out = (Map<String, Object>) mm;
                return out;
            }
        }
        return null;
    }

    /** 取 List&lt;Map&gt;；按候选 key 依次查找。 */
    static List<Map<String, Object>> list(Map<?, ?> m, String... keys) {
        if (m == null) return List.of();
        for (String k : keys) {
            if (m.get(k) instanceof List<?> l) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> out = (List<Map<String, Object>>) l;
                return out;
            }
        }
        return List.of();
    }

    /** 原始 Map（保留完整平台字段，字段扩展时插件仍可访问）。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> raw(Map<?, ?> m) {
        return m == null ? null : (Map<String, Object>) m;
    }
}
