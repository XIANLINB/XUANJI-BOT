package XuanJi.core.pipeline;

import XuanJi.api.adapter.XuanJiBot;
import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.event.XuanJiEventType;
import XuanJi.api.event.XuanJiGroup;
import XuanJi.api.event.XuanJiUser;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.pipeline.PipelineStage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 P1c：DedupStage 真正启用 xuanji_dedup 表做跨实例幂等去重。
 * 不连真实 bot / 不泄露密钥，用内存 H2。
 */
public class DedupStageTest {

    private JdbcTemplate newH2() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate j = new JdbcTemplate(ds);
        j.execute("CREATE TABLE xuanji_dedup (event_id VARCHAR(512) PRIMARY KEY, platform VARCHAR(16) NOT NULL, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        return j;
    }

    private XuanJiEvent evt(String eventId, String raw) {
        XuanJiBot bot = new XuanJiBot("qq:1001:1", "qq", "1001", XuanJiBot.Status.ONLINE, Set.of());
        return new XuanJiEvent(
                eventId,
                XuanJiEventType.MESSAGE_GROUP,
                bot,
                new XuanJiUser("2001", "2001", "U", null, 0, Instant.now()),
                new XuanJiGroup("3001", "", "3001", "", 0, Instant.now()),
                XuanJiMessage.builder().text("hi").build(),
                "42",
                Json.obj().put("k", raw),
                "message.group.normal",
                "PRODUCTION");
    }

    private static final PipelineStage.PipelineChain NOOP = () -> PipelineStage.Result.CONTINUE;

    @Test
    void sameEventIdIsDeduplicatedViaDb() {
        JdbcTemplate jdbc = newH2();
        DedupStage stage = new DedupStage(jdbc);

        // 首次：放行并落库
        PipelineStage.Result r1 = stage.handle(evt("evt-1", "a"), NOOP);
        assertEquals(PipelineStage.Result.CONTINUE, r1);
        int n1 = jdbc.queryForObject("SELECT COUNT(*) FROM xuanji_dedup WHERE event_id='evt-1'", Integer.class);
        assertEquals(1, n1, "事件 ID 应写入 xuanji_dedup");

        // 重复（同 eventId）：被跨实例去重拦截
        PipelineStage.Result r2 = stage.handle(evt("evt-1", "b"), NOOP);
        assertEquals(PipelineStage.Result.ABORT, r2, "重复事件应被去重拦截");
        int n2 = jdbc.queryForObject("SELECT COUNT(*) FROM xuanji_dedup WHERE event_id='evt-1'", Integer.class);
        assertEquals(1, n2, "去重表不应出现重复行");
    }

    @Test
    void differentEventIdsBothPass() {
        JdbcTemplate jdbc = newH2();
        DedupStage stage = new DedupStage(jdbc);
        assertEquals(PipelineStage.Result.CONTINUE, stage.handle(evt("evt-a", "x"), NOOP));
        assertEquals(PipelineStage.Result.CONTINUE, stage.handle(evt("evt-b", "y"), NOOP));
        int n = jdbc.queryForObject("SELECT COUNT(*) FROM xuanji_dedup", Integer.class);
        assertEquals(2, n);
    }

    /** 跨会话/重连重复收口：无稳定 eventId 时，同一发送者+同内容的消息应被内容键去重。 */
    @Test
    void noEventIdSameContentIsDeduplicated() {
        JdbcTemplate jdbc = newH2();
        DedupStage stage = new DedupStage(jdbc);
        // 两次投递 eventId 不同（重连重推典型场景），但内容相同 → 第二次应被去重
        XuanJiEvent first = evtNoId("g:3001:u:2001", "你好");
        XuanJiEvent second = evtNoId("g:3001:u:2001", "你好");
        assertEquals(PipelineStage.Result.CONTINUE, stage.handle(first, NOOP));
        assertEquals(PipelineStage.Result.ABORT, stage.handle(second, NOOP),
                "无 eventId 的同内容消息应被内容键去重拦截");
    }

    @Test
    void noEventIdDifferentContentBothPass() {
        JdbcTemplate jdbc = newH2();
        DedupStage stage = new DedupStage(jdbc);
        assertEquals(PipelineStage.Result.CONTINUE, stage.handle(evtNoId("g:3001:u:2001", "A"), NOOP));
        assertEquals(PipelineStage.Result.CONTINUE, stage.handle(evtNoId("g:3001:u:2001", "B"), NOOP));
    }

    private XuanJiEvent evtNoId(String scope, String text) {
        // scope 形如 "g:{groupId}:u:{userId}"，仅用于区分发送者范围
        String[] parts = scope.split(":");
        String groupId = parts[0].equals("g") ? parts[1] : null;
        String userId = parts[0].equals("g") ? parts[3] : parts[1];
        XuanJiBot bot = new XuanJiBot("qq:1001:1", "qq", "1001", XuanJiBot.Status.ONLINE, Set.of());
        XuanJiUser user = new XuanJiUser(userId, userId, "U", null, 0, Instant.now());
        XuanJiGroup group = groupId != null ? new XuanJiGroup(groupId, "", groupId, "", 0, Instant.now()) : null;
        return new XuanJiEvent(
                null, // 无 eventId
                XuanJiEventType.MESSAGE_GROUP,
                bot,
                user,
                group,
                XuanJiMessage.text(text),
                "42",
                Json.obj().put("k", "v"),
                "message.group.normal",
                "PRODUCTION");
    }
}
