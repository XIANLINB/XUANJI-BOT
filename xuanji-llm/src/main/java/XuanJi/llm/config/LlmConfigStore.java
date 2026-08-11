package XuanJi.llm.config;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * LLM 配置存储 —— 独立表 {@code xuanji_llm_config} 的唯一读写入口。
 *
 * <p>采用单行 JSON（键 {@code llm.config}）存储整个 {@link LlmConfig}，
 * 与框架其它 EAV 配置表风格一致且演进友好：将来加 bot/group 级覆盖时
 * 只需新增键（如 {@code llm.config.bot.{botKey}}）。
 */
@Slf4j
@Component
public class LlmConfigStore {

    /** 配置键：全局 LLM 配置整体 JSON */
    static final String KEY = "llm.config";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** 注入容器 ObjectMapper（Jackson 3 / tools.jackson，Boot 4 自动配置）。 */
    public LlmConfigStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** 读取配置；表中无值时返回默认配置（安全优先）。 */
    public LlmConfig get() {
        try {
            String json = jdbc.query(
                    "SELECT config_value FROM xuanji_llm_config WHERE config_key = ?",
                    rs -> rs.next() ? rs.getString(1) : null, KEY);
            if (json == null || json.isBlank()) {
                return new LlmConfig();
            }
            return mapper.readValue(json, LlmConfig.class);
        } catch (Exception e) {
            log.warn("[LLM] 读取配置失败，回退默认配置: {}", e.getMessage());
            return new LlmConfig();
        }
    }

    /** 保存配置（UPSERT）。 */
    public void save(LlmConfig config) {
        try {
            String json = mapper.writeValueAsString(config);
            jdbc.update("""
                MERGE INTO xuanji_llm_config (config_key, config_value, updated_at)
                KEY (config_key) VALUES (?, ?, CURRENT_TIMESTAMP)
                """, KEY, json);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 配置保存失败: " + e.getMessage(), e);
        }
    }
}
