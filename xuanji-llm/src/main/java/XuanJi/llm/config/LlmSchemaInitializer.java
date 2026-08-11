package XuanJi.llm.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * LLM 独立表初始化 —— 在 llm 模块内自建表，不侵入 core 的 DatabaseInitializer。
 *
 * <p>core 不反向依赖 llm，因此 LLM 域的表由本模块自治；startup 幂等（IF NOT EXISTS），
 * 先于任何 LlmConfigStore 读写执行（同模块内 PostConstruct 顺序由注册顺序保证，
 * 表创建不依赖其它 Bean，安全）。
 */
@Slf4j
@Component
public class LlmSchemaInitializer {

    private final JdbcTemplate jdbc;

    public LlmSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_config (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                config_key  VARCHAR(128) NOT NULL,
                config_value TEXT,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_llm_config_key ON xuanji_llm_config (config_key)");

        // ════ 用量统计（P1.5 升级为多维：bot + 群/单聊用户）════
        // 旧表仅按 bot 统计，无法表达群维度 → 直接重建（用量为统计型数据，历史量极小可弃）
        // 维度用空串 '' 而非 NULL：user_id 在主键内（H2 主键列隐式 NOT NULL），群维度记 user_id=''
        jdbc.execute("DROP TABLE IF EXISTS xuanji_llm_usage");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_usage (
                stat_day          BIGINT       NOT NULL,
                bot_key           VARCHAR(128) NOT NULL,
                group_id          VARCHAR(128) DEFAULT '',
                user_id           VARCHAR(128) DEFAULT '',
                used_tokens       BIGINT       DEFAULT 0,
                prompt_tokens     BIGINT       DEFAULT 0,
                completion_tokens BIGINT       DEFAULT 0,
                PRIMARY KEY (stat_day, bot_key, group_id, user_id)
            )
        """);
        // 老库缺列（用量真实化：prompt/completion 维度）
        try { jdbc.execute("ALTER TABLE xuanji_llm_usage ADD COLUMN IF NOT EXISTS prompt_tokens BIGINT DEFAULT 0"); } catch (Exception ignored) {}
        try { jdbc.execute("ALTER TABLE xuanji_llm_usage ADD COLUMN IF NOT EXISTS completion_tokens BIGINT DEFAULT 0"); } catch (Exception ignored) {};

        // ════ 群级每日限额 ════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_group_quota (
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128) NOT NULL,
                daily_limit BIGINT       DEFAULT 0,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (bot_key, group_id)
            )
        """);

        // ════ 长期记忆（显式"记住X" + 分层摘要）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_memory (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128),
                user_id     VARCHAR(128),
                mem_key     VARCHAR(256),
                mem_value   TEXT,
                mem_type    VARCHAR(8)   DEFAULT 'DETAIL',
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expire_at   BIGINT       DEFAULT 0
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_llm_memory ON xuanji_llm_memory (bot_key, group_id, user_id)");
        // 分层摘要列（老库加列，幂等）：SUMMARY=LLM 压缩的历史摘要，注入时恒在
        jdbc.execute("ALTER TABLE xuanji_llm_memory ADD COLUMN IF NOT EXISTS mem_type VARCHAR(8) DEFAULT 'DETAIL'");
        // ── P0 自学习：记忆加权（可信度）+ 纠错闭环（negative 纠正记录）──
        // confidence: 0~1 可信度（默认 0.5 中性；用户明确纠正后存 0.9；被驳斥降权）
        // hit_count / correct_count: 被调用次数 / 被证实次数（用于后续加权统计）
        // negative: 用户纠正过的错误说法（纠正记录专用，值存正确说法）
        jdbc.execute("ALTER TABLE xuanji_llm_memory ADD COLUMN IF NOT EXISTS confidence DOUBLE DEFAULT 0.5");
        jdbc.execute("ALTER TABLE xuanji_llm_memory ADD COLUMN IF NOT EXISTS hit_count BIGINT DEFAULT 0");
        jdbc.execute("ALTER TABLE xuanji_llm_memory ADD COLUMN IF NOT EXISTS correct_count BIGINT DEFAULT 0");
        jdbc.execute("ALTER TABLE xuanji_llm_memory ADD COLUMN IF NOT EXISTS negative TEXT");

