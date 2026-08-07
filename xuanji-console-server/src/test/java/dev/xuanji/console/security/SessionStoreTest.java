package dev.xuanji.console.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionStore 内存会话生命周期")
class SessionStoreTest {

    @Test
    @DisplayName("create 后 isValid=true，destroy 后 false")
    void lifecycle() {
        SessionStore store = new SessionStore(3600);
        String token = store.create();
        assertTrue(store.isValid(token));
        store.destroy(token);
        assertFalse(store.isValid(token));
    }

    @Test
    @DisplayName("未知 token 无效；ttlSeconds 与配置一致")
    void unknownTokenAndTtl() {
        SessionStore store = new SessionStore(120);
        assertFalse(store.isValid("not-a-real-token"));
        assertEquals(120, store.ttlSeconds());
    }

    @Test
    @DisplayName("每次 create 返回不同 token")
    void uniqueTokens() {
        SessionStore store = new SessionStore(3600);
        assertNotEquals(store.create(), store.create());
    }
}
