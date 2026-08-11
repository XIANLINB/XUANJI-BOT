package XuanJi.llm.proactive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群活跃度跟踪器 —— 全量消息模式下记录每个群的最近活跃信息，供主动搭话判定冷场。
 *
 * <p>内存态（主动搭话本身低频，重启丢失可接受）：
 * <ul>
 *   <li>lastMsgAt：群内最后一条真人消息时间</li>
 *   <li>lastActiveUserId/nickname：最近活跃的成员（@ 搭话目标）</li>
 *   <li>todayMsgCount：今日群消息数（天切换自动重置）</li>
 * </ul>
 * 今日主动次数从 {@code xuanji_llm_proactive_log} 表统计（重启不丢）。
 */
@Slf4j
@Component
public class GroupActivityTracker {

    /** key：botKey:groupId */
    private final ConcurrentHashMap<String, GroupState> groups = new ConcurrentHashMap<>();

    /** 群消息入站（全量消息）时更新活跃度。 */
    public void onMessage(String botKey, String groupId, String userId, String nickname) {
        if (botKey == null || groupId == null) return;
        long now = System.currentTimeMillis() / 1000;
        String key = key(botKey, groupId);
        GroupState s = groups.computeIfAbsent(key, k -> new GroupState());
        s.lastMsgAt = now;
        s.today = LocalDate.now().toEpochDay();
        s.todayMsgCount++;
        if (userId != null && !userId.isBlank()) {
            s.lastActiveUserId = userId;
            s.lastActiveNickname = nickname == null || nickname.isBlank() ? "群友" : nickname;
        }
    }

    /** 群最后消息距今是否已超过 idleSeconds 秒（冷场）。 */
    public boolean isIdle(String botKey, String groupId, long idleSeconds) {
        GroupState s = groups.get(key(botKey, groupId));
        if (s == null) return false; // 没活跃过就不主动
        return (System.currentTimeMillis() / 1000 - s.lastMsgAt) >= idleSeconds;
    }

    /** 最近活跃成员（主动 @ 目标）；无人则 null。 */
    public Map.Entry<String, String> lastActiveUser(String botKey, String groupId) {
        GroupState s = groups.get(key(botKey, groupId));
        if (s == null || s.lastActiveUserId == null) return null;
        return Map.entry(s.lastActiveUserId, s.lastActiveNickname);
    }

    /** 今日是否有真人活跃过（避免对空群/僵尸群主动）。 */
    public boolean hasActivityToday(String botKey, String groupId) {
        GroupState s = groups.get(key(botKey, groupId));
        return s != null && s.today == LocalDate.now().toEpochDay() && s.todayMsgCount > 0;
    }

    /** 当前群今日消息数（工具查询用）。 */
    public long todayMsgCount(String botKey, String groupId) {
        GroupState s = groups.get(key(botKey, groupId));
        if (s == null || s.today != LocalDate.now().toEpochDay()) return 0;
        return s.todayMsgCount;
    }

    /** 全部活跃中的群（供扫描遍历）。 */
    public List<String[]> activeGroups() {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, GroupState> e : groups.entrySet()) {
            String[] parts = e.getKey().split(":", 2);
            if (parts.length == 2) out.add(parts);
        }
        return out;
    }

    private static String key(String botKey, String groupId) {
        return botKey + ":" + groupId;
    }

    private static class GroupState {
        long lastMsgAt;
        long today;
        long todayMsgCount;
        String lastActiveUserId;
        String lastActiveNickname;
    }
}
