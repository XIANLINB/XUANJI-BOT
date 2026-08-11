package XuanJi.adapter.qqbot.storage;

import XuanJi.core.storage.BotSchemaProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import XuanJi.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 平台 schema 提供方（v3.2 两级库结构）。
 *
 * <h3>平台级共享库 {@code data/qqbot/qqbot.mv.db}（{@link #initPlatformSchema}）</h3>
 * <ul>
 *   <li>{@code qqbot_bot} — 全部 QQ 机器人档案，一 bot 一行（appid / secret / 连接模式 / 沙箱 / 状态），
 *       主键 id 全局唯一，是各 per-bot 业务表 bot_id 的关联目标</li>
 *   <li>{@code qqbot_botinfo} — XuanJiBot 基础信息（来自 /users/@me 同步），botid 外键关联 qqbot_bot（同库物理 FK）</li>
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
        // 群档案：一 bot 一群一行；群名/成员数/群备注/分类/标签 由入群事件或 GET /v2/groups/{id}/info 同步
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_group (
                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id           BIGINT       NOT NULL,
                group_id         VARCHAR(64)  NOT NULL,
                group_name       VARCHAR(255),
                owner_id         VARCHAR(64),
                member_count     INT,
                group_finger_memo VARCHAR(255),
                group_class_text VARCHAR(64),
                group_tags       VARCHAR(512),
                member_max       INT,
                join_time        BIGINT,
                status           VARCHAR(32)  DEFAULT 'active',
                is_deleted       TINYINT      DEFAULT 0,
                UNIQUE (bot_id, group_id)
            )
        """);
        // 存量库兼容：老表缺新列时补列（H2 2.x 支持 ADD COLUMN IF NOT EXISTS）
        for (String col : new String[]{"group_finger_memo VARCHAR(255)", "group_class_text VARCHAR(64)", "group_tags VARCHAR(512)", "member_max INT"}) {
            try {
                jdbc.execute("ALTER TABLE qqbot_group ADD COLUMN IF NOT EXISTS " + col);
            } catch (Exception ignored) { /* 新库已含列时忽略 */ }
        };

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

        // 机器人在群内的状态：一 bot 一群一行（多机器人同群有多行）；
        // 由 bot_state 接口定时错峰同步（30QPM 预算，见 GroupRobotStateSyncService）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_group_robot (
                id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id              BIGINT       NOT NULL,
                group_id            VARCHAR(64)  NOT NULL,
                robot_openid        VARCHAR(64),
                member_role         VARCHAR(16),
                allow_proactive_msg TINYINT,
                recv_msg_setting    VARCHAR(32),
                joined_at           BIGINT,
                updated_at          BIGINT,
                is_deleted          TINYINT      DEFAULT 0,
                UNIQUE (bot_id, group_id)
            )
        """);
        // 索引：按最后同步时间升序取"最久未同步"的待同步队列
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_qqbot_group_robot_sync ON qqbot_group_robot (updated_at)");;

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
    }

    /**
     * per-bot 日志库：消息 / 事件 / 操作日志 3 张流水表（与业务数据分库自治）。
     *
     * <p>日志库路径 {@code data/qqbot/{appid}/log/{appid}.log.mv.db}，由
     * {@code QqBotRepository.logJdbc(appId)} 惰性初始化。bot_id 逻辑关联平台库 qqbot_bot.id。
     */
    @Override
    public void initLogSchema(JdbcTemplate jdbc) {
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
                create_time BIGINT       NOT NULL,
                retracted   TINYINT      DEFAULT 0
            )
        """);
        try {
            jdbc.execute("ALTER TABLE qqbot_message ADD COLUMN IF NOT EXISTS retracted TINYINT DEFAULT 0");
        } catch (Exception ignored) { }
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

        // 管理操作日志（出站审计：禁言/撤回/审批等执行留痕，含失败与被本地校验拒绝）
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS qqbot_op_log (
                id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                bot_id        BIGINT        NOT NULL,
                op_type       VARCHAR(32)   NOT NULL,
                action        VARCHAR(16)   NOT NULL,
                group_id      VARCHAR(64),
                user_id       VARCHAR(64),
                target_msg_id VARCHAR(255),
                duration_sec  BIGINT,
                operator_id   VARCHAR(64),
                operator_name VARCHAR(128),
                operator_role VARCHAR(16),
                source        VARCHAR(16)   NOT NULL,
                status        VARCHAR(16)   NOT NULL,
                error_msg     VARCHAR(512),
                detail_json   CLOB,
                create_time   BIGINT        NOT NULL
            )
        """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_oplog_bo ON qqbot_op_log (bot_id, op_type, create_time)
        """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_qqbot_oplog_bg ON qqbot_op_log (bot_id, group_id, create_time)
        """);
    }
}
