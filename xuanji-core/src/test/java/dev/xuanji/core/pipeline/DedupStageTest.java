package dev.xuanji.core.pipeline;

import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.event.EventType;
import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.pipeline.PipelineStage;
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

    private BotEvent evt(String eventId, String raw) {
        Bot bot = new Bot("qq:1001:1", "qq", "1001", Bot.Status.ONLINE, Set.of());
        return new BotEvent(
                eventId,
                EventType.MESSAGE_GROUP,
                bot,
                new XuanjiUser("2001", "2001", "U", null, 0, Instant.now()),
                new XuanjiGroup("3001", "", "3001", "", 0, Instant.now()),
                MessageChain.builder().text("hi").build(),
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
}
