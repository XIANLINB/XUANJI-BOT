package dev.xuanji.adapter.onebot.event.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.converter.OneBotEventTypes;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.annotation.GroupMessage;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.storage.log.MessageLogger;
import dev.xuanji.sdk.event.GroupMessageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 P1b：OneBot 适配器补齐自己的群 / 成员 / 流水持久化。
 *
 * <p>不连真实 bot、不泄露密钥：用内存 H2 建与线上同构的 xuanji_onebot_* 表，
 * 直接驱动 OneBotMessageHandler.handle() 与 OneBotSessionRegistry.register()，
 * 断言数据落到 OneBot 专属表，而非 QQ 表。
 */
public class OneBotPersistenceTest {

    String captured = null;

    @GroupMessage
    public void onMsg(GroupMessageEvent e) {
        captured = e.getPlainText();
    }

    private JdbcTemplate newH2() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate j = new JdbcTemplate(ds);
        j.execute("CREATE TABLE xuanji_bot (id BIGINT AUTO_INCREMENT PRIMARY KEY, platform VARCHAR(16) NOT NULL, bot_identifier VARCHAR(64) NOT NULL, bot_key VARCHAR(64), status VARCHAR(16) DEFAULT 'OFFLINE', create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        j.execute("CREATE TABLE xuanji_onebot_group (id BIGINT AUTO_INCREMENT PRIMARY KEY, bot_id VARCHAR(64) NOT NULL, group_id VARCHAR(128) NOT NULL, group_name VARCHAR(256), status VARCHAR(16) DEFAULT 'active', is_deleted TINYINT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        j.execute("CREATE TABLE xuanji_onebot_group_member (id BIGINT AUTO_INCREMENT PRIMARY KEY, bot_id VARCHAR(64) NOT NULL, group_id VARCHAR(128) NOT NULL, member_id VARCHAR(128) NOT NULL, role VARCHAR(32), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        j.execute("CREATE TABLE xuanji_onebot_group_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, direction VARCHAR(8) NOT NULL, bot_id VARCHAR(64) NOT NULL, group_id VARCHAR(128), member_id VARCHAR(128), msg_type VARCHAR(32), content TEXT, raw_json TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        j.execute("CREATE TABLE xuanji_onebot_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, direction VARCHAR(8) NOT NULL, bot_id VARCHAR(64) NOT NULL, event_type VARCHAR(64), group_id VARCHAR(128), raw_json TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        // QQ 表也建一份（空），用于验证 OneBot 数据不污染 QQ 表
        j.execute("CREATE TABLE xuanji_qqbot_group (id BIGINT AUTO_INCREMENT PRIMARY KEY, bot_id VARCHAR(64) NOT NULL, group_id VARCHAR(128) NOT NULL, group_name VARCHAR(256), status VARCHAR(16) DEFAULT 'active', is_deleted TINYINT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        j.execute("CREATE TABLE xuanji_qqbot_group_member (id BIGINT AUTO_INCREMENT PRIMARY KEY, bot_id VARCHAR(64) NOT NULL, group_id VARCHAR(128) NOT NULL, member_id VARCHAR(128) NOT NULL, role VARCHAR(32), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        return j;
    }

    @Test
    void groupMessageSyncsGroupAndMemberToOwnTables() throws Exception {
        JdbcTemplate jdbc = newH2();
        // 把 MessageLogger 的静态日志库指向同一内存库，验证流水落库
        Field f = MessageLogger.class.getDeclaredField("logJdbc");
        f.setAccessible(true);
        f.set(null, jdbc);

        OneBotSessionRegistry registry = new OneBotSessionRegistry(null);
        OneBotProperties props = new OneBotProperties();
        props.setApiTimeoutMs(500);
        OneBotApiService api = new OneBotApiService(registry, props);
        OneBotMessageSenderImpl sender = new OneBotMessageSenderImpl(api);
        CommandRegistry cr = new CommandRegistry();
        cr.register(this, "test");
        OneBotMessageHandler handler = new OneBotMessageHandler(cr, api, sender, jdbc);

        ObjectNode data = Json.obj();
        data.set("sender", Json.obj().put("role", "member"));
        Bot bot = new Bot("onebot:99999:42", "onebot", "99999", Bot.Status.ONLINE, Set.of());
        BotEvent evt = new BotEvent(
                "onebot:99999:42",
                OneBotEventTypes.MESSAGE_GROUP,
                bot,
                new XuanjiUser("20002", "20002", "Tester", null, 0, Instant.now()),
                new XuanjiGroup("30003", "", "30003", "", 0, Instant.now()),
                MessageChain.builder().text("你好").build(),
                "42",
                data,
                "message.group.normal",
                "PRODUCTION");

        handler.handle(evt);

        int g = jdbc.queryForObject(
                "SELECT COUNT(*) FROM xuanji_onebot_group WHERE bot_id='99999' AND group_id='30003'", Integer.class);
        assertEquals(1, g, "OneBot 应有自己的群档案（xuanji_onebot_group）");

        int m = jdbc.queryForObject(
                "SELECT COUNT(*) FROM xuanji_onebot_group_member WHERE bot_id='99999' AND group_id='30003' AND member_id='20002'", Integer.class);
        assertEquals(1, m, "OneBot 应有自己的群成员档案（xuanji_onebot_group_member）");

        String role = jdbc.queryForObject(
                "SELECT role FROM xuanji_onebot_group_member WHERE bot_id='99999' AND member_id='20002'", String.class);
        assertEquals("member", role, "成员角色应从报文中透传");

        int msg = jdbc.queryForObject(
                "SELECT COUNT(*) FROM xuanji_onebot_group_message WHERE bot_id='99999' AND group_id='30003'", Integer.class);
        assertEquals(1, msg, "群消息流水应落 xuanji_onebot_group_message");

        // 关键不变量：数据不应落到 QQ 表
        int qqGroups = jdbc.queryForObject("SELECT COUNT(*) FROM xuanji_qqbot_group", Integer.class);
        assertEquals(0, qqGroups, "OneBot 数据不得污染 QQ 表");

        assertNotNull(captured, "@GroupMessage 插件方法应被调用");
    }

    @Test
    void sessionRegistryRegistersOnebotInstanceInXuanjiBot() {
        JdbcTemplate jdbc = newH2();
        OneBotSessionRegistry reg = new OneBotSessionRegistry(jdbc);
        OneBotSession session = new OneBotSession() {
            public String selfId() { return "88888"; }
            public String direction() { return "reverse"; }
            public boolean isOpen() { return true; }
            public void sendText(String t) {}
            public void close() {}
        };
        reg.register(session);
        int n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM xuanji_bot WHERE platform='onebot' AND bot_identifier='88888' AND status='ONLINE'", Integer.class);
        assertEquals(1, n, "xuanji_bot 应出现 onebot 实例行，控制台 Bot 列表才能看见");

        reg.unregister(session);
        String status = jdbc.queryForObject(
                "SELECT status FROM xuanji_bot WHERE platform='onebot' AND bot_identifier='88888'", String.class);
        assertEquals("OFFLINE", status, "注销后状态应置 OFFLINE");
    }
}
