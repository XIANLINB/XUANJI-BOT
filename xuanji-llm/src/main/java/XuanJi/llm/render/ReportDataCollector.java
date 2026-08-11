package XuanJi.llm.render;

import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.proactive.GroupActivityTracker;
import XuanJi.llm.profile.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板数据收集器 —— 把群里真实数据组装成模板渲染所需的 JSON。
 *
 * <p>新增模板类型时在此扩展收集逻辑（或由调用方直接传 {@code data} 给
 * {@link HtmlRenderService#render}）。当前提供群日报卡片数据。
 */
@Slf4j
@Service
public class ReportDataCollector {

    private final GroupActivityTracker tracker;
    private final UserProfileService profileService;
    private final MemoryService memoryService;

    public ReportDataCollector(GroupActivityTracker tracker,
                               UserProfileService profileService,
                               MemoryService memoryService) {
        this.tracker = tracker;
        this.profileService = profileService;
        this.memoryService = memoryService;
    }

    /**
     * 收集「群日报卡片」模板数据。
     *
     * @param botKey    机器人
     * @param groupId   群
     * @param groupName 群名（展示用，可为空）
     * @param summary   AI 总结文本（由调用方生成，避免循环依赖）
     * @return 模板数据 Map：groupName/date/msgCount/activeUserCount/activeUsers/summary/memSummary
     */
    public Map<String, Object> collectDailyReport(String botKey, String groupId,
                                                  String groupName, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupName", groupName == null || groupName.isBlank() ? groupId : groupName);
        data.put("groupId", groupId);
        data.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")));
        data.put("weekday", weekdayCn(LocalDate.now().getDayOfWeek().getValue()));

        long msgCount = tracker.todayMsgCount(botKey, groupId);
        data.put("msgCount", msgCount);

        List<Map<String, Object>> profiles = profileService.list(botKey, groupId);
        // 活跃用户：按 msg_count 降序取前 6 个
        List<Map<String, Object>> sorted = new ArrayList<>(profiles);
        sorted.sort((a, b) -> Long.compare(
                a.get("msgCount") instanceof Number n ? n.longValue() : 0L,
                b.get("msgCount") instanceof Number n ? n.longValue() : 0L));
        List<Map<String, Object>> top = sorted.stream().limit(6).toList();
        data.put("activeUserCount", profiles.size());
        List<Map<String, Object>> users = new ArrayList<>();
        for (Map<String, Object> p : top) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("nickname", p.get("nickname") == null ? "群友" : String.valueOf(p.get("nickname")));
            u.put("msgCount", p.get("msgCount") instanceof Number n ? n.longValue() : 0L);
            users.add(u);
        }
        data.put("activeUsers", users);

        String memSummary = memoryService.summary(botKey, groupId, null);
        data.put("memSummary", memSummary == null ? "" : memSummary);

        // AI 总结（由调用方生成）
        if (summary == null || summary.isBlank()) {
            summary = "今天群里聊了 " + msgCount + " 条消息，大家都很活跃，继续保持～";
        }
        data.put("summary", summary);

        data.put("generatedAt", java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        return data;
    }

    private static String weekdayCn(int w) {
        return switch (w) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            default -> "星期日";
        };
    }
}