        // ════ P1 工具调用经验库（错误 → fix_hint，越用越准）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_tool_learn (
                id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                tool       VARCHAR(64)  NOT NULL,
                args       TEXT,
                error      TEXT,
                fix_hint   TEXT,
                hit_count  BIGINT       DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_llm_tool_learn ON xuanji_llm_tool_learn (tool)");

        // ════ P2 人设偏离日志（自评 fail 记录）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_persona_drift (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128),
                group_id    VARCHAR(128),
                user_id     VARCHAR(128),
                persona_scope VARCHAR(8),
                reply       TEXT,
                reason      VARCHAR(512),
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_llm_drift ON xuanji_llm_persona_drift (bot_key, created_at)");

        // ════ P2 用户反馈闭环（👍/👎 + 偏好蒸馏）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_feedback (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128),
                group_id    VARCHAR(128),
                user_id     VARCHAR(128),
                reply_hash  VARCHAR(64),
                reply_text  TEXT,
                score       INT          DEFAULT 0,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_llm_feedback ON xuanji_llm_feedback (bot_key, user_id, created_at)");

        // ════ 用户画像（全量消息认知：他是谁/语气/风格）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_user_profile (
                bot_key          VARCHAR(128) NOT NULL,
                group_id         VARCHAR(128) NOT NULL,
                user_id          VARCHAR(128) NOT NULL,
                nickname         VARCHAR(64),
                member_role      VARCHAR(16),
                profile_summary  TEXT,
                speech_style     TEXT,
                msg_count        BIGINT       DEFAULT 0,
                first_seen       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_seen        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                extract_at       BIGINT       DEFAULT 0,
                updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (bot_key, group_id, user_id)
            )
        """);
        // P2-F 偏好摘要列（用户 👍/👎 反馈蒸馏出的回复偏好；必须在 user_profile 建表后执行）
        jdbc.execute("ALTER TABLE xuanji_llm_user_profile ADD COLUMN IF NOT EXISTS preference_summary TEXT");

        // ════ 主动搭话记录（防骚扰审计）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_proactive_log (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128),
                user_id     VARCHAR(128),
                action_type VARCHAR(16),
                content     TEXT,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_llm_proactive ON xuanji_llm_proactive_log (bot_key, group_id, created_at)");

        // ════ MCP 服务注册（P3：连接外部 MCP server，工具桥接进 ToolRegistry）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_mcp (
                bot_key     VARCHAR(128) NOT NULL,
                name        VARCHAR(64)  NOT NULL,
                url         VARCHAR(512) NOT NULL,
                description VARCHAR(256),
                whitelist   TINYINT      DEFAULT 0,
                enabled     TINYINT      DEFAULT 1,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (bot_key, name)
            )
        """);

        // ════ 知识库（P4：文档分段 + 关键词检索）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_kb_doc (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key     VARCHAR(128) NOT NULL,
                name        VARCHAR(128),
                char_count  INT          DEFAULT 0,
                chunk_count INT          DEFAULT 0,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_kb_chunk (
                id      BIGINT AUTO_INCREMENT PRIMARY KEY,
                doc_id  BIGINT NOT NULL,
                seq     INT    DEFAULT 0,
                content TEXT
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_kb_chunk_doc ON xuanji_llm_kb_chunk (doc_id)");

        // ════ AI 审核记录（P4）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_audit_log (
                id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key    VARCHAR(128),
                group_id   VARCHAR(128),
                user_id    VARCHAR(128),
                text       TEXT,
                action     VARCHAR(16),
                reason     VARCHAR(256),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ════ AI 日报配置（P4）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_summary_config (
                bot_key   VARCHAR(128) NOT NULL,
                group_id  VARCHAR(128) NOT NULL,
                enabled   TINYINT      DEFAULT 0,
                run_hour  INT          DEFAULT 22,
                run_minute INT         DEFAULT 0,
                image_mode TINYINT     DEFAULT 0,
                PRIMARY KEY (bot_key, group_id)
            )
        """);
        // 旧表补列（增量升级：已有表结构无 image_mode）
        try {
            jdbc.execute("ALTER TABLE xuanji_llm_summary_config ADD COLUMN IF NOT EXISTS image_mode TINYINT DEFAULT 0");
        } catch (Exception e) {
            log.debug("[LLM] summary_config image_mode 列已存在或不可加: {}", e.getMessage());
        }
        // AI 日报历史
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_summary_log (
                id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_key    VARCHAR(128),
                group_id   VARCHAR(128),
                content    TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ════ Agent 会话持久化（P3+：跨重启保留多轮会话历史）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_agent_session (
                session_key VARCHAR(256) PRIMARY KEY,
                flow        VARCHAR(32)  NOT NULL,
                state_json  TEXT,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        // 会话清理：保留最近 7 天（防无限膨胀）
        jdbc.execute("DELETE FROM xuanji_llm_agent_session WHERE updated_at < DATEADD('DAY', -7, CURRENT_TIMESTAMP)");

        // ════ 供应商 / 模型（多供应商多模型管理）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_provider (
                id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                name          VARCHAR(64)  NOT NULL,
                provider_type VARCHAR(32)  NOT NULL DEFAULT 'openai',
                base_url      VARCHAR(512),
                api_key       VARCHAR(256),
                status        TINYINT      DEFAULT 1,
                created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_model (
                id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                provider_id  BIGINT NOT NULL,
                model_name   VARCHAR(128) NOT NULL,
                capabilities VARCHAR(256) DEFAULT '',
                enabled      TINYINT DEFAULT 1,
                created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        // 供应商多 API Key（一个供应商可配多个 key，按序轮询容灾）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_api_key (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                provider_id BIGINT NOT NULL,
                api_key     VARCHAR(256) NOT NULL,
                remark      VARCHAR(64),
                enabled     TINYINT DEFAULT 1,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ════ 人格（P1.5 升级为结构化角色卡：10 字段 + 旧文本兼容）════
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS xuanji_llm_persona (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                scope       VARCHAR(8)   NOT NULL,
                bot_key     VARCHAR(128) NOT NULL,
                group_id    VARCHAR(128),
                user_id     VARCHAR(128),
                persona     TEXT,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_llm_persona ON xuanji_llm_persona (scope, bot_key, group_id, user_id)");
        // 结构化字段（老库升级为加列，幂等）
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS name VARCHAR(64)");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS age VARCHAR(16)");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS gender VARCHAR(16)");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS personality TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS background TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS scenario TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS speech_style TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS first_mes TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS mes_example TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS system_extra TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS legacy_persona TEXT");
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS roleplay_mode TINYINT DEFAULT 0");
        // P2 人设锚点（自评用）：说话风格关键词/世界观/禁忌
        jdbc.execute("ALTER TABLE xuanji_llm_persona ADD COLUMN IF NOT EXISTS anchors TEXT");
        // 旧 persona 文本 → legacy_persona（新模型组装时兜底并入，不丢老配置）
        jdbc.execute("""
            UPDATE xuanji_llm_persona SET legacy_persona = persona
            WHERE legacy_persona IS NULL AND persona IS NOT NULL AND persona <> ''
            """);
        log.info("[LLM] LLM 表就绪: config / usage / group_quota / memory / persona / user_profile / proactive_log / mcp / kb / audit_log / summary_config / agent_session / provider / model");
    }
}
