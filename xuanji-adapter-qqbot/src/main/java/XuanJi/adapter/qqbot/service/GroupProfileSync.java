package XuanJi.adapter.qqbot.service;

import XuanJi.adapter.qqbot.storage.QqBotRepository;
import XuanJi.api.action.PlatformActionHub;
import XuanJi.api.action.PlatformActions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 群档案同步 — 通过统一动作协议 {@code group.info} 拉取群基本信息并写库。
 *
 * <p>被两个入口调用：
 * <ul>
 *   <li>{@code GroupSystemEventHandler.GROUP_ADD_ROBOT}：机器人入群事件触发</li>
 *   <li>{@code GroupMessageHandler.autoSyncGroup}：首次见到某群时拉一次（补存量群真实成员数）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupProfileSync {

    private final QqBotRepository repo;
    private final PlatformActionHub actionHub;

    /** 通过统一动作协议（group.info）拉取群信息同步官方字段到档案表。 */
    public void syncGroupInfo(String appId, String groupOpenid) {
        if (groupOpenid == null || groupOpenid.isBlank()) return;
        try {
            Map<String, Object> out = actionHub.dispatch(appId, PlatformActions.GROUP_INFO,
                    Map.of("groupOpenid", groupOpenid));
            Map<String, Object> d = out == null || !Boolean.TRUE.equals(out.get("ok"))
                    || !(out.get("data") instanceof Map<?, ?>) ? null : asMap(out.get("data"));
            if (d == null) {
                log.debug("[群信息同步] 接口未返回有效数据 group={}", groupOpenid);
                return;
            }
            String name = str(d, "group_name");
            String fingerMemo = str(d, "group_finger_memo");
            String classText = str(d, "group_class_text");
            String tags = d.get("group_tags") == null ? null : String.valueOf(d.get("group_tags"));
            Object memberNum = d.get("group_member_num");
            int memberCount = memberNum instanceof Number n ? n.intValue()
                    : (memberNum == null ? 0 : Integer.parseInt(String.valueOf(memberNum)));
            if (name == null && fingerMemo == null && classText == null && memberCount == 0) {
                log.debug("[群信息同步] 接口未返回有效数据 group={}", groupOpenid);
                return;
            }
            repo.upsertGroup(appId, groupOpenid, name, null,
                    memberCount > 0 ? memberCount : null, null, "active",
                    fingerMemo, classText, tags);
            // 加入时间兜底：群信息同步时若 join_time 仍为空则补写当前时间
            repo.ensureGroupJoinTime(appId, groupOpenid, System.currentTimeMillis() / 1000);
            log.info("[群信息同步] 已更新群档案: group={}, name={}, members={}", groupOpenid, name, memberCount);
        } catch (Exception e) {
            log.debug("[群信息同步] 失败 group={}: {}", groupOpenid, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}