package dev.xuanji.adapter.qqbot.storage;

import dev.xuanji.core.storage.BotSchemaProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 平台 schema 提供方（v3.2 两级库结构）。
 *
 * <h3>平台级共享库 {@code data/qqbot/qqbot.mv.db}（{@link #initPlatformSchema}）</h3>
 * <ul>
 *   <li>{@code qqbot_bot} — 全部 QQ 机器人档案，一 bot 一行（appid / secret / 连接模式 / 沙箱 / 状态），
 *       主键 id 全局唯一，是各 per-bot 业务表 bot_id 的关联目标</li>
 *   <li>{@code qqbot_botinfo} — Bot 基础信息（来自 /users/@me 同步），botid 外键关联 qqbot_bot（同库物理 FK）</li>
 * </ul>
 *
 * <h3>per-bot 实例库 {@code data/qqbot/{appid}/data/{appid}.mv.db}（{@link #initSchema}）</h3>
 * <ul>
 *   <li>{@code qqbot_group} / {@code qqbot_group_member} — 群与群成员档案</li>
 *   <li>{@code qqbot_user} — C2C 好友 / 单聊用户档案</li>
 *   <li>{@code qqbot_message} — 群/C2C 消息流水</li>
 *   <li>{@code qqbot_event} — 系统事件流水（全部 QQ 事件类型，见 {@code QqEventType}）</li>
 * </ul>
 *
 * <p><b>注意</b>：实例库 5 张表的 {@code bot_id} 是「逻辑外键」，指向平台共享库 qqbot_bot.id；
 * 跨 H2 库文件无法建立物理外键约束，故实例库表不声明 FOREIGN KEY。
 */
@Component
@ConditionalOnQqbotEnabled
public class QqBotSchemaProvider implements BotSchemaProvider {

    @Override
    public String platform() {
        return "qqbot";
    }

    /** 平台级共享库：机器人档案表（全部 bot 合并成一张，id 全局唯一）。 */
    @Override
    public void initPlatformSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_bot (
                id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_appid       VARCHAR(64)  NOT NULL,
                bot_clientSecret VARCHAR(512),
                conn_mode       VARCHAR(16),
                is_sandbox      TINYINT      DEFAULT 0,
                status          VARCHAR(16)  DEFAULT 'OFFLINE',
                created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                is_deleted      TINYINT      DEFAULT 0,
                webhook_url     VARCHAR(255),
                UNIQUE (bot_appid)
            )
        """);
        // 兼容已存在的旧表：补加 webhook_url 列（H2 支持 IF NOT EXISTS）
        jdbc.execute("ALTER TABLE qqbot_bot ADD COLUMN IF NOT EXISTS webhook_url VARCHAR(255)");

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_botinfo (
                id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                botid        BIGINT,
                bot_id       VARCHAR(64),
                name         VARCHAR(128),
                avatar       VARCHAR(512),
                is_bot       BOOLEAN,
                union_openid VARCHAR(128),
                share_url    VARCHAR(512),
                welcome_msg  VARCHAR(1024),
                FOREIGN KEY (botid) REFERENCES qqbot_bot (id)
            )
        """);
    }

    /** per-bot 实例库：群/成员/用户/消息/事件 5 张业务表（bot_id 逻辑关联平台库 qqbot_bot.id）。 */
    @Override
    public void initSchema(JdbcTemplate jdbc) {
        // 群档案：一 bot 一群一行；group_name/owner_id/member_count/join_time 由事件或 API 逐步补全（可空）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_group (
                id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id       BIGINT       NOT NULL,
                group_id     VARCHAR(64)  NOT NULL,
                group_name   VARCHAR(255),
                owner_id     VARCHAR(64),
                member_count INT,
                join_time    BIGINT,
                status       VARCHAR(32)  DEFAULT 'active',
                is_deleted   TINYINT      DEFAULT 0,
                UNIQUE (bot_id, group_id)
            )
        """);

        // 群成员：一 bot 一群一成员一行；role=owner/admin/member
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_group_member (
                id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id     BIGINT       NOT NULL,
                group_id   VARCHAR(64)  NOT NULL,
                member_id  VARCHAR(64)  NOT NULL,
                role       VARCHAR(16),
                nickname   VARCHAR(128),
                join_time  BIGINT,
                is_deleted TINYINT      DEFAULT 0,
                UNIQUE (bot_id, group_id, member_id)
            )
        """);

        // C2C 好友 / 单聊用户：一 bot 一用户一行
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_user (
                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id           BIGINT       NOT NULL,
                platform_user_id VARCHAR(64)  NOT NULL,
                nickname         VARCHAR(128),
                remark           VARCHAR(255),
                union_openid     VARCHAR(64),
                join_time        BIGINT,
                is_deleted       TINYINT      DEFAULT 0,
                UNIQUE (bot_id, platform_user_id)
            )
        """);

        // 消息流水：群/C2C 合并，chat_type 区分；user_id 在群=成员 member_openid，C2C=用户 user_openid
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_message (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id      BIGINT       NOT NULL,
                chat_type   VARCHAR(16)  NOT NULL,
                group_id    VARCHAR(64),
                user_id     VARCHAR(64)  NOT NULL,
                direction   VARCHAR(8)    NOT NULL,
                msg_type    VARCHAR(32),
                content     CLOB,
                msg_id      VARCHAR(255),
                msg_seq     VARCHAR(64),
                event_id    VARCHAR(255),
                raw_json    CLOB,
                create_time BIGINT       NOT NULL
            )
        """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_message_bg ON qqbot_message (bot_id, group_id, create_time)
        """);
        // 聊天窗 c2c 查询按 (bot_id, user_id) 定位、消息监控按 (bot_id, chat_type) 过滤，补索引避免全表扫
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_message_bu ON qqbot_message (bot_id, user_id, create_time)
        """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_message_bt ON qqbot_message (bot_id, chat_type, create_time)
        """);

        // 系统事件流水：全部 QQ 事件类型（见 QqEventType 枚举），user_id 为关联 member_openid/user_openid
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_event (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id      BIGINT       NOT NULL,
                event_type  VARCHAR(64)  NOT NULL,
                group_id    VARCHAR(64),
                user_id     VARCHAR(64),
                raw_json    CLOB,
                create_time BIGINT       NOT NULL
            )
        """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_event_bt ON qqbot_event (bot_id, event_type, create_time)
        """);
    }
}
