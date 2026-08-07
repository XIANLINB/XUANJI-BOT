package dev.xuanji.adapter.onebot.storage;

import dev.xuanji.core.storage.BotSchemaProvider;
import org.springframework.jdbc.core.JdbcTemplate;

public class OneBotSchemaProvider
implements BotSchemaProvider {
    public String platform() {
        return "onebot";
    }

    public void initPlatformSchema(JdbcTemplate jdbc) {
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_bot (\n        id          BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_appid   VARCHAR(64)  NOT NULL,\n        bot_clientSecret VARCHAR(512),\n        conn_mode   VARCHAR(16),\n        is_sandbox  TINYINT      DEFAULT 0,\n        status      VARCHAR(16)  DEFAULT 'OFFLINE',\n        created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,\n        is_deleted  TINYINT      DEFAULT 0,\n        webhook_url VARCHAR(255),\n        UNIQUE (bot_appid)\n    )\n");
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_botinfo (\n        id           BIGINT AUTO_INCREMENT PRIMARY KEY,\n        botid        BIGINT,\n        bot_id       VARCHAR(64),\n        name         VARCHAR(128),\n        avatar       VARCHAR(512),\n        is_bot       BOOLEAN,\n        union_openid VARCHAR(128),\n        share_url    VARCHAR(512),\n        welcome_msg  VARCHAR(1024),\n        FOREIGN KEY (botid) REFERENCES onebot_bot (id)\n    )\n");
    }

    public void initSchema(JdbcTemplate jdbc) {
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_group (\n        id           BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_id       BIGINT       NOT NULL,\n        group_id     VARCHAR(64)  NOT NULL,\n        group_name   VARCHAR(255),\n        owner_id     VARCHAR(64),\n        member_count INT,\n        join_time    BIGINT,\n        status       VARCHAR(32)  DEFAULT 'active',\n        is_deleted   TINYINT      DEFAULT 0,\n        UNIQUE (bot_id, group_id)\n    )\n");
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_group_member (\n        id         BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_id     BIGINT       NOT NULL,\n        group_id   VARCHAR(64)  NOT NULL,\n        member_id  VARCHAR(64)  NOT NULL,\n        role       VARCHAR(16),\n        nickname   VARCHAR(128),\n        join_time  BIGINT,\n        is_deleted TINYINT      DEFAULT 0,\n        UNIQUE (bot_id, group_id, member_id)\n    )\n");
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_user (\n        id               BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_id           BIGINT       NOT NULL,\n        platform_user_id VARCHAR(64)  NOT NULL,\n        nickname         VARCHAR(128),\n        remark           VARCHAR(255),\n        union_openid     VARCHAR(64),\n        join_time        BIGINT,\n        is_deleted       TINYINT      DEFAULT 0,\n        UNIQUE (bot_id, platform_user_id)\n    )\n");
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_message (\n        id          BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_id      BIGINT       NOT NULL,\n        chat_type   VARCHAR(16)  NOT NULL,\n        group_id    VARCHAR(64),\n        user_id     VARCHAR(64)  NOT NULL,\n        direction   VARCHAR(8)    NOT NULL,\n        msg_type    VARCHAR(32),\n        content     CLOB,\n        msg_id      VARCHAR(255),\n        msg_seq     VARCHAR(64),\n        event_id    VARCHAR(255),\n        raw_json    CLOB,\n        create_time BIGINT       NOT NULL\n    )\n");
        jdbc.execute("    CREATE INDEX IF NOT EXISTS idx_onebot_message_bg ON onebot_message (bot_id, group_id, create_time)\n");
        jdbc.execute("    CREATE INDEX IF NOT EXISTS idx_onebot_message_bu ON onebot_message (bot_id, user_id, create_time)\n");
        jdbc.execute("    CREATE INDEX IF NOT EXISTS idx_onebot_message_bt ON onebot_message (bot_id, chat_type, create_time)\n");
        jdbc.execute("    CREATE TABLE IF NOT EXISTS onebot_event (\n        id          BIGINT AUTO_INCREMENT PRIMARY KEY,\n        bot_id      BIGINT       NOT NULL,\n        event_type  VARCHAR(64)  NOT NULL,\n        group_id    VARCHAR(64),\n        user_id     VARCHAR(64),\n        raw_json    CLOB,\n        create_time BIGINT       NOT NULL\n    )\n");
        jdbc.execute("    CREATE INDEX IF NOT EXISTS idx_onebot_event_bt ON onebot_event (bot_id, event_type, create_time)\n");
    }
}

