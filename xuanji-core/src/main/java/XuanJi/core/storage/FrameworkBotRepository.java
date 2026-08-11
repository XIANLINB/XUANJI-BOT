package XuanJi.core.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 框架库机器人注册表 — 表 {@code xuanji_bot}（platform / instance_id / bot_key / status）。
 *
 * <p>各适配器注册/注销/启停实例、控制台跨平台聚合均以此表为准。
 */
@Repository
@RequiredArgsConstructor
public class FrameworkBotRepository {

    private final JdbcTemplate jdbc;

    /** 注册或更新实例（幂等）。status 如 ONLINE / OFFLINE。 */
    public void upsert(String platform, String instanceId, String botKey, String status) {
        try {
            jdbc.update("""
                MERGE INTO xuanji_bot (platform, instance_id, bot_key, status)
                KEY (platform, instance_id) VALUES (?, ?, ?, ?)
            """, platform, instanceId, botKey, status);
        } catch (Exception e) {
            throw new IllegalStateException("框架库 xuanji_bot 注册失败: " + e.getMessage(), e);
        }
    }

    /** 更新实例在线状态。 */
    public void setStatus(String platform, String instanceId, String status) {
        jdbc.update("UPDATE xuanji_bot SET status=? WHERE platform=? AND instance_id=?",
                status, platform, instanceId);
    }

    /** 删除实例注册。 */
    public void delete(String platform, String instanceId) {
        jdbc.update("DELETE FROM xuanji_bot WHERE platform=? AND instance_id=?", platform, instanceId);
    }

    /** 全部已注册实例 id（跨平台）。 */
    public List<String> allInstanceIds() {
        try {
            return jdbc.queryForList("SELECT instance_id FROM xuanji_bot", String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
