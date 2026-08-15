package XuanJi.llm.provider;

import XuanJi.api.llm.LlmCapability;
import XuanJi.api.llm.LlmCredentials;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商 / 模型管理 —— 多供应商（DeepSeek / 智谱 / 小米 / Fish…）+ 多模型能力管理。
 *
 * <p>供应商表存凭据（baseUrl/apiKey），模型表存模型名 + 能力位（CHAT/IMAGE_UNDERSTAND/IMAGE_GEN/TTS 等，见 LlmCapability 枚举）。
 * AI 设置页按能力选择「供应商 + 模型」，运行时各能力服务从本服务解析凭据与模型名。
 *
 * <p>支持从 OpenAI 兼容供应商自动拉取模型列表（GET {baseUrl}/models）。
 */
@Slf4j
@Service
public class ProviderService {

    private final JdbcTemplate jdbc;
    private final LlmConfigStore configStore;

    public ProviderService(JdbcTemplate jdbc, LlmConfigStore configStore) {
        this.jdbc = jdbc;
        this.configStore = configStore;
    }

    // ════════════ 供应商 ════════════

    /** 供应商列表（含其下模型数）。 */
    public List<Map<String, Object>> listProviders() {
        return jdbc.query("""
            SELECT p.id, p.name, p.provider_type, p.base_url, p.api_key, p.status,
                   (SELECT COUNT(*) FROM xuanji_llm_model m WHERE m.provider_id = p.id) AS model_count
            FROM xuanji_llm_provider p ORDER BY p.id
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("providerType", rs.getString("provider_type"));
                m.put("baseUrl", LlmCredentialCipher.decrypt(rs.getString("base_url")));
                m.put("apiKey", maskKey(LlmCredentialCipher.decrypt(rs.getString("api_key"))));
                m.put("status", rs.getInt("status"));
                m.put("modelCount", rs.getInt("model_count"));
                return m;
            });
    }

    public Map<String, Object> getProvider(long id) {
        return jdbc.query("""
            SELECT id, name, provider_type, base_url, api_key, status FROM xuanji_llm_provider WHERE id = ?
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("providerType", rs.getString("provider_type"));
                m.put("baseUrl", LlmCredentialCipher.decrypt(rs.getString("base_url")));
                m.put("apiKey", LlmCredentialCipher.decrypt(rs.getString("api_key")));
                m.put("status", rs.getInt("status"));
                return m;
            }, id).stream().findFirst().orElse(null);
    }

    public long saveProvider(Long id, String name, String providerType, String baseUrl, String apiKey, Integer status) {
        int st = status == null ? 1 : status;
        // api_key 始终加密；base_url 是否加密受配置开关 encryptBaseUrl 控制（默认不加密，其为公开端点）
        boolean encBase = configStore.get().isEncryptBaseUrl();
        String encBaseUrl = encBase ? LlmCredentialCipher.encrypt(baseUrl) : baseUrl;
        if (id != null && id > 0) {
            if (apiKey == null || apiKey.isBlank()) {
                // 编辑留空 = 保留原 key（仅更新 name/type/baseUrl/status）
                jdbc.update("""
                    UPDATE xuanji_llm_provider SET name=?, provider_type=?, base_url=?, status=? WHERE id=?
                    """, name, providerType, encBaseUrl, st, id);
            } else {
                String encApiKey = LlmCredentialCipher.encrypt(apiKey);
                jdbc.update("""
                    UPDATE xuanji_llm_provider SET name=?, provider_type=?, base_url=?, api_key=?, status=? WHERE id=?
                    """, name, providerType, encBaseUrl, encApiKey, st, id);
            }
            log.info("[PROVIDER] 供应商已更新: id={}, name={}", id, name);
            return id;
        }
        String encApiKey = LlmCredentialCipher.encrypt(apiKey == null ? "" : apiKey);
        jdbc.update("""
            INSERT INTO xuanji_llm_provider (name, provider_type, base_url, api_key, status)
            VALUES (?, ?, ?, ?, ?)
            """, name, providerType, encBaseUrl, encApiKey, st);
        Long newId = jdbc.queryForObject("SELECT MAX(id) FROM xuanji_llm_provider", Long.class);
        log.info("[PROVIDER] 供应商已保存: id={}, name={}, type={}", newId, name, providerType);
        return newId == null ? 0 : newId;
    }

    public void deleteProvider(long id) {
        jdbc.update("DELETE FROM xuanji_llm_api_key WHERE provider_id = ?", id);
        jdbc.update("DELETE FROM xuanji_llm_model WHERE provider_id = ?", id);
        jdbc.update("DELETE FROM xuanji_llm_provider WHERE id = ?", id);
        log.info("[PROVIDER] 供应商已删除: id={}", id);
    }

    /** 凭据解析：按供应商 id 返回 baseUrl + 第一个可用 key；供应商不存在返回 null。 */
    public LlmCredentials credentials(long providerId) {
        Map<String, Object> p = getProvider(providerId);
        if (p == null) {
            return null;
        }
        String key = firstEnabledKey(providerId);
        if (key == null) {
            key = String.valueOf(p.get("apiKey"));
        }
        return new LlmCredentials(String.valueOf(p.get("baseUrl")), key);
    }

    /** 该供应商所有启用的 API Key（多 key 轮询容灾用）。返回已解密的明文 key。 */
    public List<String> enabledKeys(long providerId) {
        return jdbc.query("""
            SELECT api_key FROM xuanji_llm_api_key
            WHERE provider_id = ? AND enabled = 1 ORDER BY id
            """, (rs, i) -> LlmCredentialCipher.decrypt(rs.getString("api_key")), providerId);
    }

    private String firstEnabledKey(long providerId) {
        List<String> keys = enabledKeys(providerId);
        return keys.isEmpty() ? null : keys.get(0);
    }

    // ════════════ API Key 管理 ════════════

    public List<Map<String, Object>> listKeys(long providerId) {
        return jdbc.query("""
            SELECT id, provider_id, api_key, remark, enabled FROM xuanji_llm_api_key
            WHERE provider_id = ? ORDER BY id
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("providerId", rs.getLong("provider_id"));
                m.put("apiKey", maskKey(LlmCredentialCipher.decrypt(rs.getString("api_key"))));
                m.put("remark", rs.getString("remark"));
                m.put("enabled", rs.getInt("enabled"));
                return m;
            }, providerId);
    }

    public long saveKey(long providerId, String apiKey, String remark) {
        jdbc.update("""
            INSERT INTO xuanji_llm_api_key (provider_id, api_key, remark, enabled) VALUES (?, ?, ?, 1)
            """, providerId, LlmCredentialCipher.encrypt(apiKey), remark);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM xuanji_llm_api_key", Long.class);
        log.info("[PROVIDER] 供应商 Key 已添加: provider={}, remark={}", providerId, remark);
        return id == null ? 0 : id;
    }

    public void deleteKey(long id) {
        jdbc.update("DELETE FROM xuanji_llm_api_key WHERE id = ?", id);
    }

    /** 启用/停用某个 API Key（停用后不再参与轮询容灾）。 */
    public void setKeyEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE xuanji_llm_api_key SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    }

    // ════════════ 模型 ════════════

    public List<Map<String, Object>> listModels(long providerId) {
        return jdbc.query("""
            SELECT id, provider_id, model_name, capabilities, enabled FROM xuanji_llm_model
            WHERE provider_id = ? ORDER BY id
            """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("providerId", rs.getLong("provider_id"));
                m.put("modelName", rs.getString("model_name"));
                m.put("capabilities", rs.getString("capabilities"));
                m.put("enabled", rs.getInt("enabled"));
                return m;
            }, providerId);
    }

    public long saveModel(long providerId, String modelName, String capabilities) {
        jdbc.update("""
            INSERT INTO xuanji_llm_model (provider_id, model_name, capabilities, enabled)
            VALUES (?, ?, ?, 1)
            """, providerId, modelName, capabilities);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM xuanji_llm_model", Long.class);
        log.info("[PROVIDER] 模型已添加: provider={}, model={}, caps={}", providerId, modelName, capabilities);
        return id == null ? 0 : id;
    }

    public void deleteModel(long id) {
        jdbc.update("DELETE FROM xuanji_llm_model WHERE id = ?", id);
    }

    /** 更新模型（名称 + 能力位）。 */
    public void updateModel(long id, String modelName, String capabilities) {
        jdbc.update("UPDATE xuanji_llm_model SET model_name = ?, capabilities = ? WHERE id = ?",
                modelName, capabilities, id);
    }

    /** 测试供应商连接（GET {baseUrl}/models，复用拉取逻辑的凭据解析）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> testProvider(long providerId) {
        Object r = fetchModels(providerId);
        if (r instanceof Map<?, ?> m && m.containsKey("models")) {
            return Map.of("ok", true, "models", ((java.util.List<?>) m.get("models")).size());
        }
        return Map.of("ok", false, "error", String.valueOf(r));
    }

    // ════════════ 能力绑定解析 ════════════

    /** 按能力绑定（providerId + 期望能力）查模型名；未绑定/无该能力模型返回 null。
     *  以 {@link LlmCapability} 枚举为唯一匹配基准（替代原先的字符串子串 contains，
     *  旧实现会把 "STT" 误命中 "TTS" 等）。 */
    public String resolveModel(long providerId, LlmCapability capability) {
        List<Map<String, Object>> models = listModels(providerId);
        for (Map<String, Object> m : models) {
            Set<LlmCapability> caps = LlmCapability.parse(String.valueOf(m.get("capabilities")));
            if (caps.contains(capability)) {
                return String.valueOf(m.get("modelName"));
            }
        }
        return null;
    }

    // ════════════ 拉取模型列表（OpenAI 兼容 GET {baseUrl}/models） ════════════

    /** 从 OpenAI 兼容供应商自动拉取模型 ID 列表；失败返回错误说明。 */
    @SuppressWarnings("unchecked")
    public Object fetchModels(long providerId) {
        Map<String, Object> p = getProvider(providerId);
        if (p == null) {
            return "供应商不存在";
        }
        String baseUrl = String.valueOf(p.get("baseUrl"));
        String apiKey = String.valueOf(p.get("apiKey"));
        if (baseUrl.isBlank() || "null".equals(baseUrl) || apiKey.isBlank() || "null".equals(apiKey)) {
            return "请先填写供应商的 baseUrl 和 apiKey";
        }
        String modelsUrl = baseUrl.replaceAll("/+$", "") + "/models";
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(modelsUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .requestFactory(jdkFactory())
                    .build();
            Map<String, Object> resp = client.get().retrieve().body(Map.class);
            List<String> ids = new ArrayList<>();
            Object dataObj = resp != null ? resp.get("data") : null;
            if (dataObj instanceof List<?> data) {
                for (Object o : data) {
                    if (o instanceof Map<?, ?> mm) {
                        Object id = mm.get("id");
                        if (id != null) {
                            ids.add(String.valueOf(id));
                        }
                    }
                }
            }
            if (ids.isEmpty()) {
                return "拉取成功但未发现模型（该供应商可能不开放模型列表接口）";
            }
            log.info("[PROVIDER] 拉取模型列表: provider={}, {} 个", providerId, ids.size());
            return Map.of("models", ids);
        } catch (Exception e) {
            log.warn("[PROVIDER] 拉取模型失败: {}", e.getMessage());
            return "拉取失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 6) return "****";
        return key.substring(0, 3) + "****" + key.substring(key.length() - 3);
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        HttpClient http = LlmHttpClient.shared();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(15));
        return f;
    }
}