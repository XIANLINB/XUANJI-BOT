package XuanJi.console.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 控制台登录会话存储 —— 纯内存实现（无持久化、重启即失效）。
 *
 * <p>每次成功登录生成一个随机 token（32 字节 Base64url），与过期时间戳一起存入内存 Map；
 * 校验时比对 token 是否存在且未过期。登出显式销毁。适合单机部署；如需多实例共享应改为 Redis。
 */
@Component
public class SessionStore {

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final SecureRandom random = new SecureRandom();

    public SessionStore(@Value("${xuanji.console.session-ttl-seconds:28800}") long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /** 创建会话，返回 token。 */
    public String create() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, System.currentTimeMillis() + ttlMillis);
        return token;
    }

    /** 校验会话是否有效（非 null、存在且未过期）。 */
    public boolean isValid(String token) {
        if (token == null) return false;
        Long exp = sessions.get(token);        if (exp == null) return false;
        if (exp <= System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /** 销毁会话（登出）。 */
    public void destroy(String token) {
        if (token != null) sessions.remove(token);
    }

    /** 会话有效期（秒），供登录响应与 cookie Max-Age 对齐。 */
    public long ttlSeconds() {
        return ttlMillis / 1000L;
    }

    /** 当前有效会话数（数据中心·缓存清理页展示）。 */
    public int count() {
        return sessions.size();
    }

    /** 清空所有会话（强制所有用户重新登录）。返回被清空的会话数。 */
    public int clearAll() {
        int n = sessions.size();
        sessions.clear();
        return n;
    }
}
