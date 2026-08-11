package XuanJi.adapter.qqbot.service;

import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import XuanJi.core.sync.GroupStateSyncer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人群内状态同步 — 定时错峰刷新并持久化 {@code bot_state}。
 *
 * <p>应对 QQ 开放平台 <b>30 QPM</b> 限频：状态变更官方无事件通知，故按
 * "持久化缓存 + 定时错峰 + 读时兜底" 策略：
 * <ul>
 *   <li>{@link #sync}：事件驱动/手动即时同步单群（入群事件、控制台手动）</li>
 *   <li>{@link #syncDue}：每周期挑"最久未同步"的 N 个错峰同步（默认间隔 6h，角色未知 30min）</li>
 *   <li>429 熔断：单 bot 冷却 10 分钟；单对最小间隔 5 分钟</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRobotStateSyncService implements GroupStateSyncer {

    private final QqBotRepository repo;
    private final PlatformActionHub hub;

    /** 默认同步间隔：已知角色 6 小时。 */
    private static final long SYNC_INTERVAL = 6 * 3600;
    /** 角色未知时缩短为 30 分钟（尽快补全角色）。 */
    private static final long UNKNOWN_ROLE_INTERVAL = 30 * 60;
    /** 单 (bot, group) 最小同步间隔 5 分钟（防单群高频）。 */
    private static final long MIN_INTERVAL = 5 * 60;
    /** 429 熔断冷却 10 分钟。 */
    private static final long COOLDOWN_MS = 10 * 60 * 1000;

    /** botKey → 429 冷却截止毫秒。 */
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();

    @Override
    public boolean sync(String appId, String groupOpenid) {
        if (appId == null || appId.isBlank() || groupOpenid == null || groupOpenid.isBlank()) {
            return false;
        }
        Long cooldown = cooldownUntil.get(appId);
        if (cooldown != null && cooldown > System.currentTimeMillis()) {
            return false; // 该 bot 处于 429 冷却期
        }
        try {
            Map<String, Object> out = hub.dispatch(appId, PlatformActions.GROUP_BOT_STATE,
                    Map.of("groupOpenid", groupOpenid));
            if (out == null || !Boolean.TRUE.equals(out.get("ok"))
                    || !(out.get("data") instanceof Map<?, ?>)) {
                String err = out == null ? "" : String.valueOf(out.get("error"));
                if (err.contains("429") || err.contains("频率")) {
                    cooldownUntil.put(appId, System.currentTimeMillis() + COOLDOWN_MS);
                }
                return false;
            }
            Map<String, Object> d = asMap(out.get("data"));
            repo.upsertGroupRobotState(appId, groupOpenid,
                    str(d, "member_openid"), str(d, "member_role"),
                    boolOrNull(d.get("allow_proactive_msg")), str(d, "recv_msg_setting"),
                    parseJoinedAt(d.get("joined_at")));
            return true;
        } catch (Exception e) {
            log.debug("[群状态] 同步失败 appId={} group={}: {}", appId, groupOpenid, e.getMessage());
            return false;
        }
    }

    @Override
    public int syncDue(int limit) {
        List<Map<String, Object>> pairs = repo.listGroupRobotPairs();
        long now = System.currentTimeMillis() / 1000;
        int done = 0;
        for (Map<String, Object> p : pairs) {
            if (done >= limit) break;
            String appId = String.valueOf(p.get("APPID"));
            String groupId = String.valueOf(p.get("group_id"));
            Object updatedAt = p.get("updated_at");
            long last = updatedAt instanceof Number n ? n.longValue() : 0;
            long interval = p.get("member_role") == null ? UNKNOWN_ROLE_INTERVAL : SYNC_INTERVAL;
            if (last > 0 && now - last < Math.max(interval, MIN_INTERVAL)) {
                continue; // 未到同步间隔
            }
            if (sync(appId, groupId)) {
                done++;
            }
        }
        return done;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean boolOrNull(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    /** "2026-07-31T22:20:16+08:00" → epoch 秒；解析失败返回 null。 */
    private static Long parseJoinedAt(Object v) {
        if (v == null) return null;
        try {
            return OffsetDateTime.parse(String.valueOf(v), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toEpochSecond();
        } catch (Exception e) {
            try {
                return Long.parseLong(String.valueOf(v).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
